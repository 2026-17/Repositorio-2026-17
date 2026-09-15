package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Programa;
import cr.acusab.una.sigac.data.repository.ProgramaRepository;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProgramaService {

    private final ProgramaRepository programaRepository;

    public ProgramaService(ProgramaRepository programaRepository) {
        this.programaRepository = programaRepository;
    }

    public List<ProgramaResponse> listarProgramasActivos() {

        return programaRepository
                .findByActivoTrueOrderByNombreAsc()
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    private ProgramaResponse mapearAResponse(Programa programa) {

        return new ProgramaResponse(
                programa.getId(),
                programa.getCodigo(),
                programa.getNombre()
        );
    }
}
