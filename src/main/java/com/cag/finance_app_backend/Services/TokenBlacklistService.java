package com.cag.finance_app_backend.Services;

import com.cag.finance_app_backend.Models.BlacklistedToken;
import com.cag.finance_app_backend.Repositories.BlacklistedTokenRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_PREFIX = "blacklist:token:";

    private final BlacklistedTokenRepository blacklistRepo;
    private final StringRedisTemplate stringRedisTemplate;

    public TokenBlacklistService(BlacklistedTokenRepository blacklistRepo, StringRedisTemplate stringRedisTemplate) {
        this.blacklistRepo = blacklistRepo;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Blacklists a token by storing it in Redis with a TTL equal to its remaining lifespan,
     * and persists it in MySQL as a fallback.
     */
    public void addToBlacklist(String token) {
        long remainingSeconds = extractRemainingSeconds(token);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(Math.max(remainingSeconds, 0));

        // 1. Write to Redis with TTL
        try {
            long redisTtl = Math.max(remainingSeconds, 60); // Minimum 60s to guard against boundary races
            stringRedisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "blacklisted", redisTtl, TimeUnit.SECONDS);
            logger.info("Token added to Redis blacklist with TTL: {} seconds", redisTtl);
        } catch (Exception e) {
            logger.warn("Redis unavailable during addToBlacklist. Relying on MySQL fallback: {}", e.getMessage());
        }

        // 2. Write to MySQL for persistent fallback
        try {
            if (!blacklistRepo.existsByToken(token)) {
                BlacklistedToken blacklistedToken = new BlacklistedToken(null, token, LocalDateTime.now(), expiresAt);
                blacklistRepo.save(blacklistedToken);
                logger.info("Token saved to MySQL blacklist fallback.");
            }
        } catch (Exception e) {
            logger.error("Failed to save token to MySQL blacklist: {}", e.getMessage());
        }
    }

    /**
     * Checks if a token is blacklisted.
     * Uses Redis as primary source. If Redis is down, seamlessly falls back to MySQL.
     */
    public boolean isBlacklisted(String token) {
        try {
            // Check Redis cache first
            Boolean exists = stringRedisTemplate.hasKey(BLACKLIST_PREFIX + token);
            if (Boolean.TRUE.equals(exists)) {
                return true;
            }
            // Redis is healthy and key was not found -> token is active and valid
            return false;
        } catch (Exception e) {
            // Redis connection failure / timeout -> query MySQL fallback
            logger.warn("Redis unavailable during isBlacklisted check. Falling back to MySQL: {}", e.getMessage());
            return blacklistRepo.existsByToken(token);
        }
    }

    /**
     * Scheduled background job to clean up expired tokens from MySQL so the table never bloats.
     * Runs automatically every hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void purgeExpiredTokens() {
        try {
            int deletedCount = blacklistRepo.deleteByExpiresAtBefore(LocalDateTime.now());
            if (deletedCount > 0) {
                logger.info("Purged {} expired tokens from MySQL blacklist.", deletedCount);
            }
        } catch (Exception e) {
            logger.error("Failed to purge expired tokens from MySQL: {}", e.getMessage());
        }
    }

    /**
     * Extracts the remaining time-to-live in seconds from the Google JWT 'exp' payload claim.
     */
    private long extractRemainingSeconds(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
                String payloadJson = new String(decodedBytes, StandardCharsets.UTF_8);
                JsonObject jsonObject = JsonParser.parseString(payloadJson).getAsJsonObject();
                if (jsonObject.has("exp")) {
                    long expSeconds = jsonObject.get("exp").getAsLong();
                    long currentSeconds = Instant.now().getEpochSecond();
                    return expSeconds - currentSeconds;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract expiration from token; using 1-hour fallback: {}", e.getMessage());
        }
        return 3600; // Default to 1 hour
    }
}
