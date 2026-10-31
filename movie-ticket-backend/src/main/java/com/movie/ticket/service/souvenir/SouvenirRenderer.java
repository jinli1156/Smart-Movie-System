package com.movie.ticket.service.souvenir;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/** AI 负责氛围画面；订单事实和凭证说明由程序排版，避免生成文字失真。 */
@Component
public class SouvenirRenderer {
    public static final int WIDTH = 1080, HEIGHT = 1560;
    private final SouvenirSettings settings;
    public SouvenirRenderer(SouvenirSettings settings) { this.settings = settings; }

    public Path path(String key) throws IOException {
        if (key == null || !key.matches("[a-f0-9-]{36}\\.png")) throw new IOException("纪念票文件不存在");
        return Path.of(settings.getStoragePath()).toAbsolutePath().normalize().resolve(key);
    }

    public String render(byte[] background, JsonNode facts, String headline, String caption) throws IOException {
        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(background))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("AI 图片无法解码");
            var reader = readers.next();
            try {
                reader.setInput(input);
                long width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 64 || height < 64 || width * height > 16_777_216) throw new IOException("AI 图片尺寸无效");
                image = reader.read(0);
            } finally { reader.dispose(); }
        }
        var canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = canvas.createGraphics();
        Color ink = new Color(235, 244, 255), cyan = new Color(109, 226, 255), violet = new Color(176, 159, 255);
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(new Color(6, 12, 36)); g.fillRect(0, 0, WIDTH, HEIGHT);
            double scale = Math.max((double) WIDTH / image.getWidth(), (double) HEIGHT / image.getHeight());
            int w = (int) Math.ceil(image.getWidth() * scale), h = (int) Math.ceil(image.getHeight() * scale);
            g.drawImage(image, (WIDTH - w) / 2, (HEIGHT - h) / 2, w, h, null);
            g.setPaint(new GradientPaint(0, 250, new Color(6, 12, 36, 20), 0, 1050, new Color(6, 12, 36, 248)));
            g.fillRect(0, 250, WIDTH, 800);
            g.setColor(new Color(6, 12, 36, 248)); g.fillRect(0, 1050, WIDTH, HEIGHT - 1050);
            var frame = new Polygon(new int[]{72,1008,1040,1040,1008,72,40,40}, new int[]{40,40,72,1488,1520,1520,1488,72}, 8);
            g.setColor(new Color(102, 166, 255, 28)); g.setStroke(new BasicStroke(16)); g.drawPolygon(frame);
            g.setColor(new Color(135, 177, 255, 85)); g.setStroke(new BasicStroke(5)); g.drawPolygon(frame);
            g.setColor(cyan); g.setStroke(new BasicStroke(1.5f)); g.drawPolygon(frame);
            g.setFont(font(24, true)); g.drawString("响指电影  /  AI 观影纪念票", 80, 108);
            g.setFont(font(22, false)); g.setColor(violet); g.drawString("STELLAR ARCHIVE  /  星空科幻收藏", 80, 154);
            g.setColor(cyan); g.setFont(font(25, false)); g.drawString("把这一场光影，存入宇宙。", 80, 550);
            g.setColor(ink); g.setFont(font(62, true)); lines(g, facts.path("movieTitle").asText(), 80, 640, 920, 2);
            g.setColor(violet); g.setFont(font(24, true)); g.drawString("CINEMA MEMORIES  ·  专属星空纪念", 80, 795);
            g.setColor(ink); g.setFont(font(40, true)); lines(g, headline, 80, 865, 920, 2);
            g.setColor(new Color(191, 205, 235)); g.setFont(font(28, false)); lines(g, caption, 80, 1000, 920, 2);
            g.setColor(new Color(119, 160, 222, 120));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{9, 10}, 0));
            g.drawLine(80, 1100, 1000, 1100);
            g.setColor(ink); g.setFont(font(30, true)); lines(g, facts.path("cinemaName").asText(), 80, 1160, 920, 1);
            g.setColor(new Color(191, 205, 235)); g.setFont(font(27, false));
            lines(g, "场次  " + facts.path("showtime").asText().replace('T', ' '), 80, 1215, 920, 1);
            lines(g, "影厅  " + facts.path("hallName").asText(), 80, 1265, 920, 1);
            lines(g, "座位  " + facts.path("seats").asText(), 80, 1315, 920, 2);
            String orderNo = facts.path("orderNo").asText();
            // 保留真实订单尾号用于收藏识别，不绘制可用于入场的取票码。
            g.setColor(violet); g.setFont(font(24, false));
            g.drawString("订单尾号  " + orderNo.substring(Math.max(0, orderNo.length() - 6)), 80, 1415);
            g.setColor(cyan); g.setFont(font(25, true));
            g.drawString("AI 创作 · 仅作观影纪念 · 非入场凭证", 80, 1476);
        } finally { g.dispose(); }
        String key = UUID.randomUUID() + ".png";
        Path file = path(key); Files.createDirectories(file.getParent());
        if (!ImageIO.write(canvas, "png", file.toFile())) throw new IOException("纪念票保存失败");
        return key;
    }

    private Font font(int size, boolean bold) { return new Font("Microsoft YaHei", bold ? Font.BOLD : Font.PLAIN, size); }

    private void lines(Graphics2D g, String text, int x, int y, int maxWidth, int maxLines) {
        StringBuilder line = new StringBuilder(); int lineNo = 1;
        for (int cp : text.codePoints().toArray()) {
            String next = new String(Character.toChars(cp));
            if (g.getFontMetrics().stringWidth(line + next) > maxWidth) {
                if (lineNo == maxLines) {
                    while (!line.isEmpty() && g.getFontMetrics().stringWidth(line + "…") > maxWidth)
                        line.deleteCharAt(line.length() - 1);
                    g.drawString(line + "…", x, y); return;
                }
                g.drawString(line.toString(), x, y); y += g.getFontMetrics().getHeight() + 5;
                line.setLength(0); lineNo++;
            }
            line.append(next);
        }
        g.drawString(line.toString(), x, y);
    }
}
