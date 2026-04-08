package com.magicvs.backend.service;

import com.magicvs.backend.model.ArchetypeDeckCard;
import com.magicvs.backend.model.Card;
import com.magicvs.backend.model.MetagameArchetype;
import com.magicvs.backend.repository.CardRepository;
import com.magicvs.backend.repository.MetagameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetagameScraperService {

    private final MetagameRepository metagameRepository;
    private final CardRepository cardRepository;

    private static final String BASE_URL = "https://www.mtggoldfish.com";
    private static final String METAGAME_URL = BASE_URL + "/metagame/standard#paper";

    @Scheduled(cron = "0 0 0 * * ?") // Daily at midnight
    @Transactional
    public void scrapeMetagame() {
        log.info("Starting daily metagame scraping from MTGGoldfish...");
        try {
            Document doc = Jsoup.connect(METAGAME_URL)
                    .userAgent("Mozilla/5.0")
                    .get();

            // Archetypes are typically in <div> with class archetype-tile within some container
            Elements archetypeTiles = doc.select(".archetype-tile");
            if (archetypeTiles.isEmpty()) {
                // Alternative selector for different views
                archetypeTiles = doc.select("tr.me-table-row");
            }

            // For this implementation, we focus on the most popular archetypes
            // Limit to top 15 to keep it efficient
            int count = 0;
            for (Element tile : archetypeTiles) {
                if (count >= 15) break;
                
                try {
                    processArchetypeTile(tile);
                    count++;
                } catch (Exception e) {
                    log.error("Error processing archetype tile: {}", e.getMessage());
                }
            }

            log.info("Metagame scraping completed. Processed {} archetypes.", count);
        } catch (IOException e) {
            log.error("Failed to connect to MTGGoldfish: {}", e.getMessage());
        }
    }

    private void processArchetypeTile(Element tile) throws IOException {
        String name = tile.select(".archetype-tile-description-title").text();
        if (name.isEmpty()) {
            name = tile.select(".deck-price-paper a").text();
        }
        
        String metaPercentageStr = tile.select(".archetype-tile-statistics").text();
        // Extract percentage (e.g. "15.0%")
        Double metaPercentage = extractDouble(metaPercentageStr);
        
        String archetypeUrl = tile.select(".archetype-tile-description-title a").attr("href");
        if (archetypeUrl.isEmpty()) {
            archetypeUrl = tile.select("a").first().attr("href");
        }
        
        if (!archetypeUrl.startsWith("http")) {
            archetypeUrl = BASE_URL + archetypeUrl;
        }

        log.info("Scraping full deck for archetype: {} (URL: {})", name, archetypeUrl);
        
        MetagameArchetype archetype = metagameRepository.findByName(name)
                .orElse(new MetagameArchetype());
        
        archetype.setName(name);
        archetype.setMetaPercentage(metaPercentage);
        archetype.setArchetypeUrl(archetypeUrl);
        archetype.setLastUpdated(LocalDateTime.now());
        
        // Clear existing cards to perform a full sync
        archetype.getDeckCards().clear();
        
        // Visit archetype page to get full list
        scrapeFullDeck(archetype, archetypeUrl);
        
        metagameRepository.save(archetype);
    }

    private void scrapeFullDeck(MetagameArchetype archetype, String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .get();

        // MTGGoldfish deck list structure
        // Cards are usually in tables or lists within .deck-view-deck-table
        Elements cardRows = doc.select("table.deck-view-deck-table tr");
        
        boolean isSideboard = false;
        int creatures = 0, spells = 0, lands = 0;

        for (Element row : cardRows) {
            // Check if we reached the sideboard section
            if (row.hasClass("deck-header") || row.text().toLowerCase().contains("sideboard")) {
                if (row.text().toLowerCase().contains("sideboard")) {
                    isSideboard = true;
                }
                continue;
            }

            String quantityStr = row.select("td.deck-col-qty").text();
            String cardName = row.select("td.deck-col-card a").text();
            
            if (quantityStr.isEmpty() || cardName.isEmpty()) continue;

            try {
                int qty = Integer.parseInt(quantityStr.trim());
                Optional<Card> cardOpt = cardRepository.findFirstByNameIgnoreCase(cardName);
                
                if (cardOpt.isPresent()) {
                    Card card = cardOpt.get();
                    archetype.addDeckCard(new ArchetypeDeckCard(archetype, card, qty, isSideboard));
                    
                    if (!isSideboard) {
                        String typeLine = card.getTypeLine().toLowerCase();
                        if (typeLine.contains("creature")) creatures += qty;
                        else if (typeLine.contains("land")) lands += qty;
                        else if (typeLine.contains("instant") || typeLine.contains("sorcery") || 
                                 typeLine.contains("enchantment") || typeLine.contains("artifact") || 
                                 typeLine.contains("planeswalker")) spells += qty;
                    }
                } else {
                    log.warn("Card not found in DB: {}", cardName);
                }
            } catch (NumberFormatException e) {
                // Skip rows with invalid quantity
            }
        }
        
        archetype.setCreaturesCount(creatures);
        archetype.setSpellsCount(spells);
        archetype.setLandsCount(lands);
        
        // Tier logic (simplified for this example)
        if (archetype.getMetaPercentage() != null) {
            if (archetype.getMetaPercentage() > 8.0) archetype.setTier("1");
            else if (archetype.getMetaPercentage() > 4.0) archetype.setTier("2");
            else archetype.setTier("3");
        }
    }

    private Double extractDouble(String text) {
        if (text == null || text.isEmpty()) return 0.0;
        try {
            String cleaned = text.replaceAll("[^0-9.]", "");
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
