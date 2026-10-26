package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.service.AsignacionProfesorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsignacionProfesorServiceTest {

    private GrupoRepository grupoRepository;
    private CursoRepository cursoRepository;
    private ProfesorRepository profesorRepository;
    private PeriodoAcademicoRepository periodoRepository;
    private HorarioGrupoRepository horarioRepository;
    private UsuarioRepository usuarioRepository;
    private AsignacionProfesorService service;

    @BeforeEach
    void setUp() {
        grupoRepository = mock(GrupoRepository.class);
        cursoRepository = mock(CursoRepository.class);
        profesorRepository = mock(ProfesorRepository.class);
        periodoRepository = mock(PeriodoAcademicoRepository.class);
        horarioRepository = mock(HorarioGrupoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new AsignacionProfesorService(
                grupoRepository, cursoRepository, profesorRepository,
                periodoRepository, horarioRepository, usuarioRepository
        );
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listarProfesoresActivos_incluyeActivosEInactivos() {
        Profesor profesor = new Profesor();
        profesor.setId(8L);
        profesor.setNumeroIdentificacion("118760542");
        profesor.setNombres("Ana");
        profesor.setPrimerApellido("Mora");
        profesor.setSegundoApellido("Solís");
        profesor.setEspecialidad("Piano");
        profesor.setActivo(false);
        when(profesorRepository.findAllByOrderByNombresAscPrimerApellidoAsc())
                .thenReturn(List.of(profesor));

        var resultado = service.listarProfesoresActivos();

        assertEquals(1, resultado.size());
        assertEquals(8L, resultado.get(0).idProfesor());
        assertEquals("Ana Mora Solís", resultado.get(0).nombreCompleto());
        assertFalse(resultado.get(0).activo());
        verify(profesorRepository).findAllByOrderByNombresAscPrimerApellidoAsc();
    }

    @Test
    void asignar_publicaGrupoYRegistraElUsuarioQueRealizoLaAsignacion() {
        Long idGrupo = 10L;
        Long idProfesor = 4L;
        Long idPeriodo = 2L;
        LocalDate inicioPeriodo = LocalDate.now().plusDays(15);

        Grupo grupo = new Grupo();
        grupo.setId(idGrupo);
        grupo.setIdPeriodo(idPeriodo);
        grupo.setIdCurso(3L);
        grupo.setCodigo("MUS-01-A");
        grupo.setEstado("PLANIFICADO");

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(idPeriodo);
        periodo.setNombre("Periodo futuro");
        periodo.setEstado("PLANIFICADO");
        periodo.setFechaInicio(inicioPeriodo);
        periodo.setFechaFin(inicioPeriodo.plusDays(90));

        Curso curso = new Curso();
        curso.setId(3L);
        curso.setCodigo("MUS-01");
        curso.setNombre("Musica");
        curso.setActivo(true);

        Profesor profesor = new Profesor();
        profesor.setId(idProfesor);
        profesor.setActivo(true);
        profesor.setNombres("Ana");
        profesor.setPrimerApellido("Mora");
        profesor.setSegundoApellido("Solís");

        Usuario usuario = new Usuario();
        usuario.setId(77L);

        HorarioGrupo horario = new HorarioGrupo();
        horario.setId(50L);
        horario.setIdGrupo(idGrupo);
        horario.setDiaSemana((short) 2);
        horario.setHoraInicio(LocalTime.of(10, 0));
        horario.setHoraFin(LocalTime.of(11, 0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "cedula-director", "credencial", List.of()
                )
        );

        when(grupoRepository.findByIdParaAsignacion(idGrupo)).thenReturn(Optional.of(grupo));
        when(periodoRepository.findById(idPeriodo)).thenReturn(Optional.of(periodo));
        when(cursoRepository.findById(3L)).thenReturn(Optional.of(curso));
        when(profesorRepository.findById(idProfesor)).thenReturn(Optional.of(profesor));
        when(usuarioRepository.findByCedula("cedula-director")).thenReturn(Optional.of(usuario));
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection()))
                .thenReturn(List.of(horario));
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.asignar(idGrupo, idProfesor);

        assertEquals(idProfesor, resultado.idProfesor());
        assertEquals("Ana Mora Solís", resultado.nombreProfesor());
        assertEquals(idProfesor, grupo.getIdProfesor());
        assertEquals("PUBLICADO", grupo.getEstado());
        assertEquals(77L, grupo.getIdUsuarioUltimaModificacion());
        verify(grupoRepository).save(grupo);
    }

    @Test
    void asignar_permiteAunqueTodoEsteBloqueado() {
        // Profesor inactivo, curso inactivo, período cerrado y ya iniciado,
        // grupo sin horario: igual se debe permitir la asignación.
        Long idGrupo = 31L;
        Long idProfesor = 14L;
        Long idPeriodo = 9L;

        Grupo grupo = new Grupo();
        grupo.setId(idGrupo);
        grupo.setIdPeriodo(idPeriodo);
        grupo.setIdCurso(3L);
        grupo.setCodigo("MUS-02-A");
        grupo.setEstado("EN_CURSO");

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(idPeriodo);
        periodo.setNombre("Período iniciado");
        periodo.setEstado("CERRADO");
        periodo.setFechaInicio(LocalDate.now(java.time.ZoneId.of("America/Costa_Rica")).minusDays(1));
        periodo.setFechaFin(LocalDate.now(java.time.ZoneId.of("America/Costa_Rica")).plusDays(60));

        Curso curso = new Curso();
        curso.setId(3L);
        curso.setActivo(false);

        Profesor profesor = new Profesor();
        profesor.setId(idProfesor);
        profesor.setActivo(false);
        profesor.setNombres("Luis");
        profesor.setPrimerApellido("Rojas");

        Usuario usuario = new Usuario();
        usuario.setId(77L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "cedula-director", "credencial", List.of()
                )
        );

        when(profesorRepository.findById(idProfesor)).thenReturn(Optional.of(profesor));
        when(grupoRepository.findByIdParaAsignacion(idGrupo)).thenReturn(Optional.of(grupo));
        when(periodoRepository.findById(idPeriodo)).thenReturn(Optional.of(periodo));
        when(cursoRepository.findById(3L)).thenReturn(Optional.of(curso));
        when(usuarioRepository.findByCedula("cedula-director")).thenReturn(Optional.of(usuario));
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection()))
                .thenReturn(List.of());
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.asignar(idGrupo, idProfesor);

        assertEquals(idProfesor, resultado.idProfesor());
        assertEquals(idProfesor, grupo.getIdProfesor());
        assertEquals("EN_CURSO", grupo.getEstado());
        verify(grupoRepository).save(grupo);
        verify(grupoRepository, never()).findGruposProfesorEnPeriodo(
                anyLong(), anyLong(), any(LocalDate.class), any(LocalDate.class)
        );
    }

    @Test
    void eliminar_quitaAsignacionYRegistraUsuario() {
        Long idGrupo = 41L;

        Grupo grupo = new Grupo();
        grupo.setId(idGrupo);
        grupo.setIdPeriodo(12L);
        grupo.setIdCurso(9L);
        grupo.setIdProfesor(18L);
        grupo.setEstado("PUBLICADO");

        Usuario usuario = new Usuario();
        usuario.setId(77L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "cedula-director", "credencial", List.of()
                )
        );

        when(grupoRepository.findByIdParaAsignacion(idGrupo)).thenReturn(Optional.of(grupo));
        when(usuarioRepository.findByCedula("cedula-director")).thenReturn(Optional.of(usuario));
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.eliminarAsignacion(idGrupo));

        assertNull(grupo.getIdProfesor());
        assertEquals("PLANIFICADO", grupo.getEstado());
        assertEquals(77L, grupo.getIdUsuarioUltimaModificacion());
        verify(grupoRepository).save(grupo);
    }

    @Test
    void eliminar_permiteCuandoElPeriodoYaInicio() {
        Long idGrupo = 42L;

        Grupo grupo = new Grupo();
        grupo.setId(idGrupo);
        grupo.setIdPeriodo(13L);
        grupo.setIdProfesor(18L);
        grupo.setEstado("EN_CURSO");

        Usuario usuario = new Usuario();
        usuario.setId(77L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "cedula-director", "credencial", List.of()
                )
        );

        when(grupoRepository.findByIdParaAsignacion(idGrupo)).thenReturn(Optional.of(grupo));
        when(usuarioRepository.findByCedula("cedula-director")).thenReturn(Optional.of(usuario));
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.eliminarAsignacion(idGrupo));

        assertNull(grupo.getIdProfesor());
        verify(grupoRepository).save(grupo);
    }
}
