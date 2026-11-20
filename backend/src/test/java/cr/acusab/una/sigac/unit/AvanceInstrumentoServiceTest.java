package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.CursoReconocido;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantePlanEstudio;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.AvanceEstudianteService;
import cr.acusab.una.sigac.logic.service.AvanceInstrumentoService;
import cr.acusab.una.sigac.presentation.dto.request.NivelIngresoRequest;
import cr.acusab.una.sigac.presentation.dto.response.AvanceInstrumentosResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Avance por instrumento.
 *
 * Plan 10: Instrumento I..XIV (cursos 201..214, niveles 1..14) y
 * Solfeo I (301). Estudiante 1 con Piano (5) y Guitarra (6).
 */
class AvanceInstrumentoServiceTest {

    private static final long ID_ESTUDIANTE = 1L;
    private static final long ID_PLAN = 10L;
    private static final int PIANO = 5;
    private static final int GUITARRA = 6;

    private static final String[] ROMANOS = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV"
    };

    private MatriculaDetalleRepository detalleRepository;
    private CursoReconocidoRepository reconocidoRepository;
    private EstudianteInstrumentoRepository estudianteInstrumentoRepository;

    private AvanceInstrumentoService avance;
    private AvanceEstudianteService avanceEstudiante;

    private final List<Object[]> nivelesAprobadosSigac = new ArrayList<>();
    private final List<Object[]> cursosAprobadosSigac = new ArrayList<>();
    private final List<CursoReconocido> reconocidos = new ArrayList<>();
    private final List<Curso> cursos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        detalleRepository = mock(MatriculaDetalleRepository.class);
        reconocidoRepository = mock(CursoReconocidoRepository.class);
        MallaCurricularRepository mallaRepository = mock(MallaCurricularRepository.class);
        CursoRepository cursoRepository = mock(CursoRepository.class);
        estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        EstudiantePlanEstudioRepository estudiantePlanRepository = mock(EstudiantePlanEstudioRepository.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);

        avance = new AvanceInstrumentoService(detalleRepository, reconocidoRepository, mallaRepository, cursoRepository);

        avanceEstudiante = new AvanceEstudianteService(
                avance,
                estudianteRepository,
                estudianteInstrumentoRepository,
                estudiantePlanRepository,
                mock(PlanEstudioRepository.class),
                mock(PeriodoAcademicoRepository.class),
                reconocidoRepository,
                mock(BitacoraRepository.class),
                usuarioRepository,
                new ObjectMapper()
        );

        // Malla: Instrumento I..XIV y Solfeo I.
        List<MallaCurricular> malla = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            cursos.add(curso(201L + i, "Instrumento " + ROMANOS[i], true));
            malla.add(malla(201L + i, i + 1, 1));
        }
        cursos.add(curso(301L, "Solfeo I", false));
        malla.add(malla(301L, 1, 2));

        when(mallaRepository.findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc(ID_PLAN))
                .thenReturn(malla);
        when(cursoRepository.findAllById(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return cursos.stream().filter(c -> ids.contains(c.getId())).toList();
        });

        when(detalleRepository.findNivelesInstrumentoAprobados(ID_ESTUDIANTE))
                .thenAnswer(inv -> new ArrayList<>(nivelesAprobadosSigac));
        when(detalleRepository.findCursosAprobados(ID_ESTUDIANTE))
                .thenAnswer(inv -> new ArrayList<>(cursosAprobadosSigac));

        // cursos_reconocidos en memoria.
        when(reconocidoRepository.findByIdEstudiante(ID_ESTUDIANTE))
                .thenAnswer(inv -> new ArrayList<>(reconocidos));
        when(reconocidoRepository.findByIdEstudianteAndIdInstrumento(eq(ID_ESTUDIANTE), anyInt()))
                .thenAnswer(inv -> reconocidos.stream()
                        .filter(r -> r.getIdInstrumento() != null && r.getIdInstrumento().equals(inv.getArgument(1)))
                        .toList());
        when(reconocidoRepository.findByIdEstudianteAndIdInstrumentoAndOrigen(eq(ID_ESTUDIANTE), anyInt(), anyString()))
                .thenAnswer(inv -> reconocidos.stream()
                        .filter(r -> r.getIdInstrumento() != null && r.getIdInstrumento().equals(inv.getArgument(1))
                                && r.getOrigen().equals(inv.getArgument(2)))
                        .toList());
        when(reconocidoRepository.save(any(CursoReconocido.class))).thenAnswer(inv -> {
            reconocidos.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        // Estudiante con Piano (del plan) y Guitarra (extra).
        when(estudianteRepository.existsById(ID_ESTUDIANTE)).thenReturn(true);
        EstudiantePlanEstudio inscripcion = new EstudiantePlanEstudio();
        inscripcion.setIdEstudiante(ID_ESTUDIANTE);
        inscripcion.setIdPlanEstudio(ID_PLAN);
        when(estudiantePlanRepository.findByIdEstudianteAndActivoTrue(ID_ESTUDIANTE)).thenReturn(List.of(inscripcion));

        EstudiantesInstrumento piano = asignacion(50L, PIANO, "Piano", true);
        EstudiantesInstrumento guitarra = asignacion(51L, GUITARRA, "Guitarra", false);
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(ID_ESTUDIANTE))
                .thenReturn(List.of(piano, guitarra));
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndIdInstrumento_IdAndFechaFinIsNull(ID_ESTUDIANTE, PIANO))
                .thenReturn(Optional.of(piano));

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
    void estudianteNuevo_leCorrespondeInstrumentoI() {
        assertEquals("Instrumento I", avance.siguienteNivel(ID_ESTUDIANTE, PIANO, ID_PLAN).orElseThrow().getNombre());
    }

    @Test
    void aproboVEnPiano_leCorrespondeVIEnPianoEIEnGuitarra() {
        for (int i = 0; i < 5; i++) {
            nivelesAprobadosSigac.add(new Object[]{201L + i, PIANO, new BigDecimal("90"), 3L});
        }

        assertEquals("Instrumento VI", avance.siguienteNivel(ID_ESTUDIANTE, PIANO, ID_PLAN).orElseThrow().getNombre());
        assertEquals("Instrumento I", avance.siguienteNivel(ID_ESTUDIANTE, GUITARRA, ID_PLAN).orElseThrow().getNombre());

        // Para un nivel de guitarra, Instrumento V de piano no cuenta.
        assertFalse(avance.cursosAprobadosParaInstrumento(ID_ESTUDIANTE, GUITARRA).containsKey(205L));
        assertTrue(avance.cursosAprobadosParaInstrumento(ID_ESTUDIANTE, PIANO).containsKey(205L));
    }

    @Test
    void completoLosCatorceNiveles_noHaySiguiente() {
        for (int i = 0; i < 14; i++) {
            nivelesAprobadosSigac.add(new Object[]{201L + i, PIANO, new BigDecimal("90"), 3L});
        }

        assertTrue(avance.siguienteNivel(ID_ESTUDIANTE, PIANO, ID_PLAN).isEmpty());
    }

    @Test
    void nivelDeIngresoIII_convalidaIyIIYLeCorrespondeIII() {
        NivelIngresoRequest request = new NivelIngresoRequest();
        request.setIdCurso(203L);
        request.setMotivo("Prueba de ubicación del 10/01/2027");

        AvanceInstrumentosResponse respuesta = avanceEstudiante.registrarNivelIngreso(ID_ESTUDIANTE, PIANO, request);

        assertEquals(2, reconocidos.size());
        assertTrue(reconocidos.stream().allMatch(r ->
                "CONVALIDADO".equals(r.getCondicion()) && "NIVEL_INGRESO".equals(r.getOrigen())
                        && r.getIdInstrumento() == PIANO));
        assertEquals(List.of(201L, 202L), reconocidos.stream().map(CursoReconocido::getIdCurso).toList());

        assertEquals("Instrumento III", avance.siguienteNivel(ID_ESTUDIANTE, PIANO, ID_PLAN).orElseThrow().getNombre());
        // Guitarra no se ve afectada.
        assertEquals("Instrumento I", avance.siguienteNivel(ID_ESTUDIANTE, GUITARRA, ID_PLAN).orElseThrow().getNombre());

        AvanceInstrumentosResponse.Instrumento pianoAvance = respuesta.instrumentos().get(0);
        assertTrue(pianoAvance.delPlan());
        assertEquals(14, pianoAvance.niveles().size());
        assertEquals("CONVALIDADO", pianoAvance.niveles().get(0).estado());
        assertEquals("CONVALIDADO", pianoAvance.niveles().get(1).estado());
        assertEquals("SIGUIENTE", pianoAvance.niveles().get(2).estado());
        assertEquals("PENDIENTE", pianoAvance.niveles().get(3).estado());
    }

    @Test
    void nivelDeIngreso_conNivelesAprobadosEnSigac_seRechaza() {
        nivelesAprobadosSigac.add(new Object[]{201L, PIANO, new BigDecimal("90"), 3L});

        NivelIngresoRequest request = new NivelIngresoRequest();
        request.setIdCurso(204L);
        request.setMotivo("Prueba de ubicación");

        assertThrows(BusinessException.class,
                () -> avanceEstudiante.registrarNivelIngreso(ID_ESTUDIANTE, PIANO, request));
        assertTrue(reconocidos.isEmpty());
    }

    @Test
    void cursosReconocidos_cuentanParaLosRequisitos() {
        CursoReconocido solfeo = new CursoReconocido();
        solfeo.setIdEstudiante(ID_ESTUDIANTE);
        solfeo.setIdCurso(301L);
        solfeo.setCondicion("APROBADO");
        solfeo.setOrigen("CERTIFICACION");
        solfeo.setFecha(LocalDate.of(2025, 12, 1));
        reconocidos.add(solfeo);

        Map<Long, AvanceInstrumentoService.CursoAprobado> aprobados = avance.cursosAprobados(ID_ESTUDIANTE);

        assertTrue(aprobados.containsKey(301L));
        // Sin nota, pero reconocido: cumple aunque el plan exija nota mínima.
        assertTrue(aprobados.get(301L).cumple(new BigDecimal("70")));
        assertTrue(avance.cursosAprobadosParaInstrumento(ID_ESTUDIANTE, PIANO).containsKey(301L));
    }

    private static Curso curso(Long id, String nombre, boolean instrumento) {
        Curso curso = new Curso();
        curso.setId(id);
        curso.setCodigo("C-" + id);
        curso.setNombre(nombre);
        curso.setActivo(true);
        curso.setEsCursoInstrumento(instrumento);
        return curso;
    }

    private static MallaCurricular malla(Long idCurso, int nivel, int orden) {
        MallaCurricular malla = new MallaCurricular();
        malla.setIdPlanEstudio(ID_PLAN);
        malla.setIdCurso(idCurso);
        malla.setNivel((short) nivel);
        malla.setOrdenEnNivel((short) orden);
        malla.setActivo(true);
        return malla;
    }

    private static EstudiantesInstrumento asignacion(Long id, int idInstrumento, String nombre, boolean delPlan) {
        Instrumento instrumento = new Instrumento();
        instrumento.setId(idInstrumento);
        instrumento.setNombre(nombre);

        Estudiante estudiante = new Estudiante();
        estudiante.setId(ID_ESTUDIANTE);

        EstudiantesInstrumento asignacion = new EstudiantesInstrumento();
        asignacion.setId(id);
        asignacion.setEstudiante(estudiante);
        asignacion.setInstrumento(instrumento);
        asignacion.setFechaInicio(LocalDate.of(2026, 1, 10));
        asignacion.setEsPrincipal(delPlan);
        return asignacion;
    }
}
