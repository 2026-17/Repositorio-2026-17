package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.service.AsignacionProfesorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

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
    void listarProfesoresActivos_soloUsaElCatalogoDeActivos() {
        Profesor profesor = new Profesor();
        profesor.setId(8L);
        profesor.setNumeroIdentificacion("118760542");
        profesor.setNombres("Ana");
        profesor.setPrimerApellido("Mora");
        profesor.setSegundoApellido("Solís");
        profesor.setEspecialidad("Piano");
        when(profesorRepository.findByActivoTrueOrderByNombresAscPrimerApellidoAsc())
                .thenReturn(List.of(profesor));

        var resultado = service.listarProfesoresActivos();

        assertEquals(1, resultado.size());
        assertEquals(8L, resultado.get(0).idProfesor());
        assertEquals("Ana Mora Solís", resultado.get(0).nombreCompleto());
        verify(profesorRepository).findByActivoTrueOrderByNombresAscPrimerApellidoAsc();
    }

    @Test
    void asignar_rechazaConflictoDeHorarioConOtroGrupoDelMismoPeriodo() {
        Long idGrupo = 1L;
        Long idProfesor = 9L;
        Long idPeriodo = 4L;

        Grupo destino = new Grupo();
        destino.setId(idGrupo);
        destino.setIdPeriodo(idPeriodo);
        destino.setIdCurso(3L);
        destino.setEstado("PLANIFICADO");

        Grupo existente = new Grupo();
        existente.setId(2L);
        existente.setIdPeriodo(idPeriodo);
        existente.setIdCurso(7L);
        existente.setIdProfesor(idProfesor);
        existente.setCodigo("G-B");
        existente.setEstado("PUBLICADO");

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(idPeriodo);
        periodo.setNombre("Periodo futuro");
        periodo.setEstado("PLANIFICADO");
        periodo.setFechaInicio(LocalDate.now().plusDays(20));
        periodo.setFechaFin(LocalDate.now().plusDays(120));

        Curso curso = new Curso();
        curso.setId(3L);
        curso.setActivo(true);

        Profesor profesor = new Profesor();
        profesor.setId(idProfesor);
        profesor.setActivo(true);

        HorarioGrupo nuevo = new HorarioGrupo();
        nuevo.setIdGrupo(idGrupo);
        nuevo.setDiaSemana((short) 1);
        nuevo.setHoraInicio(LocalTime.of(9, 0));
        nuevo.setHoraFin(LocalTime.of(10, 0));

        HorarioGrupo ocupado = new HorarioGrupo();
        ocupado.setIdGrupo(2L);
        ocupado.setDiaSemana((short) 1);
        ocupado.setHoraInicio(LocalTime.of(9, 30));
        ocupado.setHoraFin(LocalTime.of(10, 30));

        when(grupoRepository.findByIdParaAsignacion(idGrupo)).thenReturn(Optional.of(destino));
        when(periodoRepository.findById(idPeriodo)).thenReturn(Optional.of(periodo));
        when(cursoRepository.findById(3L)).thenReturn(Optional.of(curso));
        when(profesorRepository.findByIdParaAsignacion(idProfesor)).thenReturn(Optional.of(profesor));
        when(grupoRepository.findGruposProfesorEnPeriodo(
                eq(idProfesor), eq(idPeriodo), any(LocalDate.class), any(LocalDate.class)
        )).thenReturn(List.of(existente));
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection()))
                .thenReturn(List.of(nuevo), List.of(ocupado));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.asignar(idGrupo, idProfesor)
        );

        assertTrue(error.getMessage().contains("Conflicto de horario"));
        verify(grupoRepository, never()).save(any(Grupo.class));
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
        when(profesorRepository.findByIdParaAsignacion(idProfesor)).thenReturn(Optional.of(profesor));
        when(profesorRepository.findById(idProfesor)).thenReturn(Optional.of(profesor));
        when(usuarioRepository.findByCedula("cedula-director")).thenReturn(Optional.of(usuario));
        when(grupoRepository.findGruposProfesorEnPeriodo(
                eq(idProfesor), eq(idPeriodo), any(LocalDate.class), any(LocalDate.class)
        )).thenReturn(List.of());
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection()))
                .thenReturn(List.of(horario), List.of(horario));
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.asignar(idGrupo, idProfesor);

        assertEquals(idProfesor, resultado.idProfesor());
        assertEquals("Ana Mora Solís", resultado.nombreProfesor());
        assertEquals(idProfesor, grupo.getIdProfesor());
        assertEquals("PUBLICADO", grupo.getEstado());
        assertEquals(77L, grupo.getIdUsuarioUltimaModificacion());
        verify(grupoRepository).save(grupo);
    }

}
