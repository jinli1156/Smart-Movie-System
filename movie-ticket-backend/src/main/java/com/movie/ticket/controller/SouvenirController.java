package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.service.souvenir.SouvenirService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/souvenirs")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class SouvenirController {
    private final SouvenirService souvenirs;
    @GetMapping("/capabilities") public Result<Map<String, Object>> capabilities() { return Result.success(souvenirs.capabilities()); }
    @GetMapping("/orders/{orderNo}") public Result<List<SouvenirService.Ticket>> list(@PathVariable String orderNo) { return Result.success(souvenirs.list(orderNo)); }
    @PostMapping("/orders/{orderNo}") public Result<SouvenirService.Ticket> create(@PathVariable String orderNo, @Valid @RequestBody SouvenirService.Create input) {
        return Result.success(souvenirs.create(orderNo, input));
    }
    @GetMapping("/{id}/file") public ResponseEntity<Resource> file(@PathVariable String id) throws IOException {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=cinema-memory.png")
            .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(souvenirs.file(id)));
    }
}
