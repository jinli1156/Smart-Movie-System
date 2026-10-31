package com.movie.ticket.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** 按配置启动开发环境内嵌 Redis，并随应用关闭释放进程。 */
@Configuration
@Profile("dev")
@Slf4j
public class EmbeddedRedisConfig {

    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;
    private static final String WINDOWS_REDIS_EXECUTABLE =
            "redis-server-5.0.14.1-windows-amd64.exe";

    private RedisServer redisServer;

    @PostConstruct
    public void start() throws IOException {
        if (isRedisRunning()) {
            log.info("检测到 {}:{} 已有 Redis 服务，复用现有实例", REDIS_HOST, REDIS_PORT);
            return;
        }

        redisServer = createRedisServer();
        redisServer.start();
        log.info("嵌入式 Redis 已启动在端口 {}", REDIS_PORT);
    }

    private RedisServer createRedisServer() throws IOException {
        if (!System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win")) {
            return new RedisServer(REDIS_PORT);
        }

        // embedded-redis 1.4.3 依赖 PROCESSOR_ARCHITECTURE 环境变量判断
        // Windows 架构。部分 IDE/终端不会传递该变量，导致启动时空指针。
        // Windows 下直接使用依赖包内自带的 x64 可执行文件，绕过不可靠的检测。
        Path redisDirectory = Files.createTempDirectory("movie-ticket-redis-");
        Path executable = redisDirectory.resolve(WINDOWS_REDIS_EXECUTABLE);
        try (InputStream input = EmbeddedRedisConfig.class.getClassLoader()
                .getResourceAsStream(WINDOWS_REDIS_EXECUTABLE)) {
            if (input == null) {
                throw new IOException("未找到内嵌 Redis 可执行文件: " + WINDOWS_REDIS_EXECUTABLE);
            }
            Files.copy(input, executable, StandardCopyOption.REPLACE_EXISTING);
        }

        File redisExecutable = executable.toFile();
        redisDirectory.toFile().deleteOnExit();
        redisExecutable.deleteOnExit();
        return new RedisServer(REDIS_PORT, redisExecutable);
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
