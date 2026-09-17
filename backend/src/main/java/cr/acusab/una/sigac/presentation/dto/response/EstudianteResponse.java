package cr.acusab.una.sigac.presentation.dto.response;

import java.time.LocalDate;

public record EstudianteResponse(
        Long id,
        Integer idTipoIdentificacion,
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
        Long idCentroEducativo,
        String condicionesRelevantes,
        Integer idInstrumento,
        boolean menor
) {}
