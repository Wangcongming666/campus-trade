package com.campus.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** PUT /users/me/interests：整体覆盖，最多 5 个；空数组表示清空。 */
@Data
public class InterestUpdateDTO {
    @NotNull(message = "categoryIds 不能为空（清空请传 []）")
    @Size(max = 5, message = "兴趣分类最多选择 5 个")
    private List<@NotNull(message = "分类 ID 不能为空") Long> categoryIds;
}
