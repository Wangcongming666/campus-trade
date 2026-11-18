package com.campus.trade.storage;

import com.campus.trade.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/** MinIO 存储（storage.type=minio 时使用），首次写入时自动建桶并设为公共读。 */
@Component
@RequiredArgsConstructor
public class MinioFileStorage implements FileStorage {

    private final StorageProperties props;
    private volatile MinioClient client;
    private volatile boolean bucketReady;

    private MinioClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    StorageProperties.Minio m = props.getMinio();
                    client = MinioClient.builder().endpoint(m.getEndpoint())
                            .credentials(m.getAccessKey(), m.getSecretKey()).build();
                    client.setTimeout(3000, 10000, 10000);
                }
            }
        }
        return client;
    }

    @Override
    public String store(String objectName, InputStream in, long size, String contentType) throws Exception {
        StorageProperties.Minio m = props.getMinio();
        MinioClient c = client();
        if (!bucketReady) {
            if (!c.bucketExists(BucketExistsArgs.builder().bucket(m.getBucket()).build())) {
                c.makeBucket(MakeBucketArgs.builder().bucket(m.getBucket()).build());
                String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                        + "\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + m.getBucket() + "/*\"]}]}";
                c.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(m.getBucket()).config(policy).build());
            }
            bucketReady = true;
        }
        c.putObject(PutObjectArgs.builder().bucket(m.getBucket()).object(objectName)
                .stream(in, size, -1).contentType(contentType).build());
        String base = StringUtils.hasText(m.getPublicUrl()) ? m.getPublicUrl()
                : trimSlash(m.getEndpoint()) + "/" + m.getBucket();
        return trimSlash(base) + "/" + objectName;
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
