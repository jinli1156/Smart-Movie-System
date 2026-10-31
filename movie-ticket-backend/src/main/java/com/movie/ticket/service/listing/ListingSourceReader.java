package com.movie.ticket.service.listing;

import com.movie.ticket.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.ZipInputStream;

@Component
public class ListingSourceReader {
    public record Source(String text, List<String> names, List<String> images, List<MultipartFile> imageFiles, List<String> textParts) {
        public Source(String text,List<String> names,List<String> images,List<MultipartFile> imageFiles) {
            this(text,names,images,imageFiles,text.isBlank()?List.of():List.of(text));
        }
    }
    public Source read(String text, List<MultipartFile> files) {
        return read(text,files,"MOVIE");
    }
    public Source read(String text, List<MultipartFile> files,String kind) {
        return read(text,files,kind,false);
    }
    public Source read(String text, List<MultipartFile> files,String kind,boolean aiImport) {
        StringBuilder content = new StringBuilder(Objects.toString(text,""));
        List<String> textParts=new ArrayList<>(); if (text!=null && !text.isBlank()) textParts.add(text.strip());
        List<String> names = new ArrayList<>(), images = new ArrayList<>();
        List<MultipartFile> imageFiles = new ArrayList<>();
        if (files == null) files = List.of();
        if (files.size() > 3) throw new BusinessException(400,"一次最多上传 3 个文件");
        long total = 0;
        for (MultipartFile file : files) {
            total += file.getSize();
            if (file.isEmpty() || file.getSize() > 5*1024*1024 || total > 8*1024*1024) throw new BusinessException(400,"单个文件限 5MB，合计限 8MB");
            String name = Objects.toString(file.getOriginalFilename(), "资料").replace('\\','/');
            name = name.substring(name.lastIndexOf('/')+1);
            if (name.length() > 150) name = name.substring(name.length()-150);
            String ext = name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
            try {
                byte[] bytes = file.getBytes(); String body;
                switch (ext) {
                    case "jpg", "jpeg", "png" -> {
                        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                            var readers = ImageIO.getImageReaders(stream);
                            if (!readers.hasNext()) throw new IOException();
                            var reader = readers.next();
                            try {
                                reader.setInput(stream);
                                if ((long)reader.getWidth(0)*reader.getHeight(0) > 20_000_000) throw new BusinessException(400,"图片像素过大，请压缩到 2000 万像素以内");
                                String format = reader.getFormatName();
                                if (!(format.equalsIgnoreCase("png") && ext.equals("png") || format.equalsIgnoreCase("JPEG") && !ext.equals("png"))) throw new IOException();
                            } finally { reader.dispose(); }
                        }
                        images.add("data:image/"+(ext.equals("png")?"png":"jpeg")+";base64,"+Base64.getEncoder().encodeToString(bytes));
                        imageFiles.add(file); body = "";
                    }
                    case "pdf" -> {
                        try (var pdf = Loader.loadPDF(bytes)) {
                            if (pdf.getNumberOfPages() > 30) throw new BusinessException(400,"PDF 最多支持 30 页，请拆分资料");
                            if (!pdf.getCurrentAccessPermission().canExtractContent()) throw new BusinessException(400,"PDF 禁止提取文字，请提供可读取资料");
                            body = new PDFTextStripper().getText(pdf);
                            if (body.isBlank()) throw new BusinessException(400,"PDF 未提取到文字；扫描件请转为 JPG/PNG 上传识别");
                        }
                    }
                    case "docx" -> body = docx(bytes);
                    case "xlsx" -> body = aiImport?new ListingSpreadsheetReader().readText(bytes):new ListingSpreadsheetReader().read(bytes,kind);
                    case "txt", "md", "json", "csv" -> {
                        try { body = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().replace("\uFEFF", ""); }
                        catch (CharacterCodingException e) { throw new BusinessException(400,"文字文件请使用 UTF-8 编码"); }
                        if (body.indexOf('\0') >= 0) throw new IOException();
                    }
                    default -> throw new BusinessException(400,"支持 Excel（.xlsx）、JPG、PNG、TXT、MD、JSON、CSV、PDF 和 DOCX 文件");
                }
                names.add(name); if (!body.isBlank()) { content.append("\n\n").append(body); textParts.add(body.strip()); }
            } catch (BusinessException e) { throw e; }
            catch (Exception e) { throw new BusinessException(400,"无法读取文件「"+name+"」，请检查格式、内容或加密状态"); }
            if (content.length() > 40000) throw new BusinessException(400,"资料文字总计不能超过 40000 字，请拆分后上传");
        }
        if (content.toString().isBlank() && images.isEmpty()) throw new BusinessException(400,"请上传资料或粘贴文字");
        if (content.length() > 40000) throw new BusinessException(400,"资料文字总计不能超过 40000 字");
        return new Source(content.toString().strip(), names, images, imageFiles,textParts);
    }
    private String docx(byte[] bytes) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry; int count=0;
            while ((entry=zip.getNextEntry())!=null) {
                if (++count>500) throw new IOException();
                if (!entry.getName().equals("word/document.xml")) continue;
                byte[] xml=zip.readNBytes(2_000_001);
                if (xml.length>2_000_000) throw new IOException();
                var factory=DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
                var doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
                var paragraphs=doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","p");
                StringBuilder body=new StringBuilder();
                for (int p=0;p<paragraphs.getLength();p++) body.append(paragraphs.item(p).getTextContent()).append('\n');
                return body.toString();
            }
        }
        throw new IOException();
    }
}
