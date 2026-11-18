package com.campus.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    /** minio | local */
    private String type = "local";
    /** 单个文件最大字节数，默认 5MB */
    private long maxFileSize = 5L * 1024 * 1024;
    private Local local = new Local();
    private Minio minio = new Minio();

    @Data
    public static class Local {
        /** 本地落盘根目录 */
        private String path = "./uploads";
        /** 访问前缀（静态映射 /files/**），可配置为带域名的绝对地址 */
        private String urlPrefix = "/files";
    }

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "campus-trade";
        /** 对外访问前缀，为空时使用 endpoint/bucket */
        private String publicUrl;
    }
}
