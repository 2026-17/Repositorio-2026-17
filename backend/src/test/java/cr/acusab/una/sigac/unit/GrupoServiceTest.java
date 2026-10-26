package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.GrupoService;
import cr.acusab.una.sigac.logic.service.ValidacionHorarioProfesorService;
import cr.acusab.una.sigac.presentation.dto.request.GrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.request.HorarioGrupoRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GrupoServiceTest {

    private GrupoRepository grupoRepository;
    private HorarioGrupoRepository horarioRepository;
    private CursoRepository cursoRepository;
    private PeriodoAcademicoRepository periodoRepository;
    private ProfesorRepository profesorRepository;
    private GrupoService service;

    @BeforeEach
    void setUp() {
        grupoRepository = mock(GrupoRepository.class);
        horarioRepository = mock(HorarioGrupoRepository.class);
        cursoRepository = mock(CursoRepository.class);
        periodoRepository = mock(PeriodoAcademicoRepository.class);
        profesorRepository = mock(ProfesorRepository.class);
        service = new GrupoService(
                grupoRepository, horarioRepository, cursoRepository,
                periodoRepository, profesorRepository,
                new ValidacionHorarioProfesorService(grupoRepository, horarioRepository),
                mock(UsuarioRepository.class)
        );

        Curso curso = new Curso();
        curso.setId(3L);
        curso.setActivo(true);
        when(cursoRepository.findById(3L)).thenReturn(Optional.of(curso));

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(4L);
        periodo.setEstado("PLANIFICADO");
        periodo.setFechaInicio(LocalDate.now().plusDays(10));
        periodo.setFechaFin(LocalDate.now().plusDays(100));
        when(periodoRepository.findById(4L)).thenReturn(Optional.of(periodo));
    }

    private GrupoRequestDTO request(LocalTime inicio, LocalTime fin) {
        HorarioGrupoRequestDTO horario = new HorarioGrupoRequestDTO();
        horario.setDiaSemana((short) 1);
        horario.setHoraInicio(inicio);
        horario.setHoraFin(fin);

        GrupoRequestDTO request = new GrupoRequestDTO();
        request.setIdCurso(3L);
        request.setIdPeriodo(4L);
        request.setCodigo("MUS-01-B");
        request.setCupoMaximo((short) 15);
        request.setModalidad("PRESENCIAL");
        request.setEstado("PLANIFICADO");
        request.setHorarios(List.of(horario));
        return request;
    }

    @Test
    void crear_rechazaHorarioEnLaMadrugada() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.crear(request(LocalTime.of(5, 0), LocalTime.of(6, 30)))
        );

        assertTrue(error.getMessage().contains("7:00 a. m."));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void crear_rechazaHorarioQueTerminaDespuesDeLasDiez() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.crear(request(LocalTime.of(21, 0), LocalTime.of(22, 30)))
        );

        assertTrue(error.getMessage().contains("10:00 p. m."));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void crear_rechazaProfesorConChoqueDeHorario() {
        Profesor profesor = new Profesor();
        profesor.setId(9L);
        profesor.setActivo(true);
        when(profesorRepository.findByIdParaAsignacion(9L)).thenReturn(Optional.of(profesor));

        Grupo existente = new Grupo();
        existente.setId(2L);
        existente.setCodigo("MUS-02-A");
        when(grupoRepository.findGruposProfesorEnPeriodo(
                eq(9L), eq(4L), any(LocalDate.class), any(LocalDate.class)
        )).thenReturn(List.of(existente));

        HorarioGrupo ocupado = new HorarioGrupo();
        ocupado.setIdGrupo(2L);
        ocupado.setDiaSemana((short) 1);
        ocupado.setHoraInicio(LocalTime.of(9, 30));
        ocupado.setHoraFin(LocalTime.of(10, 30));
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection()))
                .thenReturn(List.of(ocupado));

        GrupoRequestDTO request = request(LocalTime.of(9, 0), LocalTime.of(10, 0));
        request.setIdProfesor(9L);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.crear(request)
        );

        assertTrue(error.getMessage().contains("Conflicto de horario"));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }
}
