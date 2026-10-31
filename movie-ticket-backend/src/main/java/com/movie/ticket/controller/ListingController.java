package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.service.listing.ListingService;
import com.movie.ticket.service.listing.ListingImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.security.UserContext;
import java.util.Set;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/listing") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CINEMA_ADMIN')")
public class ListingController {
    private final ListingService service;
    private final ListingImportService imports;
    @PostMapping(value="/import",consumes="multipart/form-data") public Result<ListingImportService.Batch> importFile(@RequestParam String kind,
        @RequestParam(required=false) Long cinemaId,@RequestParam(required=false) String text,@RequestPart(required=false) List<MultipartFile> files,
        @RequestParam(defaultValue="AUTO") String importMode) {
        return Result.success(imports.create(kind,cinemaId,text,files,importMode));
    }
    @GetMapping("/batches/{id}") public Result<List<Map<String,Object>>> batch(@PathVariable String id) { return Result.success(service.batch(id)); }
    @GetMapping("/templates/{kind}") public ResponseEntity<byte[]> template(@PathVariable String kind,@RequestParam(defaultValue="xlsx") String format) throws java.io.IOException {
        if (!Set.of("MOVIE","CINEMA","HALL").contains(kind) || !Set.of("xlsx","csv","json").contains(format)) throw new BusinessException(400,"模板类型或格式无效");
        if (!kind.equals("HALL")) UserContext.requirePlatformAdmin();
        String filename=kind.toLowerCase(java.util.Locale.ROOT)+"-batch-template."+format;
        var resource=new ClassPathResource("listing/templates/"+filename);
        String contentType=switch(format) { case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; case "csv" -> "text/csv;charset=UTF-8"; default -> "application/json;charset=UTF-8"; };
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString())
            .contentType(MediaType.parseMediaType(contentType)).body(resource.getContentAsByteArray());
    }
    @GetMapping("/capabilities") public Result<Map<String,Object>> capabilities() { return Result.success(service.capabilities()); }
    @GetMapping("/drafts") public Result<Map<String,Object>> list(@RequestParam(required=false) String kind,@RequestParam(required=false) String status,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Result.success(service.list(kind,status,page,size)); }
    @PostMapping(value="/drafts",consumes="multipart/form-data") public Result<ListingService.Draft> create(@RequestParam String kind,
        @RequestParam(required=false) Long cinemaId,@RequestParam(required=false) String text,@RequestPart(required=false) List<MultipartFile> files) {
        return Result.success(service.create(kind,cinemaId,text,files));
    }
    @GetMapping("/drafts/{id}") public Result<ListingService.Draft> get(@PathVariable String id) { return Result.success(service.get(id)); }
    @PutMapping("/drafts/{id}") public Result<ListingService.Draft> save(@PathVariable String id,@RequestBody ListingService.Edit edit) { return Result.success(service.save(id,edit)); }
    @PostMapping("/drafts/{id}/reject") public Result<ListingService.Draft> reject(@PathVariable String id,@RequestBody ListingService.Review review) { return Result.success(service.reject(id,review)); }
    @PostMapping("/drafts/{id}/publish") public Result<ListingService.Draft> publish(@PathVariable String id,@RequestBody ListingService.Review review) { return Result.success(service.publish(id,review)); }
}
