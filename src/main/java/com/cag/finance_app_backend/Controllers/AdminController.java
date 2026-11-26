package com.cag.finance_app_backend.Controllers;

import com.cag.finance_app_backend.DTOs.UserDTO;
import com.cag.finance_app_backend.Models.AppUser;
import com.cag.finance_app_backend.Services.AdminService; // You'll need this

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.security.Principal;

@RestController
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // Get all users
    @GetMapping("/admin/users")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> formattedUsers = adminService.getAllUsersFormatted();
        return ResponseEntity.ok(formattedUsers);
    }

    // Approve user and assign default 'UPLOAD_TX' permission
    @PostMapping("/admin/users/{userId}/approve")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public ResponseEntity<?> approveUser(@PathVariable Long userId, Principal principal) {
        String requesterEmail = principal.getName();
        adminService.approveUser(userId, requesterEmail);
        return ResponseEntity.ok(Map.of("message", "User approved and 'UPLOAD_TX' permission assigned!"));
    }

    // Update user permissions
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PutMapping("/admin/users/{userId}/permissions")
    public ResponseEntity<?> updateUserPermissions(
        @PathVariable Long userId, 
        @RequestBody Set<String> newPermissions,
        Principal principal
    ) {
        String loggedInEmail = principal.getName();
        AppUser user = adminService.updateUserPermissions(userId, newPermissions, loggedInEmail);
        
        return ResponseEntity.ok(Map.of(
            "message", "Permissions updated successfully for " + user.getEmail()
        ));
    }

    @PutMapping("/admin/users/{id}/revoke-approval")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public ResponseEntity<?> revokeUserApproval(@PathVariable Long id, Principal principal) {
        String loggedInEmail = principal.getName();
        adminService.revokeUserApproval(id, loggedInEmail);

        // Return a clean success message so the React frontend can trigger a success Snackbar
        return ResponseEntity.ok(Map.of("message", "User access has been successfully revoked."));
    }

    @PutMapping("/admin/users/{id}/promote-to-admin")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public ResponseEntity<?> promoteToAdmin(@PathVariable Long id, Principal principal) {
        AppUser user = adminService.promoteToAdmin(id, principal.getName());
        
        return ResponseEntity.ok(Map.of("message", "User " + user.getName() + " successfully promoted to Admin!"));
    }

    @PutMapping("/admin/users/{id}/demote-from-admin")
    @PreAuthorize("hasAuthority('MANAGE_USERS')") 
    public ResponseEntity<?> demoteFromAdmin(@PathVariable Long id, Principal principal) {
        AppUser user = adminService.demoteFromAdmin(id, principal.getName());
        System.out.println("principal value: " + principal.getName());
        return ResponseEntity.ok(Map.of("message", "User " + user.getName() + " has been successfully demoted to normal status."));
    }
}