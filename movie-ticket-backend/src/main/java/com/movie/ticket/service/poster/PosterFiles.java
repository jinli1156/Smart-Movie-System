package com.movie.ticket.service.poster;

import com.movie.ticket.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/** Keeps portraits and results outside the public upload directory. */
@Component
public class PosterFiles {
    private final PosterSettings settings;
    public PosterFiles(PosterSettings settings) { this.settings = settings; }

    public byte[] portrait(MultipartFile file) throws IOException {
        if (file == null) return null;
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new BusinessException(400, "照片须为不超过 5MB 的 JPG 或 PNG 图片");
        return normalize(file.getBytes(), true);
    }

    private byte[] normalize(byte[] bytes, boolean portrait) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(400, "无法读取图片，请使用 JPG 或 PNG 图片");
            var reader = readers.next();
            BufferedImage source;
            try {
                if (portrait && !java.util.Set.of("JPEG", "PNG").contains(reader.getFormatName().toUpperCase(java.util.Locale.ROOT)))
                    throw new BusinessException(400, "请选择 JPG 或 PNG 格式的人物照片");
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 32 || height < 32 || (long) width * height > 24_000_000)
                    throw new BusinessException(400, "图片尺寸须至少为 32×32，且不超过 2400 万像素");
                source = reader.read(0);
            } finally { reader.dispose(); }
            if (source == null) throw new BusinessException(400, "图片内容无效");
            double scale = portrait ? Math.min(1d, 2048d / Math.max(source.getWidth(), source.getHeight())) : 1d;
            var output = new BufferedImage(Math.max(1, (int) (source.getWidth() * scale)),
                Math.max(1, (int) (source.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
            var g = output.createGraphics();
            try {
                g.setColor(Color.WHITE); g.fillRect(0, 0, output.getWidth(), output.getHeight());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(source, 0, 0, output.getWidth(), output.getHeight(), null);
            } finally { g.dispose(); }
            var buffer = new ByteArrayOutputStream();
            ImageIO.write(output, portrait ? "jpg" : "png", buffer);
            return buffer.toByteArray();
        }
    }

    public String savePortrait(byte[] bytes) throws IOException { return save(bytes, ".jpg"); }
    public String savePoster(byte[] bytes) throws IOException { return save(normalize(bytes, false), ".png"); }
    private String save(byte[] bytes, String suffix) throws IOException {
        String key = UUID.randomUUID() + suffix;
        Path target = path(key); Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".poster-", ".tmp");
        try {
            Files.write(temp, bytes); Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        return key;
    }
    public Path path(String key) {
        if (key == null || !key.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.(jpg|png)"))
            throw new BusinessException(404, "图片不存在");
        return Path.of(settings.getStoragePath()).toAbsolutePath().normalize().resolve(key);
    }
    public void delete(String key) throws IOException { if (key != null) Files.deleteIfExists(path(key)); }
}
