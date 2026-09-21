package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.data.repository.EstadoUsuarioRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.ProfesorService;
import cr.acusab.una.sigac.logic.service.UsuarioService;
import cr.acusab.una.sigac.presentation.dto.request.ProfesorCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProfesorUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.ProfesorResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfesorServiceTest {

    @Mock
    private ProfesorRepository profesorRepository;

    @Mock
    private TipoIdentificacionRepository tipoIdentificacionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EstadoUsuarioRepository estadoUsuarioRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private InstrumentoRepository instrumentoRepository;

    @InjectMocks
    private ProfesorService profesorService;

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /*
     * HU-013 - Registrar profesor.
     * Caso normal.
     */
    @Test
    void debeRegistrarProfesorCorrectamente() {

        ProfesorCreateRequest request = crearRequestProfesor();
        TiposIdentificacion tipo = crearTipoIdentificacion();

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        when(profesorRepository
                .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        1,
                        "117770999"
                ))
                .thenReturn(Optional.empty());

        when(profesorRepository.save(any(Profesor.class)))
                .thenAnswer(invocation -> {
                    Profesor profesor = invocation.getArgument(0);
                    profesor.setId(1L);
                    return profesor;
                });

        ProfesorResponse response =
                profesorService.registrar(request);

        assertNotNull(response);

        ArgumentCaptor<Profesor> captor =
                ArgumentCaptor.forClass(Profesor.class);

        verify(profesorRepository).save(captor.capture());

        Profesor guardado = captor.getValue();

        assertEquals(
                "117770999",
                guardado.getNumeroIdentificacion()
        );

        assertEquals(
                "Carlos",
                guardado.getNombres()
        );

        assertEquals(
                "Ramirez",
                guardado.getPrimerApellido()
        );

        assertEquals(
                "Profesor de musica",
                guardado.getClasePuesto()
        );

        assertTrue(guardado.getActivo());
        assertEquals(tipo, guardado.getIdTipoIdentificacion());
    }

    /*
     * HU-013 - Registrar profesor.
     * Caso de error: tipo de identificación inexistente.
     */
    @Test
    void noDebeRegistrarProfesorSiTipoIdentificacionNoExiste() {

        ProfesorCreateRequest request =
                crearRequestProfesor();

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> profesorService.registrar(request)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(profesorRepository, never())
                .save(any(Profesor.class));
    }

    /*
     * HU-013 - Registrar profesor.
     * Caso de error: identificación duplicada.
     */
    @Test
    void noDebeRegistrarProfesorConIdentificacionDuplicada() {

        ProfesorCreateRequest request =
                crearRequestProfesor();

        TiposIdentificacion tipo =
                crearTipoIdentificacion();

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        when(profesorRepository
                .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        1,
                        "117770999"
                ))
                .thenReturn(Optional.of(crearProfesor()));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> profesorService.registrar(request)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(profesorRepository, never())
                .save(any(Profesor.class));
    }

    /*
     * HU-013 - Registrar profesor.
     * Caso límite: si no se indica fecha de ingreso,
     * se utiliza la fecha actual.
     */
    @Test
    void debeUsarFechaActualSiFechaIngresoEsNula() {

        ProfesorCreateRequest request =
                crearRequestProfesor();

        request.setFechaIngreso(null);

        TiposIdentificacion tipo =
                crearTipoIdentificacion();

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.of(tipo));

        when(profesorRepository
                .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        1,
                        "117770999"
                ))
                .thenReturn(Optional.empty());

        when(profesorRepository.save(any(Profesor.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        LocalDate hoy = LocalDate.now();

        profesorService.registrar(request);

        ArgumentCaptor<Profesor> captor =
                ArgumentCaptor.forClass(Profesor.class);

        verify(profesorRepository).save(captor.capture());

        assertEquals(
                hoy,
                captor.getValue().getFechaIngreso()
        );
    }

    /*
     * HU-013 - Listar profesores.
     * Sin filtro se listan todos.
     */
    @Test
    void debeListarTodosLosProfesoresCuandoActivoEsNulo() {

        Profesor profesor =
                crearProfesor();

        when(profesorRepository.findAll())
                .thenReturn(List.of(profesor));

        List<ProfesorResponse> resultado =
                profesorService.listar(null);

        assertEquals(1, resultado.size());

        verify(profesorRepository).findAll();

        verify(profesorRepository, never())
                .findByActivo(anyBoolean());
    }

    /*
     * HU-013 - Listar profesores.
     * Filtrado por estado.
     */
    @Test
    void debeListarProfesoresFiltradosPorEstado() {

        Profesor profesor =
                crearProfesor();

        when(profesorRepository.findByActivo(true))
                .thenReturn(List.of(profesor));

        List<ProfesorResponse> resultado =
                profesorService.listar(true);

        assertEquals(1, resultado.size());

        verify(profesorRepository)
                .findByActivo(true);

        verify(profesorRepository, never())
                .findAll();
    }

    /*
     * HU-013 - Consultar profesor.
     * Caso normal.
     */
    @Test
    void debeObtenerProfesorExistente() {

        Profesor profesor =
                crearProfesor();

        when(profesorRepository.findById(1L))
                .thenReturn(Optional.of(profesor));

        ProfesorResponse resultado =
                profesorService.obtener(1L);

        assertNotNull(resultado);

        verify(profesorRepository)
                .findById(1L);
    }

    /*
     * HU-013 - Consultar profesor.
     * Caso de error.
     */
    @Test
    void noDebeObtenerProfesorInexistente() {

        when(profesorRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> profesorService.obtener(99L)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    /*
     * HU-013 - Modificar profesor.
     * Caso normal.
     */
    @Test
    void debeModificarProfesorCorrectamente() {

        Profesor profesor =
                crearProfesor();

        ProfesorUpdateRequest request =
                crearRequestActualizacion();

        when(profesorRepository.findById(1L))
                .thenReturn(Optional.of(profesor));

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(
                        Optional.of(crearTipoIdentificacion())
                );

        when(profesorRepository.save(any(Profesor.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        ProfesorResponse resultado =
                profesorService.modificar(
                        1L,
                        request
                );

        assertNotNull(resultado);

        ArgumentCaptor<Profesor> captor =
                ArgumentCaptor.forClass(Profesor.class);

        verify(profesorRepository)
                .save(captor.capture());

        Profesor modificado =
                captor.getValue();

        assertEquals(
                "Carlos Alberto",
                modificado.getNombres()
        );

        assertEquals(
                "8888-8888",
                modificado.getTelefono()
        );

        assertEquals(
                "carlos.actualizado@correo.com",
                modificado.getCorreo()
        );

        assertEquals(
                "Profesor coordinador",
                modificado.getClasePuesto()
        );
    }

    /*
     * HU-013 - Modificar profesor.
     * Caso de error: identificación duplicada.
     */
    @Test
    void noDebeModificarProfesorConIdentificacionDuplicada() {

        Profesor profesor =
                crearProfesor();

        ProfesorUpdateRequest request =
                crearRequestActualizacion();

        request.setNumeroIdentificacion(
                "222222222"
        );

        when(profesorRepository.findById(1L))
                .thenReturn(Optional.of(profesor));

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(
                        Optional.of(crearTipoIdentificacion())
                );

        when(profesorRepository
                .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        1,
                        "222222222"
                ))
                .thenReturn(
                        Optional.of(new Profesor())
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> profesorService.modificar(
                                1L,
                                request
                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(profesorRepository, never())
                .save(any(Profesor.class));
    }

    /*
     * HU-013 - Modificar profesor.
     * Caso de error: tipo de identificación inexistente.
     */
    @Test
    void noDebeModificarProfesorSiTipoIdentificacionNoExiste() {

        Profesor profesor =
                crearProfesor();

        ProfesorUpdateRequest request =
                crearRequestActualizacion();

        when(profesorRepository.findById(1L))
                .thenReturn(Optional.of(profesor));

        when(tipoIdentificacionRepository.findById(1))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> profesorService.modificar(
                                1L,
                                request
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(profesorRepository, never())
                .save(any(Profesor.class));
    }

    /*
     * HU-013 - Dar de baja profesor.
     */
    @Test
    void debeDarDeBajaProfesorCorrectamente() {

        Profesor profesor =
                crearProfesor();

        when(profesorRepository.findById(1L))
                .thenReturn(Optional.of(profesor));

        when(profesorRepository.save(any(Profesor.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        ProfesorResponse resultado =
                profesorService.darDeBaja(1L);

        assertNotNull(resultado);

        ArgumentCaptor<Profesor> captor =
                ArgumentCaptor.forClass(Profesor.class);

        verify(profesorRepository)
                .save(captor.capture());

        Profesor modificado =
                captor.getValue();

        assertFalse(modificado.getActivo());

        assertEquals(
                LocalDate.now(),
                modificado.getFechaSalida()
        );

        verify(profesorRepository, never())
                .delete(any(Profesor.class));
    }

    /*
     * HU-013 - Dar de baja profesor inexistente.
     */
    @Test
    void noDebeDarDeBajaProfesorInexistente() {

        when(profesorRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> profesorService.darDeBaja(99L)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(profesorRepository, never())
                .save(any(Profesor.class));
    }

    /*
     * HU-013 - Eliminación física.
     */
    @Test
    void debeEliminarProfesorCorrectamente() {

        Profesor profesor =
                crearProfesor();

        when(profesorRepository.findById(1L))
                .thenReturn(Optional.of(profesor));

        profesorService.eliminar(1L);

        verify(profesorRepository)
                .delete(profesor);
    }

    /*
     * HU-013 - Eliminar profesor inexistente.
     */
    @Test
    void noDebeEliminarProfesorInexistente() {

        when(profesorRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> profesorService.eliminar(99L)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(profesorRepository, never())
                .delete(any(Profesor.class));
    }

    /*
     * =========================================================
     * Métodos auxiliares
     * =========================================================
     */

    private ProfesorCreateRequest crearRequestProfesor() {

        ProfesorCreateRequest request =
                new ProfesorCreateRequest();

        request.setIdTipoIdentificacion(1);
        request.setNumeroIdentificacion("117770999");
        request.setNombres("Carlos");
        request.setPrimerApellido("Ramirez");
        request.setSegundoApellido("Mora");
        request.setTelefono("8888-7777");
        request.setCorreo("carlos@correo.com");
        request.setDireccion("Heredia");
        request.setGradoAcademico("Licenciatura");
        request.setEspecialidad("Musica");
        request.setEmpleador("SIGAC");

        request.setClasePuesto(
                "Profesor de musica"
        );

        request.setFechaIngreso(
                LocalDate.of(2026, 1, 10)
        );

        return request;
    }

    private ProfesorUpdateRequest
    crearRequestActualizacion() {

        ProfesorUpdateRequest request =
                new ProfesorUpdateRequest();

        request.setIdTipoIdentificacion(1);
        request.setNumeroIdentificacion("117770999");
        request.setNombres("Carlos Alberto");
        request.setPrimerApellido("Ramirez");
        request.setSegundoApellido("Mora");
        request.setTelefono("8888-8888");

        request.setCorreo(
                "carlos.actualizado@correo.com"
        );

        request.setDireccion("San Jose");
        request.setGradoAcademico("Maestria");

        request.setEspecialidad(
                "Educacion Musical"
        );

        request.setEmpleador("SIGAC");

        request.setClasePuesto(
                "Profesor coordinador"
        );

        request.setFechaIngreso(
                LocalDate.of(2026, 1, 10)
        );

        return request;
    }

    private TiposIdentificacion
    crearTipoIdentificacion() {

        TiposIdentificacion tipo =
                new TiposIdentificacion();

        tipo.setId(1);
        tipo.setCodigo("CED");
        tipo.setNombre("Cedula");

        return tipo;
    }

    private Profesor crearProfesor() {

        Profesor profesor =
                new Profesor();

        profesor.setId(1L);

        profesor.setIdTipoIdentificacion(
                crearTipoIdentificacion()
        );

        profesor.setNumeroIdentificacion(
                "117770999"
        );

        profesor.setNombres("Carlos");
        profesor.setPrimerApellido("Ramirez");
        profesor.setSegundoApellido("Mora");
        profesor.setTelefono("8888-7777");
        profesor.setCorreo("carlos@correo.com");
        profesor.setDireccion("Heredia");

        profesor.setGradoAcademico(
                "Licenciatura"
        );

        profesor.setEspecialidad("Musica");
        profesor.setEmpleador("SIGAC");

        profesor.setClasePuesto(
                "Profesor de musica"
        );

        profesor.setFechaIngreso(
                LocalDate.of(2026, 1, 10)
        );

        profesor.setActivo(true);

        return profesor;
    }
}