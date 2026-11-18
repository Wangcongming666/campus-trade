package com.campus.trade.storage;

import java.io.InputStream;

public interface FileStorage {
    /**
     * 保存文件并返回可访问的 URL。
     *
     * @param objectName  相对路径，如 2026/11/18/xxx.png
     */
    String store(String objectName, InputStream in, long size, String contentType) throws Exception;
}
