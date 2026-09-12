package cr.acusab.una.sigac.presentation.controller;


import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.presentation.dto.response.RoleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;




@RestController
@RequestMapping("/roles")
public class RoleController {
    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public List<RoleResponse> listar() {
        return roleRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCodigo(), role.getNombre());
    }

}
