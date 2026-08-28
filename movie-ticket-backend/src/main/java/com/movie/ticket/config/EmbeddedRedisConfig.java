package com.movie.ticket.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("dev")
@Slf4j
public class EmbeddedRedisConfig {

    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;

    private RedisServer redisServer;

    @PostConstruct
    public void start() throws IOException {
        if (isRedisRunning()) {
            log.info("检测到 {}:{} 已有 Redis 服务，复用现有实例", REDIS_HOST, REDIS_PORT);
            return;
        }

        redisServer = new RedisServer(REDIS_PORT);
        redisServer.start();
        log.info("嵌入式 Redis 已启动在端口 {}", REDIS_PORT);
    }

    @PreDestroy
    public void stop() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
            log.info("嵌入式 Redis 已停止");
        }
    }

    private boolean isRedisRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(REDIS_HOST, REDIS_PORT), 300);
            socket.setSoTimeout(500);
            socket.getOutputStream().write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            byte[] response = new byte[32];
            int length = socket.getInputStream().read(response);
            return length > 0 && new String(response, 0, length, StandardCharsets.US_ASCII)
                    .startsWith("+PONG");
        } catch (IOException ignored) {
            return false;
        }
    }
}
