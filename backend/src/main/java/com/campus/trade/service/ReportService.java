package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trade.common.*;
import com.campus.trade.common.Constants.ReportStatus;
import com.campus.trade.dto.ReportCreateDTO;
import com.campus.trade.dto.ReportHandleDTO;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Report;
import com.campus.trade.entity.SysUser;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.ReportMapper;
import com.campus.trade.mapper.SysUserMapper;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.vo.ReportVO;
import com.campus.trade.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 举报：用户提交，管理员处理（只记录结果，不自动下架/封禁，由管理员另行操作）。 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final GoodsMapper goodsMapper;
    private final SysUserMapper userMapper;

    public ReportVO create(ReportCreateDTO dto) {
        Long uid = SecurityUtils.requireUserId();
        if ("GOODS".equals(dto.getTargetType())) {
            Goods g = goodsMapper.selectById(dto.getTargetId());
            if (g == null) {
                throw new BizException(ErrorCode.GOODS_NOT_FOUND);
            }
            if (uid.equals(g.getSellerId())) {
                throw new BizException(ErrorCode.REPORT_SELF);
            }
        } else {
            if (uid.equals(dto.getTargetId())) {
                throw new BizException(ErrorCode.REPORT_SELF);
            }
            if (userMapper.selectById(dto.getTargetId()) == null) {
                throw new BizException(ErrorCode.USER_NOT_FOUND);
            }
        }
        if (reportMapper.exists(new LambdaQueryWrapper<Report>().eq(Report::getReporterId, uid)
                .eq(Report::getTargetType, dto.getTargetType()).eq(Report::getTargetId, dto.getTargetId())
                .eq(Report::getStatus, ReportStatus.PENDING))) {
            throw new BizException(ErrorCode.REPORT_DUPLICATE);
        }
        Report r = new Report();
        r.setReporterId(uid);
        r.setTargetType(dto.getTargetType());
        r.setTargetId(dto.getTargetId());
        r.setReason(dto.getReason().trim());
        r.setDetail(StringUtils.hasText(dto.getDetail()) ? dto.getDetail().trim() : null);
        r.setStatus(ReportStatus.PENDING);
        reportMapper.insert(r);
        return toVOs(List.of(reportMapper.selectById(r.getId()))).get(0);
    }

    public PageResult<ReportVO> page(String status, PageQuery pq) {
        Page<Report> page = reportMapper.selectPage(pq.toPage(), new LambdaQueryWrapper<Report>()
                .eq(StringUtils.hasText(status), Report::getStatus, status)
                .orderByDesc(Report::getCreateTime).orderByDesc(Report::getId));
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /** 处理：APPROVE→APPROVED，REJECT→REJECTED；仅 PENDING 可处理（60007）。 */
    public ReportVO handle(Long id, ReportHandleDTO dto) {
        Long admin = SecurityUtils.requireUserId();
        if (reportMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }
        boolean approve = Constants.AuditResult.APPROVE.equals(dto.getResult());
        int n = reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, id).eq(Report::getStatus, ReportStatus.PENDING)
                .set(Report::getStatus, approve ? ReportStatus.APPROVED : ReportStatus.REJECTED)
                .set(Report::getResult, dto.getResult())
                .set(Report::getRemark, StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : null)
                .set(Report::getAuditorId, admin)
                .set(Report::getAuditTime, LocalDateTime.now()));
        if (n == 0) {
            throw new BizException(ErrorCode.REPORT_ALREADY_HANDLED);
        }
        return toVOs(List.of(reportMapper.selectById(id))).get(0);
    }

    private List<ReportVO> toVOs(List<Report> list) {
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = new HashSet<>();
        Set<Long> goodsIds = new HashSet<>();
        for (Report r : list) {
            userIds.add(r.getReporterId());
            if ("USER".equals(r.getTargetType())) {
                userIds.add(r.getTargetId());
            } else {
                goodsIds.add(r.getTargetId());
            }
        }
        Map<Long, SysUser> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Goods> goods = goodsIds.isEmpty() ? Map.of() : goodsMapper.selectByIdsIncludingDeleted(goodsIds)
                .stream().collect(Collectors.toMap(Goods::getId, Function.identity()));
        return list.stream().map(r -> {
            ReportVO vo = new ReportVO();
            vo.setId(r.getId());
            vo.setReporter(UserBriefVO.of(users.get(r.getReporterId())));
            vo.setTargetType(r.getTargetType());
            vo.setTargetId(r.getTargetId());
            if ("USER".equals(r.getTargetType())) {
                SysUser t = users.get(r.getTargetId());
                vo.setTargetName(t == null ? null : t.getNickname());
            } else {
                Goods g = goods.get(r.getTargetId());
                vo.setTargetName(g == null ? null : g.getTitle());
            }
            vo.setReason(r.getReason());
            vo.setDetail(r.getDetail());
            vo.setStatus(r.getStatus());
            vo.setResult(r.getResult());
            vo.setRemark(r.getRemark());
            vo.setAuditorId(r.getAuditorId());
            vo.setAuditTime(r.getAuditTime());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).toList();
    }
}
