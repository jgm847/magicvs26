package com.magicvs.backend.controller;

import com.magicvs.backend.dto.NewsDto;
import com.magicvs.backend.model.IngestionJobType;
import com.magicvs.backend.service.IngestionJobProducer;
import com.magicvs.backend.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Profile("backend")
public class NewsController {

    private final NewsService newsService;
    private final IngestionJobProducer ingestionJobProducer;

    @GetMapping
    public List<NewsDto> getNews() {
        return newsService.getAllNews();
    }

    @GetMapping("/last-updated")
    public Map<String, LocalDateTime> getLastUpdated() {
        return Map.of("date", newsService.getLastUpdateDate());
    }

    @PostMapping("/scrape")
    public ResponseEntity<Map<String, Object>> manualScrape() {
        UUID jobId = ingestionJobProducer.enqueue(IngestionJobType.NEWS_SYNC, Map.of());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "message", "Sincronización de noticias encolada",
                "jobId", jobId));
    }
}
