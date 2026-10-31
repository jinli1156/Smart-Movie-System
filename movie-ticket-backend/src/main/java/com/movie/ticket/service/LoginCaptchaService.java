package com.movie.ticket.service;

import com.movie.ticket.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** 图形验证码的生成与一次性校验，使用 Redis 保存验证码答案并设置两分钟有效期。 */
@Service
@RequiredArgsConstructor
public class LoginCaptchaService {
    private final StringRedisTemplate redis;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); redis.call('DEL',KEYS[1]); return v", String.class);

    public record Challenge(String captchaId, String image, int expiresIn) {}

    /** 生成验证码图像与挑战 ID，并保存两分钟有效的答案。 */
    public Challenge create() {
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < 4; i++) answer.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        String id = UUID.randomUUID().toString();
        String image = render(answer.toString());
        redis.opsForValue().set("auth:captcha:" + id, answer.toString(), Duration.ofMinutes(2));
        return new Challenge(id, image, 120);
    }

    /** 消费验证码并核对输入，阻止同一挑战重复使用。 */
    public void verify(String id, String answer) {
        if (id == null || !id.matches("[a-f0-9-]{36}") || answer == null || answer.isBlank()) {
            throw new BusinessException(400, "请输入验证码");
        }
        // Every attempt consumes the challenge atomically, including incorrect answers.
        String expected = redis.execute(CONSUME, List.of("auth:captcha:" + id));
        if (expected == null) throw new BusinessException(400, "验证码已过期，请刷新后重试");
        if (!expected.equalsIgnoreCase(answer.trim())) throw new BusinessException(400, "验证码错误，请重新输入");
    }

    private String render(String answer) {
        BufferedImage image = new BufferedImage(144, 52, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(246, 243, 231));
            g.fillRect(0, 0, 144, 52);
            for (int i = 0; i < 8; i++) {
                g.setColor(new Color(165 + RANDOM.nextInt(50), 160 + RANDOM.nextInt(45), 140 + RANDOM.nextInt(45)));
                g.drawLine(RANDOM.nextInt(144), RANDOM.nextInt(52), RANDOM.nextInt(144), RANDOM.nextInt(52));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            for (int i = 0; i < answer.length(); i++) {
                var original = g.getTransform();
                int x = 13 + i * 32;
                g.rotate((RANDOM.nextDouble() - .5) * .4, x + 10, 27);
                g.setColor(new Color(70 + RANDOM.nextInt(60), 65 + RANDOM.nextInt(45), 45 + RANDOM.nextInt(40)));
                g.drawString(String.valueOf(answer.charAt(i)), x, 36 + RANDOM.nextInt(5));
                g.setTransform(original);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("验证码生成失败", e);
        } finally { g.dispose(); }
    }
}
