package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.EspacioInstrumentoService;
import cr.acusab.una.sigac.logic.service.EspecialidadProfesor;
import cr.acusab.una.sigac.logic.service.ValidacionHorarioProfesorService;
import cr.acusab.una.sigac.presentation.dto.request.EspaciosInstrumentoRequest;
import cr.acusab.una.sigac.presentation.dto.request.HorarioGrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.EspacioInstrumentoResponse;
import cr.acusab.una.sigac.presentation.dto.response.InstrumentoProfesorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Espacios de "cualquier instrumento del profesor" - crear y agenda.
 *
 * Instrumentos activos: Violín (7), Viola (8) y Violonchelo (9).
 * Profesora 12: "Violín, Viola". Profesor 13: solo "Violín".
 */
class EspaciosVariosInstrumentosTest {

    private static final int VIOLIN = 7;
    private static final int VIOLA = 8;
    private static final int VIOLONCHELO = 9;

    private GrupoRepository grupoRepository;
    private ProfesorRepository profesorRepository;
    private MatriculaDetalleRepository detalleRepository;
    private EspacioInstrumentoService service;

    private Profesor profesora;
    private Profesor profesorUnInstrumento;
    private final List<Instrumento> instrumentos = new ArrayList<>();
    private long siguienteId = 100;

    @BeforeEach
    void setUp() {
        grupoRepository = mock(GrupoRepository.class);
        profesorRepository = mock(ProfesorRepository.class);
        detalleRepository = mock(MatriculaDetalleRepository.class);
        HorarioGrupoRepository horarioRepository = mock(HorarioGrupoRepository.class);
        PeriodoAcademicoRepository periodoRepository = mock(PeriodoAcademicoRepository.class);
        InstrumentoRepository instrumentoRepository = mock(InstrumentoRepository.class);
        CursoRepository cursoRepository = mock(CursoRepository.class);
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);

        service = new EspacioInstrumentoService(
                grupoRepository, horarioRepository, periodoRepository, instrumentoRepository,
                profesorRepository, cursoRepository, estudianteRepository, detalleRepository,
                new ValidacionHorarioProfesorService(grupoRepository, horarioRepository), usuarioRepository);

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(4L);
        periodo.setNombre("I ciclo 2027");
        periodo.setEstado("PLANIFICADO");
        periodo.setFechaInicio(LocalDate.of(2027, 2, 1));
        periodo.setFechaFin(LocalDate.of(2027, 6, 30));
        when(periodoRepository.findById(4L)).thenReturn(Optional.of(periodo));
        when(periodoRepository.findAllById(anyCollection())).thenReturn(List.of(periodo));

        instrumentos.add(instrumento(VIOLIN, "Violín"));
        instrumentos.add(instrumento(VIOLA, "Viola"));
        instrumentos.add(instrumento(VIOLONCHELO, "Violonchelo"));
        when(instrumentoRepository.findByActivoTrue()).thenAnswer(inv -> new ArrayList<>(instrumentos));
        when(instrumentoRepository.findById(anyInt())).thenAnswer(inv -> instrumentos.stream()
                .filter(i -> i.getId().equals(inv.getArgument(0))).findFirst());
        when(instrumentoRepository.findAllById(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return instrumentos.stream().filter(i -> ids.contains(i.getId())).toList();
        });

        profesora = profesor(12L, "Violín, Viola");
        profesorUnInstrumento = profesor(13L, "Violín");
        when(profesorRepository.findById(12L)).thenReturn(Optional.of(profesora));
        when(profesorRepository.findById(13L)).thenReturn(Optional.of(profesorUnInstrumento));
        when(profesorRepository.findAllById(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return java.util.stream.Stream.of(profesora, profesorUnInstrumento)
                    .filter(p -> ids.contains(p.getId())).toList();
        });

        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> {
            Grupo g = inv.getArgument(0);
            g.setId(siguienteId++);
            return g;
        });

        Estudiante ana = new Estudiante();
        ana.setId(1L);
        ana.setNombres("Ana");
        ana.setPrimerApellido("Prueba");
        when(estudianteRepository.findAllById(anyCollection())).thenReturn(List.of(ana));

        Curso nivel = new Curso();
        nivel.setId(201L);
        nivel.setNombre("Instrumento I");
        when(cursoRepository.findAllById(anyCollection())).thenReturn(List.of(nivel));

        Usuario director = new Usuario();
        director.setId(1L);
        when(usuarioRepository.findByCedula("111")).thenReturn(Optional.of(director));

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("111");
        SecurityContext contexto = mock(SecurityContext.class);
        when(contexto.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(contexto);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crear_sinInstrumento_profesorDeDosInstrumentos_creaEspaciosSinInstrumento() {

        service.crear(request(12L, null, bloque(1, "07:10", "07:50"), bloque(1, "07:50", "08:30")));

        ArgumentCaptor<Grupo> grupos = ArgumentCaptor.forClass(Grupo.class);
        verify(grupoRepository, times(2)).save(grupos.capture());

        for (Grupo espacio : grupos.getAllValues()) {
            assertEquals(Grupo.TIPO_ESPACIO_INSTRUMENTO, espacio.getTipoGrupo());
            assertNull(espacio.getIdInstrumento());
            assertNull(espacio.getIdCurso());
            assertEquals((short) 1, espacio.getCupoMaximo());
            assertEquals(12L, espacio.getIdProfesor());
        }
        assertEquals("ESP-VAR-12-1", grupos.getAllValues().get(0).getCodigo());
        assertEquals("ESP-VAR-12-2", grupos.getAllValues().get(1).getCodigo());
    }

    @Test
    void crear_sinInstrumento_profesorDeUnSoloInstrumento_seRechaza() {

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.crear(request(13L, null, bloque(1, "07:10", "07:50"))));

        assertTrue(error.getMessage().contains("al menos dos instrumentos activos"));
        assertTrue(error.getMessage().contains("solo tiene Violín"));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void crear_sinInstrumento_cuentaSoloLosInstrumentosActivos() {
        instrumentos.removeIf(i -> i.getId() == VIOLA);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.crear(request(12L, null, bloque(1, "07:10", "07:50"))));

        assertTrue(error.getMessage().contains("al menos dos instrumentos activos"));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void especialidad_comparaElNombreExacto_noContiene() {
        Profesor p = profesor(20L, " violín ,Violonchelo");

        assertEquals(List.of("Violín", "Violonchelo"),
                EspecialidadProfesor.instrumentosDe(p, instrumentos).stream().map(Instrumento::getNombre).toList());
        // "Viola" no está aunque "Violonchelo" la "contenga" en parte.
        assertFalse(EspecialidadProfesor.da(p, instrumentos.get(1)));
        assertTrue(EspecialidadProfesor.da(p, instrumentos.get(0)));
    }

    @Test
    void instrumentosDelProfesor_devuelveLosQueDa() {
        List<InstrumentoProfesorResponse> lista = service.instrumentosDelProfesor(12L);

        assertEquals(List.of("Violín", "Viola"), lista.stream().map(InstrumentoProfesorResponse::nombre).toList());
    }

    @Test
    void agenda_espacioSinInstrumento_muestraLosDelProfesorYLoQueCursaElOcupante() {
        Grupo libre = espacio(1001L, null, 12L);
        Grupo tomado = espacio(1002L, null, 12L);
        when(grupoRepository.findEspacios(4L, null, 12L)).thenReturn(List.of(libre, tomado));
        when(detalleRepository.findOcupantesDeGrupos(anyCollection(), eq("REGISTRADA")))
                .thenReturn(List.<Object[]>of(new Object[]{1002L, 201L, 1L, VIOLA}));

        List<EspacioInstrumentoResponse> agenda = service.listar(4L, null, 12L);

        EspacioInstrumentoResponse r1 = agenda.stream().filter(e -> e.idGrupo() == 1001L).findFirst().orElseThrow();
        assertTrue(r1.variosInstrumentos());
        assertNull(r1.idInstrumento());
        assertEquals("Violín o Viola", r1.nombreInstrumento());
        assertFalse(r1.ocupado());
        assertNull(r1.nombreInstrumentoEstudiante());

        EspacioInstrumentoResponse r2 = agenda.stream().filter(e -> e.idGrupo() == 1002L).findFirst().orElseThrow();
        assertTrue(r2.ocupado());
        assertEquals(VIOLA, r2.idInstrumentoEstudiante());
        assertEquals("Viola", r2.nombreInstrumentoEstudiante());
        assertEquals("Instrumento I", r2.nombreNivel());
    }

    @Test
    void agenda_filtradaPorInstrumento_soloIncluyeLosEspaciosVariosDeProfesoresQueLoDan() {
        Grupo deLaProfesora = espacio(1001L, null, 12L);
        Grupo deOtro = espacio(1003L, null, 13L);
        when(grupoRepository.findEspacios(4L, VIOLA, null)).thenReturn(List.of(deLaProfesora, deOtro));

        List<EspacioInstrumentoResponse> agenda = service.listar(4L, VIOLA, null);

        assertEquals(List.of(1001L), agenda.stream().map(EspacioInstrumentoResponse::idGrupo).toList());
    }

    /* ------------------------------------------------------------ */

    private EspaciosInstrumentoRequest request(Long idProfesor, Integer idInstrumento, HorarioGrupoRequestDTO... bloques) {
        EspaciosInstrumentoRequest request = new EspaciosInstrumentoRequest();
        request.setIdPeriodo(4L);
        request.setIdInstrumento(idInstrumento);
        request.setIdProfesor(idProfesor);
        request.setBloques(List.of(bloques));
        return request;
    }

    private static HorarioGrupoRequestDTO bloque(int dia, String inicio, String fin) {
        HorarioGrupoRequestDTO bloque = new HorarioGrupoRequestDTO();
        bloque.setDiaSemana((short) dia);
        bloque.setHoraInicio(LocalTime.parse(inicio));
        bloque.setHoraFin(LocalTime.parse(fin));
        return bloque;
    }

    private static Grupo espacio(Long id, Integer idInstrumento, Long idProfesor) {
        Grupo g = new Grupo();
        g.setId(id);
        g.setCodigo("ESP-VAR-" + idProfesor + "-" + id);
        g.setTipoGrupo(Grupo.TIPO_ESPACIO_INSTRUMENTO);
        g.setIdInstrumento(idInstrumento);
        g.setIdProfesor(idProfesor);
        g.setIdPeriodo(4L);
        g.setCupoMaximo((short) 1);
        g.setEstado("PUBLICADO");
        return g;
    }

    private static Instrumento instrumento(int id, String nombre) {
        Instrumento i = new Instrumento();
        i.setId(id);
        i.setCodigo(nombre.toUpperCase());
        i.setNombre(nombre);
        i.setActivo(true);
        return i;
    }

    private static Profesor profesor(Long id, String especialidad) {
        Profesor p = new Profesor();
        p.setId(id);
        p.setNombres("Prof");
        p.setPrimerApellido(String.valueOf(id));
        p.setActivo(true);
        p.setEspecialidad(especialidad);
        return p;
    }
}
