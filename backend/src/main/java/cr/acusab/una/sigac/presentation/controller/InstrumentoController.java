package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.presentation.dto.response.InstrumentoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Catalogo usado por el formulario de Profesor (HU-013) para el selector
// de especialidad (piano, canto, teoria musical, etc).
@RestController
@RequestMapping("/instrumentos")
public class InstrumentoController {

    private final InstrumentoRepository instrumentoRepository;

    public InstrumentoController(InstrumentoRepository instrumentoRepository) {
        this.instrumentoRepository = instrumentoRepository;
    }

    @GetMapping
    public List<InstrumentoResponse> listar() {
        return instrumentoRepository.findAll().stream()
                .filter(Instrumento::getActivo)
                .map(this::toResponse)
                .toList();
    }

    private InstrumentoResponse toResponse(Instrumento instrumento) {
        return new InstrumentoResponse(instrumento.getId(), instrumento.getCodigo(), instrumento.getNombre());
    }
}
