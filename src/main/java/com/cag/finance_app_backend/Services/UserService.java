package com.cag.finance_app_backend.Services;

import com.cag.finance_app_backend.DTOs.UserDTO;
import com.cag.finance_app_backend.Exceptions.UserNotFoundException;
import com.cag.finance_app_backend.Models.AppUser;
import com.cag.finance_app_backend.Repositories.UserRepository;
import org.springframework.stereotype.Service;


@Service
public class UserService {

    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public UserDTO getUserProfile(String email) {
        // 1. Fetch fresh data from MySQL
        AppUser user = userRepo.findByEmail(email)
                        .orElseThrow(() -> new UserNotFoundException("User not found"));

        return UserDTO.fromEntity(user);
    }
}