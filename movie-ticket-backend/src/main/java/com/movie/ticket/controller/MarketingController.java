package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.service.marketing.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/admin/marketing")
@PreAuthorize("hasAnyRole('ADMIN','CINEMA_ADMIN')")
@RequiredArgsConstructor
public class MarketingController {
    private final MarketingService marketing;
    @GetMapping("/capabilities") public Result<?> capabilities() { return Result.success(marketing.capabilities()); }
    @GetMapping("/campaigns") public Result<?> list() { return Result.success(marketing.campaigns()); }
    @PostMapping("/campaigns") public Result<?> create(@Valid @RequestBody MarketingDtos.Campaign input) { return Result.success(marketing.createCampaign(input)); }
    @GetMapping("/campaigns/{id}") public Result<?> detail(@PathVariable String id) { return Result.success(marketing.detail(id)); }
    @PostMapping("/campaigns/{id}/batches") public Result<?> batch(@PathVariable String id,@Valid @RequestBody MarketingDtos.Batch input) { return Result.success(marketing.createBatch(id,input)); }
    @PostMapping("/tasks/{id}/retry") public Result<?> retry(@PathVariable String id,@RequestBody MarketingDtos.Retry input) { marketing.retry(id,input); return Result.success(); }
    @PostMapping("/campaigns/{id}/versions") public Result<?> version(@PathVariable String id,@Valid @RequestBody MarketingDtos.Version input) { return Result.success(marketing.saveVersion(id,input)); }
    @PostMapping("/versions/{id}/submit-review") public Result<?> submit(@PathVariable String id) { marketing.submit(id); return Result.success(); }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/versions/{id}/review") public Result<?> review(@PathVariable String id,@Valid @RequestBody MarketingDtos.Review input) { marketing.review(id,input); return Result.success(); }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/versions/{id}/publish") public Result<?> publish(@PathVariable String id,@Valid @RequestBody MarketingDtos.Publish input) { return Result.success(marketing.publish(id,input)); }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/publications/{id}/offline") public Result<?> offline(@PathVariable String id) { marketing.offline(id); return Result.success(); }
    @GetMapping("/assets/{id}/file") public ResponseEntity<Resource> file(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(new FileSystemResource(marketing.privateAsset(id)));
    }
}
