package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trade.common.*;
import com.campus.trade.common.Constants.AuditResult;
import com.campus.trade.common.Constants.AuthStatus;
import com.campus.trade.common.Constants.GoodsStatus;
import com.campus.trade.dto.AdminGoodsQueryDTO;
import com.campus.trade.dto.AdminUserQueryDTO;
import com.campus.trade.dto.AuditDTO;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.StudentAuth;
import com.campus.trade.entity.SysUser;
import com.campus.trade.entity.TradeOrder;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.StudentAuthMapper;
import com.campus.trade.mapper.SysUserMapper;
import com.campus.trade.mapper.TradeOrderMapper;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.vo.AdminUserVO;
import com.campus.trade.vo.GoodsVO;
import com.campus.trade.vo.StatsVO;
import com.campus.trade.vo.StudentAuthVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 后台管理：商品审核与上下架、用户封禁、学生认证审核、统计。 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final GoodsMapper goodsMapper;
    private final SysUserMapper userMapper;
    private final StudentAuthMapper studentAuthMapper;
    private final TradeOrderMapper orderMapper;
    private final GoodsService goodsService;
    private final CategoryService categoryService;
    private final AuthService authService;

    // ------------------------------------------------------------------ 商品

    /** 待审核商品（先提交先审），返回完整 GoodsVO（含 description、images）。 */
    public PageResult<GoodsVO> auditList(PageQuery pq) {
        Page<Goods> page = goodsMapper.selectPage(pq.toPage(), new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, GoodsStatus.PENDING_AUDIT)
                .orderByAsc(Goods::getUpdateTime).orderByAsc(Goods::getId));
        List<GoodsVO> vos = adminVOs(page.getRecords());
        for (int i = 0; i < vos.size(); i++) {
            vos.get(i).setDescription(page.getRecords().get(i).getDescription());
            vos.get(i).setImages(goodsService.imagesOf(page.getRecords().get(i).getId()));
        }
        return PageResult.of(page, vos);
    }

    /** 审核：APPROVE→ON_SHELF；REJECT→REJECTED（reason 必填）。仅 PENDING_AUDIT 可审（条件更新）。 */
    public GoodsVO audit(Long goodsId, AuditDTO dto) {
        Long admin = SecurityUtils.requireUserId();
        boolean approve = AuditResult.APPROVE.equals(dto.getResult());
        String reason = StringUtils.hasText(dto.getReason()) ? dto.getReason().trim() : null;
        if (!approve && reason == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "驳回时必须填写原因");
        }
        if (goodsMapper.selectById(goodsId) == null) {
            throw new BizException(ErrorCode.GOODS_NOT_FOUND);
        }
        int n = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId).eq(Goods::getStatus, GoodsStatus.PENDING_AUDIT)
                .set(Goods::getStatus, approve ? GoodsStatus.ON_SHELF : GoodsStatus.REJECTED)
                .set(Goods::getAuditReason, reason)
                .set(Goods::getAuditorId, admin)
                .set(Goods::getAuditTime, LocalDateTime.now()));
        if (n == 0) {
            throw new BizException(ErrorCode.GOODS_NOT_PENDING_AUDIT);
        }
        return adminVOs(List.of(goodsMapper.selectById(goodsId))).get(0);
    }

    /** 全部商品（任意状态，不含已删除），可按 status/keyword/categoryId/sellerId 过滤。 */
    public PageResult<GoodsVO> goodsList(AdminGoodsQueryDTO q) {
        LambdaQueryWrapper<Goods> w = new LambdaQueryWrapper<Goods>()
                .eq(StringUtils.hasText(q.getStatus()), Goods::getStatus, q.getStatus())
                .eq(q.getSellerId() != null, Goods::getSellerId, q.getSellerId());
        if (q.getCategoryId() != null) {
            w.in(Goods::getCategoryId, categoryService.selfAndDescendantIds(q.getCategoryId()));
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = escapeLike(q.getKeyword().trim());
            w.and(x -> x.like(Goods::getTitle, kw).or().like(Goods::getDescription, kw));
        }
        w.orderByDesc(Goods::getCreateTime).orderByDesc(Goods::getId);
        Page<Goods> page = goodsMapper.selectPage(q.toPage(), w);
        return PageResult.of(page, adminVOs(page.getRecords()));
    }

    /** 管理员上/下架：仅 ON_SHELF ↔ OFF_SHELF（已预订/已售/待审/驳回不可改，20005）。 */
    public GoodsVO goodsStatus(Long goodsId, String target) {
        if (!GoodsStatus.ON_SHELF.equals(target) && !GoodsStatus.OFF_SHELF.equals(target)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "status 取值为 ON_SHELF/OFF_SHELF");
        }
        Goods g = goodsMapper.selectById(goodsId);
        if (g == null) {
            throw new BizException(ErrorCode.GOODS_NOT_FOUND);
        }
        if (!target.equals(g.getStatus())) {
            String from = GoodsStatus.ON_SHELF.equals(target) ? GoodsStatus.OFF_SHELF : GoodsStatus.ON_SHELF;
            if (goodsMapper.casStatus(goodsId, from, target) == 0) {
                throw new BizException(ErrorCode.GOODS_STATUS_NOT_ALLOWED,
                        "当前状态 " + g.getStatus() + " 不能变更为 " + target);
            }
        }
        return adminVOs(List.of(goodsMapper.selectById(goodsId))).get(0);
    }

    private List<GoodsVO> adminVOs(List<Goods> list) {
        List<GoodsVO> vos = goodsService.toListVOs(list, null);
        for (int i = 0; i < vos.size(); i++) {
            vos.get(i).setAuditReason(list.get(i).getAuditReason());
        }
        return vos;
    }

    // ------------------------------------------------------------------ 用户

    public PageResult<AdminUserVO> userList(AdminUserQueryDTO q) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<SysUser>()
                .eq(StringUtils.hasText(q.getStatus()), SysUser::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getRole()), SysUser::getRole, q.getRole());
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = escapeLike(q.getKeyword().trim());
            w.and(x -> x.like(SysUser::getUsername, kw).or().like(SysUser::getNickname, kw)
                    .or().like(SysUser::getPhone, kw).or().like(SysUser::getStudentNo, kw));
        }
        w.orderByDesc(SysUser::getCreateTime).orderByDesc(SysUser::getId);
        Page<SysUser> page = userMapper.selectPage(q.toPage(), w);
        return PageResult.of(page, AdminService::toAdminUser);
    }

    /** 封禁/解封：不能操作自己（60002）、不能封禁管理员（60003）；封禁时作废其全部登录态。 */
    public AdminUserVO userStatus(Long userId, String status) {
        if (!Constants.UserStatus.NORMAL.equals(status) && !Constants.UserStatus.BANNED.equals(status)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "status 取值为 NORMAL/BANNED");
        }
        Long admin = SecurityUtils.requireUserId();
        if (admin.equals(userId)) {
            throw new BizException(ErrorCode.ADMIN_SELF_OPERATION);
        }
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (Constants.Role.ADMIN.equals(u.getRole()) && Constants.UserStatus.BANNED.equals(status)) {
            throw new BizException(ErrorCode.ADMIN_TARGET_IS_ADMIN);
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, userId)
                .set(SysUser::getStatus, status));
        if (Constants.UserStatus.BANNED.equals(status)) {
            authService.revokeAllSessions(userId);
        } else {
            authService.clearBanMark(userId);
        }
        return toAdminUser(userMapper.selectById(userId));
    }

    private static AdminUserVO toAdminUser(SysUser u) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setPhone(u.getPhone());
        vo.setStudentNo(u.getStudentNo());
        vo.setSchool(u.getSchool());
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        vo.setCreditScore(u.getCreditScore());
        vo.setStudentAuthStatus(u.getStudentAuthStatus());
        vo.setCreateTime(u.getCreateTime());
        vo.setLastLoginTime(u.getLastLoginTime());
        return vo;
    }

    // ------------------------------------------------------------------ 学生认证

    public PageResult<StudentAuthVO> verifyList(String status, PageQuery pq) {
        Page<StudentAuth> page = studentAuthMapper.selectPage(pq.toPage(), new LambdaQueryWrapper<StudentAuth>()
                .eq(StringUtils.hasText(status), StudentAuth::getStatus, status)
                .orderByDesc(StudentAuth::getUpdateTime).orderByDesc(StudentAuth::getId));
        Set<Long> uids = page.getRecords().stream().map(StudentAuth::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = uids.isEmpty() ? Map.of() : userMapper.selectBatchIds(uids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return PageResult.of(page, a -> toVerifyVO(a, users.get(a.getUserId())));
    }

    /** 审核认证：APPROVE 回写 student_no/school 与 APPROVED；REJECT 需 reason。仅 PENDING 可审（60004）。 */
    @Transactional(rollbackFor = Exception.class)
    public StudentAuthVO verify(Long id, AuditDTO dto) {
        Long admin = SecurityUtils.requireUserId();
        StudentAuth a = studentAuthMapper.selectById(id);
        if (a == null) {
            throw new BizException(ErrorCode.VERIFY_NOT_FOUND);
        }
        boolean approve = AuditResult.APPROVE.equals(dto.getResult());
        String reason = StringUtils.hasText(dto.getReason()) ? dto.getReason().trim() : null;
        if (!approve && reason == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "驳回时必须填写原因");
        }
        if (approve && userMapper.exists(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStudentNo, a.getStudentNo()).ne(SysUser::getId, a.getUserId()))) {
            throw new BizException(ErrorCode.STUDENT_NO_EXISTS);
        }
        int n = studentAuthMapper.update(null, new LambdaUpdateWrapper<StudentAuth>()
                .eq(StudentAuth::getId, id).eq(StudentAuth::getStatus, AuthStatus.PENDING)
                .set(StudentAuth::getStatus, approve ? AuthStatus.APPROVED : AuthStatus.REJECTED)
                .set(StudentAuth::getReason, reason)
                .set(StudentAuth::getAuditorId, admin)
                .set(StudentAuth::getAuditTime, LocalDateTime.now()));
        if (n == 0) {
            throw new BizException(ErrorCode.VERIFY_ALREADY_HANDLED);
        }
        LambdaUpdateWrapper<SysUser> uw = new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, a.getUserId())
                .set(SysUser::getStudentAuthStatus, approve ? AuthStatus.APPROVED : AuthStatus.REJECTED);
        if (approve) {
            uw.set(SysUser::getStudentNo, a.getStudentNo()).set(SysUser::getSchool, a.getSchool());
        }
        try {
            userMapper.update(null, uw);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.STUDENT_NO_EXISTS);
        }
        return toVerifyVO(studentAuthMapper.selectById(id), userMapper.selectById(a.getUserId()));
    }

    private static StudentAuthVO toVerifyVO(StudentAuth a, SysUser u) {
        StudentAuthVO vo = new StudentAuthVO();
        vo.setId(a.getId());
        vo.setUserId(a.getUserId());
        if (u != null) {
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
        }
        vo.setStudentNo(a.getStudentNo());
        vo.setRealName(a.getRealName());
        vo.setSchool(a.getSchool());
        vo.setCardImageUrl(a.getCardImageUrl());
        vo.setStatus(a.getStatus());
        vo.setReason(a.getReason());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }

    // ------------------------------------------------------------------ 统计

    /** 后台统计；days 仅 7 或 30（控制器已校验）。 */
    public StatsVO stats(int days) {
        StatsVO s = new StatsVO();
        s.setUserCount(userMapper.selectCount(null));
        s.setGoodsCount(goodsMapper.selectCount(null)); // @TableLogic：自动排除已删除
        s.setOrderCount(orderMapper.selectCount(null));
        s.setGmv(orderMapper.sumCompletedPrice());
        s.setPendingGoodsAudit(goodsMapper.selectCount(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, GoodsStatus.PENDING_AUDIT)));
        s.setPendingStudentVerify(studentAuthMapper.selectCount(new LambdaQueryWrapper<StudentAuth>()
                .eq(StudentAuth::getStatus, AuthStatus.PENDING)));

        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(days - 1L).atStartOfDay();
        Map<String, Long> users = byDay(userMapper.countByDay(since));
        Map<String, Long> goods = byDay(goodsMapper.countByDay(since));
        Map<String, Long> orders = byDay(orderMapper.countByDay(since));
        List<StatsVO.TrendItem> trend = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            String d = today.minusDays(i).toString();
            trend.add(new StatsVO.TrendItem(d, users.getOrDefault(d, 0L),
                    goods.getOrDefault(d, 0L), orders.getOrDefault(d, 0L)));
        }
        s.setTrend(trend);

        List<Map<String, Object>> rows = goodsMapper.countByTopCategory();
        Map<Long, String> names = categoryService.namesByIds(rows.stream()
                .map(r -> ((Number) r.get("cid")).longValue()).collect(Collectors.toSet()));
        s.setCategoryShares(rows.stream()
                .map(r -> {
                    long cid = ((Number) r.get("cid")).longValue();
                    return new StatsVO.CategoryShare(cid, names.get(cid), ((Number) r.get("c")).longValue());
                })
                .sorted(Comparator.comparingLong(StatsVO.CategoryShare::getCount).reversed()
                        .thenComparing(StatsVO.CategoryShare::getCategoryId))
                .toList());

        s.setHotGoods(goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                        .orderByDesc(Goods::getViewCount).orderByDesc(Goods::getId).last("LIMIT 10"))
                .stream().map(g -> {
                    StatsVO.HotGoods h = new StatsVO.HotGoods();
                    h.setId(g.getId());
                    h.setTitle(g.getTitle());
                    h.setCoverUrl(g.getCoverUrl());
                    h.setPrice(g.getPrice());
                    h.setViewCount(g.getViewCount());
                    h.setFavoriteCount(g.getFavoriteCount());
                    return h;
                }).toList());
        return s;
    }

    private static Map<String, Long> byDay(List<Map<String, Object>> rows) {
        Map<String, Long> m = new HashMap<>();
        for (Map<String, Object> r : rows) {
            m.put(String.valueOf(r.get("d")), ((Number) r.get("c")).longValue());
        }
        return m;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
