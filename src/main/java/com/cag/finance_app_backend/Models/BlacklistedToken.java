package com.cag.finance_app_backend.Models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blacklisted_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Google tokens are huge, so we MUST use columnDefinition = "TEXT"
    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String token;

    @Column(nullable = false)
    private LocalDateTime blacklistedAt;

    @Column(nullable = true)
    private LocalDateTime expiresAt;

    public BlacklistedToken(Long id, String token, LocalDateTime blacklistedAt) {
        this.id = id;
        this.token = token;
        this.blacklistedAt = blacklistedAt;
    }
}
