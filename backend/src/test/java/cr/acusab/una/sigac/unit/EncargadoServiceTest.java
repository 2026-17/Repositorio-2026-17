package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Encargado;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudianteEncargado;
import cr.acusab.una.sigac.data.entity.TipoIdentificacion;
import cr.acusab.una.sigac.data.repository.ContactoEmergenciaRepository;
import cr.acusab.una.sigac.data.repository.EncargadoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteEncargadoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import cr.acusab.una.sigac.logic.service.EncargadoService;
import cr.acusab.una.sigac.presentation.dto.request.ContactoEmergenciaRequest;
import cr.acusab.una.sigac.presentation.dto.request.EncargadoCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EncargadoUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EncargadoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EncargadoServiceTest {

    private EstudianteRepository estudianteRepository;
    private EncargadoRepository encargadoRepository;
    private EstudianteEncargadoRepository estudianteEncargadoRepository;
    private ContactoEmergenciaRepository contactoEmergenciaRepository;
    private TipoIdentificacionRepository tipoIdentificacionRepository;

    private EncargadoService encargadoService;

    @BeforeEach
    void setUp() {

        estudianteRepository =
                mock(EstudianteRepository.class);

        encargadoRepository =
                mock(EncargadoRepository.class);

        estudianteEncargadoRepository =
                mock(EstudianteEncargadoRepository.class);

        contactoEmergenciaRepository =
                mock(ContactoEmergenciaRepository.class);

        tipoIdentificacionRepository =
                mock(TipoIdentificacionRepository.class);

        encargadoService = new EncargadoService(
                estudianteRepository,
                encargadoRepository,
                estudianteEncargadoRepository,
                contactoEmergenciaRepository,
                tipoIdentificacionRepository
        );
    }

    @Test
    void registrarEncargadoCorrectamente() {

        Estudiante estudiante = crearEstudianteMenor();
        TipoIdentificacion tipo = crearTipoCedula();
        EncargadoCreateRequest request = crearRequestValido();

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        when(contactoEmergenciaRepository
                .countByEstudiante_IdAndActivoTrue(1L))
                .thenReturn(0L);

        when(encargadoRepository
                .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        1,
                        "123456789"
                ))
                .thenReturn(Optional.empty());

        when(encargadoRepository.save(any(Encargado.class)))
                .thenAnswer(invocation -> {

                    Encargado encargado =
                            invocation.getArgument(0);

                    encargado.setId(10L);

                    return encargado;
                });

        when(contactoEmergenciaRepository
                .findByEstudiante_IdAndEncargado_IdAndActivoTrue(
                        1L,
                        10L
                ))
                .thenReturn(List.of());

        EncargadoResponse response =
                encargadoService.registrar(request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(1L, response.getIdEstudiante());
        assertEquals("123456789", response.getNumeroIdentificacion());
        assertEquals("Maria", response.getNombres());
        assertEquals("Rodriguez", response.getPrimerApellido());
        assertEquals("MADRE", response.getParentesco());
        assertTrue(response.getActivo());

        verify(encargadoRepository)
                .save(any(Encargado.class));

        verify(estudianteEncargadoRepository)
                .save(any(EstudianteEncargado.class));
    }

    @Test
    void noDebeRegistrarEncargadoParaEstudianteMayorEdad() {

        Estudiante estudiante = new Estudiante();

        estudiante.setId(2L);
        estudiante.setActivo(true);
        estudiante.setFechaNacimiento(
                LocalDate.now().minusYears(25)
        );

        when(estudianteRepository.findByIdAndActivoTrue(2L))
                .thenReturn(Optional.of(estudiante));

        EncargadoCreateRequest request =
                crearRequestValido();

        request.setIdEstudiante(2L);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> encargadoService.registrar(request)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "Solo se puede registrar un encargado para un estudiante menor de edad.",
                exception.getReason()
        );

        verify(encargadoRepository, never())
                .save(any(Encargado.class));
    }

    @Test
    void noDebeRegistrarCedulaInvalida() {

        Estudiante estudiante = crearEstudianteMenor();
        TipoIdentificacion tipo = crearTipoCedula();

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        EncargadoCreateRequest request =
                crearRequestValido();

        request.setNumeroIdentificacion("12345");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> encargadoService.registrar(request)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "La cédula debe contener 9 dígitos.",
                exception.getReason()
        );

        verify(encargadoRepository, never())
                .save(any(Encargado.class));
    }

    @Test
    void noDebePermitirMasDeDosContactosEmergencia() {

        Estudiante estudiante = crearEstudianteMenor();
        TipoIdentificacion tipo = crearTipoCedula();

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        when(contactoEmergenciaRepository
                .countByEstudiante_IdAndActivoTrue(1L))
                .thenReturn(2L);

        EncargadoCreateRequest request =
                crearRequestValido();

        ContactoEmergenciaRequest contacto =
                new ContactoEmergenciaRequest();

        contacto.setNombre("Contacto prueba");
        contacto.setTelefono("88887777");
        contacto.setRelacion("Tio");

        request.setContactosEmergencia(
                List.of(contacto)
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> encargadoService.registrar(request)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "El estudiante puede tener como máximo dos contactos de emergencia.",
                exception.getReason()
        );

        verify(encargadoRepository, never())
                .save(any(Encargado.class));
    }

    @Test
    void noDebeAsociarDosVecesMismoEncargado() {

        Estudiante estudiante = crearEstudianteMenor();
        TipoIdentificacion tipo = crearTipoCedula();

        Encargado encargadoExistente =
                crearEncargadoExistente(tipo);

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        when(contactoEmergenciaRepository
                .countByEstudiante_IdAndActivoTrue(1L))
                .thenReturn(0L);

        when(encargadoRepository
                .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        1,
                        "123456789"
                ))
                .thenReturn(Optional.of(encargadoExistente));

        when(estudianteEncargadoRepository
                .existsByIdEstudianteAndIdEncargado(
                        1L,
                        10L
                ))
                .thenReturn(true);

        EncargadoCreateRequest request =
                crearRequestValido();

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> encargadoService.registrar(request)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "El encargado ya se encuentra asociado a este estudiante.",
                exception.getReason()
        );

        verify(encargadoRepository, never())
                .save(any(Encargado.class));
    }

    @Test
    void modificarEncargadoCorrectamente() {

        Estudiante estudiante = crearEstudianteMenor();
        TipoIdentificacion tipo = crearTipoCedula();

        Encargado encargado =
                crearEncargadoExistente(tipo);

        EstudianteEncargado relacion =
                new EstudianteEncargado();

        relacion.setIdEstudiante(1L);
        relacion.setIdEncargado(10L);
        relacion.setParentesco("MADRE");
        relacion.setEsResponsablePrincipal(true);
        relacion.setEsContactoEmergencia(false);
        relacion.setAutorizadoRetiro(true);

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(estudianteEncargadoRepository
                .findByIdEstudianteAndIdEncargado(
                        1L,
                        10L
                ))
                .thenReturn(Optional.of(relacion));

        when(encargadoRepository.findByIdAndActivoTrue(10L))
                .thenReturn(Optional.of(encargado));

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        when(contactoEmergenciaRepository
                .countByEstudiante_IdAndActivoTrue(1L))
                .thenReturn(0L);

        when(contactoEmergenciaRepository
                .findByEstudiante_IdAndEncargado_IdAndActivoTrue(
                        1L,
                        10L
                ))
                .thenReturn(List.of());

        EncargadoUpdateRequest request =
                crearUpdateRequestValido();

        request.setTelefonoPrincipal("88880000");

        EncargadoResponse response =
                encargadoService.modificar(
                        1L,
                        10L,
                        request
                );

        assertNotNull(response);

        assertEquals(
                "88880000",
                response.getTelefonoPrincipal()
        );

        assertEquals(
                "88880000",
                encargado.getTelefonoPrincipal()
        );

        verify(encargadoRepository)
                .save(encargado);

        verify(estudianteEncargadoRepository)
                .save(relacion);
    }

    private Estudiante crearEstudianteMenor() {

        Estudiante estudiante =
                new Estudiante();

        estudiante.setId(1L);
        estudiante.setActivo(true);

        estudiante.setFechaNacimiento(
                LocalDate.now().minusYears(14)
        );

        return estudiante;
    }

    private TipoIdentificacion crearTipoCedula() {

        TipoIdentificacion tipo =
                new TipoIdentificacion();

        tipo.setId(1);
        tipo.setCodigo("CEDULA");
        tipo.setNombre("Cedula de identidad");
        tipo.setActivo(true);

        return tipo;
    }

    private Encargado crearEncargadoExistente(
            TipoIdentificacion tipo
    ) {

        Encargado encargado =
                new Encargado();

        encargado.setId(10L);
        encargado.setIdTipoIdentificacion(tipo);
        encargado.setNumeroIdentificacion("123456789");
        encargado.setNombres("Maria");
        encargado.setPrimerApellido("Rodriguez");
        encargado.setSegundoApellido("Lopez");
        encargado.setTelefonoPrincipal("88889999");
        encargado.setActivo(true);

        return encargado;
    }

    private EncargadoCreateRequest crearRequestValido() {

        EncargadoCreateRequest request =
                new EncargadoCreateRequest();

        request.setIdEstudiante(1L);
        request.setIdTipoIdentificacion(1);
        request.setNumeroIdentificacion("123456789");
        request.setNombres("Maria");
        request.setPrimerApellido("Rodriguez");
        request.setSegundoApellido("Lopez");
        request.setTelefonoPrincipal("88889999");
        request.setTelefonoSecundario("22224444");
        request.setCorreo("maria@correo.test");
        request.setOcupacion("Administradora");
        request.setEstadoCivil("CASADA");
        request.setDireccion("Heredia, Costa Rica");
        request.setParentesco("MADRE");
        request.setEsResponsablePrincipal(true);
        request.setAutorizadoRetiro(true);
        request.setObservaciones("Prueba HU-023");
        request.setContactosEmergencia(List.of());

        return request;
    }

    private EncargadoUpdateRequest crearUpdateRequestValido() {

        EncargadoUpdateRequest request =
                new EncargadoUpdateRequest();

        request.setIdTipoIdentificacion(1);
        request.setNumeroIdentificacion("123456789");
        request.setNombres("Maria");
        request.setPrimerApellido("Rodriguez");
        request.setSegundoApellido("Lopez");
        request.setTelefonoPrincipal("88889999");
        request.setTelefonoSecundario("22224444");
        request.setCorreo("maria@correo.test");
        request.setOcupacion("Administradora");
        request.setEstadoCivil("CASADA");
        request.setDireccion("Heredia, Costa Rica");
        request.setParentesco("MADRE");
        request.setEsResponsablePrincipal(true);
        request.setAutorizadoRetiro(true);
        request.setObservaciones("Prueba modificación HU-023");
        request.setContactosEmergencia(List.of());

        return request;
    }
}