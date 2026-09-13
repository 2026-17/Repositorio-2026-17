package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.presentation.dto.response.PeriodoAcademicoResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PeriodoAcademicoService {

    private final PeriodoAcademicoRepository periodoAcademicoRepository;

    public PeriodoAcademicoService(
            PeriodoAcademicoRepository periodoAcademicoRepository
    ) {
        this.periodoAcademicoRepository = periodoAcademicoRepository;
    }

    public List<PeriodoAcademicoResponse> listarPeriodosDisponibles() {

        List<String> estadosDisponibles = List.of(
                "PLANIFICADO",
                "MATRICULA",
                "EN_CURSO"
        );

        return periodoAcademicoRepository
                .findByEstadoInOrderByFechaInicioDesc(estadosDisponibles)
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    private PeriodoAcademicoResponse mapearAResponse(
            PeriodoAcademico periodo
    ) {
        return new PeriodoAcademicoResponse(
                periodo.getId(),
                periodo.getCodigo(),
                periodo.getNombre(),
                periodo.getFechaInicio(),
                periodo.getFechaFin(),
                periodo.getEstado()
        );
    }
}
