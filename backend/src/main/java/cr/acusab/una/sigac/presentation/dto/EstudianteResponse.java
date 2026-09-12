package cr.acusab.una.sigac.presentation.dto;

import java.time.LocalDate;

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
        Long idCentroEducativo,
        Integer idInstrumento,
        boolean menor
) {}
