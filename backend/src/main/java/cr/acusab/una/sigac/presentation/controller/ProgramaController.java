package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.ProgramaService;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/programas")
public class ProgramaController {

    private final ProgramaService programaService;

    public ProgramaController(ProgramaService programaService) {
        this.programaService = programaService;
    }

    @GetMapping
    public List<ProgramaResponse> listarProgramasActivos() {
        return programaService.listarProgramasActivos();
    }
}