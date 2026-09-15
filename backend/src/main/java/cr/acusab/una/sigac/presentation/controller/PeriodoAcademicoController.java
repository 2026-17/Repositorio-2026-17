package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.PeriodoAcademicoService;
import cr.acusab.una.sigac.presentation.dto.response.PeriodoAcademicoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/periodos-academicos")
public class PeriodoAcademicoController {

    private final PeriodoAcademicoService periodoAcademicoService;

    public PeriodoAcademicoController(
            PeriodoAcademicoService periodoAcademicoService
    ) {
        this.periodoAcademicoService = periodoAcademicoService;
    }

    @GetMapping
    public List<PeriodoAcademicoResponse> listarPeriodosDisponibles() {
        return periodoAcademicoService.listarPeriodosDisponibles();
    }
}