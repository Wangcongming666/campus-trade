package com.campus.trade.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页请求参数：pageNum 从 1 开始（默认 1），pageSize 默认 10，最大 100。
 */
@Data
public class PageQuery {
    public static final int MAX_PAGE_SIZE = 100;

    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private Integer pageNum = 1;
    @Schema(description = "每页条数，最大 100", defaultValue = "10")
    private Integer pageSize = 10;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public <T> Page<T> toPage() {
        return new Page<>(safePageNum(), safePageSize());
    }
}
