package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trade.common.*;
import com.campus.trade.dto.ReviewCreateDTO;
import com.campus.trade.entity.Review;
import com.campus.trade.entity.SysUser;
import com.campus.trade.entity.TradeOrder;
import com.campus.trade.mapper.ReviewMapper;
import com.campus.trade.mapper.SysUserMapper;
import com.campus.trade.mapper.TradeOrderMapper;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.vo.ReviewVO;
import com.campus.trade.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单评价与信誉分。信誉分 = clamp(round(avg(收到的全部评分) × 20), 0, 100)，每次新增评价后重算。
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final TradeOrderMapper orderMapper;
    private final SysUserMapper userMapper;

    @Transactional(rollbackFor = Exception.class)
    public ReviewVO create(ReviewCreateDTO dto) {
        Long uid = SecurityUtils.requireUserId();
        TradeOrder o = orderMapper.selectById(dto.getOrderId());
        if (o == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        Long toUser;
        if (uid.equals(o.getBuyerId())) {
            toUser = o.getSellerId();
        } else if (uid.equals(o.getSellerId())) {
            toUser = o.getBuyerId();
        } else {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!Constants.OrderStatus.COMPLETED.equals(o.getStatus())) {
            throw new BizException(ErrorCode.REVIEW_ORDER_NOT_COMPLETED);
        }
        if (reviewMapper.exists(new LambdaQueryWrapper<Review>().eq(Review::getOrderId, o.getId())
                .eq(Review::getFromUserId, uid))) {
            throw new BizException(ErrorCode.REVIEW_EXISTS);
        }
        Review r = new Review();
        r.setOrderId(o.getId());
        r.setFromUserId(uid);
        r.setToUserId(toUser);
        r.setScore(dto.getScore());
        r.setContent(dto.getContent() == null || dto.getContent().isBlank() ? null : dto.getContent().trim());
        try {
            reviewMapper.insert(r);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.REVIEW_EXISTS);
        }
        recomputeCredit(toUser);
        return toVOs(List.of(reviewMapper.selectById(r.getId()))).get(0);
    }

    public PageResult<ReviewVO> pageForUser(Long userId, PageQuery pq) {
        Page<Review> page = reviewMapper.selectPage(pq.toPage(), new LambdaQueryWrapper<Review>()
                .eq(Review::getToUserId, userId).orderByDesc(Review::getCreateTime).orderByDesc(Review::getId));
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /** clamp(round(avg × 20), 0, 100)；无评价时保持原值。 */
    public static int creditOf(BigDecimal avg) {
        int v = avg.multiply(BigDecimal.valueOf(20)).setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, v));
    }

    private void recomputeCredit(Long userId) {
        BigDecimal avg = reviewMapper.avgScore(userId);
        if (avg == null) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, userId)
                .set(SysUser::getCreditScore, creditOf(avg)));
    }

    private List<ReviewVO> toVOs(List<Review> list) {
        if (list.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        Set<Long> ids = list.stream().map(Review::getFromUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return list.stream().map(r -> {
            ReviewVO vo = new ReviewVO();
            vo.setId(r.getId());
            vo.setOrderId(r.getOrderId());
            vo.setFromUser(UserBriefVO.of(users.get(r.getFromUserId())));
            vo.setScore(r.getScore());
            vo.setContent(r.getContent());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).toList();
    }
}
