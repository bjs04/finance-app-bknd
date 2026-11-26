package com.cag.finance_app_backend.Services;

import com.cag.finance_app_backend.Exceptions.UserNotFoundException;
import com.cag.finance_app_backend.Models.AppUser;
import com.cag.finance_app_backend.Repositories.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private UserRepository userRepo;

    @Value("${google.client.id}")
    private String clientId;

    public AuthService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }


    public Map<String, Object> processGoogleLogin(String tokenString) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();

        // 1. Verify the incoming token with Google's servers
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();

        GoogleIdToken idToken = verifier.verify(tokenString);
        
        if (idToken == null) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        // 2. Query the repository (Layer 2) to check if user exists in MySQL
        AppUser user = userRepo.findByEmail(email)
                    .orElse(null);;

        // Case A: New User -> Save to DB as unapproved (approved = 0)
        if (user == null) {
            user = new AppUser(null, email, name, 0, "NORMAL", Collections.emptySet());
            userRepo.save(user);

            resultMap.put("status", "NEW_USER");
            resultMap.put("message", "Account registered! Please await Admin approval.");
            return resultMap;
        }

        // Case B: Existing User but still waiting for Admin approval
        if (user.getApproved() == 0) {
            //System.out.println(">>> TRUTH DETECTOR: User is still pending approval: " + user.getEmail());
            resultMap.put("status", "PENDING");
            resultMap.put("message", "Your account is still pending approval...");
            return resultMap;
        }

        // Case C: Approved User -> Send profile details to frontend
        resultMap.put("status", "APPROVED");
        resultMap.put("email", user.getEmail());
        resultMap.put("name", user.getName());
        resultMap.put("role", user.getRole());
        
        List<String> permissionList = user.getPermissions().stream()
            .map(permission -> permission.getName())
            .toList();
            
        resultMap.put("permissions", permissionList);
        
        return resultMap;
    }

    // Add this inside AuthService.java

    public AppUser verifyTokenAndGetUser(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                System.out.println(">>> TRUTH DETECTOR: Token is: " + idTokenString);
                String email = idToken.getPayload().getEmail();
                // Fetch user from DB to get their REAL, un-tamperable role and approved status
                System.out.println(">>> TRUTH DETECTOR: Google says the email is: " + email);
                return userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
            }
        } catch (Exception e) {
            System.out.println("Token validation failed: " + e.getMessage());
        }
        return null; // Token is invalid, expired, or tampered with!
    }
}