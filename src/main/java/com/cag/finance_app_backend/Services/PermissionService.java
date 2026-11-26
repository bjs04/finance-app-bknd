package com.cag.finance_app_backend.Services;

import org.springframework.stereotype.Service;

import com.cag.finance_app_backend.Models.AppUser;
import com.cag.finance_app_backend.Models.Permission;
import com.cag.finance_app_backend.Repositories.PermissionRepository;

@Service
public class PermissionService {
    private final PermissionRepository permRepo;
    
    public PermissionService(PermissionRepository permRepo) { 
        this.permRepo = permRepo; 
    }

    public void addDefaultPermissionToUser(AppUser user) {
        Permission perm = permRepo.findByName("UPLOAD_TX");
        user.getPermissions().add(perm);
    }
}