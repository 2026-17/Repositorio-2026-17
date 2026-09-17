package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.data.entity.TipoIdentificacion;
import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import cr.acusab.una.sigac.presentation.dto.response.TipoIdentificacionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Catalogo usado por los formularios de Estudiante y Profesor (HU-013) para
// el selector de tipo de identificacion (cedula fisica, juridica, DIMEX, etc).
@RestController
@RequestMapping("/tipos-identificacion")
public class TipoIdentificacionController {

    private final TipoIdentificacionRepository tipoIdentificacionRepository;

    public TipoIdentificacionController(TipoIdentificacionRepository tipoIdentificacionRepository) {
        this.tipoIdentificacionRepository = tipoIdentificacionRepository;
    }

    @GetMapping
    public List<TipoIdentificacionResponse> listar() {
        return tipoIdentificacionRepository.findAll().stream()
                .filter(TiposIdentificacion::getActivo)
                .map(this::toResponse)
                .toList();
    }

    private TipoIdentificacionResponse toResponse(TiposIdentificacion tipo) {
        return new TipoIdentificacionResponse(tipo.getId(), tipo.getCodigo(), tipo.getNombre());
    }
}
