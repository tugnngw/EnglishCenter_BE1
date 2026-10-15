package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.response.RoleResponse;

import java.util.List;

public interface RoleService {

    /**
     * Get all roles in the system, sorted by role name.
     *
     * @return List of all roles
     */
    List<RoleResponse> getAllRoles();
}
