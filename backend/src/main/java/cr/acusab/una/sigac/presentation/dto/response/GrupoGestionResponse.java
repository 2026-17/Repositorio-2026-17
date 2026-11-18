package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/*
 * Grupo para la pantalla de gestión del Director Académico: datos del
 * grupo, nombres legibles de curso, período y profesor, cupos ocupados
 * y qué acciones admite.
 *
 * - editable: false si el grupo está CERRADO o CANCELADO.
 * - eliminable: false si tiene matrículas (aunque estén retiradas),
 *   sesiones de clase o avances curriculares registrados.
 * - tieneMatriculas: si es true no se puede cambiar su curso ni su período.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrupoGestionResponse {

    private Long id;
    private String codigo;

    private Long idCurso;
    private String codigoCurso;
    private String nombreCurso;

    private Long idPeriodo;
    private String nombrePeriodo;
    private String estadoPeriodo;
    private LocalDate fechaInicioPeriodo;
    private LocalDate fechaFinPeriodo;

    private Long idProfesor;
    private String nombreProfesor;

    private Short cupoMaximo;
    private long inscritos;

    private String modalidad;
    private String aula;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;

    private List<GrupoResponseDTO.Horario> horarios;

    private boolean editable;
    private boolean eliminable;
    private boolean tieneMatriculas;
}
