package com.movie.ticket.service.listing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.exception.BusinessException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class ListingSourceReaderTest {
    private final ListingSourceReader reader=new ListingSourceReader();
    private MockMultipartFile file(String name,byte[] bytes) { return new MockMultipartFile("files",name,"application/octet-stream",bytes); }
    @Test void excelTemplateRoundTripsChineseLabelsDatesAndNumbersForAllThreeTypes() throws Exception {
        for (String kind:List.of("MOVIE","CINEMA","HALL")) {
            byte[] template=new ClassPathResource("listing/templates/"+kind.toLowerCase()+"-batch-template.xlsx").getContentAsByteArray();
            try (var workbook=new XSSFWorkbook(new ByteArrayInputStream(template))) {
                var sheet=workbook.getSheetAt(0); var data=sheet.getRow(1); if (data==null) data=sheet.createRow(1);
                var aliases=ListingRecordParser.aliases(kind);
                for (var label:sheet.getRow(0)) {
                    String key=aliases.get(label.getStringCellValue());
                    var cell=data.getCell(label.getColumnIndex(),org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    if ("title".equals(key) || "name".equals(key)) cell.setCellValue("真实资料名称");
                    if ("duration".equals(key)) cell.setCellValue(123);
                    if ("releaseDate".equals(key)) cell.setCellValue(LocalDateTime.of(2026,11,1,0,0));
                    if ("phone".equals(key)) cell.setCellValue("010-12345678");
                    if ("lng".equals(key)) cell.setCellValue(116.1234567);
                    if ("totalRows".equals(key)) cell.setCellValue(8);
                    if ("totalCols".equals(key)) cell.setCellValue(10);
                }
                var out=new ByteArrayOutputStream(); workbook.write(out);
                var source=reader.read(null,List.of(file("template.xlsx",out.toByteArray())),kind);
                var fields=new ObjectMapper().readTree(source.text());
                assertThat(fields.path(kind.equals("MOVIE")?"title":"name").asText()).isEqualTo("真实资料名称");
                if (kind.equals("MOVIE")) { assertThat(fields.path("duration").asText()).isEqualTo("123"); assertThat(fields.path("releaseDate").asText()).isEqualTo("2026-11-01"); }
                if (kind.equals("CINEMA")) { assertThat(fields.path("phone").asText()).isEqualTo("010-12345678"); assertThat(fields.path("lng").asText()).isEqualTo("116.1234567"); }
                if (kind.equals("HALL")) assertThat(fields.path("totalRows").asText()).isEqualTo("8");
            }
        }
    }
    @Test void spreadsheetFormulaIsRejectedAndMultipleRowsArePreserved() throws Exception {
        try (var workbook=new XSSFWorkbook()) {
            var sheet=workbook.createSheet("资料"); sheet.createRow(0).createCell(0).setCellValue("电影名称"); sheet.getRow(0).createCell(1).setCellValue("时长（分钟）");
            sheet.createRow(1).createCell(0).setCellValue("片名"); sheet.getRow(1).createCell(1).setCellFormula("100+20");
            var out=new ByteArrayOutputStream(); workbook.write(out);
            assertThatThrownBy(()->reader.read(null,List.of(file("file.xlsx",out.toByteArray())))).isInstanceOf(BusinessException.class).hasMessageContaining("公式");
            sheet.getRow(1).getCell(1).setCellFormula(null); sheet.getRow(1).getCell(1).setCellValue(120); sheet.createRow(2).createCell(0).setCellValue("第二部"); var two=new ByteArrayOutputStream(); workbook.write(two);
            assertThat(new ObjectMapper().readTree(reader.read(null,List.of(file("file.xlsx",two.toByteArray()))).text()).size()).isEqualTo(2);
        }
    }
    @Test void aiSpreadsheetKeepsFreeformLabelsAndAllSheetsButRejectsFormulas() throws Exception {
        try (var workbook=new XSSFWorkbook()) {
            var sheet=workbook.createSheet("发行笔记");
            sheet.createRow(0).createCell(0).setCellValue("本月推荐影片");
            sheet.createRow(1).createCell(0).setCellValue("银幕旅途，林导演执导");
            sheet.getRow(1).createCell(1).setCellValue(120);
            workbook.createSheet("补充资料").createRow(0).createCell(0).setCellValue("周末来信，喜剧，95分钟");
            var out=new ByteArrayOutputStream(); workbook.write(out);
            var source=reader.read(null,List.of(file("原始资料.xlsx",out.toByteArray())),"MOVIE",true);
            assertThat(source.text()).contains("发行笔记","银幕旅途，林导演执导\t120","补充资料","周末来信");
            assertThatThrownBy(()->reader.read(null,List.of(file("原始资料.xlsx",out.toByteArray())),"MOVIE")).hasMessageContaining("对应字段");
            sheet.getRow(1).getCell(1).setCellFormula("100+20");
            var formula=new ByteArrayOutputStream(); workbook.write(formula);
            assertThatThrownBy(()->reader.read(null,List.of(file("公式.xlsx",formula.toByteArray())),"MOVIE",true)).hasMessageContaining("公式");
        }
    }
    @Test void pdfAndDocxExtractTextAndScannedPdfHasClearError() throws Exception {
        byte[] bytes;
        try (var doc=new PDDocument()) {
            var page=new PDPage(); doc.addPage(page);
            try (var stream=new PDPageContentStream(doc,page)) { stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12); stream.newLineAtOffset(40,700); stream.showText("Movie duration: 120 minutes"); stream.endText(); }
            var out=new ByteArrayOutputStream(); doc.save(out); bytes=out.toByteArray();
        }
        assertThat(reader.read(null,List.of(file("source.pdf",bytes))).text()).contains("120 minutes");
        try (var doc=new PDDocument()) { doc.addPage(new PDPage()); var out=new ByteArrayOutputStream(); doc.save(out); assertThatThrownBy(()->reader.read(null,List.of(file("scan.pdf",out.toByteArray())))).hasMessageContaining("扫描件"); }
        var zip=new ByteArrayOutputStream();
        try (var stream=new ZipOutputStream(zip)) { stream.putNextEntry(new ZipEntry("word/document.xml")); stream.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>片名：银幕旅途</w:t></w:r></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8)); }
        assertThat(reader.read(null,List.of(file("source.docx",zip.toByteArray()))).text()).contains("银幕旅途");
    }
    @Test void invalidEncodingSpoofedImageEmptyAndOversizedTextAreRejected() {
        assertThatThrownBy(()->reader.read(null,List.of())).hasMessageContaining("上传资料");
        assertThatThrownBy(()->reader.read("字".repeat(40001),List.of())).hasMessageContaining("40000");
        assertThatThrownBy(()->reader.read(null,List.of(file("bad.png","not image".getBytes(StandardCharsets.UTF_8))))).hasMessageContaining("无法读取");
        assertThatThrownBy(()->reader.read(null,List.of(file("bad.txt",new byte[]{(byte)0xff})))).hasMessageContaining("UTF-8");
    }
}
