package com.cag.finance_app_backend.Services;

import com.cag.finance_app_backend.Models.AppUser;
import com.cag.finance_app_backend.Models.Permission;
import com.cag.finance_app_backend.Repositories.UserRepository;
import com.cag.finance_app_backend.Repositories.PermissionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepo, PermissionRepository permRepo) {
        return args -> {
            // 1. Create the master permissions in the database if they don't exist
            String[] masterPermissions = {"UPLOAD_TX", "VERIFY_TX", "FUND_MANAGER", "MANAGE_USERS", "VIEW_ALL_TX"};
            for (String pName : masterPermissions) {
                if (permRepo.findByName(pName) == null) {
                    permRepo.save(new Permission(null, pName));
                }
            }

            // 2. Fetch all those permissions from the database so we can assign them to you
            Set<Permission> allPermissions = new HashSet<>(permRepo.findAll());

            String myAdminEmail = "benjonathan0110@gmail.com"; 
            AppUser existingUser = userRepo.findByEmail(myAdminEmail)
                                .orElse(null);

            if (existingUser == null) {
                // CASE A: Create a brand new Admin account WITH all permissions
                AppUser adminUser = new AppUser(null, myAdminEmail, "Ben", 1, "MASTER_ADMIN", allPermissions);
                userRepo.save(adminUser);
                System.out.println(">>> SUCCESS: Default Admin account created for " + myAdminEmail);
            } 
            else if (!"MASTER_ADMIN".equals(existingUser.getRole()) || existingUser.getApproved() == 0) {
                // CASE B: Elevate existing account to Admin AND assign permissions
                existingUser.setRole("MASTER_ADMIN");
                existingUser.setApproved(1);
                existingUser.setPermissions(allPermissions);
                userRepo.save(existingUser);
                System.out.println(">>> SUCCESS: Existing account elevated to MASTER_ADMIN for " + myAdminEmail);
            } else {
                System.out.println(">>> Admin account already verified and ready to go.");
            }
        };
    }
}