package com.magicvs.backend.controller;

import com.magicvs.backend.model.Card;
import com.magicvs.backend.service.ScryfallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cards/import")
public class CardImportController {

    @Autowired
    private ScryfallService scryfallService;

    @PostMapping("/standard")
    public ResponseEntity<Map<String, Object>> importStandard() {
        int count = scryfallService.importAllCardsSpanish();
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Importación masiva (español + standard) vía disco completada");
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    /**
     * Importa una carta por nombre.
     */
    @PostMapping("/name")
    public ResponseEntity<Map<String, Object>> importByName(
            @RequestParam String name,
            @RequestParam(defaultValue = "true") boolean onlyStandard) {
        
        Card card = scryfallService.importCardByName(name, onlyStandard);
        Map<String, Object> response = new HashMap<>();
        if (card != null) {
            response.put("message", "Carta importada con éxito");
            response.put("cardName", card.getName());
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "No se pudo encontrar o importar la carta");
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Importa todas las cartas de un set.
     */
    @PostMapping("/set")
    public ResponseEntity<Map<String, Object>> importBySet(
            @RequestParam String code,
            @RequestParam(defaultValue = "true") boolean onlyStandard) {
        
        int count = scryfallService.importCardsBySet(code, onlyStandard);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Importación del set " + code + " completada");
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    /**
     * Importación masiva de todas las cartas en español vía Bulk Data.
     */
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> importBulk() {
        int count = scryfallService.importAllCardsSpanish();
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Importación masiva de cartas (español) completada exitosamente desde el archivo local");
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    /**
     * Importación masiva de todas las reglas (rulings) vía Bulk Data.
     */
    @PostMapping("/rulings")
    public ResponseEntity<Map<String, Object>> importRulings() {
        int count = scryfallService.importRulings();
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Importación masiva de reglas vía disco completada");
        response.put("count", count);
        return ResponseEntity.ok(response);
    }
}
