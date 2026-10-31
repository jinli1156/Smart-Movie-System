package com.movie.ticket.service.marketing;

import com.movie.ticket.exception.BusinessException;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
public class MarketingAssets {
    private final MarketingSettings settings;
    public MarketingAssets(MarketingSettings settings) { this.settings=settings; }
    Path path(String key) {
        if(key==null||!key.matches("[a-f0-9-]{36}\\.png")) throw new BusinessException(404,"素材不存在");
        Path root=Path.of(settings.getStoragePath()).toAbsolutePath().normalize();
        Path file=root.resolve(key).normalize();
        if(!file.startsWith(root)) throw new BusinessException(404,"素材不存在");
        return file;
    }
    private BufferedImage decode(byte[] bytes) throws IOException {
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IOException("图片无法解码");
            var reader=readers.next();
            try {
                reader.setInput(input);
                long width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<64||height<64||width*height>16_777_216) throw new IOException("图片尺寸无效");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
    public String saveBackground(byte[] bytes) throws IOException {
        BufferedImage image=decode(bytes);
        String key=MarketingStore.id()+".png";
        Files.createDirectories(path(key).getParent());
        if(!ImageIO.write(image,"png",path(key).toFile())) throw new IOException("无法保存图片");
        return key;
    }
    public record Rendered(String key,int width,int height,String checksum) {}
    public Rendered generatedPoster(String key) throws IOException {
        Path file=path(key);
        BufferedImage image=ImageIO.read(file.toFile());
        if(image==null) throw new IOException("海报素材不可用");
        return new Rendered(key,image.getWidth(),image.getHeight(),hash(Files.readAllBytes(file)));
    }
    public Rendered render(String backgroundKey,String ratio,String movieTitle,String headline,String cinema) throws IOException {
        BufferedImage background=ImageIO.read(path(backgroundKey).toFile());
        if(background==null) throw new IOException("背景素材不可用");
        int width=ratio.equals("16:9")?1536:ratio.equals("3:4")?864:1024;
        int height=ratio.equals("16:9")?864:ratio.equals("3:4")?1152:1024;
        BufferedImage canvas=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double scale=Math.max((double)width/background.getWidth(),(double)height/background.getHeight());
            int w=(int)Math.ceil(background.getWidth()*scale),h=(int)Math.ceil(background.getHeight()*scale);
            g.drawImage(background,(width-w)/2,(height-h)/2,w,h,null);
            g.setPaint(new GradientPaint(0,height/4f,new Color(4,10,24,20),0,height,new Color(4,10,24,245)));
            g.fillRect(0,0,width,height);
            int margin=width/16;
            g.setColor(new Color(255,190,100));
            g.setFont(new Font("Microsoft YaHei",Font.BOLD,width/36));
            g.drawString("响指电影  /  影院精选",margin,margin+width/36);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Microsoft YaHei",Font.BOLD,width/17));
            drawLines(g,movieTitle,margin,height*55/100,width-margin*2,2);
            g.setFont(new Font("Microsoft YaHei",Font.PLAIN,width/30));
            drawLines(g,headline,margin,height*73/100,width-margin*2,2);
            g.setFont(new Font("Microsoft YaHei",Font.PLAIN,width/38));
            g.setColor(new Color(220,228,240));
            drawLines(g,cinema+" · 查看场次，选择好座",margin,height*91/100,width-margin*2,1);
        } finally { g.dispose(); }
        String key=MarketingStore.id()+".png";
        Path file=path(key); Files.createDirectories(file.getParent()); ImageIO.write(canvas,"png",file.toFile());
        return new Rendered(key,width,height,hash(Files.readAllBytes(file)));
    }
    private void drawLines(Graphics2D g,String text,int x,int y,int maxWidth,int maxLines) {
        var line=new StringBuilder(); int lines=1;
        for(int cp:text.codePoints().toArray()) {
            String next=new String(Character.toChars(cp));
            if(g.getFontMetrics().stringWidth(line+next)>maxWidth) {
                g.drawString(line.toString(),x,y); y+=g.getFontMetrics().getHeight()+6; line.setLength(0);
                if(++lines>maxLines) return;
            }
            line.append(next);
        }
        if(!line.isEmpty()) g.drawString(line.toString(),x,y);
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
}
