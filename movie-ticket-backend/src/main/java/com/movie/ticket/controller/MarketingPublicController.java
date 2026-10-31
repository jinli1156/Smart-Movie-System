package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.service.marketing.MarketingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/marketing")
@RequiredArgsConstructor
public class MarketingPublicController {
    private final MarketingService marketing;
    @GetMapping("/placements") public ResponseEntity<Result<?>> placements(@RequestParam String slot,@RequestParam(required=false) Long cinemaId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(marketing.placements(slot,cinemaId)));
    }
    @GetMapping("/assets/{id}") public ResponseEntity<Resource> asset(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(new FileSystemResource(marketing.publicAsset(id)));
    }
}
