package com.cag.finance_app_backend.Controllers;

import com.cag.finance_app_backend.DTOs.UserDTO;
import com.cag.finance_app_backend.Services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentProfile(Principal principal) {
        // principal.getName() automatically extracts the email from the JWT!
        String loggedInEmail = principal.getName();
        
        UserDTO currentUser = userService.getUserProfile(loggedInEmail);
        
        return ResponseEntity.ok(currentUser);
    }
}