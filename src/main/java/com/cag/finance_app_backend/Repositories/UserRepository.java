package com.cag.finance_app_backend.Repositories;

import com.cag.finance_app_backend.Models.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    
    // Spring Data JPA automatically turns this into: SELECT * FROM users WHERE email = ?
    Optional<AppUser> findByEmail(String email);
}