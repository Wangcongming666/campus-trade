package com.campus.trade.service;

import com.campus.trade.common.BizException;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.config.StorageProperties;
import com.campus.trade.storage.LocalFileStorage;
import com.campus.trade.storage.MinioFileStorage;
import com.campus.trade.vo.FileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传：仅 jpg/jpeg/png/webp，≤ 5MB。storage.type=minio 时写 MinIO，失败自动降级本地磁盘。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Map<String, String> ALLOWED = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");
    private static final DateTimeFormatter DIR_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties props;
    private final LocalFileStorage localStorage;
    private final MinioFileStorage minioStorage;

    public FileVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "" : StringUtils.getFilename(file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(original);
        ext = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!ALLOWED.containsKey(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        if (file.getSize() > props.getMaxFileSize()) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE);
        }
        if (!matchesMagic(file, ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        String objectName = LocalDate.now().format(DIR_FMT) + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String contentType = ALLOWED.get(ext);
        String url = null;
        if ("minio".equalsIgnoreCase(props.getType())) {
            try (InputStream in = file.getInputStream()) {
                url = minioStorage.store(objectName, in, file.getSize(), contentType);
            } catch (Exception e) {
                log.warn("MinIO 不可用，降级到本地存储: {}", e.getMessage());
            }
        }
        if (url == null) {
            try (InputStream in = file.getInputStream()) {
                url = localStorage.store(objectName, in, file.getSize(), contentType);
            } catch (Exception e) {
                log.error("local storage failed", e);
                throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
            }
        }
        return new FileVO(url, original, file.getSize());
    }

    /** 校验文件头，防止改扩展名伪装。 */
    private boolean matchesMagic(MultipartFile file, String ext) {
        byte[] h = new byte[12];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(h, 0, h.length);
        } catch (IOException e) {
            return false;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "png" -> n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "webp" -> n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default -> false;
        };
    }
}
