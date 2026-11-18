package com.campus.trade.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** PUT /users/me：只更新非空字段。 */
@Data
public class UserUpdateDTO {
    @Size(min = 1, max = 32, message = "昵称长度为 1-32 位")
    private String nickname;
    @Size(max = 512, message = "头像地址过长")
    private String avatar;
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
}
