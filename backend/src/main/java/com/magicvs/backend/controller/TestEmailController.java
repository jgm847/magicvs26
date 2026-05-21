package com.magicvs.backend.controller;

import com.magicvs.backend.model.User;
import com.magicvs.backend.model.Match;
import com.magicvs.backend.model.MatchStatus;
import com.magicvs.backend.model.UserAchievement;
import com.magicvs.backend.model.Achievement;
import com.magicvs.backend.repository.RegistroRepository;
import com.magicvs.backend.repository.MatchRepository;
import com.magicvs.backend.repository.UserAchievementRepository;
import com.magicvs.backend.repository.AchievementRepository;
import com.magicvs.backend.service.DailyReportService;
import com.magicvs.backend.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/test/daily-email")
public class TestEmailController {

    private final DailyReportService dailyReportService;
    private final MatchService matchService;
    private final RegistroRepository userRepository;
    private final MatchRepository matchRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementRepository achievementRepository;

    public TestEmailController(DailyReportService dailyReportService,
                               MatchService matchService,
                               RegistroRepository userRepository,
                               MatchRepository matchRepository,
                               UserAchievementRepository userAchievementRepository,
                               AchievementRepository achievementRepository) {
        this.dailyReportService = dailyReportService;
        this.matchService = matchService;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.achievementRepository = achievementRepository;
    }

    @PostMapping("/trigger")
    public ResponseEntity<String> triggerEmail() {
        dailyReportService.sendDailyReports();
        return ResponseEntity.ok("Proceso de envío de emails diarios iniciado.");
    }

    @PostMapping("/simulate-match")
    public ResponseEntity<String> simulateMatch(@RequestParam Long userId, @RequestParam boolean won) {
        matchService.recordMatchResult(userId, won);
        return ResponseEntity.ok("Partida simulada registrada para el usuario " + userId);
    }

    @PostMapping("/populate-mock-data")
    @Transactional
    public ResponseEntity<String> populateMockData(@RequestParam Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // 1. Encontrar o crear un oponente
        User opponent = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(userId))
                .findFirst()
                .orElseGet(() -> {
                    User opt = new User();
                    opt.setUsername("Opponent_Bot");
                    opt.setEmail("bot@magicvs.local");
                    opt.setPasswordHash("hash");
                    opt.setElo(1200);
                    opt.setActive(true);
                    return userRepository.save(opt);
                });

        // 2. Crear una partida ganada por el usuario en las últimas 24 horas (hace 2 horas)
        Match match1 = new Match();
        match1.setPlayer1(user);
        match1.setPlayer2(opponent);
        match1.setWinnerId(user.getId());
        match1.setEloBeforeP1(user.getEloRating());
        match1.setEloBeforeP2(opponent.getEloRating());
        match1.setEloAfterP1(user.getEloRating() + 25);
        match1.setEloAfterP2(opponent.getEloRating() - 25);
        match1.setEloChange(25);
        match1.setStatus(MatchStatus.FINISHED);
        match1.setScoreP1(2);
        match1.setScoreP2(1);
        match1.setFinishedAt(LocalDateTime.now().minusHours(2));
        matchRepository.save(match1);

        // Actualizar ELO y partidas en el usuario
        user.setElo(user.getEloRating() + 25);
        userRepository.save(user);

        // 3. Crear un logro ganado hace 1 hora (ej: FIRST_WIN)
        Optional<Achievement> achOpt = achievementRepository.findByKey("FIRST_WIN");
        if (achOpt.isPresent()) {
            Achievement ach = achOpt.get();
            Optional<UserAchievement> uaOpt = userAchievementRepository.findByUserAndAchievement(user, ach);
            UserAchievement ua;
            if (uaOpt.isPresent()) {
                ua = uaOpt.get();
            } else {
                ua = new UserAchievement();
                ua.setUser(user);
                ua.setAchievement(ach);
            }
            ua.setProgressValue(ach.getTargetValue());
            ua.setEarnedAt(LocalDateTime.now().minusHours(1));
            userAchievementRepository.save(ua);
        }

        return ResponseEntity.ok("Datos de prueba (1 partida finalizada +25 ELO y logro FIRST_WIN desbloqueado en las últimas 24 horas) creados con éxito para el usuario: " + user.getUsername());
    }
}
