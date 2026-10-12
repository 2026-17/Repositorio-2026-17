package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.UsuarioCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Reglas de formato de los DTO (PoliticaDatosPersona): correo, teléfono y
 * nombres. Se valida con el mismo Validator que usa Spring en los controllers.
 */
class ValidacionDatosPersonaTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void crearValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "juan.@gmail.com", ".juan@gmail.com", "juan@gmail.com.", "jua..n@gmail.com", "juan@gmail..com",
            "juan@-gmail.com", "juan@.gmail.com", "juan()@gmail.com", "juan,perez@gmail.com", "juan@gmail.c",
            "juan@gmail"
    })
    void correoConFormatoInvalidoSeRechaza(String correo) {
        assertTrue(mensajes(usuario("118760542", "Juan Pérez", correo))
                .contains("El correo electrónico no tiene un formato válido."), correo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"juan.perez@una.ac.cr", "j_p+tag@correo.co.cr"})
    void correoValidoSeAcepta(String correo) {
        assertTrue(mensajes(usuario("118760542", "Juan Pérez", correo)).isEmpty(), correo);
    }

    @Test
    void telefonoDeSieteDigitosSeRechaza() {
        assertTrue(mensajes(estudianteConTelefono("8888432"))
                .contains("El teléfono debe tener 8 dígitos (ej. 8888-4321)."));
    }

    @Test
    void telefonoDeOchoDigitosYTelefonoVacioSeAceptan() {
        assertTrue(mensajes(estudianteConTelefono("88884321")).isEmpty());
        assertTrue(mensajes(estudianteConTelefono("")).isEmpty());
        assertTrue(mensajes(estudianteConTelefono(null)).isEmpty());
    }

    @Test
    void nombreConNumerosSeRechazaYConTildesSeAcepta() {
        assertTrue(mensajes(usuario("118760542", "Juan 2", "juan@correo.com")).contains("Solo se permiten letras."));
        assertTrue(mensajes(usuario("118760542", "María Ñúñez O'Neil-Peña", "juan@correo.com")).isEmpty());
    }

    private UsuarioCreateRequest usuario(String cedula, String nombre, String correo) {
        return new UsuarioCreateRequest(cedula, nombre, correo, 1, 1);
    }

    private EstudianteCreateRequest estudianteConTelefono(String telefono) {
        return new EstudianteCreateRequest(1, "118760542", "Ana", "Mora", null, LocalDate.now().minusYears(20),
                "FEMENINO", 10101, "Barrio Centro", telefono, "ana@correo.com", "Universitaria", null,
                null, 3, null, List.of(1L), List.of(), null);
    }

    private Set<String> mensajes(Object dto) {
        Set<ConstraintViolation<Object>> violaciones = validator.validate(dto);
        return violaciones.stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
