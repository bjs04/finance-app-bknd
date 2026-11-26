package com.cag.finance_app_backend.DTOs; // Adjust to your exact package name

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.cag.finance_app_backend.Models.AppUser;
import com.cag.finance_app_backend.Models.Permission;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String email;
    private String name;
    private String role;
    private Integer approved;
    private List<String> permissions;

    public static UserDTO fromEntity(AppUser user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setApproved(user.getApproved());

        // Safely flatten the permissions array
        if (user.getPermissions() != null) {
            List<String> flatPermissions = user.getPermissions().stream()
                    .map(Permission::getName)
                    .collect(Collectors.toList());
            dto.setPermissions(flatPermissions);
        } else {
            dto.setPermissions(Collections.emptyList());
        }

        return dto;
    }
}