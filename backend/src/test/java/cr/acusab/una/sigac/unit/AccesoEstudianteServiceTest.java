package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.AccesoEstudianteService;
import cr.acusab.una.sigac.logic.service.ContrasenaTemporalService;
import cr.acusab.una.sigac.presentation.dto.response.ContrasenaTemporalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Contraseña temporal que genera la administración para un estudiante
 * cuando el correo no le llega.
 */
class AccesoEstudianteServiceTest {

    private EstudianteRepository estudianteRepository;
    private UsuarioRepository usuarioRepository;
    private ContrasenaTemporalService contrasenaTemporalService;
    private AccesoEstudianteService service;

    @BeforeEach
    void setUp() {
        estudianteRepository = mock(EstudianteRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        contrasenaTemporalService = mock(ContrasenaTemporalService.class);
        service = new AccesoEstudianteService(estudianteRepository, usuarioRepository, contrasenaTemporalService);
    }

    @Test
    void generaLaContrasenaGuardaElUsuarioYLaDevuelve() {
        Usuario usuario = usuario("ACTIVO");
        when(estudianteRepository.findById(5L)).thenReturn(Optional.of(estudiante(usuario)));
        when(contrasenaTemporalService.asignarSinCorreo(usuario)).thenReturn("Abc-2345defg");

        ContrasenaTemporalResponse respuesta = service.generarContrasenaTemporal(5L, "111111111");

        assertEquals("Abc-2345defg", respuesta.contrasenaTemporal());
        assertNotNull(respuesta.generadaEn());
        verify(usuarioRepository).save(usuario);
        // El toString (lo que saldría en un log) no muestra la contraseña
        assertFalse(respuesta.toString().contains("Abc-2345defg"));
    }

    @Test
    void cuentaInactivaDaUnErrorClaro() {
        Usuario usuario = usuario("INACTIVO");
        when(estudianteRepository.findById(5L)).thenReturn(Optional.of(estudiante(usuario)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.generarContrasenaTemporal(5L, "111111111"));

        assertEquals("La cuenta del estudiante no está activa. "
                + "Reactívela antes de generar una contraseña temporal.", error.getMessage());
        verify(contrasenaTemporalService, never()).asignarSinCorreo(any());
    }

    @Test
    void estudianteSinUsuarioDaUnErrorClaro() {
        when(estudianteRepository.findById(5L)).thenReturn(Optional.of(estudiante(null)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.generarContrasenaTemporal(5L, "111111111"));

        assertEquals("El estudiante no tiene una cuenta de acceso a SIGAC.", error.getMessage());
    }

    @Test
    void estudianteQueNoExisteDaUnErrorClaro() {
        when(estudianteRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.generarContrasenaTemporal(5L, "111111111"));
        verify(contrasenaTemporalService, never()).asignarSinCorreo(any());
    }

    private Estudiante estudiante(Usuario usuario) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5L);
        estudiante.setUsuario(usuario);
        return estudiante;
    }

    private Usuario usuario(String codigoEstado) {
        EstadosUsuario estado = new EstadosUsuario();
        estado.setCodigo(codigoEstado);
        Usuario usuario = new Usuario();
        usuario.setId(90L);
        usuario.setCedula("118760542");
        usuario.setIdEstadoUsuario(estado);
        return usuario;
    }
}
