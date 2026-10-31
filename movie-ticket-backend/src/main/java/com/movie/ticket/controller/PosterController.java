package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.service.poster.PosterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController @RequestMapping("/posters")
@PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class PosterController {
    private final PosterService posters;
    @GetMapping("/capabilities") public Result<Map<String, Object>> capabilities() { return Result.success(posters.capabilities()); }
    @GetMapping public Result<List<PosterService.Poster>> list() { return Result.success(posters.list()); }
    @GetMapping("/{id}") public Result<PosterService.Poster> get(@PathVariable String id) { return Result.success(posters.get(id)); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PosterService.Poster> create(@Valid @RequestPart("data") PosterService.Create input,
                                              @RequestPart(value = "photo", required = false) MultipartFile photo) throws IOException {
        return Result.success(posters.create(input, photo));
    }
    @PostMapping("/{id}/retry") public Result<PosterService.Poster> retry(@PathVariable String id) { return Result.success(posters.retry(id)); }
    @GetMapping("/{id}/file") public ResponseEntity<Resource> file(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=personal-movie-poster.png")
            .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(posters.file(id)));
    }
}
