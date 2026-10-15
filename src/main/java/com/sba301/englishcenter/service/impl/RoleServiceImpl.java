package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.dto.response.RoleResponse;
import com.sba301.englishcenter.entity.Role;
import com.sba301.englishcenter.repository.RoleRepository;
import com.sba301.englishcenter.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        List<Role> roles = roleRepository.findAll();

        return roles.stream()
                .sorted((r1, r2) -> r1.getRoleName().compareTo(r2.getRoleName()))
                .map(this::toRoleResponse)
                .toList();
    }

    private RoleResponse toRoleResponse(Role role) {
        return RoleResponse.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .build();
    }
}
