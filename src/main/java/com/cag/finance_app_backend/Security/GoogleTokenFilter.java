package com.cag.finance_app_backend.Security;

import com.cag.finance_app_backend.Models.AppUser;
import com.cag.finance_app_backend.Services.AuthService;
import com.cag.finance_app_backend.Services.TokenBlacklistService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class GoogleTokenFilter extends OncePerRequestFilter {

    private final AuthService authService;
    private final TokenBlacklistService blacklistService;

    public GoogleTokenFilter(AuthService authService, TokenBlacklistService blacklistService) {
        this.authService = authService;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // 1. Look for the "Authorization: Bearer <token>" header from React
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            //Check if token is blacklisted
            if (blacklistService.isBlacklisted(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Session expired. Please log in again.");
                return; // Instantly kill the request and stop the filter chain!
            }

            // 2. Verify the token using your existing service
            AppUser user = authService.verifyTokenAndGetUser(token);
            //System.out.println(">>> TRUTH DETECTOR: User is approved and authenticated: " + user.getEmail() + " with role: " + user.getRole());

            // 3. If valid and approved, log them into Spring Security!
            if (user != null && user.getApproved() == 1) {
                
                // Convert your custom Permissions into Spring Security Authorities
                List<GrantedAuthority> authorities = user.getPermissions().stream()
                        .map(perm -> new SimpleGrantedAuthority(perm.getName()))
                        .collect(Collectors.toList());

                // Create the active login token for this specific request
                UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);

                // Save it to the server's memory for this request
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 4. Pass the request down the chain to the Controller
        filterChain.doFilter(request, response);
    }
}