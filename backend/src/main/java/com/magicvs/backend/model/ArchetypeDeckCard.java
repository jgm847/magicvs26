package com.magicvs.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "archetype_deck_cards")
@Data
@NoArgsConstructor
public class ArchetypeDeckCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archetype_id", nullable = false)
    private MetagameArchetype archetype;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    private Integer quantity;

    @Column(name = "is_sideboard")
    private Boolean isSideboard;

    public ArchetypeDeckCard(MetagameArchetype archetype, Card card, Integer quantity, Boolean isSideboard) {
        this.archetype = archetype;
        this.card = card;
        this.quantity = quantity;
        this.isSideboard = isSideboard;
    }
}
