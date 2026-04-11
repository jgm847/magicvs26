package com.magicvs.backend.service;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectMapper;
import com.magicvs.backend.model.*;
import com.magicvs.backend.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.databind.util.TokenBuffer;

@Service
public class ScryfallService {

    private static final Logger logger = LoggerFactory.getLogger(ScryfallService.class);
    private static final String SCRYFALL_API_BASE = "https://api.scryfall.com";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardSetRepository cardSetRepository;

    @Autowired
    private CardFaceRepository cardFaceRepository;

    @Autowired
    private CardLegalityRepository cardLegalityRepository;

    @Autowired
    private CardPriceRepository cardPriceRepository;

    @Autowired
    private RulingRepository rulingRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    @Value("${scryfall.import.temp-dir:#{systemProperties['java.io.tmpdir']}}")
    private String tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean isImporting = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        long cardCount = cardRepository.count();
        long rulingCount = rulingRepository.count();

        if (cardCount == 0) {
            logger.info("Base de datos de cartas vacía. Iniciando importación completa...");
            int importedCards = importAllCardsSpanish();
            if (importedCards > 0) {
                importRulings();
            }
        } else {
            // DIAGNÓSTICO: Comprobar si las cartas existentes tienen oracle_id
            cardRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 5)).getContent().forEach(c -> {
                logger.info("DIAGNÓSTICO: Carta '{}' - oracle_id: {}", c.getName(), c.getOracleId());
            });

            if (rulingCount == 0) {
                logger.info("Cartas detectadas ({}) pero las reglas están vacías. Importando reglas...", cardCount);
                importRulings();
            } else {
                logger.info("Base de datos de cartas ({}) y reglas ({}) ya pobladas.", cardCount, rulingCount);
            }
        }
    }

    /**
     * Tarea programada que intenta repoblar la base de datos si está vacía.
     * Se ejecuta cada hora, con un retraso inicial de 5 minutos para no chocar con el arranque.
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 300000)
    public void retryImportIfEmpty() {
        if (cardRepository.count() == 0 || rulingRepository.count() == 0) {
            if (isImporting.get()) {
                logger.info("Importación ya en curso o programada. Omitiendo tarea programada.");
                return;
            }
            logger.info("Base de datos incompleta detectada. Reintentando importación masiva...");
            onApplicationReady();
        }
    }

    /**
     * Importa una carta por su nombre.
     */
    @Transactional
    public Card importCardByName(String name, boolean onlyStandard) {
        String query = name;
        if (onlyStandard) {
            query += " f:standard";
        }
        String url = SCRYFALL_API_BASE + "/cards/named?fuzzy=" + query;
        try {
            JsonNode root = restTemplate.getForObject(url, JsonNode.class);
            if (root != null) {
                return saveOrUpdateCard(root);
            }
        } catch (Exception e) {
            logger.error("Error al importar carta por nombre: {}", name, e);
        }
        return null;
    }

    /**
     * Importa todas las cartas de una expansión específica.
     */
    @Transactional
    public int importCardsBySet(String setCode, boolean onlyStandard) {
        String query = "set:" + setCode;
        if (onlyStandard) {
            query += " f:standard";
        }
        String url = SCRYFALL_API_BASE + "/cards/search?q=" + query;
        return fetchAndSaveAll(url);
    }

    @Transactional
    public int importAllCardsSpanish() {
        if (!isImporting.compareAndSet(false, true)) {
            logger.warn("Ya hay una importación masiva de cartas en curso.");
            return 0;
        }
        
        File tempFile = null;
        try {
            tempFile = downloadBulkFile("all_cards");
            if (tempFile == null) return 0;

            logger.info("Iniciando procesamiento de archivo local: {}", tempFile.getAbsolutePath());
            
            int count = 0;
            int savedCount = 0;
            JsonFactory factory = new JsonFactory();
            
            try (InputStream is = new FileInputStream(tempFile);
                 JsonParser parser = factory.createParser(is)) {
                 
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    throw new RuntimeException("Se esperaba un array JSON al inicio del archivo bulk");
                }

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    // Estamos en START_OBJECT de una carta
                    count++;
                    
                    // Usamos un TokenBuffer para capturar los tokens solo si decidimos procesar la carta
                    TokenBuffer tb = TokenBuffer.forBuffering(parser, null);
                    tb.copyCurrentEvent(parser); // START_OBJECT
                    
                    boolean isSpanish = false;
                    boolean isStandard = false;
                    boolean scanComplete = false;

                    // Escaneo rápido de campos sin construir el árbol completo
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String fieldName = parser.currentName();
                        tb.copyCurrentEvent(parser); // Record field name
                        parser.nextToken(); // Move to value

                        if ("lang".equals(fieldName)) {
                            isSpanish = "es".equals(parser.getText());
                            tb.copyCurrentStructure(parser); // Record value and advance
                        } else if ("legalities".equals(fieldName)) {
                            tb.copyCurrentEvent(parser); // Record START_OBJECT
                            while (parser.nextToken() != JsonToken.END_OBJECT) {
                                String legField = parser.currentName();
                                tb.copyCurrentEvent(parser); // Record sub-field name
                                parser.nextToken(); // Move to sub-value
                                if ("standard".equals(legField)) {
                                    isStandard = "legal".equals(parser.getText());
                                }
                                tb.copyCurrentStructure(parser); // Record sub-value and advance
                            }
                            tb.copyCurrentEvent(parser); // Record END_OBJECT
                        } else {
                            // Copiar valor (sea lo que sea) y avanzar
                            tb.copyCurrentStructure(parser);
                        }
                    }
                    tb.copyCurrentEvent(parser); // Record END_OBJECT (de la carta)

                    // Solo si cumple el filtro, construimos el JsonNode a partir del buffer
                    if (isSpanish && isStandard) {
                        try (JsonParser bufferParser = tb.asParser()) {
                            JsonNode cardNode = objectMapper.readTree(bufferParser);
                            
                            // DEBUG: Verificar presencia de oracle_id
                            if (!cardNode.has("oracle_id")) {
                                logger.warn("Carta encontrada sin oracle_id: {}", cardNode.has("name") ? cardNode.get("name").asText() : "desconocida");
                            }

                            saveOrUpdateCard(cardNode);
                            savedCount++;
                            
                            if (savedCount % 300 == 0) {
                                entityManager.flush();
                                entityManager.clear();
                                logger.info("Procesadas {} cartas en español (de {} escaneadas)...", savedCount, count);
                            }
                        } catch (Exception e) {
                            logger.error("Error al procesar carta válida tras escaneo", e);
                        }
                    }

                    if (count % 10000 == 0) {
                        logger.info("Escaneadas {} cartas del archivo masivo...", count);
                    }
                }
            }
            logger.info("Importación de cartas finalizada con éxito. Se han guardado {} registros.", savedCount);
            return savedCount;
        } catch (Exception e) {
            logger.error("Error durante la importación desde archivo local", e);
            return 0;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
                logger.info("Archivo temporal eliminado: {}", tempFile.getAbsolutePath());
            }
            isImporting.set(false);
        }
    }

    /**
     * Importa todas las reglas (rulings) usando el sistema de Bulk Data de Scryfall.
     */
    @Transactional
    public int importRulings() {
        File tempFile = null;
        try {
            tempFile = downloadBulkFile("rulings");
            if (tempFile == null) return 0;

            logger.info("Iniciando procesamiento de reglas desde archivo local: {}", tempFile.getAbsolutePath());
            
            int count = 0;
            int savedCount = 0;
            JsonFactory factory = new JsonFactory();
            
            try (InputStream is = new FileInputStream(tempFile);
                 JsonParser parser = factory.createParser(is)) {
                 
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    throw new RuntimeException("Se esperaba un array JSON al inicio del archivo de reglas");
                }

                // IMPORTANTE: Avanzamos al primer objeto dentro del array
                parser.nextToken(); 

                try (MappingIterator<JsonNode> it = objectMapper.readerFor(JsonNode.class).readValues(parser)) {
                    while (it.hasNextValue()) {
                        JsonNode rulingNode = it.nextValue();
                        count++;
                        try {
                            if (rulingNode.has("oracle_id") && !rulingNode.get("oracle_id").isNull()) {
                                UUID oracleId = UUID.fromString(rulingNode.get("oracle_id").asText());
                                List<Card> cards = cardRepository.findByOracleId(oracleId);
                                
                                if (!cards.isEmpty()) {
                                    for (Card card : cards) {
                                        saveOrUpdateRuling(card, rulingNode);
                                        savedCount++;
                                    }
                                }
                            }
                            
                            if (count % 10000 == 0) {
                                logger.info("Escaneadas {} reglas de Scryfall. Asociadas {} hasta ahora.", count, savedCount);
                            }
                            
                            if (savedCount > 0 && savedCount % 1000 == 0) {
                                entityManager.flush();
                                entityManager.clear();
                                logger.info("Guardadas e indexadas {} reglas en base de datos...", savedCount);
                            }
                        } catch (Exception e) {
                            logger.warn("Saltando regla posiblemente malformada en entrada {}: {}", count, e.getMessage());
                        }
                    }
                }
            }
            logger.info("Importación de reglas finalizada. Total escaneadas: {}. Total asociadas: {}.", count, savedCount);
            return savedCount;
        } catch (Exception e) {
            logger.error("Error durante la importación de reglas desde archivo local", e);
            return 0;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Descarga el archivo de Bulk Data a una ubicación temporal en disco.
     */
    private File downloadBulkFile(String type) {
        String downloadUri = getBulkDataDownloadUri(type);
        if (downloadUri == null) {
            logger.error("No se pudo obtener la URL de descarga para {}", type);
            return null;
        }

        try {
            Path tempPath = Paths.get(tempDir, "scryfall_bulk_" + type + ".json");
            File file = tempPath.toFile();
            
            // Asegurar que el directorio existe
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            // Comprobación de espacio (mínimo 3GB libres para all_cards)
            long requiredSpace = type.equals("all_cards") ? 3000L * 1024 * 1024 : 500L * 1024 * 1024;
            long usableSpace = file.getParentFile().getUsableSpace();
            if (usableSpace < requiredSpace) {
                logger.error("ESPACIO EN DISCO INSUFICIENTE en {}. Disponible: {} MB, Requerido: {} MB", 
                    tempDir, usableSpace / (1024 * 1024), requiredSpace / (1024 * 1024));
                return null;
            }

            logger.info("Iniciando descarga de Bulk Data '{}' desde: {}", type, downloadUri);
            
            return restTemplate.execute(downloadUri, org.springframework.http.HttpMethod.GET, null, response -> {
                long contentLength = response.getHeaders().getContentLength();
                if (contentLength > 0) {
                    logger.info("Tamaño esperado del archivo '{}': {} MB", type, contentLength / (1024 * 1024));
                }

                try (InputStream is = response.getBody();
                     FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[1024 * 64]; // 64KB buffer
                    int bytesRead;
                    long totalRead = 0;
                    long lastLogged = 0;
                    
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        
                        // Log cada 100MB
                        if (totalRead - lastLogged > 100 * 1024 * 1024) {
                            if (contentLength > 0) {
                                int percent = (int) ((totalRead * 100) / contentLength);
                                logger.info("Progreso de descarga '{}': {}% ({} MB)...", type, percent, totalRead / (1024 * 1024));
                            } else {
                                logger.info("Descargados {} MB para '{}'...", totalRead / (1024 * 1024), type);
                            }
                            lastLogged = totalRead;
                        }
                    }
                    fos.flush();
                    
                    if (contentLength > 0 && totalRead < contentLength) {
                        throw new RuntimeException("Descarga incompleta: se recibieron " + totalRead + " bytes de " + contentLength);
                    }
                }
                logger.info("Descarga finalizada: {} ({} MB)", file.getAbsolutePath(), file.length() / (1024 * 1024));
                return file;
            });
        } catch (Exception e) {
            logger.error("Error al descargar el archivo bulk para {}: {}", type, e.getMessage());
            return null;
        }
    }

    private String getBulkDataDownloadUri(String type) {
        String url = SCRYFALL_API_BASE + "/bulk-data/" + type;
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            if (response != null && response.has("download_uri")) {
                return response.get("download_uri").asText();
            }
        } catch (Exception e) {
            logger.error("Error al obtener metadatos de bulk-data para {}", type, e);
        }
        return null;
    }

    private void saveOrUpdateRuling(Card card, JsonNode node) {
        // En una implementación real, buscaríamos si ya existe para evitar duplicados
        // Por simplicidad en este MVP, borramos existentes antes si fuera necesario
        // Pero el modelo actual no tiene un ID único natural claro para rulings en Scryfall (no hay ruling_id)
        // Scryfall recomienda usar la combinación de oracle_id, source, published_at y comment
        
        Ruling ruling = new Ruling();
        ruling.setCard(card);
        ruling.setSource(node.get("source").asText());
        ruling.setPublishedAt(LocalDate.parse(node.get("published_at").asText()));
        ruling.setComment(node.get("comment").asText());
        ruling.setRawJson(node.toString());
        rulingRepository.save(ruling);
    }

    private int fetchAndSaveAll(String initialUrl) {
        int count = 0;
        String nextUrl = initialUrl;
        boolean errorOcurrido = false;

        while (nextUrl != null) {
            JsonNode response = fetchWithRetry(nextUrl);
            if (response == null || !response.has("data")) {
                errorOcurrido = true;
                break;
            }

            JsonNode data = response.get("data");
            for (JsonNode cardNode : data) {
                try {
                    saveOrUpdateCard(cardNode);
                    count++;
                } catch (Exception e) {
                    logger.error("Error al guardar carta individual: {}", cardNode.has("name") ? cardNode.get("name").asText() : "desconocida", e);
                }
            }

            if (response.has("has_more") && response.get("has_more").asBoolean()) {
                nextUrl = response.get("next_page").asText();
                // Rate limiting: Scryfall agradece 100ms entre peticiones
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorOcurrido = true;
                    break;
                }
            } else {
                nextUrl = null;
            }
        }

        if (errorOcurrido && nextUrl != null) {
            logger.error("LA IMPORTACIÓN SE HA DETENIDO PREMATURAMENTE. Se han importado {} cartas pero faltaban más páginas.", count);
        }
        
        return count;
    }

    /**
     * Realiza una petición GET con lógica de reintentos para fallos transitorios.
     */
    private JsonNode fetchWithRetry(String url) {
        int maxRetries = 3;
        int retryCount = 0;
        long backoffMs = 2000; // 2 segundos iniciales

        while (retryCount < maxRetries) {
            try {
                logger.info("Fetching cards from: {}", url);
                return restTemplate.getForObject(url, JsonNode.class);
            } catch (HttpStatusCodeException e) {
                retryCount++;
                int statusCode = e.getStatusCode().value();
                
                if (retryCount >= maxRetries) {
                    logger.error("Error persistente tras {} reintentos al consultar Scryfall: {} {}", maxRetries, statusCode, e.getStatusText());
                    break;
                }
                
                // Reintentar en caso de 503 (mantenimiento), 429 (rate limit) o 404 (a veces transitorio en paginación)
                if (statusCode == 503 || statusCode == 429 || statusCode == 404) {
                    logger.warn("Scryfall respondió con {}. Reintentando en {}ms... (Intento {}/{})", 
                        statusCode, backoffMs, retryCount, maxRetries);
                    try {
                        Thread.sleep(backoffMs);
                        backoffMs *= 2; // Exponencial
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    logger.error("Error no recuperable al consultar Scryfall: {} {}", statusCode, e.getStatusText());
                    break;
                }
            } catch (Exception e) {
                logger.error("Error inesperado al consultar Scryfall", e);
                break;
            }
        }
        return null;
    }

    private Card saveOrUpdateCard(JsonNode node) {
        UUID scryfallId = UUID.fromString(node.get("id").asText());
        Card card = cardRepository.findByScryfallId(scryfallId).orElse(new Card());

        card.setScryfallId(scryfallId);
        card.setOracleId(node.has("oracle_id") ? UUID.fromString(node.get("oracle_id").asText()) : null);
        card.setName(node.get("name").asText());
        card.setLang(node.has("lang") ? node.get("lang").asText() : "en");
        card.setReleasedAt(node.has("released_at") ? LocalDate.parse(node.get("released_at").asText()) : null);
        card.setLayout(node.has("layout") ? node.get("layout").asText() : null);
        card.setManaCost(node.has("mana_cost") ? node.get("mana_cost").asText() : null);
        card.setCmc(node.has("cmc") ? new BigDecimal(node.get("cmc").asText()) : BigDecimal.ZERO);
        card.setTypeLine(node.has("type_line") ? node.get("type_line").asText() : null);
        card.setOracleText(node.has("oracle_text") ? node.get("oracle_text").asText() : null);
        card.setPower(node.has("power") ? node.get("power").asText() : null);
        card.setToughness(node.has("toughness") ? node.get("toughness").asText() : null);
        card.setLoyalty(node.has("loyalty") ? node.get("loyalty").asText() : null);
        card.setDefense(node.has("defense") ? node.get("defense").asText() : null);
        card.setCollectorNumber(node.get("collector_number").asText());
        card.setRarity(node.get("rarity").asText());
        card.setFlavorText(node.has("flavor_text") ? node.get("flavor_text").asText() : null);
        card.setArtist(node.has("artist") ? node.get("artist").asText() : null);
        card.setReserved(node.has("reserved") ? node.get("reserved").asBoolean() : false);
        card.setReprint(node.has("reprint") ? node.get("reprint").asBoolean() : false);
        card.setDigital(node.has("digital") ? node.get("digital").asBoolean() : false);
        card.setFoil(node.has("foil") ? node.get("foil").asBoolean() : true);
        card.setNonfoil(node.has("nonfoil") ? node.get("nonfoil").asBoolean() : true);
        card.setPromo(node.has("promo") ? node.get("promo").asBoolean() : false);
        card.setFullArt(node.has("full_art") ? node.get("full_art").asBoolean() : false);
        card.setTextless(node.has("textless") ? node.get("textless").asBoolean() : false);
        card.setScryfallUri(node.has("scryfall_uri") ? node.get("scryfall_uri").asText() : null);
        card.setPrintsSearchUri(node.has("prints_search_uri") ? node.get("prints_search_uri").asText() : null);
        card.setRulingsUri(node.has("rulings_uri") ? node.get("rulings_uri").asText() : null);
        
        // IDs externos
        card.setArenaId(node.has("arena_id") ? node.get("arena_id").asInt() : null);
        card.setMtgoId(node.has("mtgo_id") ? node.get("mtgo_id").asInt() : null);
        card.setTcgplayerId(node.has("tcgplayer_id") ? node.get("tcgplayer_id").asInt() : null);
        card.setCardmarketId(node.has("cardmarket_id") ? node.get("cardmarket_id").asInt() : null);
        card.setEdhrecRank(node.has("edhrec_rank") ? node.get("edhrec_rank").asInt() : null);

        // Imágenes para cartas de una sola cara
        if (node.has("image_uris")) {
            JsonNode images = node.get("image_uris");
            card.setSmallImageUri(images.has("small") ? images.get("small").asText() : null);
            card.setNormalImageUri(images.has("normal") ? images.get("normal").asText() : null);
            card.setLargeImageUri(images.has("large") ? images.get("large").asText() : null);
            card.setPngImageUri(images.has("png") ? images.get("png").asText() : null);
            card.setArtCropUri(images.has("art_crop") ? images.get("art_crop").asText() : null);
            card.setBorderCropUri(images.has("border_crop") ? images.get("border_crop").asText() : null);
        }

        // Datos JSON complejos
        card.setColorsJson(node.has("colors") ? node.get("colors").toString() : "[]");
        card.setColorIdentityJson(node.has("color_identity") ? node.get("color_identity").toString() : "[]");
        card.setGamesJson(node.has("games") ? node.get("games").toString() : "[]");
        card.setKeywordsJson(node.has("keywords") ? node.get("keywords").toString() : "[]");
        card.setProducedManaJson(node.has("produced_mana") ? node.get("produced_mana").toString() : "[]");
        card.setPurchaseUrisJson(node.has("purchase_uris") ? node.get("purchase_uris").toString() : "{}");
        card.setRelatedUrisJson(node.has("related_uris") ? node.get("related_uris").toString() : "{}");
        card.setRawJson(node.toString());
        card.setSyncedAt(LocalDateTime.now());

        // Asignar Set
        String setCode = node.get("set").asText();
        CardSet cardSet = cardSetRepository.findByCode(setCode).orElseGet(() -> createNewSet(node));
        card.setSet(cardSet);

        // Guardar primero la carta para tener ID si es nueva
        card = cardRepository.save(card);

        // Legalidades
        if (node.has("legalities")) {
            updateLegalities(card, node.get("legalities"));
        }

        // Precios
        if (node.has("prices")) {
            updatePrices(card, node.get("prices"));
        }

        // Caras (si tiene)
        if (node.has("card_faces")) {
            updateFaces(card, node.get("card_faces"));
        }

        return card;
    }

    private CardSet createNewSet(JsonNode node) {
        CardSet set = new CardSet();
        set.setScryfallId(UUID.fromString(node.get("set_id").asText()));
        set.setCode(node.get("set").asText());
        set.setName(node.get("set_name").asText());
        set.setReleasedAt(LocalDate.parse(node.get("released_at").asText())); // Aproximado si no hay set info
        set.setSetType(node.has("set_type") ? node.get("set_type").asText() : "unknown");
        set.setDigital(node.has("digital") ? node.get("digital").asBoolean() : false);
        return cardSetRepository.save(set);
    }

    private void updateLegalities(Card card, JsonNode legalitiesNode) {
        // Eliminar existentes para este card
        cardLegalityRepository.deleteByCard(card);
        
        for (Map.Entry<String, JsonNode> entry : legalitiesNode.properties()) {
            CardLegality legality = new CardLegality();
            legality.setCard(card);
            legality.setFormatName(entry.getKey());
            legality.setLegalityStatus(entry.getValue().asText());
            cardLegalityRepository.save(legality);
        }
    }

    private void updatePrices(Card card, JsonNode pricesNode) {
        CardPrice price = cardPriceRepository.findByCard(card).orElse(new CardPrice());
        price.setCard(card);
        price.setUsd(pricesNode.has("usd") && !pricesNode.get("usd").isNull() ? new BigDecimal(pricesNode.get("usd").asText()) : null);
        price.setUsdFoil(pricesNode.has("usd_foil") && !pricesNode.get("usd_foil").isNull() ? new BigDecimal(pricesNode.get("usd_foil").asText()) : null);
        price.setUsdEtched(pricesNode.has("usd_etched") && !pricesNode.get("usd_etched").isNull() ? new BigDecimal(pricesNode.get("usd_etched").asText()) : null);
        price.setEur(pricesNode.has("eur") && !pricesNode.get("eur").isNull() ? new BigDecimal(pricesNode.get("eur").asText()) : null);
        price.setEurFoil(pricesNode.has("eur_foil") && !pricesNode.get("eur_foil").isNull() ? new BigDecimal(pricesNode.get("eur_foil").asText()) : null);
        price.setTix(pricesNode.has("tix") && !pricesNode.get("tix").isNull() ? new BigDecimal(pricesNode.get("tix").asText()) : null);
        price.setUpdatedAt(LocalDateTime.now());
        cardPriceRepository.save(price);
    }

    private void updateFaces(Card card, JsonNode facesNode) {
        // En una implementación real, podríamos querer ser más cuidadosos al borrar y recrear
        cardFaceRepository.deleteByCard(card);
        int order = 0;
        for (JsonNode faceNode : facesNode) {
            CardFace face = new CardFace();
            face.setCard(card);
            face.setFaceOrder(order++);
            face.setName(faceNode.get("name").asText());
            face.setManaCost(faceNode.has("mana_cost") ? faceNode.get("mana_cost").asText() : null);
            face.setTypeLine(faceNode.has("type_line") ? faceNode.get("type_line").asText() : null);
            face.setOracleText(faceNode.has("oracle_text") ? faceNode.get("oracle_text").asText() : null);
            face.setPower(faceNode.has("power") ? faceNode.get("power").asText() : null);
            face.setToughness(faceNode.has("toughness") ? faceNode.get("toughness").asText() : null);
            face.setLoyalty(faceNode.has("loyalty") ? faceNode.get("loyalty").asText() : null);
            face.setDefense(faceNode.has("defense") ? faceNode.get("defense").asText() : null);
            face.setFlavorText(faceNode.has("flavor_text") ? faceNode.get("flavor_text").asText() : null);
            face.setArtist(faceNode.has("artist") ? faceNode.get("artist").asText() : null);
            face.setColorsJson(faceNode.has("colors") ? faceNode.get("colors").toString() : "[]");
            
            if (faceNode.has("image_uris")) {
                JsonNode images = faceNode.get("image_uris");
                face.setSmallImageUri(images.has("small") ? images.get("small").asText() : null);
                face.setNormalImageUri(images.has("normal") ? images.get("normal").asText() : null);
                face.setLargeImageUri(images.has("large") ? images.get("large").asText() : null);
                face.setPngImageUri(images.has("png") ? images.get("png").asText() : null);
                face.setArtCropUri(images.has("art_crop") ? images.get("art_crop").asText() : null);
                face.setBorderCropUri(images.has("border_crop") ? images.get("border_crop").asText() : null);
            }
            
            face.setRawJson(faceNode.toString());
            cardFaceRepository.save(face);
        }
    }
}
