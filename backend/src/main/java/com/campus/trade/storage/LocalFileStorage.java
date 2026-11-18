package com.campus.trade.storage;

import com.campus.trade.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/** 本地磁盘存储：落在 storage.local.path，经 /files/** 静态映射访问。 */
@Component
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

    private final StorageProperties props;

    @Override
    public String store(String objectName, InputStream in, long size, String contentType) throws Exception {
        Path root = Paths.get(props.getLocal().getPath()).toAbsolutePath().normalize();
        Path target = root.resolve(objectName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("invalid path");
        }
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        String prefix = props.getLocal().getUrlPrefix();
        return (prefix.endsWith("/") ? prefix : prefix + "/") + objectName;
    }
}
