package com.cag.finance_app_backend.Services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class TokenValidator {

    // Pull your public Google Client ID from application.properties
    @Value("${google.client.id}")
    private String clientId;

    public GoogleIdToken.Payload validateToken(String tokenStr) {
        try {
            // 1. Build the official Google Verifier object
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), 
                    new GsonFactory())
                    // Specify the Client ID that you expect the token was issued for
                    .setAudience(Collections.singletonList(clientId))
                    .build();

            // 2. THE CRITICAL METHOD CALL: Verify the token string!
            GoogleIdToken idToken = verifier.verify(tokenStr);

            // 3. Check the result
            if (idToken != null) {
                // Verification SUCCESS! 
                return idToken.getPayload(); 
            } else {
                // Verification FAILED! 
                System.out.println("Invalid ID token.");
                return null;
            }

        } catch (Exception e) {
            System.err.println("Token verification threw an exception: " + e.getMessage());
            return null;
        }
    }

}
    