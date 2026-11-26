package com.cag.finance_app_backend.Controllers;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cag.finance_app_backend.Services.AuthService;
import com.cag.finance_app_backend.Services.TokenBlacklistService;

import jakarta.servlet.http.HttpServletRequest;


@RestController
@CrossOrigin (origins = "http://localhost:5173")
public class AuthController {
    
    private final AuthService authService;
    private final TokenBlacklistService blacklistService;

    public AuthController(AuthService authService, TokenBlacklistService blacklistService) {
        this.authService = authService;
        this.blacklistService = blacklistService;
    }
    
    @PostMapping("/verifyToken")
    public ResponseEntity<?> verifyToken(@RequestBody Map<String, String> request) {
        String googleToken = request.get("token");
        
        if (googleToken == null || googleToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Google token is missing in payload"));
        }

        try {
            // Forward processing to the service layer
            Map<String, Object> authResult = authService.processGoogleLogin(googleToken);
            String status = (String) authResult.get("status");

            //System.out.println("AuthController: Received status from AuthService: " + status);
            // Handle unapproved or new access states with 403 Forbidden
            if ("NEW_USER".equals(status) || "PENDING".equals(status)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", authResult.get("message")));
            }

            // Success Path -> Returns HTTP 200 OK along with payload details
            return ResponseEntity.ok(Map.of(
                "email", authResult.get("email"),
                "name", authResult.get("name"),
                "role", authResult.get("role"),
                "approved", 1
            ));

        } catch (IllegalArgumentException e) {
            // Thrown if the token is fake or expired
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Session expired or invalid token. Please log in again."));
        } catch (Exception e) {
            // General safety catch-all
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred on the authentication server"));
        }
    }

    @GetMapping("/auth/verify")
    public ResponseEntity<?> verifySession(Principal principal) {
        // If the request makes it past your GoogleTokenFilter and SecurityConfig to reach this line,
        // it implicitly means the token is 100% valid and the user is approved!

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Token is active",
            "email", principal.getName()
        ));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            // Throw the token into the database!
            blacklistService.addToBlacklist(token);
        }
        
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

}
