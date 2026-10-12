package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Bitacora;
import cr.acusab.una.sigac.data.entity.EstadoMatricula;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.Matricula;
import cr.acusab.una.sigac.data.entity.MatriculaDetalle;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.BitacoraRepository;
import cr.acusab.una.sigac.data.repository.EstadoMatriculaRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.MatriculaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.BajaEstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.DarBajaEstudianteRequest;
import cr.acusab.una.sigac.presentation.dto.response.DarBajaEstudianteResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BajaEstudianteServiceTest {

    private static final Long ID_ESTUDIANTE = 10L;
    private static final Long ID_USUARIO = 99L;
    private static final String CEDULA_ADMIN = "1-1111-1111";

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private MatriculaRepository matriculaRepository;

    @Mock
    private MatriculaDetalleRepository matriculaDetalleRepository;

    @Mock
    private EstadoMatriculaRepository estadoMatriculaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private BitacoraRepository bitacoraRepository;

    @Mock
    private Authentication authentication;

    private BajaEstudianteService bajaEstudianteService;

    @BeforeEach
    void setUp() {
        bajaEstudianteService = new BajaEstudianteService(
                estudianteRepository,
                matriculaRepository,
                matriculaDetalleRepository,
                estadoMatriculaRepository,
                usuarioRepository,
                bitacoraRepository
        );

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Debe rechazar una solicitud nula")
    void darDeBaja_debeRechazarSolicitudNula() {

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        null
                )
        );

        assertEquals(
                "El motivo de baja es obligatorio.",
                exception.getMessage()
        );

        verifyNoInteractions(
                estudianteRepository,
                matriculaRepository,
                matriculaDetalleRepository,
                estadoMatriculaRepository,
                usuarioRepository,
                bitacoraRepository
        );
    }

    @Test
    @DisplayName("Debe rechazar un motivo nulo")
    void darDeBaja_debeRechazarMotivoNulo() {

        DarBajaEstudianteRequest request =
                new DarBajaEstudianteRequest(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        request
                )
        );

        assertEquals(
                "El motivo de baja es obligatorio.",
                exception.getMessage()
        );

        verifyNoInteractions(estudianteRepository);
    }

    @Test
    @DisplayName("Debe rechazar un motivo vacío")
    void darDeBaja_debeRechazarMotivoVacio() {

        DarBajaEstudianteRequest request =
                new DarBajaEstudianteRequest("   ");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        request
                )
        );

        assertEquals(
                "El motivo de baja es obligatorio.",
                exception.getMessage()
        );

        verifyNoInteractions(estudianteRepository);
    }

    @Test
    @DisplayName("Debe rechazar un motivo mayor a 500 caracteres")
    void darDeBaja_debeRechazarMotivoMayorA500Caracteres() {

        String motivo = "A".repeat(501);

        DarBajaEstudianteRequest request =
                new DarBajaEstudianteRequest(motivo);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        request
                )
        );

        assertEquals(
                "El motivo de baja no puede superar 500 caracteres.",
                exception.getMessage()
        );

        verifyNoInteractions(estudianteRepository);
    }

    @Test
    @DisplayName("Debe rechazar la baja cuando el estudiante no existe")
    void darDeBaja_debeRechazarEstudianteInexistente() {

        when(estudianteRepository.findById(ID_ESTUDIANTE))
                .thenReturn(Optional.empty());

        DarBajaEstudianteRequest request =
                new DarBajaEstudianteRequest(
                        "Retiro voluntario"
                );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        request
                )
        );

        assertEquals(
                "El estudiante indicado no existe.",
                exception.getMessage()
        );

        verify(estudianteRepository).findById(ID_ESTUDIANTE);

        verifyNoInteractions(
                matriculaRepository,
                matriculaDetalleRepository,
                estadoMatriculaRepository,
                usuarioRepository,
                bitacoraRepository
        );
    }

    @Test
    @DisplayName("Debe rechazar la baja de un estudiante que ya está inactivo")
    void darDeBaja_debeRechazarEstudianteYaInactivo() {

        Estudiante estudiante = mock(Estudiante.class);

        when(estudiante.getActivo()).thenReturn(false);

        when(estudianteRepository.findById(ID_ESTUDIANTE))
                .thenReturn(Optional.of(estudiante));

        DarBajaEstudianteRequest request =
                new DarBajaEstudianteRequest(
                        "Retiro voluntario"
                );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        request
                )
        );

        assertEquals(
                "El estudiante indicado ya se encuentra inactivo.",
                exception.getMessage()
        );

        verify(estudianteRepository).findById(ID_ESTUDIANTE);

        verifyNoInteractions(
                matriculaRepository,
                matriculaDetalleRepository,
                estadoMatriculaRepository,
                usuarioRepository,
                bitacoraRepository
        );
    }

    @Test
    @DisplayName("Debe exigir un usuario autenticado")
    void darDeBaja_debeExigirUsuarioAutenticado() {

        Estudiante estudiante = mock(Estudiante.class);

        when(estudiante.getActivo()).thenReturn(true);

        when(estudianteRepository.findById(ID_ESTUDIANTE))
                .thenReturn(Optional.of(estudiante));

        SecurityContextHolder.clearContext();

        DarBajaEstudianteRequest request =
                new DarBajaEstudianteRequest(
                        "Retiro voluntario"
                );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        request
                )
        );

        assertEquals(
                "No se pudo identificar al usuario autenticado.",
                exception.getMessage()
        );

        verifyNoInteractions(
                matriculaRepository,
                matriculaDetalleRepository,
                estadoMatriculaRepository,
                usuarioRepository,
                bitacoraRepository
        );
    }

    @Test
    @DisplayName("Debe rechazar la operación si el usuario autenticado no existe")
    void darDeBaja_debeRechazarUsuarioAutenticadoInexistente() {

        Estudiante estudiante = mock(Estudiante.class);

        when(estudiante.getActivo()).thenReturn(true);

        when(estudianteRepository.findById(ID_ESTUDIANTE))
                .thenReturn(Optional.of(estudiante));

        configurarAutenticacion();

        when(usuarioRepository.findByCedula(CEDULA_ADMIN))
                .thenReturn(Optional.empty());

        DarBajaEstudianteRequest request =
                new DarBajaEstudianteRequest(
                        "Retiro voluntario"
                );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        request
                )
        );

        assertTrue(
                exception.getMessage()
                        .contains("usuario administrativo autenticado")
        );

        verify(usuarioRepository)
                .findByCedula(CEDULA_ADMIN);

        verifyNoInteractions(
                matriculaRepository,
                matriculaDetalleRepository,
                estadoMatriculaRepository,
                bitacoraRepository
        );
    }

    @Test
    @DisplayName("Debe requerir el estado CANCELADA de matrícula")
    void darDeBaja_debeRequerirEstadoCancelada() {

        Estudiante estudiante = mock(Estudiante.class);
        Usuario usuario = mock(Usuario.class);

        when(estudiante.getActivo()).thenReturn(true);

        when(estudianteRepository.findById(ID_ESTUDIANTE))
                .thenReturn(Optional.of(estudiante));

        configurarAutenticacion();

        when(usuarioRepository.findByCedula(CEDULA_ADMIN))
                .thenReturn(Optional.of(usuario));

        when(estadoMatriculaRepository
                .findByCodigoAndActivoTrue("CANCELADA"))
                .thenReturn(Optional.empty());

        DarBajaEstudianteRequest request =
                new DarBajaEstudianteRequest(
                        "Retiro voluntario"
                );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        request
                )
        );

        assertTrue(
                exception.getMessage()
                        .contains("CANCELADA")
        );

        verify(estadoMatriculaRepository)
                .findByCodigoAndActivoTrue("CANCELADA");

        verifyNoInteractions(
                matriculaRepository,
                matriculaDetalleRepository,
                bitacoraRepository
        );
    }

    @Test
    @DisplayName("Debe dar de baja correctamente a un estudiante sin matrículas activas")
    void darDeBaja_debeDarBajaSinMatriculasActivas() {

        Estudiante estudiante = mock(Estudiante.class);
        Usuario usuario = mock(Usuario.class);
        EstadoMatricula cancelada =
                mock(EstadoMatricula.class);

        when(estudiante.getId())
                .thenReturn(ID_ESTUDIANTE);

        when(estudiante.getActivo())
                .thenReturn(true);

        when(usuario.getId())
                .thenReturn(ID_USUARIO);

        when(estudianteRepository.findById(ID_ESTUDIANTE))
                .thenReturn(Optional.of(estudiante));

        configurarAutenticacion();

        when(usuarioRepository.findByCedula(CEDULA_ADMIN))
                .thenReturn(Optional.of(usuario));

        when(estadoMatriculaRepository
                .findByCodigoAndActivoTrue("CANCELADA"))
                .thenReturn(Optional.of(cancelada));

        when(matriculaRepository
                .findByEstudianteAndEstado(
                        ID_ESTUDIANTE,
                        "REGISTRADA"
                ))
                .thenReturn(List.of());

        DarBajaEstudianteRequest request =
                new DarBajaEstudianteRequest(
                        "  Retiro voluntario  "
                );

        DarBajaEstudianteResponse response =
                bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        request
                );

        assertNotNull(response);

        assertEquals(
                ID_ESTUDIANTE,
                response.idEstudiante()
        );

        assertEquals(
                "INACTIVO",
                response.estado()
        );

        assertEquals(
                "Retiro voluntario",
                response.motivoBaja()
        );

        assertNotNull(response.fechaBaja());

        assertEquals(
                0,
                response.matriculasCanceladas()
        );

        assertEquals(
                0,
                response.cursosRetirados()
        );

        verify(estudiante).setActivo(false);

        verify(estudiante)
                .setMotivoBaja("Retiro voluntario");

        verify(estudiante)
                .setFechaBaja(response.fechaBaja());

        verify(estudiante)
                .setIdUsuarioUltimaModificacion(ID_USUARIO);

        verify(estudianteRepository)
                .save(estudiante);

        verify(matriculaRepository)
                .findByEstudianteAndEstado(
                        ID_ESTUDIANTE,
                        "REGISTRADA"
                );

        verifyNoInteractions(
                matriculaDetalleRepository,
                bitacoraRepository
        );
    }

    @Test
    @DisplayName("Debe cancelar matrículas y retirar los cursos activos del estudiante")
    void darDeBaja_debeCancelarMatriculasYRetirarCursos() {

        Estudiante estudiante = mock(Estudiante.class);
        Usuario usuario = mock(Usuario.class);
        EstadoMatricula cancelada =
                mock(EstadoMatricula.class);

        Matricula matricula = mock(Matricula.class);

        MatriculaDetalle detalle1 =
                mock(MatriculaDetalle.class);

        MatriculaDetalle detalle2 =
                mock(MatriculaDetalle.class);

        when(estudiante.getId())
                .thenReturn(ID_ESTUDIANTE);

        when(estudiante.getActivo())
                .thenReturn(true);

        when(usuario.getId())
                .thenReturn(ID_USUARIO);

        when(matricula.getId())
                .thenReturn(100L);

        when(detalle1.getId())
                .thenReturn(1001L);

        when(detalle2.getId())
                .thenReturn(1002L);

        when(estudianteRepository.findById(ID_ESTUDIANTE))
                .thenReturn(Optional.of(estudiante));

        configurarAutenticacion();

        when(usuarioRepository.findByCedula(CEDULA_ADMIN))
                .thenReturn(Optional.of(usuario));

        when(estadoMatriculaRepository
                .findByCodigoAndActivoTrue("CANCELADA"))
                .thenReturn(Optional.of(cancelada));

        when(matriculaRepository
                .findByEstudianteAndEstado(
                        ID_ESTUDIANTE,
                        "REGISTRADA"
                ))
                .thenReturn(List.of(matricula));

        when(matriculaDetalleRepository
                .findByMatricula_IdAndRetiradoFalse(100L))
                .thenReturn(
                        List.of(detalle1, detalle2)
                );

        String motivo =
                "Cambio de institución";

        DarBajaEstudianteResponse response =
                bajaEstudianteService.darDeBaja(
                        ID_ESTUDIANTE,
                        new DarBajaEstudianteRequest(motivo)
                );

        assertEquals(
                1,
                response.matriculasCanceladas()
        );

        assertEquals(
                2,
                response.cursosRetirados()
        );

        assertEquals(
                "INACTIVO",
                response.estado()
        );

        assertEquals(
                motivo,
                response.motivoBaja()
        );

        LocalDate fechaEsperada =
                response.fechaBaja()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

        verify(detalle1).setRetirado(true);
        verify(detalle2).setRetirado(true);

        verify(detalle1)
                .setFechaRetiro(fechaEsperada);

        verify(detalle2)
                .setFechaRetiro(fechaEsperada);

        verify(detalle1)
                .setMotivoRetiro(motivo);

        verify(detalle2)
                .setMotivoRetiro(motivo);

        verify(matriculaDetalleRepository)
                .save(detalle1);

        verify(matriculaDetalleRepository)
                .save(detalle2);

        verify(matricula)
                .setEstado(cancelada);

        verify(matriculaRepository)
                .save(matricula);

        verify(estudiante)
                .setActivo(false);

        verify(estudiante)
                .setMotivoBaja(motivo);

        verify(estudiante)
                .setFechaBaja(response.fechaBaja());

        verify(estudiante)
                .setIdUsuarioUltimaModificacion(
                        ID_USUARIO
                );

        verify(estudianteRepository)
                .save(estudiante);

        /*
         * Se registra una entrada de bitácora por cada
         * detalle retirado y otra por la matrícula cancelada:
         *
         * 2 detalles + 1 matrícula = 3 registros.
         */
        verify(bitacoraRepository, times(3))
                .save(any(Bitacora.class));
    }

    @Test
    @DisplayName("Debe registrar correctamente la auditoría de la baja")
    void darDeBaja_debeRegistrarAuditoria() {

        Estudiante estudiante = mock(Estudiante.class);
        Usuario usuario = mock(Usuario.class);
        EstadoMatricula cancelada =
                mock(EstadoMatricula.class);

        Matricula matricula = mock(Matricula.class);
        MatriculaDetalle detalle =
                mock(MatriculaDetalle.class);

        when(estudiante.getId())
                .thenReturn(ID_ESTUDIANTE);

        when(estudiante.getActivo())
                .thenReturn(true);

        when(usuario.getId())
                .thenReturn(ID_USUARIO);

        when(matricula.getId())
                .thenReturn(200L);

        when(detalle.getId())
                .thenReturn(2001L);

        when(estudianteRepository.findById(ID_ESTUDIANTE))
                .thenReturn(Optional.of(estudiante));

        configurarAutenticacion();

        when(usuarioRepository.findByCedula(CEDULA_ADMIN))
                .thenReturn(Optional.of(usuario));

        when(estadoMatriculaRepository
                .findByCodigoAndActivoTrue("CANCELADA"))
                .thenReturn(Optional.of(cancelada));

        when(matriculaRepository
                .findByEstudianteAndEstado(
                        ID_ESTUDIANTE,
                        "REGISTRADA"
                ))
                .thenReturn(List.of(matricula));

        when(matriculaDetalleRepository
                .findByMatricula_IdAndRetiradoFalse(200L))
                .thenReturn(List.of(detalle));

        bajaEstudianteService.darDeBaja(
                ID_ESTUDIANTE,
                new DarBajaEstudianteRequest(
                        "Retiro administrativo"
                )
        );

        ArgumentCaptor<Bitacora> captor =
                ArgumentCaptor.forClass(Bitacora.class);

        verify(bitacoraRepository, times(2))
                .save(captor.capture());

        List<Bitacora> registros =
                captor.getAllValues();

        assertEquals(2, registros.size());

        Bitacora bitacoraDetalle =
                registros.get(0);

        assertEquals(
                ID_USUARIO,
                bitacoraDetalle.getIdUsuario()
        );

        assertEquals(
                "matriculas_detalles",
                bitacoraDetalle.getTablaAfectada()
        );

        assertEquals(
                2001L,
                bitacoraDetalle.getIdRegistro()
        );

        assertEquals(
                "UPDATE",
                bitacoraDetalle.getAccion()
        );

        assertNotNull(
                bitacoraDetalle.getFechaEvento()
        );

        assertTrue(
                bitacoraDetalle.getDatosNuevos()
                        .contains("\"retirado\":true")
        );

        assertTrue(
                bitacoraDetalle.getDatosNuevos()
                        .contains("Retiro administrativo")
        );

        Bitacora bitacoraMatricula =
                registros.get(1);

        assertEquals(
                "matriculas",
                bitacoraMatricula.getTablaAfectada()
        );

        assertEquals(
                200L,
                bitacoraMatricula.getIdRegistro()
        );

        assertTrue(
                bitacoraMatricula.getDatosNuevos()
                        .contains("\"estado\":\"CANCELADA\"")
        );
    }

    private void configurarAutenticacion() {

        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getName())
                .thenReturn(CEDULA_ADMIN);

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }
}