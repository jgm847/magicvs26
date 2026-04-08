package com.magicvs.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "metagame_archetypes")
@Data
@NoArgsConstructor
public class MetagameArchetype {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String tier;

    @Column(name = "meta_percentage")
    private Double metaPercentage;

    @Column(name = "win_rate")
    private Double winRate;

    @Column(name = "top_player")
    private String topPlayer;

    @Column(name = "creatures_count")
    private Integer creaturesCount;

    @Column(name = "spells_count")
    private Integer spellsCount;

    @Column(name = "lands_count")
    private Integer landsCount;

    @Column(name = "archetype_url")
    private String archetypeUrl;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @OneToMany(mappedBy = "archetype", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArchetypeDeckCard> deckCards = new ArrayList<>();

    public void addDeckCard(ArchetypeDeckCard card) {
        deckCards.add(card);
        card.setArchetype(this);
    }
}
