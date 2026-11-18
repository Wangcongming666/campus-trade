package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trade.common.Constants;
import com.campus.trade.entity.Category;
import com.campus.trade.mapper.CategoryMapper;
import com.campus.trade.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public List<Category> listEnabled() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, Constants.CategoryStatus.ENABLED)
                .orderByAsc(Category::getSort).orderByAsc(Category::getId));
    }

    /** 平铺：仅 ENABLED，按 sort 升序。 */
    public List<CategoryVO> flat() {
        return listEnabled().stream().map(CategoryService::toVO).toList();
    }

    /** 树：仅 ENABLED；父分类停用时其子分类不出现在树中。 */
    public List<CategoryVO> tree() {
        List<CategoryVO> all = flat();
        Map<Long, CategoryVO> byId = new LinkedHashMap<>();
        all.forEach(c -> {
            c.setChildren(new ArrayList<>());
            byId.put(c.getId(), c);
        });
        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO c : all) {
            if (c.getParentId() == null) {
                roots.add(c);
            } else if (byId.containsKey(c.getParentId())) {
                byId.get(c.getParentId()).getChildren().add(c);
            }
        }
        return roots;
    }

    public Category getEnabled(Long id) {
        Category c = id == null ? null : categoryMapper.selectById(id);
        return c != null && Constants.CategoryStatus.ENABLED.equals(c.getStatus()) ? c : null;
    }

    /** 分类本身 + 所有启用的子孙分类 ID（用于按一级分类筛选商品）。 */
    public Set<Long> selfAndDescendantIds(Long id) {
        List<Category> all = listEnabled();
        Map<Long, List<Long>> children = all.stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId, Collectors.mapping(Category::getId, Collectors.toList())));
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(id);
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (result.add(cur)) {
                children.getOrDefault(cur, List.of()).forEach(stack::push);
            }
        }
        return result;
    }

    public Map<Long, String> namesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

    private static CategoryVO toVO(Category c) {
        CategoryVO vo = new CategoryVO();
        vo.setId(c.getId());
        vo.setParentId(c.getParentId());
        vo.setName(c.getName());
        vo.setSort(c.getSort());
        return vo;
    }
}
