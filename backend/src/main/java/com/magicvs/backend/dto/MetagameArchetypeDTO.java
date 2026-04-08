package com.magicvs.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MetagameArchetypeDTO {
    private Long id;
    private String name;
    private String tier;
    private Double metaPercentage;
    private Double winRate;
    private String topPlayer;
    private Integer creaturesCount;
    private Integer spellsCount;
    private Integer landsCount;
    private List<ArchetypeDeckCardDTO> cards;
}
