package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.TipoIdentificacion;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.EstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EstudianteServiceTest {

    private EstudianteRepository estudianteRepository;
    private TipoIdentificacionRepository tipoIdentificacionRepository;
    private UsuarioRepository usuarioRepository;

    private EstudianteService estudianteService;

    @BeforeEach
    void setUp() {
        estudianteRepository = mock(EstudianteRepository.class);
        tipoIdentificacionRepository = mock(TipoIdentificacionRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);

        estudianteService = new EstudianteService(
                estudianteRepository,
                tipoIdentificacionRepository,
                usuarioRepository
        );
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * HU-022 - Registrar estudiante
     * ============================================================
     */

    @Test
    void registrarEstudianteCorrectamente() {

        EstudianteCreateRequest request = crearRequestValido();

        TipoIdentificacion tipo = mock(TipoIdentificacion.class);
        when(tipo.getId()).thenReturn(1);
        when(tipo.getCodigo()).thenReturn("CED");
        when(tipo.getNombre()).thenReturn("Cedula");

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        when(estudianteRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(1, "123456789"))
                .thenReturn(Optional.empty());

        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);
        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        configurarUsuarioAutenticado();

        Estudiante estudianteGuardado = mock(Estudiante.class);
        when(estudianteGuardado.getId()).thenReturn(100L);
        when(estudianteGuardado.getIdTipoIdentificacion()).thenReturn(tipo);
        when(estudianteGuardado.getNumeroIdentificacion()).thenReturn("123456789");
        when(estudianteGuardado.getNombres()).thenReturn("Ana");
        when(estudianteGuardado.getPrimerApellido()).thenReturn("Perez");
        when(estudianteGuardado.getActivo()).thenReturn(true);

        when(estudianteRepository.save(any(Estudiante.class)))
                .thenReturn(estudianteGuardado);

        EstudianteResponse respuesta = estudianteService.registrar(request);

        assertNotNull(respuesta);
        assertEquals(100L, respuesta.getId());
        assertEquals("Ana", respuesta.getNombres());
        assertTrue(respuesta.getActivo());

        verify(estudianteRepository).save(any(Estudiante.class));
    }

    @Test
    void noDebeRegistrarEstudianteConIdentificacionDuplicada() {

        EstudianteCreateRequest request = crearRequestValido();

        TipoIdentificacion tipo = mock(TipoIdentificacion.class);
        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        Estudiante existente = mock(Estudiante.class);
        when(estudianteRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(1, "123456789"))
                .thenReturn(Optional.of(existente));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> estudianteService.registrar(request)
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    /*
     * ============================================================
     * HU-022 - Dar de baja estudiante
     * ============================================================
     */

    @Test
    void debeDarDeBajaEstudianteConservandoHistorial() {

        TipoIdentificacion tipo = mock(TipoIdentificacion.class);

        Estudiante estudiante = new Estudiante();
        estudiante.setId(100L);
        estudiante.setActivo(true);
        estudiante.setIdTipoIdentificacion(tipo);

        when(estudianteRepository.findById(100L))
                .thenReturn(Optional.of(estudiante));

        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);
        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        configurarUsuarioAutenticado();

        when(estudianteRepository.save(any(Estudiante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        estudianteService.darDeBaja(100L);

        assertFalse(estudiante.getActivo());
        assertNotNull(estudiante.getFechaEgreso());

        verify(estudianteRepository, never()).delete(any(Estudiante.class));
    }

    @Test
    void noDebeDarDeBajaEstudianteInexistente() {

        when(estudianteRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> estudianteService.darDeBaja(999L)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    /*
     * ============================================================
     * Métodos auxiliares para las pruebas
     * ============================================================
     */

    private EstudianteCreateRequest crearRequestValido() {

        EstudianteCreateRequest request = new EstudianteCreateRequest();

        request.setIdTipoIdentificacion(1);
        request.setNumeroIdentificacion("123456789");
        request.setNombres("Ana");
        request.setPrimerApellido("Perez");

        return request;
    }

    private void configurarUsuarioAutenticado() {

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("123456789");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }
}
