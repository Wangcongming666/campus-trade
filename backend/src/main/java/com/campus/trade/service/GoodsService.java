package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trade.common.*;
import com.campus.trade.common.Constants.GoodsStatus;
import com.campus.trade.dto.GoodsFilterDTO;
import com.campus.trade.dto.GoodsSaveDTO;
import com.campus.trade.entity.Favorite;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.GoodsImage;
import com.campus.trade.entity.SysUser;
import com.campus.trade.mapper.FavoriteMapper;
import com.campus.trade.mapper.GoodsImageMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.SysUserMapper;
import com.campus.trade.security.LoginUser;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.vo.GoodsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsService {

    /** 游客/他人可见的状态（详情页仍展示已预订、已售出） */
    private static final Set<String> PUBLIC_STATUSES = Set.of(GoodsStatus.ON_SHELF, GoodsStatus.RESERVED, GoodsStatus.SOLD);
    /** 卖家可编辑/删除的状态（进行中的交易 RESERVED、已售 SOLD 不可改） */
    private static final Set<String> EDITABLE_STATUSES = Set.of(GoodsStatus.PENDING_AUDIT, GoodsStatus.ON_SHELF,
            GoodsStatus.OFF_SHELF, GoodsStatus.REJECTED);

    private final GoodsMapper goodsMapper;
    private final GoodsImageMapper imageMapper;
    private final SysUserMapper userMapper;
    private final FavoriteMapper favoriteMapper;
    private final CategoryService categoryService;
    private final UserBehaviorService behaviorService;

    // ------------------------------------------------------------------ 查询

    /** 商品列表/搜索：只查 ON_SHELF。 */
    public PageResult<GoodsVO> page(GoodsFilterDTO q) {
        LambdaQueryWrapper<Goods> w = new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, GoodsStatus.ON_SHELF);
        if (q.getCategoryId() != null) {
            w.in(Goods::getCategoryId, categoryService.selfAndDescendantIds(q.getCategoryId()));
        }
        w.ge(q.getMinPrice() != null, Goods::getPrice, q.getMinPrice());
        w.le(q.getMaxPrice() != null, Goods::getPrice, q.getMaxPrice());
        if (StringUtils.hasText(q.getCondition())) {
            w.eq(Goods::getCondition, q.getCondition().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = escapeLike(q.getKeyword().trim());
            w.and(x -> x.like(Goods::getTitle, kw).or().like(Goods::getDescription, kw));
        }
        String sort = q.getSort() == null ? "latest" : q.getSort();
        switch (sort) {
            case "price_asc" -> w.orderByAsc(Goods::getPrice).orderByDesc(Goods::getCreateTime);
            case "price_desc" -> w.orderByDesc(Goods::getPrice).orderByDesc(Goods::getCreateTime);
            case "hot" -> w.orderByDesc(Goods::getViewCount).orderByDesc(Goods::getCreateTime);
            case "latest" -> w.orderByDesc(Goods::getCreateTime);
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "sort 取值为 latest/price_asc/price_desc/hot");
        }
        w.orderByDesc(Goods::getId);
        Page<Goods> page = goodsMapper.selectPage(q.toPage(), w);
        return PageResult.of(page, toListVOs(page.getRecords(), SecurityUtils.currentUserId()));
    }

    /** 商品详情：浏览数 +1；登录用户（非卖家本人）异步写浏览行为。 */
    public GoodsVO detail(Long id) {
        LoginUser viewer = SecurityUtils.currentUser();
        Goods g = goodsMapper.selectById(id);
        if (g == null) {
            throw new BizException(ErrorCode.GOODS_NOT_FOUND);
        }
        boolean owner = viewer != null && viewer.userId().equals(g.getSellerId());
        boolean privileged = owner || (viewer != null && viewer.isAdmin());
        if (!privileged && !PUBLIC_STATUSES.contains(g.getStatus())) {
            throw new BizException(GoodsStatus.OFF_SHELF.equals(g.getStatus())
                    ? ErrorCode.GOODS_OFF_SHELF : ErrorCode.GOODS_NOT_FOUND);
        }
        goodsMapper.incrViewCount(id);
        g.setViewCount(g.getViewCount() + 1);
        if (viewer != null && !owner) {
            behaviorService.recordView(viewer.userId(), id);
        }
        GoodsVO vo = toListVOs(List.of(g), viewer == null ? null : viewer.userId()).get(0);
        vo.setDescription(g.getDescription());
        vo.setImages(imagesOf(id));
        if (privileged) {
            vo.setAuditReason(g.getAuditReason());
        }
        return vo;
    }

    /** 我的发布（可按 status 过滤），含 auditReason。 */
    public PageResult<GoodsVO> myGoods(String status, PageQuery pq) {
        Long uid = SecurityUtils.requireUserId();
        LambdaQueryWrapper<Goods> w = new LambdaQueryWrapper<Goods>().eq(Goods::getSellerId, uid)
                .eq(StringUtils.hasText(status), Goods::getStatus, status)
                .orderByDesc(Goods::getCreateTime).orderByDesc(Goods::getId);
        Page<Goods> page = goodsMapper.selectPage(pq.toPage(), w);
        List<GoodsVO> vos = toListVOs(page.getRecords(), uid);
        for (int i = 0; i < vos.size(); i++) {
            vos.get(i).setAuditReason(page.getRecords().get(i).getAuditReason());
        }
        return PageResult.of(page, vos);
    }

    /** 我的收藏（按收藏时间倒序）。 */
    public PageResult<GoodsVO> myFavorites(PageQuery pq) {
        Long uid = SecurityUtils.requireUserId();
        Page<Goods> page = pq.toPage();
        goodsMapper.selectFavoritePage(page, uid);
        return PageResult.of(page, toListVOs(page.getRecords(), uid));
    }

    /**
     * 批量组装列表 VO（不含 description、images、auditReason），一次查询卖家、分类与收藏状态。
     * 推荐模块可直接复用。
     *
     * @param viewerId 当前用户，游客传 null（favorited 恒为 false）
     */
    public List<GoodsVO> toListVOs(List<Goods> list, Long viewerId) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> sellerIds = list.stream().map(Goods::getSellerId).collect(Collectors.toSet());
        Map<Long, SysUser> sellers = userMapper.selectBatchIds(sellerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, String> catNames = categoryService.namesByIds(
                list.stream().map(Goods::getCategoryId).collect(Collectors.toSet()));
        Set<Long> favored = new HashSet<>();
        if (viewerId != null) {
            List<Long> goodsIds = list.stream().map(Goods::getId).toList();
            favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, viewerId)
                            .in(Favorite::getGoodsId, goodsIds))
                    .forEach(f -> favored.add(f.getGoodsId()));
        }
        List<GoodsVO> result = new ArrayList<>(list.size());
        for (Goods g : list) {
            GoodsVO vo = new GoodsVO();
            vo.setId(g.getId());
            vo.setTitle(g.getTitle());
            vo.setPrice(g.getPrice());
            vo.setOriginalPrice(g.getOriginalPrice());
            vo.setCondition(g.getCondition());
            vo.setTradeMethod(g.getTradeMethod());
            vo.setStatus(g.getStatus());
            vo.setCategoryId(g.getCategoryId());
            vo.setCategoryName(catNames.get(g.getCategoryId()));
            vo.setCoverUrl(g.getCoverUrl());
            vo.setViewCount(g.getViewCount());
            vo.setFavoriteCount(g.getFavoriteCount());
            vo.setFavorited(favored.contains(g.getId()));
            SysUser s = sellers.get(g.getSellerId());
            vo.setSeller(s == null ? null : UserConverter.toSeller(s));
            vo.setCreateTime(g.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    public List<String> imagesOf(Long goodsId) {
        return imageMapper.selectList(new LambdaQueryWrapper<GoodsImage>().eq(GoodsImage::getGoodsId, goodsId)
                        .orderByAsc(GoodsImage::getSortNo))
                .stream().map(GoodsImage::getUrl).toList();
    }

    // ------------------------------------------------------------------ 写操作

    /** 发布商品：状态 PENDING_AUDIT，图片按顺序写入 goods_image，封面取第一张。 */
    @Transactional(rollbackFor = Exception.class)
    public GoodsVO create(GoodsSaveDTO dto) {
        Long uid = SecurityUtils.requireUserId();
        checkCategory(dto.getCategoryId());
        Goods g = new Goods();
        g.setSellerId(uid);
        fill(g, dto);
        g.setStatus(GoodsStatus.PENDING_AUDIT);
        g.setViewCount(0);
        g.setFavoriteCount(0);
        g.setDeleted(0);
        goodsMapper.insert(g);
        saveImages(g.getId(), dto.getImages());
        return ownerView(g.getId());
    }

    /** 修改本人商品：重新进入 PENDING_AUDIT，清空审核意见，图片整体替换。 */
    @Transactional(rollbackFor = Exception.class)
    public GoodsVO update(Long id, GoodsSaveDTO dto) {
        Goods g = requireOwn(id);
        if (!EDITABLE_STATUSES.contains(g.getStatus())) {
            throw new BizException(ErrorCode.GOODS_STATUS_NOT_ALLOWED, "商品已被预订或已售出，不能修改");
        }
        checkCategory(dto.getCategoryId());
        fill(g, dto);
        g.setStatus(GoodsStatus.PENDING_AUDIT);
        g.setAuditReason(null);
        g.setUpdateTime(null);
        goodsMapper.updateById(g);
        imageMapper.delete(new LambdaQueryWrapper<GoodsImage>().eq(GoodsImage::getGoodsId, id));
        saveImages(id, dto.getImages());
        return ownerView(id);
    }

    /** 卖家上/下架：只允许 ON_SHELF ↔ OFF_SHELF。 */
    public GoodsVO changeStatus(Long id, String target) {
        Goods g = requireOwn(id);
        String from;
        if (GoodsStatus.OFF_SHELF.equals(target)) {
            from = GoodsStatus.ON_SHELF;
        } else if (GoodsStatus.ON_SHELF.equals(target)) {
            from = GoodsStatus.OFF_SHELF;
        } else {
            throw new BizException(ErrorCode.BAD_REQUEST, "卖家只能将商品设为 ON_SHELF 或 OFF_SHELF");
        }
        if (target.equals(g.getStatus())) {
            return ownerView(id);
        }
        if (!from.equals(g.getStatus()) || goodsMapper.casStatus(id, from, target) == 0) {
            throw new BizException(ErrorCode.GOODS_STATUS_NOT_ALLOWED,
                    "当前状态 " + g.getStatus() + " 不能变更为 " + target);
        }
        return ownerView(id);
    }

    /** 逻辑删除本人商品（已预订的商品不可删除）。 */
    public void delete(Long id) {
        Goods g = requireOwn(id);
        if (GoodsStatus.RESERVED.equals(g.getStatus())) {
            throw new BizException(ErrorCode.GOODS_STATUS_NOT_ALLOWED, "商品已被预订，不能删除");
        }
        goodsMapper.deleteById(id);
    }

    public Goods requireOwn(Long id) {
        Long uid = SecurityUtils.requireUserId();
        Goods g = goodsMapper.selectById(id);
        if (g == null) {
            throw new BizException(ErrorCode.GOODS_NOT_FOUND);
        }
        if (!uid.equals(g.getSellerId())) {
            throw new BizException(ErrorCode.GOODS_NOT_OWNER);
        }
        return g;
    }

    private GoodsVO ownerView(Long id) {
        Goods g = goodsMapper.selectById(id);
        GoodsVO vo = toListVOs(List.of(g), g.getSellerId()).get(0);
        vo.setDescription(g.getDescription());
        vo.setImages(imagesOf(id));
        vo.setAuditReason(g.getAuditReason());
        return vo;
    }

    private void checkCategory(Long categoryId) {
        if (categoryService.getEnabled(categoryId) == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    private void fill(Goods g, GoodsSaveDTO dto) {
        g.setTitle(dto.getTitle().trim());
        g.setDescription(dto.getDescription());
        g.setPrice(dto.getPrice());
        g.setOriginalPrice(dto.getOriginalPrice());
        g.setCondition(dto.getCondition());
        g.setTradeMethod(StringUtils.hasText(dto.getTradeMethod()) ? dto.getTradeMethod() : "FACE");
        g.setCategoryId(dto.getCategoryId());
        List<String> images = dto.getImages() == null ? List.of() : dto.getImages();
        g.setCoverUrl(images.isEmpty() ? null : images.get(0));
    }

    private void saveImages(Long goodsId, List<String> images) {
        if (images == null) {
            return;
        }
        if (images.size() > 9) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品图片最多 9 张");
        }
        for (int i = 0; i < images.size(); i++) {
            GoodsImage img = new GoodsImage();
            img.setGoodsId(goodsId);
            img.setUrl(images.get(i));
            String path = images.get(i);
            int slash = path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            img.setName(name.length() > 128 ? name.substring(0, 128) : name);
            img.setSortNo(i + 1);
            imageMapper.insert(img);
        }
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
