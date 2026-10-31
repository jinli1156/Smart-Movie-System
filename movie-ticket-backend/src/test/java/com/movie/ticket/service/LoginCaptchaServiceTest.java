package com.movie.ticket.service;

import com.movie.ticket.dto.LoginRequest;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginCaptchaServiceTest {
    private static final String ID = "12345678-1234-1234-1234-123456789012";

    @Test void createsPngAndKeepsAnswerOnlyInRedisWithExpiry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var challenge = new LoginCaptchaService(redis).create();
        assertEquals(120, challenge.expiresIn());
        assertTrue(challenge.image().startsWith("data:image/png;base64,"));
        byte[] png = Base64.getDecoder().decode(challenge.image().split(",")[1]);
        assertEquals(0x89, png[0] & 0xff);
        verify(values).set(eq("auth:captcha:" + challenge.captchaId()), matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}"), eq(Duration.ofMinutes(2)));
    }

    @Test void acceptsCaseInsensitiveAnswerAndRejectsReuse() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of("auth:captcha:" + ID)))).thenReturn("AB23", null);
        var service = new LoginCaptchaService(redis);
        assertDoesNotThrow(() -> service.verify(ID, " ab23 "));
        assertThrows(BusinessException.class, () -> service.verify(ID, "AB23"));
        verify(redis, times(2)).execute(any(RedisScript.class), eq(List.of("auth:captcha:" + ID)));
    }

    @Test void rejectsWrongExpiredAndMissingChallenges() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn("AB23", null);
        var service = new LoginCaptchaService(redis);
        assertThrows(BusinessException.class, () -> service.verify(ID, "WRNG"));
        assertThrows(BusinessException.class, () -> service.verify(ID, "AB23"));
        assertThrows(BusinessException.class, () -> service.verify(null, null));
    }

    @Test void credentialsAreNotCheckedBeforeCaptchaPasses() {
        UserMapper users = mock(UserMapper.class);
        LoginCaptchaService captcha = mock(LoginCaptchaService.class);
        doThrow(new BusinessException(400, "验证码错误")).when(captcha).verify(ID, "WRNG");
        var auth = new AuthService(users, null, null, null, captcha);
        LoginRequest request = new LoginRequest();
        request.setUsername("test"); request.setPassword("test");
        request.setCaptchaId(ID); request.setCaptcha("WRNG");
        assertThrows(BusinessException.class, () -> auth.login(request));
        verifyNoInteractions(users);
    }
}
