package com.campus.trade.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
public class CategoryVO {
    private Long id;
    private Long parentId;
    private String name;
    private Integer sort;
    /** 仅树接口返回 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CategoryVO> children;
}
