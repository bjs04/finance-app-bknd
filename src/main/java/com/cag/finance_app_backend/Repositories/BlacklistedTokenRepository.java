package com.cag.finance_app_backend.Repositories;

import com.cag.finance_app_backend.Models.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {
    
    // Spring Data JPA will automatically write the SQL query for this!
    boolean existsByToken(String token);

    @Transactional
    int deleteByExpiresAtBefore(LocalDateTime time);
}
