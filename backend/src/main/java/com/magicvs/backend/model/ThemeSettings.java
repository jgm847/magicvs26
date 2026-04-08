package com.magicvs.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "theme_settings")
@Data
@NoArgsConstructor
public class ThemeSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theme_name", unique = true, nullable = false)
    private String themeName;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "secondary_color")
    private String secondaryColor;

    @Column(name = "tertiary_color")
    private String tertiaryColor;

    @Column(name = "neutral_color")
    private String neutralColor;

    @Column(name = "headline_font")
    private String headlineFont;

    @Column(name = "body_font")
    private String bodyFont;

    private Boolean active;

    public ThemeSettings(String themeName, String primaryColor, String secondaryColor, String tertiaryColor, String neutralColor, String headlineFont, String bodyFont, Boolean active) {
        this.themeName = themeName;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.tertiaryColor = tertiaryColor;
        this.neutralColor = neutralColor;
        this.headlineFont = headlineFont;
        this.bodyFont = bodyFont;
        this.active = active;
    }
}
