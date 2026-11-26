package com.cag.finance_app_backend.Services;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cag.finance_app_backend.DTOs.UserDTO;
import com.cag.finance_app_backend.Exceptions.UserNotFoundException;
import com.cag.finance_app_backend.Models.AppUser;
import com.cag.finance_app_backend.Models.Permission;
import com.cag.finance_app_backend.Repositories.UserRepository;
import com.cag.finance_app_backend.Repositories.PermissionRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepo;
    private final PermissionService permService;
    private final PermissionRepository permRepo;

    public AdminService(UserRepository userRepo, PermissionService permService, PermissionRepository permRepo) {
        this.userRepo = userRepo;
        this.permService = permService;
        this.permRepo = permRepo;
    }

    private void enforceHierarchy(AppUser requester, AppUser targetUser) {
        if (requester.getId().equals(targetUser.getId())) {
            throw new AccessDeniedException("Safety protocol: You cannot revoke or modify your own active account.");
        }
        // Master Admins can modify anyone. No block needed.
        if ("MASTER_ADMIN".equals(requester.getRole())) {
            return; 
        }

        // Regular Admins CANNOT modify other Admins or Master Admins
        if ("ADMIN".equals(requester.getRole())) {
            if ("ADMIN".equals(targetUser.getRole()) || "MASTER_ADMIN".equals(targetUser.getRole())) {
                throw new AccessDeniedException("You do not have permission to modify another administrator.");
            }
        }
    }

    public List<UserDTO> getAllUsersFormatted() {
        // 1. Fetch raw database entities
        List<AppUser> users = userRepo.findAll();

        // 2. Translate each AppUser into a UserDTO
        return users.stream()
            .map(UserDTO::fromEntity) 
            .collect(Collectors.toList());
    }

    public void approveUser(Long userId, String requesterEmail) {
        AppUser requester = userRepo.findByEmail(requesterEmail)
                .orElseThrow(() -> new UserNotFoundException("Requester not found"));
        AppUser targetUser = userRepo.findById(userId).orElseThrow(() -> new UserNotFoundException("User with ID " + userId + " was not found."));
        

        enforceHierarchy(requester, targetUser);

        targetUser.setApproved(1);
        // Add default permission
        permService.addDefaultPermissionToUser(targetUser); 
        
        userRepo.save(targetUser);
    }

    public AppUser updateUserPermissions(
        Long userId, 
        Set<String> newPermissions,
        String loggedInEmail
    ) {
        // 1. Find the user in MySQL
        AppUser targetUser = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        AppUser requester = userRepo.findByEmail(loggedInEmail)
                .orElseThrow(() -> new UserNotFoundException("Requester not found"));

        // 1. The bouncer ensures only a MASTER_ADMIN can touch this ADMIN target
        enforceHierarchy(requester, targetUser);

        targetUser.getPermissions().clear(); 
        Set<Permission> convertedPermissions = newPermissions.stream()
                                    .map(permissionName -> {
                                        Permission permission = permRepo.findByName(permissionName);
                                        if (permission == null) {
                                            throw new IllegalArgumentException("Permission not found: " + permissionName);
                                        }
                                        return permission;
                                    })
                                    .collect(Collectors.toSet());

        // 4. Add the newly converted permissions
        targetUser.getPermissions().addAll(convertedPermissions);

        // 4. Save and return (Hibernate will automatically write the DELETE and INSERT SQL queries for you)
        return userRepo.save(targetUser);
    }

    @Transactional
    public AppUser revokeUserApproval(Long userId, String requesterEmail) {
        AppUser requester = userRepo.findByEmail(requesterEmail)
                .orElseThrow(() -> new UserNotFoundException("Requester not found"));
        AppUser targetUser = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

        enforceHierarchy(requester, targetUser);

        targetUser.setApproved(0);

        if (targetUser.getPermissions() != null) {
            try {
                targetUser.getPermissions().removeIf(permission -> "UPLOAD_TX".equals(permission.getName()));
            } catch (UnsupportedOperationException e) {
                var editablePermissions = new ArrayList<>(targetUser.getPermissions());
                editablePermissions.removeIf(p -> "UPLOAD_TX".equals(p.getName()));
                targetUser.setPermissions(new HashSet<>(editablePermissions));
            }
        }
        
        return userRepo.save(targetUser);
    }

    @Transactional
    public AppUser promoteToAdmin(Long targetUserId, String requesterEmail) {
        AppUser requester = userRepo.findByEmail(requesterEmail)
                .orElseThrow(() -> new UserNotFoundException("Requester not found"));
                
        AppUser targetUser = userRepo.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Only Master Admins can promote someone to Admin, so let's enforce that directly
        enforceHierarchy(requester, targetUser);
        if (!"MASTER_ADMIN".equals(requester.getRole())) {
            throw new AccessDeniedException("Only Master Admins can assign the Admin role.");
        }

        targetUser.setRole("ADMIN");
        List<Permission> allPermissions = permRepo.findAll();
        targetUser.setPermissions(new HashSet<>(allPermissions));
        return userRepo.save(targetUser);
    }

    @Transactional
    public AppUser demoteFromAdmin(Long targetUserId, String requesterEmail) {
        AppUser requester = userRepo.findByEmail(requesterEmail)
                .orElseThrow(() -> new UserNotFoundException("Requester not found"));
                
        AppUser targetUser = userRepo.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // 1. The bouncer ensures only a MASTER_ADMIN can touch this ADMIN target
        enforceHierarchy(requester, targetUser);

        // 2. Change the role back to normal
        targetUser.setRole("NORMAL");

        // 3. Strip all admin permissions, and reset them to the default basic permission
        targetUser.getPermissions().clear();
        
        Permission basicPermission = permRepo.findByName("UPLOAD_TX");
        if (basicPermission != null) {
            targetUser.getPermissions().add(basicPermission);
        }

        return userRepo.save(targetUser);
    }
}