package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstadoUsuarioRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.ProfesorService;
import cr.acusab.una.sigac.logic.service.UsuarioService;
import cr.acusab.una.sigac.presentation.dto.request.ProfesorCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.UsuarioCreateRequest;
import cr.acusab.una.sigac.presentation.dto.response.ProfesorResponse;
import cr.acusab.una.sigac.presentation.dto.response.UsuarioResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * HU-013 - Registrar profesor: el profesor y su cuenta de usuario se crean
 * juntos, con rol PROFESOR y estado ACTIVO.
 */
class ProfesorServiceTest {

    private ProfesorRepository profesorRepository;
    private TipoIdentificacionRepository tipoIdentificacionRepository;
    private UsuarioRepository usuarioRepository;
    private RoleRepository roleRepository;
    private EstadoUsuarioRepository estadoUsuarioRepository;
    private UsuarioService usuarioService;

    private ProfesorService service;

    private Usuario usuarioCreado;

    @BeforeEach
    void setUp() {

        profesorRepository = mock(ProfesorRepository.class);
        tipoIdentificacionRepository = mock(TipoIdentificacionRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        roleRepository = mock(RoleRepository.class);
        estadoUsuarioRepository = mock(EstadoUsuarioRepository.class);
        usuarioService = mock(UsuarioService.class);

        service = new ProfesorService(
                profesorRepository,
                tipoIdentificacionRepository,
                usuarioRepository,
                roleRepository,
                estadoUsuarioRepository,
                usuarioService,
                mock(InstrumentoRepository.class)
        );

        TiposIdentificacion cedula = new TiposIdentificacion();
        cedula.setId(1);
        cedula.setCodigo("CEDULA");
        cedula.setNombre("Cedula de identidad");
        when(tipoIdentificacionRepository.findById(1)).thenReturn(Optional.of(cedula));

        Role rolProfesor = new Role();
        rolProfesor.setId(4);
        rolProfesor.setCodigo("PROFESOR");
        when(roleRepository.findByCodigoIgnoreCase("PROFESOR")).thenReturn(Optional.of(rolProfesor));

        EstadosUsuario activo = new EstadosUsuario();
        activo.setId(9);
        activo.setCodigo("ACTIVO");
        when(estadoUsuarioRepository.findByCodigoIgnoreCase("ACTIVO")).thenReturn(Optional.of(activo));

        usuarioCreado = new Usuario();
        usuarioCreado.setId(77L);

        UsuarioResponse respuestaUsuario = mock(UsuarioResponse.class);
        when(respuestaUsuario.getId()).thenReturn(77L);
        when(usuarioService.crearUsuario(any(UsuarioCreateRequest.class))).thenReturn(respuestaUsuario);
        when(usuarioRepository.findById(77L)).thenReturn(Optional.of(usuarioCreado));

        when(profesorRepository.save(any(Profesor.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registrar_creaLaCuentaConRolProfesorYEstadoActivo_ignorandoLoEnviado() {

        // El formulario envía idRol=1 e idEstadoUsuario=1 como valores de relleno.
        UsuarioCreateRequest usuarioRequest = usuario(1, 1);

        service.registrar(profesor("118760542"), usuarioRequest);

        ArgumentCaptor<UsuarioCreateRequest> enviado = ArgumentCaptor.forClass(UsuarioCreateRequest.class);
        verify(usuarioService).crearUsuario(enviado.capture());

        assertEquals(4, enviado.getValue().getIdRol());
        assertEquals(9, enviado.getValue().getIdEstadoUsuario());
    }

    @Test
    void registrar_enlazaLaCuentaCreadaAlProfesor() {

        service.registrar(profesor("118760542"), usuario(1, 1));

        ArgumentCaptor<Profesor> guardado = ArgumentCaptor.forClass(Profesor.class);
        verify(profesorRepository).save(guardado.capture());

        assertSame(usuarioCreado, guardado.getValue().getIdUsuario());
        assertTrue(guardado.getValue().getActivo());
    }

    @Test
    void registrar_conservaLosDatosDelProfesorYUsaLaFechaActualSiNoSeEnvia() {

        ProfesorCreateRequest request = profesor("118760542");
        request.setClasePuesto("Profesor de música");
        request.setEspecialidad("Guitarra,Piano");

        ProfesorResponse respuesta = service.registrar(request, usuario(1, 1));

        assertEquals("118760542", respuesta.getNumeroIdentificacion());
        assertEquals("Profesor de música", respuesta.getClasePuesto());
        assertEquals("Guitarra,Piano", respuesta.getEspecialidad());
        assertEquals(LocalDate.now(), respuesta.getFechaIngreso());
    }

    @Test
    void registrar_identificacionDuplicada_seRechazaSinCrearLaCuenta() {

        when(profesorRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(1, "118760542"))
                .thenReturn(Optional.of(new Profesor()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.registrar(profesor("118760542"), usuario(1, 1)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(usuarioService, never()).crearUsuario(any());
        verify(profesorRepository, never()).save(any());
    }

    @Test
    void registrar_tipoDeIdentificacionInexistente_seRechazaSinCrearLaCuenta() {

        ProfesorCreateRequest request = profesor("118760542");
        request.setIdTipoIdentificacion(99);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.registrar(request, usuario(1, 1)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(usuarioService, never()).crearUsuario(any());
    }

    @Test
    void registrar_sinRolProfesorEnElCatalogo_fallaSinCrearLaCuenta() {

        when(roleRepository.findByCodigoIgnoreCase("PROFESOR")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.registrar(profesor("118760542"), usuario(1, 1)));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        verify(usuarioService, never()).crearUsuario(any());
    }

    @Test
    void registrar_sinEstadoActivoEnElCatalogo_fallaSinCrearLaCuenta() {

        when(estadoUsuarioRepository.findByCodigoIgnoreCase("ACTIVO")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> service.registrar(profesor("118760542"), usuario(1, 1)));

        verify(usuarioService, never()).crearUsuario(any());
    }

    @Test
    void registrar_cuentaDuplicada_propagaElErrorYNoGuardaAlProfesor() {

        when(usuarioService.crearUsuario(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "La cédula ya está registrada"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.registrar(profesor("118760542"), usuario(1, 1)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(profesorRepository, never()).save(any());
    }

    private ProfesorCreateRequest profesor(String identificacion) {

        ProfesorCreateRequest request = new ProfesorCreateRequest();
        request.setIdTipoIdentificacion(1);
        request.setNumeroIdentificacion(identificacion);
        request.setNombres("Marcela");
        request.setPrimerApellido("Vargas");
        request.setCorreo("marcela@test.cr");

        return request;
    }

    private UsuarioCreateRequest usuario(int idRol, int idEstado) {

        UsuarioCreateRequest request = new UsuarioCreateRequest();
        request.setCedula("118760542");
        request.setNombre("Marcela Vargas");
        request.setCorreo("marcela@test.cr");
        request.setContrasena("Password123!");
        request.setIdRol(idRol);
        request.setIdEstadoUsuario(idEstado);

        return request;
    }
}
