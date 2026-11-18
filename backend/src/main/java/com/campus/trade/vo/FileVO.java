package com.campus.trade.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileVO {
    private String url;
    private String name;
    /** 字节数（基本类型，JSON 输出为数字） */
    private long size;
}
