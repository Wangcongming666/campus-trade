package com.campus.trade.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 信息流分页 {records, total, pageNum, pageSize, pages, hasMore}（在 PageResult 基础上增加 hasMore）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "信息流分页结果")
public class FeedPage<T> {
    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;
    private long pages;
    @Schema(description = "是否还有下一页")
    private boolean hasMore;

    public static <T> FeedPage<T> of(List<T> records, long total, long pageNum, long pageSize) {
        long pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new FeedPage<>(records, total, pageNum, pageSize, pages, pageNum * pageSize < total);
    }
}
