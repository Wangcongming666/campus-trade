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
        String ext = validateImage(file);
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

    /** 上传图片校验（扩展名、大小、文件头），返回小写扩展名。图片识别等接口复用。 */
    public String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
        } catch (IOException e) {
            head = new byte[0];
        }
        return validateImage(file.getOriginalFilename(), file.getSize(), head);
    }

    /** 同上，按文件名、字节数与文件头校验。 */
    public String validateImage(String filename, long size, byte[] head) {
        String name = filename == null ? "" : StringUtils.getFilename(filename);
        String ext = StringUtils.getFilenameExtension(name);
        ext = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!ALLOWED.containsKey(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        if (size > props.getMaxFileSize()) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE);
        }
        if (!matchesMagic(head, ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        return ext;
    }

    /**
     * 读取本站本地存储中的已上传图片（防 SSRF：只接受以本地访问前缀（默认 /files/）或 /uploads/ 开头的站内相对路径，
     * 不发起任何网络请求；路径规范化后必须仍在存储根目录内）。校验类型与大小后返回字节。
     * 其他 URL（http(s)://、MinIO 地址、含 .. 或查询串等）一律 400。
     */
    public byte[] readLocalImage(String url) {
        String rel = localRelativePath(url);
        if (rel == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "imageUrl 仅支持本站上传的图片路径（如 /files/2026/11/18/xxx.jpg）");
        }
        java.nio.file.Path root = java.nio.file.Paths.get(props.getLocal().getPath()).toAbsolutePath().normalize();
        java.nio.file.Path target = root.resolve(rel).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "imageUrl 非法");
        }
        if (!java.nio.file.Files.isRegularFile(target)) {
            throw new BizException(ErrorCode.AI_IMAGE_NOT_FOUND);
        }
        try {
            long size = java.nio.file.Files.size(target);
            byte[] head;
            try (InputStream in = java.nio.file.Files.newInputStream(target)) {
                head = in.readNBytes(12);
            }
            validateImage(target.getFileName().toString(), size, head);
            return java.nio.file.Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BizException(ErrorCode.AI_IMAGE_NOT_FOUND);
        }
    }

    /** 站内路径 → 存储根目录下的相对路径；不合法返回 null。 */
    String localRelativePath(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim();
        if (u.isEmpty() || u.contains("..") || u.contains("\\") || u.contains("?") || u.contains("#")
                || u.contains("%") || u.contains("://") || u.startsWith("//")) {
            return null;
        }
        String prefix = props.getLocal().getUrlPrefix();
        prefix = prefix == null ? "/files/" : (prefix.endsWith("/") ? prefix : prefix + "/");
        for (String p : new String[]{prefix, "/uploads/"}) {
            if (p.startsWith("/") && u.startsWith(p) && u.length() > p.length()) {
                return u.substring(p.length());
            }
        }
        return null;
    }

    /** 校验文件头，防止改扩展名伪装。 */
    private static boolean matchesMagic(byte[] h, String ext) {
        int n = h == null ? 0 : h.length;
        return switch (ext) {
            case "jpg", "jpeg" -> n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "png" -> n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "webp" -> n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default -> false;
        };
    }
}
