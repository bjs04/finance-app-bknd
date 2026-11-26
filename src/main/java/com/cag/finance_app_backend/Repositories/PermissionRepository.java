package com.cag.finance_app_backend.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cag.finance_app_backend.Models.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Permission findByName(String name);
}
