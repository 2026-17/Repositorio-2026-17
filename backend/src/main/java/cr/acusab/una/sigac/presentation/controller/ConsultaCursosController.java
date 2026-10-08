package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.ConsultaCursosService;
import cr.acusab.una.sigac.presentation.dto.response.CursoDisponibleResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
 * HU-10 - Consultar cursos disponibles de un período académico.
 *
 * Rol: ESTUDIANTE. Permite conocer horario, grupo, profesor asignado
 * y cupos disponibles antes de solicitar matrícula.
 */
@RestController
@RequestMapping("/consulta-cursos")
public class ConsultaCursosController {

    private final ConsultaCursosService consultaCursosService;

    public ConsultaCursosController(
            ConsultaCursosService consultaCursosService
    ) {
        this.consultaCursosService = consultaCursosService;
    }

    @GetMapping
    public ResponseEntity<List<CursoDisponibleResponseDTO>> consultarDisponibles(
            @RequestParam Long idPeriodo,
            @RequestParam Long idPrograma,
            @RequestParam(required = false) String texto
    ) {

        return ResponseEntity.ok(
                consultaCursosService.consultarDisponibles(
                        idPeriodo,
                        idPrograma,
                        texto
                )
        );
    }
}
