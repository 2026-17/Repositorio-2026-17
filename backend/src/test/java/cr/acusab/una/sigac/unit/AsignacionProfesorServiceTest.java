package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Bitacora;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.Horario;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.BitacoraRepository;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.AsignacionProfesorService;
import cr.acusab.una.sigac.presentation.dto.request.AsignarProfesorRequest;
import cr.acusab.una.sigac.presentation.dto.request.HorarioRequest;
import cr.acusab.una.sigac.presentation.dto.response.AsignacionGrupoResponse;
import cr.acusab.una.sigac.presentation.dto.response.GrupoDeProfesorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsignacionProfesorServiceTest {

    private GrupoRepository grupoRepository;
    private HorarioRepository horarioRepository;
    private ProfesorRepository profesorRepository;
    private CursoRepository cursoRepository;
    private PeriodoAcademicoRepository periodoAcademicoRepository;
    private BitacoraRepository bitacoraRepository;
    private UsuarioRepository usuarioRepository;

    private AsignacionProfesorService asignacionProfesorService;

    @BeforeEach
    void setUp() {
        grupoRepository = mock(GrupoRepository.class);
        horarioRepository = mock(HorarioRepository.class);
        profesorRepository = mock(ProfesorRepository.class);
        cursoRepository = mock(CursoRepository.class);
        periodoAcademicoRepository = mock(PeriodoAcademicoRepository.class);
        bitacoraRepository = mock(BitacoraRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);

        asignacionProfesorService = new AsignacionProfesorService(
                grupoRepository,
                horarioRepository,
                profesorRepository,
                cursoRepository,
                periodoAcademicoRepository,
                bitacoraRepository,
                usuarioRepository
        );

        when(grupoRepository.save(any(Grupo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(horarioRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Usuario usuarioAutenticado = mock(Usuario.class);
        when(usuarioAutenticado.getId()).thenReturn(7L);
        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuarioAutenticado));

        configurarUsuarioAutenticado();
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * HU-014 - Asignar profesor a un grupo
     * ============================================================
     */

    @Test
    void debeAsignarProfesorCorrectamente() {

        Grupo grupo = grupoDe(10L, 1L, 2L, null, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));

        Profesor profesor = profesorActivo(5L);
        when(profesorRepository.findById(5L)).thenReturn(Optional.of(profesor));

        PeriodoAcademico periodo = periodoFuturo(2L);
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo));

        when(grupoRepository.findByIdProfesorAndIdPeriodoAndIdNot(5L, 2L, 10L))
                .thenReturn(List.of());

        Curso curso = cursoDe(1L, "CUR-101", "Piano Individual I");
        when(cursoRepository.findById(1L)).thenReturn(Optional.of(curso));

        AsignarProfesorRequest request = requestConUnHorario();

        AsignacionGrupoResponse respuesta = asignacionProfesorService.asignar(10L, request);

        assertNotNull(respuesta);
        assertEquals(5L, respuesta.getIdProfesor());
        assertEquals("PLANIFICADO", respuesta.getEstadoGrupo());
        assertEquals(1, respuesta.getHorarios().size());

        verify(horarioRepository).deleteByIdGrupo_Id(10L);
        verify(bitacoraRepository).save(any(Bitacora.class));
    }

    @Test
    void noDebeAsignarProfesorInexistente() {

        Grupo grupo = grupoDe(10L, 1L, 2L, null, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));
        when(profesorRepository.findById(5L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> asignacionProfesorService.asignar(10L, requestConUnHorario())
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void noDebeAsignarProfesorInactivo() {

        Grupo grupo = grupoDe(10L, 1L, 2L, null, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));

        Profesor profesorInactivo = profesorActivo(5L);
        profesorInactivo.setActivo(false);
        when(profesorRepository.findById(5L)).thenReturn(Optional.of(profesorInactivo));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> asignacionProfesorService.asignar(10L, requestConUnHorario())
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void noDebeAsignarSiElPeriodoYaInicio() {

        Grupo grupo = grupoDe(10L, 1L, 2L, null, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));

        Profesor profesor = profesorActivo(5L);
        when(profesorRepository.findById(5L)).thenReturn(Optional.of(profesor));

        PeriodoAcademico periodoIniciado = new PeriodoAcademico();
        periodoIniciado.setId(2L);
        periodoIniciado.setFechaInicio(LocalDate.now().minusDays(1));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodoIniciado));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> asignacionProfesorService.asignar(10L, requestConUnHorario())
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void noDebeAsignarSiHayConflictoDeHorario() {

        Grupo grupo = grupoDe(10L, 1L, 2L, null, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));

        Profesor profesor = profesorActivo(5L);
        when(profesorRepository.findById(5L)).thenReturn(Optional.of(profesor));

        PeriodoAcademico periodo = periodoFuturo(2L);
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo));

        Grupo otroGrupo = grupoDe(20L, 3L, 2L, 5L, "CUR-202-A");
        when(grupoRepository.findByIdProfesorAndIdPeriodoAndIdNot(5L, 2L, 10L))
                .thenReturn(List.of(otroGrupo));

        Horario horarioExistente = new Horario();
        horarioExistente.setIdGrupo(otroGrupo);
        horarioExistente.setDiaSemana((short) 1);
        horarioExistente.setHoraInicio(LocalTime.of(8, 0));
        horarioExistente.setHoraFin(LocalTime.of(10, 0));

        when(horarioRepository.findByIdGrupo_IdIn(List.of(20L)))
                .thenReturn(List.of(horarioExistente));

        // El nuevo horario solicitado (lunes 9-11) se solapa con el existente (lunes 8-10).
        AsignarProfesorRequest request = new AsignarProfesorRequest(
                5L, List.of(new HorarioRequest((short) 1, LocalTime.of(9, 0), LocalTime.of(11, 0), "A1"))
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> asignacionProfesorService.asignar(10L, request)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void debeReasignarReemplazandoHorariosYRegistrandoBitacora() {

        Grupo grupo = grupoDe(10L, 1L, 2L, 99L, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));

        Profesor profesorNuevo = profesorActivo(5L);
        when(profesorRepository.findById(5L)).thenReturn(Optional.of(profesorNuevo));

        PeriodoAcademico periodo = periodoFuturo(2L);
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo));

        when(grupoRepository.findByIdProfesorAndIdPeriodoAndIdNot(5L, 2L, 10L))
                .thenReturn(List.of());

        Curso curso = cursoDe(1L, "CUR-101", "Piano Individual I");
        when(cursoRepository.findById(1L)).thenReturn(Optional.of(curso));

        asignacionProfesorService.asignar(10L, requestConUnHorario());

        assertEquals(5L, grupo.getIdProfesor());

        ArgumentCaptor<Bitacora> captor = ArgumentCaptor.forClass(Bitacora.class);
        verify(bitacoraRepository).save(captor.capture());

        Bitacora bitacora = captor.getValue();
        assertEquals("grupos", bitacora.getTablaAfectada());
        assertEquals(10L, bitacora.getIdRegistro());
        assertTrue(bitacora.getDatosAnteriores().contains("99"));
        assertTrue(bitacora.getDatosNuevos().contains("5"));
    }

    @Test
    void debeEliminarAsignacionAntesDeIniciarElPeriodo() {

        Grupo grupo = grupoDe(10L, 1L, 2L, 5L, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));

        PeriodoAcademico periodo = periodoFuturo(2L);
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo));

        asignacionProfesorService.eliminarAsignacion(10L);

        assertNull(grupo.getIdProfesor());
        verify(horarioRepository).deleteByIdGrupo_Id(10L);
        verify(bitacoraRepository).save(any(Bitacora.class));
    }

    @Test
    void noDebeEliminarAsignacionSiElPeriodoYaInicio() {

        Grupo grupo = grupoDe(10L, 1L, 2L, 5L, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));

        PeriodoAcademico periodoIniciado = new PeriodoAcademico();
        periodoIniciado.setId(2L);
        periodoIniciado.setFechaInicio(LocalDate.now());
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodoIniciado));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> asignacionProfesorService.eliminarAsignacion(10L)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(horarioRepository, never()).deleteByIdGrupo_Id(anyLong());
    }

    /*
     * ============================================================
     * HU-014 - Habilitar ("publicar") un grupo
     * ============================================================
     */

    @Test
    void noDebePublicarSinProfesorAsignado() {

        Grupo grupo = grupoDe(10L, 1L, 2L, null, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> asignacionProfesorService.publicar(10L)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void debePublicarCuandoHayProfesorAsignado() {

        Grupo grupo = grupoDe(10L, 1L, 2L, 5L, "CUR-101-A");
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));

        Curso curso = cursoDe(1L, "CUR-101", "Piano Individual I");
        when(cursoRepository.findById(1L)).thenReturn(Optional.of(curso));

        PeriodoAcademico periodo = periodoFuturo(2L);
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo));

        Profesor profesor = profesorActivo(5L);
        when(profesorRepository.findById(5L)).thenReturn(Optional.of(profesor));

        when(horarioRepository.findByIdGrupo_Id(10L)).thenReturn(List.of());

        AsignacionGrupoResponse respuesta = asignacionProfesorService.publicar(10L);

        assertEquals("PUBLICADO", respuesta.getEstadoGrupo());
        assertEquals("PUBLICADO", grupo.getEstado());
    }

    /*
     * ============================================================
     * HU-014 - Cursos asignados a un profesor
     * ============================================================
     */

    @Test
    void debeListarGruposAsignadosAUnProfesor() {

        when(profesorRepository.existsById(5L)).thenReturn(true);

        Grupo grupo = grupoDe(10L, 1L, 2L, 5L, "CUR-101-A");
        when(grupoRepository.findByIdProfesor(5L)).thenReturn(List.of(grupo));

        Curso curso = cursoDe(1L, "CUR-101", "Piano Individual I");
        when(cursoRepository.findAllById(List.of(1L))).thenReturn(List.of(curso));

        PeriodoAcademico periodo = periodoFuturo(2L);
        when(periodoAcademicoRepository.findAllById(List.of(2L))).thenReturn(List.of(periodo));

        when(horarioRepository.findByIdGrupo_IdIn(List.of(10L))).thenReturn(List.of());

        List<GrupoDeProfesorResponse> resultado =
                asignacionProfesorService.listarGruposDeProfesor(5L, null);

        assertEquals(1, resultado.size());
        assertEquals("CUR-101", resultado.get(0).getCodigoCurso());
    }

    /*
     * ============================================================
     * Métodos auxiliares para las pruebas
     * ============================================================
     */

    private Grupo grupoDe(Long id, Long idCurso, Long idPeriodo, Long idProfesor, String codigo) {
        Grupo grupo = new Grupo();
        grupo.setId(id);
        grupo.setIdCurso(idCurso);
        grupo.setIdPeriodo(idPeriodo);
        grupo.setIdProfesor(idProfesor);
        grupo.setCodigo(codigo);
        grupo.setCupoMaximo((short) 20);
        grupo.setModalidad("PRESENCIAL");
        grupo.setEstado("PLANIFICADO");
        return grupo;
    }

    private Profesor profesorActivo(Long id) {
        Profesor profesor = new Profesor();
        profesor.setId(id);
        profesor.setNombres("Marlene");
        profesor.setPrimerApellido("Solano");
        profesor.setActivo(true);
        return profesor;
    }

    private Curso cursoDe(Long id, String codigo, String nombre) {
        Curso curso = new Curso();
        curso.setId(id);
        curso.setCodigo(codigo);
        curso.setNombre(nombre);
        return curso;
    }

    private PeriodoAcademico periodoFuturo(Long id) {
        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(id);
        periodo.setCodigo("2026-II");
        periodo.setFechaInicio(LocalDate.now().plusDays(30));
        periodo.setFechaFin(LocalDate.now().plusDays(120));
        return periodo;
    }

    private AsignarProfesorRequest requestConUnHorario() {
        return new AsignarProfesorRequest(
                5L,
                List.of(new HorarioRequest((short) 1, LocalTime.of(8, 0), LocalTime.of(10, 0), "A1"))
        );
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
