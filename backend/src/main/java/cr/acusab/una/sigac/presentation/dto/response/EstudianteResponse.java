package cr.acusab.una.sigac.presentation.dto.response;

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
        String centroEducativo,
        Integer idInstrumento,
        boolean menor,
        // Presente solo cuando se asignó una beca durante el registro.
        BecaResponse beca
) {}
