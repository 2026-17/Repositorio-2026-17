package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/*
 * Programa académico con su resumen para la pantalla de gestión:
 * plan vigente, cuántas versiones de plan tiene y cuántos cursos
 * activos hay en la malla del plan vigente.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProgramaDetalleResponse {

    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private Instant creadoEn;
    private Instant actualizadoEn;

    // null si el programa no tiene un plan vigente.
    private PlanResumen planVigente;

    private int cantidadPlanes;
    private long cantidadCursos;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanResumen {
        private Long id;
        private String codigo;
        private String nombre;
        private String version;
        private LocalDate fechaVigenciaDesde;
        private BigDecimal notaMinimaAprobacion;
    }
}
