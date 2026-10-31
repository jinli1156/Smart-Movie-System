package com.movie.ticket.service.listing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.movie.ticket.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.*;

/** Reads local workbook values only. Formula and external-link evaluation are never used. */
@Component
public class ListingSpreadsheetReader {
    private final ObjectMapper json=new ObjectMapper();
    /** Preserves arbitrary sheet layouts for AI without requiring known column labels. */
    public String readText(byte[] bytes) throws IOException {
        try (var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder content=new StringBuilder();
            DataFormatter formatter=new DataFormatter(Locale.ROOT);
            int rows=0;
            boolean hasContent=false;
            for (Sheet sheet:book) {
                if (sheet.getLastRowNum()>200 || (rows+=sheet.getPhysicalNumberOfRows())>200) throw new BusinessException(400,"Excel 请控制在 200 行以内");
                content.append("工作表：").append(sheet.getSheetName()).append('\n');
                for (Row row:sheet) {
                    if (row.getLastCellNum()>30) throw new BusinessException(400,"Excel 最多支持 30 列");
                    for (int c=0;c<row.getLastCellNum();c++) {
                        if (c>0) content.append('\t');
                        String cell=value(row.getCell(c),"",formatter);
                        hasContent|=!cell.isBlank(); content.append(cell);
                        if (content.length()>40000) throw new BusinessException(400,"资料文字总计不能超过 40000 字，请拆分后上传");
                    }
                    content.append('\n');
                }
            }
            return hasContent?content.toString():"";
        }
    }
    public String read(byte[] bytes,String kind) throws IOException {
        try (var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets()==0) throw new BusinessException(400,"Excel 没有工作表");
            Sheet sheet=book.getSheet("填写资料"); if (sheet==null) sheet=book.getSheetAt(0);
            if (sheet.getLastRowNum()>200 || sheet.getPhysicalNumberOfRows()>200) throw new BusinessException(400,"Excel 请控制在 200 行以内，每批最多 20 条资料");
            Map<String,String> aliases=ListingRecordParser.aliases(kind);
            DataFormatter formatter=new DataFormatter(Locale.ROOT);
            Row header=sheet.getRow(sheet.getFirstRowNum());
            if (header==null) throw new BusinessException(400,"Excel 表头为空");
            ObjectNode fields=json.createObjectNode();
            if (text(header.getCell(0),formatter).strip().equals("字段")) {
                Set<String> seen=new HashSet<>();
                for (int r=header.getRowNum()+1;r<=sheet.getLastRowNum();r++) {
                    Row row=sheet.getRow(r); if (row==null) continue;
                    String label=text(row.getCell(0),formatter).strip(); String key=aliases.get(label);
                    if (key==null || key.equals("status")) continue;
                    if (!seen.add(key)) throw new BusinessException(400,"Excel 字段重复："+label);
                    fields.put(key,value(row.getCell(1),key,formatter));
                }
            } else {
                if (header.getLastCellNum()>30) throw new BusinessException(400,"Excel 最多支持 30 列");
                List<Row> records=new ArrayList<>();
                for (int r=header.getRowNum()+1;r<=sheet.getLastRowNum();r++) {
                    Row row=sheet.getRow(r); if (row==null) continue;
                    boolean any=false; for (Cell c:row) { if (!text(c,formatter).isBlank()) any=true; }
                    if (any) records.add(row);
                }
                if (records.isEmpty()) throw new BusinessException(400,"Excel 没有资料行，请填写后上传");
                ListingRecordParser.limit(records.size());
                Set<String> seen=new HashSet<>();
                for (Cell label:header) { String key=aliases.get(text(label,formatter).strip()); if (key!=null && !seen.add(key)) throw new BusinessException(400,"Excel 表头字段重复"); }
                var output=json.createArrayNode();
                for (Row row:records) {
                    ObjectNode item=json.createObjectNode();
                    for (int c=0;c<header.getLastCellNum();c++) {
                        String key=aliases.get(text(header.getCell(c),formatter).strip());
                        if (key!=null && !key.equals("status")) item.put(key,value(row.getCell(c),key,formatter));
                    }
                    if (item.isEmpty()) throw new BusinessException(400,"Excel 未发现对应字段，请下载相应类型模板");
                    output.add(item);
                }
                return output.size()==1?output.get(0).toString():output.toString();
            }
            if (fields.isEmpty()) throw new BusinessException(400,"Excel 未发现对应字段，请下载相应类型的 Excel 模板");
            return fields.toString();
        }
    }
    private String value(Cell cell,String key,DataFormatter formatter) {
        String iso=isoDate(cell);
        if (iso!=null) return key.equals("releaseDate")?java.time.LocalDate.parse(iso.substring(0,10)).toString():iso;
        if (cell==null || cell.getCellType()==CellType.BLANK) return "";
        if (cell.getCellType()==CellType.FORMULA) throw new BusinessException(400,"Excel 请填写实际值，不支持公式单元格");
        if (cell.getCellType()==CellType.ERROR) throw new BusinessException(400,"Excel 存在错误单元格，请修正后上传");
        if (cell.getCellType()==CellType.NUMERIC) {
            if (key.equals("releaseDate") && DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            return java.math.BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).strip();
    }
    // Some spreadsheet exporters use ISO date cells, which POI's transitional cell-type enum does not expose.
    private String isoDate(Cell cell) {
        if (!(cell instanceof org.apache.poi.xssf.usermodel.XSSFCell xssf)) return null;
        var attribute=xssf.getCTCell().getDomNode().getAttributes().getNamedItem("t");
        return attribute!=null && attribute.getNodeValue().equals("d")?xssf.getCTCell().getV():null;
    }
    private String text(Cell cell,DataFormatter formatter) {
        String iso=isoDate(cell); return iso!=null?iso:formatter.formatCellValue(cell);
    }
}
