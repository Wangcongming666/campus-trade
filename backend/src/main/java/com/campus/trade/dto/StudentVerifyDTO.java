package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentVerifyDTO {
    @NotBlank(message = "学号不能为空")
    @Size(max = 32, message = "学号过长")
    private String studentNo;
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 32, message = "姓名过长")
    private String realName;
    @NotBlank(message = "学校不能为空")
    @Size(max = 100, message = "学校名称过长")
    private String school;
    @NotBlank(message = "请上传学生证或校园卡图片")
    @Size(max = 512, message = "图片地址过长")
    private String cardImageUrl;
}
