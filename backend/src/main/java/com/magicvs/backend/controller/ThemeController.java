package com.magicvs.backend.controller;

import com.magicvs.backend.model.ThemeSettings;
import com.magicvs.backend.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/theme")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ThemeController {

    private final ThemeService themeService;

    @GetMapping("/active")
    public ResponseEntity<ThemeSettings> getActiveTheme() {
        return themeService.getActiveTheme()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
