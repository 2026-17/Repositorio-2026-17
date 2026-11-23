package cr.acusab.una.sigac.presentation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Todo lo que necesita la pantalla "Editar estudiante" en una sola respuesta.
public record EstudianteEdicionResponse(
        EstudianteResponse estudiante,
        LocalDate fechaIngreso,
        boolean activo,
        InstrumentoEdicion instrumentoPrincipal,
        List<InstrumentoEdicion> instrumentosExtra,
        List<EncargadoResponse> encargados,
        // Contactos activos que no pertenecen a un encargado; cuentan para el máximo de 2.
        int contactosSinEncargado,
        PolizaEdicion poliza,
        BecaEdicion beca,
        int cursosReconocidos
) {

    public record InstrumentoEdicion(
            Long idEstudianteInstrumento,
            Integer idInstrumento,
            String nombreInstrumento,
            Long idProfesor,
            String nombreProfesor,
            boolean profesorActivo,
            String nivelActual
    ) {}

    public record PolizaEdicion(
            String nombreArchivo,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            boolean vencida
    ) {}

    public record BecaEdicion(
            String codigoPeriodo,
            String nombreTipoBeca,
            BigDecimal porcentaje
    ) {}
}
