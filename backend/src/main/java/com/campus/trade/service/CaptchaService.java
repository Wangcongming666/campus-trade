package com.campus.trade.service;

import com.campus.trade.common.BizException;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.security.RedisKeys;
import com.campus.trade.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/** 图形验证码：4 位字符，PNG base64，Redis 存 5 分钟，一次性使用。 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    /** 测试环境可设 captcha.enabled=false 跳过校验 */
    @Value("${captcha.enabled:true}")
    private boolean enabled;

    public CaptchaVO generate() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String key = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(RedisKeys.captcha(key), code.toString(), TTL);
        return new CaptchaVO(key, "data:image/png;base64," + render(code.toString()));
    }

    /** 校验并删除验证码；不匹配或过期抛 10006。 */
    public void verify(String key, String code) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(key) || !StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID);
        }
        String stored = redis.opsForValue().getAndDelete(RedisKeys.captcha(key));
        if (stored == null || !stored.equalsIgnoreCase(code.trim())) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID);
        }
    }

    private String render(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 6; i++) {
                g.setColor(randomColor(150, 220));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(randomColor(20, 120));
                double angle = (random.nextDouble() - 0.5) * 0.5;
                int x = 12 + i * 26;
                int y = 30;
                g.rotate(angle, x, y);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.rotate(-angle, x, y);
            }
            for (int i = 0; i < 40; i++) {
                g.setColor(randomColor(100, 200));
                g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 1, 1);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "验证码生成失败");
        }
    }

    private Color randomColor(int min, int max) {
        return new Color(min + random.nextInt(max - min), min + random.nextInt(max - min), min + random.nextInt(max - min));
    }
}
