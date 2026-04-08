package com.magicvs.backend.service;

import com.magicvs.backend.model.ThemeSettings;
import com.magicvs.backend.repository.ThemeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ThemeService {

    private final ThemeRepository themeRepository;

    @PostConstruct
    public void init() {
        if (themeRepository.findByThemeName("Aetheric Edge").isEmpty()) {
            log.info("Initializing 'Aetheric Edge' theme settings...");
            ThemeSettings aethericEdge = new ThemeSettings(
                    "Aetheric Edge",
                    "#8E44AD", // Primary (Purple)
                    "#3498DB", // Secondary (Blue)
                    "#F1C40F", // Tertiary (Yellow)
                    "#121212", // Neutral (Dark)
                    "Space Grotesk",
                    "Manrope",
                    true
            );
            themeRepository.save(aethericEdge);
        }
    }

    public Optional<ThemeSettings> getActiveTheme() {
        return themeRepository.findByActiveTrue();
    }

    @Transactional
    public void setActiveTheme(String themeName) {
        themeRepository.findAll().forEach(t -> t.setActive(false));
        themeRepository.findByThemeName(themeName).ifPresent(t -> t.setActive(true));
    }
}
