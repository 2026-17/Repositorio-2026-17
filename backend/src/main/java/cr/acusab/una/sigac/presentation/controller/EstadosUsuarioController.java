package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.repository.EstadoUsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.response.EstadoUsuarioResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/estados-usuarios")
public class EstadosUsuarioController {

    private final EstadoUsuarioRepository estadoUsuarioRepository;

    public EstadosUsuarioController(EstadoUsuarioRepository estadoUsuarioRepository) {
        this.estadoUsuarioRepository = estadoUsuarioRepository;
    }

    @GetMapping
    public List<EstadoUsuarioResponse> listar() {
        return estadoUsuarioRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private EstadoUsuarioResponse toResponse(EstadosUsuario estado) {
        return new EstadoUsuarioResponse(estado.getId(), estado.getCodigo(), estado.getNombre());
    }
}