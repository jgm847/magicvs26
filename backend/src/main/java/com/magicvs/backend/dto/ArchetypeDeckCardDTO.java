package com.magicvs.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArchetypeDeckCardDTO {
    private String name;
    private Integer quantity;
    private Boolean isSideboard;
    private String imageUrl;
    private String scryfallId;
}
