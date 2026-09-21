package cr.acusab.una.sigac.presentation.dto.response;

import java.time.LocalDate;
import java.util.List;

public record EstudianteResponse(
        Long id,
        String numeroIdentificacion,
        String nombres,
        String primerApellido,
        String segundoApellido,
        LocalDate fechaNacimiento,
        int edad,
        String genero,
        String direccion,
        String telefono,
        String correo,
        String nivelEscolar,
        String centroEducativo,
        Integer idInstrumento,
        boolean menor,
        // Presente solo cuando se asignó una beca durante el registro.
        BecaResponse beca,
        // Planes de estudio en los que el estudiante está inscrito (activos).
        List<Long> idsPlanEstudio
) {}
