package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trade.common.BizException;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.entity.Category;
import com.campus.trade.entity.UserInterest;
import com.campus.trade.mapper.UserInterestMapper;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** 用户兴趣分类（冷启动推荐用）。 */
@Service
@RequiredArgsConstructor
public class UserInterestService {

    public static final int MAX_INTERESTS = 5;

    private final UserInterestMapper interestMapper;
    private final CategoryService categoryService;

    /** 当前用户兴趣分类（仅返回仍启用的分类，按分类 sort 排序）。 */
    public List<CategoryVO> list() {
        return listOf(SecurityUtils.requireUserId());
    }

    /** 推荐模块可按 userId 直接取用户兴趣分类 ID。 */
    public List<Long> categoryIdsOf(Long userId) {
        return interestMapper.selectList(new LambdaQueryWrapper<UserInterest>().eq(UserInterest::getUserId, userId)
                        .orderByAsc(UserInterest::getCreateTime, UserInterest::getId))
                .stream().map(UserInterest::getCategoryId).toList();
    }

    /** 整体覆盖：同一事务内先删后插；最多 5 个，必须均为 ENABLED 分类，否则 400；空数组清空。 */
    @Transactional(rollbackFor = Exception.class)
    public List<CategoryVO> replace(List<Long> categoryIds) {
        Long uid = SecurityUtils.requireUserId();
        LinkedHashSet<Long> ids = new LinkedHashSet<>(categoryIds == null ? List.of() : categoryIds);
        if (ids.size() > MAX_INTERESTS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "兴趣分类最多选择 " + MAX_INTERESTS + " 个");
        }
        Set<Long> enabled = categoryService.listEnabled().stream().map(Category::getId).collect(Collectors.toSet());
        for (Long id : ids) {
            if (!enabled.contains(id)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "分类不存在或已停用：" + id);
            }
        }
        interestMapper.delete(new LambdaQueryWrapper<UserInterest>().eq(UserInterest::getUserId, uid));
        for (Long id : ids) {
            UserInterest ui = new UserInterest();
            ui.setUserId(uid);
            ui.setCategoryId(id);
            interestMapper.insert(ui);
        }
        return listOf(uid);
    }

    private List<CategoryVO> listOf(Long uid) {
        Set<Long> mine = new HashSet<>(categoryIdsOf(uid));
        if (mine.isEmpty()) {
            return new ArrayList<>();
        }
        return categoryService.flat().stream().filter(c -> mine.contains(c.getId())).toList();
    }
}
