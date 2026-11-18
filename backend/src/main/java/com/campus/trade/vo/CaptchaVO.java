package com.campus.trade.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CaptchaVO {
    private String captchaKey;
    /** data:image/png;base64,... */
    private String imageBase64;
}
