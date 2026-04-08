package com.magicvs.backend.repository;

import com.magicvs.backend.model.ThemeSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThemeRepository extends JpaRepository<ThemeSettings, Long> {
    Optional<ThemeSettings> findByActiveTrue();
    Optional<ThemeSettings> findByThemeName(String themeName);
}
