package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.data.entity.GradosAcademicos;
import cr.acusab.una.sigac.data.repository.GradoAcademicoRepository;
import cr.acusab.una.sigac.presentation.dto.response.GradoAcademicoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Catalogo usado por el formulario de Profesor (HU-013) para el selector
// de grado academico (Bachillerato, Licenciatura, Maestria, etc).
@RestController
@RequestMapping("/grados-academicos")
public class GradoAcademicoController {

    private final GradoAcademicoRepository gradoAcademicoRepository;

    public GradoAcademicoController(GradoAcademicoRepository gradoAcademicoRepository) {
        this.gradoAcademicoRepository = gradoAcademicoRepository;
    }

    @GetMapping
    public List<GradoAcademicoResponse> listar() {
        return gradoAcademicoRepository.findAll().stream()
                .filter(GradosAcademicos::getActivo)
                .map(this::toResponse)
                .toList();
    }

    private GradoAcademicoResponse toResponse(GradosAcademicos grado) {
        return new GradoAcademicoResponse(grado.getId(), grado.getCodigo(), grado.getNombre());
    }
}
