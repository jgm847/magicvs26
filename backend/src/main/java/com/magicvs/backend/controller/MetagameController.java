package com.magicvs.backend.controller;

import com.magicvs.backend.dto.ArchetypeDeckCardDTO;
import com.magicvs.backend.dto.MetagameArchetypeDTO;
import com.magicvs.backend.model.MetagameArchetype;
import com.magicvs.backend.repository.MetagameRepository;
import com.magicvs.backend.service.MetagameScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/metagame")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MetagameController {

    private final MetagameRepository metagameRepository;
    private final MetagameScraperService metagameScraperService;

    @GetMapping
    public ResponseEntity<List<MetagameArchetypeDTO>> getMetagame() {
        List<MetagameArchetype> archetypes = metagameRepository.findAll();
        
        List<MetagameArchetypeDTO> dtos = archetypes.stream().map(a -> 
            MetagameArchetypeDTO.builder()
                .id(a.getId())
                .name(a.getName())
                .tier(a.getTier())
                .metaPercentage(a.getMetaPercentage())
                .winRate(a.getWinRate())
                .topPlayer(a.getTopPlayer())
                .creaturesCount(a.getCreaturesCount())
                .spellsCount(a.getSpellsCount())
                .landsCount(a.getLandsCount())
                .cards(a.getDeckCards().stream().map(c -> 
                    ArchetypeDeckCardDTO.builder()
                        .name(c.getCard().getName())
                        .quantity(c.getQuantity())
                        .isSideboard(c.getIsSideboard())
                        .imageUrl(c.getCard().getScryfallUri()) // Assuming this holds an image URL or can be mapped
                        .scryfallId(c.getCard().getScryfallId().toString())
                        .build()
                ).collect(Collectors.toList()))
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/scrape")
    public ResponseEntity<String> triggerScrape() {
        new Thread(metagameScraperService::scrapeMetagame).start();
        return ResponseEntity.ok("Scraping started in background.");
    }
}
