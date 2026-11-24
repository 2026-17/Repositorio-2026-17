package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.MatriculaInvalidaException;
import cr.acusab.una.sigac.logic.service.AgrupacionService;
import cr.acusab.una.sigac.logic.service.AvanceAgrupacionService;
import cr.acusab.una.sigac.logic.service.AvanceInstrumentoService;
import cr.acusab.una.sigac.logic.service.MatriculaEstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.ConfirmarSeleccionMatriculaRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.OfertaMatriculaResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Matrícula con lecciones de instrumento (espacios).
 *
 * Estudiante 1 (período 5, plan 10) lleva Piano (5, profesora 12, del
 * plan) y Guitarra (6, profesor 13, extra). Malla: Instrumento I (201),
 * Instrumento II (202) y Solfeo I (301).
 *
 * Espacios del período:
 *   1001 Piano, profesora 12, lunes 7:10-7:50
 *   1002 Piano, profesor 99 (no es el suyo), martes 8:00-8:40
 *   1003 Guitarra, profesor 13, lunes 7:30-8:10 (choca con 1001)
 *   1004 Guitarra, profesor 13, martes 13:00-13:40
 * Grupo normal 2001: Solfeo I, miércoles 17:00-18:00.
 */
class MatriculaLeccionesInstrumentoTest {

    private static final long ID_ESTUDIANTE = 1L;
    private static final long ID_PERIODO = 5L;
    private static final long ID_PLAN = 10L;
    private static final int PIANO = 5;
    private static final int GUITARRA = 6;

    private MatriculaDetalleRepository detalleRepository;
    private CursoReconocidoRepository reconocidoRepository;
    private MatriculaEstudianteService service;

    private final List<Grupo> grupos = new ArrayList<>();
    private final List<HorarioGrupo> horarios = new ArrayList<>();
    private final List<Object[]> ocupados = new ArrayList<>();
    private final List<RequisitosCurso> requisitos = new ArrayList<>();
    private final List<CursoReconocido> reconocidos = new ArrayList<>();
    private final List<Curso> cursos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        PeriodoAcademicoRepository periodoRepository = mock(PeriodoAcademicoRepository.class);
        AutorizacionesMatriculaRepository autorizacionRepository = mock(AutorizacionesMatriculaRepository.class);
        EstudiantePlanEstudioRepository estudiantePlanRepository = mock(EstudiantePlanEstudioRepository.class);
        PlanEstudioRepository planRepository = mock(PlanEstudioRepository.class);
        MallaCurricularRepository mallaRepository = mock(MallaCurricularRepository.class);
        CursoRepository cursoRepository = mock(CursoRepository.class);
        GrupoRepository grupoRepository = mock(GrupoRepository.class);
        HorarioGrupoRepository horarioRepository = mock(HorarioGrupoRepository.class);
        RequisitosCursoRepository requisitosRepository = mock(RequisitosCursoRepository.class);
        MatriculaRepository matriculaRepository = mock(MatriculaRepository.class);
        EstadoMatriculaRepository estadoMatriculaRepository = mock(EstadoMatriculaRepository.class);
        EstudianteInstrumentoRepository estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);
        InstrumentoRepository instrumentoRepository = mock(InstrumentoRepository.class);
        detalleRepository = mock(MatriculaDetalleRepository.class);
        reconocidoRepository = mock(CursoReconocidoRepository.class);

        service = new MatriculaEstudianteService(
                estudianteRepository, periodoRepository, autorizacionRepository, estudiantePlanRepository,
                planRepository, mallaRepository, cursoRepository, grupoRepository, horarioRepository,
                mock(ProfesorRepository.class), requisitosRepository, matriculaRepository, detalleRepository,
                estadoMatriculaRepository, mock(JdbcTemplate.class),
                new AvanceInstrumentoService(detalleRepository, reconocidoRepository, mallaRepository, cursoRepository),
                estudianteInstrumentoRepository, instrumentoRepository,
                mock(AvanceAgrupacionService.class), mock(AgrupacionService.class)
        );

        // Sesión del estudiante.
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("111");
        SecurityContext contexto = mock(SecurityContext.class);
        when(contexto.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(contexto);

        Estudiante estudiante = new Estudiante();
        estudiante.setId(ID_ESTUDIANTE);
        estudiante.setActivo(true);
        estudiante.setNombres("Ana");
        estudiante.setPrimerApellido("Solís");
        when(estudianteRepository.findByUsuario_Cedula("111")).thenReturn(Optional.of(estudiante));

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(ID_PERIODO);
        periodo.setNombre("I ciclo 2027");
        periodo.setEstado("MATRICULA");
        when(periodoRepository.findByEstadoInOrderByFechaInicioDesc(anyCollection())).thenReturn(List.of(periodo));

        EstadosAutorizacionesMatricula autorizada = new EstadosAutorizacionesMatricula();
        autorizada.setCodigo("AUTORIZADA");
        AutorizacionesMatricula autorizacion = new AutorizacionesMatricula();
        autorizacion.setId(50L);
        autorizacion.setIdEstudiante(estudiante);
        autorizacion.setIdPeriodo(periodo);
        autorizacion.setIdEstadoAutorizacion(autorizada);
        when(autorizacionRepository.findByIdEstudiante_IdAndIdPeriodo_Id(ID_ESTUDIANTE, ID_PERIODO))
                .thenReturn(Optional.of(autorizacion));

        EstudiantePlanEstudio inscripcion = new EstudiantePlanEstudio();
        inscripcion.setIdEstudiante(ID_ESTUDIANTE);
        inscripcion.setIdPlanEstudio(ID_PLAN);
        when(estudiantePlanRepository.findByIdEstudianteAndActivoTrue(ID_ESTUDIANTE)).thenReturn(List.of(inscripcion));

        PlanEstudio plan = new PlanEstudio();
        plan.setId(ID_PLAN);
        plan.setNombre("Plan 2027");
        when(planRepository.findAllById(anyCollection())).thenReturn(List.of(plan));

        // Malla y cursos.
        cursos.add(curso(201L, "Instrumento I", true));
        cursos.add(curso(202L, "Instrumento II", true));
        cursos.add(curso(301L, "Solfeo I", false));
        List<MallaCurricular> malla = List.of(malla(201L, 1, 1), malla(202L, 2, 1), malla(301L, 1, 2));
        when(mallaRepository.findByIdPlanEstudioInAndActivoTrue(anyCollection())).thenReturn(malla);
        when(mallaRepository.findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc(ID_PLAN))
                .thenReturn(List.of(malla(201L, 1, 1), malla(301L, 1, 2), malla(202L, 2, 1)));
        when(cursoRepository.findAllById(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return cursos.stream().filter(c -> ids.contains(c.getId())).toList();
        });

        // Instrumentos del estudiante.
        Instrumento piano = instrumento(PIANO, "Piano");
        Instrumento guitarra = instrumento(GUITARRA, "Guitarra");
        when(instrumentoRepository.findAllById(anyCollection())).thenReturn(List.of(piano, guitarra));
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(ID_ESTUDIANTE))
                .thenReturn(List.of(
                        asignacion(60L, estudiante, piano, profesor(12L), true),
                        asignacion(61L, estudiante, guitarra, profesor(13L), false)));

        // Espacios y grupo normal.
        grupos.add(espacio(1001L, PIANO, 12L));
        grupos.add(espacio(1002L, PIANO, 99L));
        grupos.add(espacio(1003L, GUITARRA, 13L));
        grupos.add(espacio(1004L, GUITARRA, 13L));
        Grupo solfeo = new Grupo();
        solfeo.setId(2001L);
        solfeo.setCodigo("SOL-I-A");
        solfeo.setIdCurso(301L);
        solfeo.setIdPeriodo(ID_PERIODO);
        solfeo.setCupoMaximo((short) 20);
        solfeo.setEstado("PUBLICADO");
        grupos.add(solfeo);

        horarios.add(horario(1001L, 1, "07:10", "07:50"));
        horarios.add(horario(1002L, 2, "08:00", "08:40"));
        horarios.add(horario(1003L, 1, "07:30", "08:10"));
        horarios.add(horario(1004L, 2, "13:00", "13:40"));
        horarios.add(horario(2001L, 3, "17:00", "18:00"));

        when(grupoRepository.findAllByIdParaActualizar(anyCollection())).thenAnswer(inv -> filtrarGrupos(inv.getArgument(0)));
        when(grupoRepository.findAllById(anyCollection())).thenAnswer(inv -> filtrarGrupos(inv.getArgument(0)));
        when(grupoRepository.findByIdPeriodoAndEstadoIn(eq(ID_PERIODO), anyCollection())).thenAnswer(inv -> new ArrayList<>(grupos));
        when(grupoRepository.findEspacios(eq(ID_PERIODO), anyInt(), anyLong())).thenAnswer(inv -> grupos.stream()
                .filter(Grupo::esEspacioInstrumento)
                .filter(g -> g.getIdInstrumento().equals(inv.getArgument(1)) && g.getIdProfesor().equals(inv.getArgument(2)))
                .toList());
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return horarios.stream().filter(h -> ids.contains(h.getIdGrupo())).toList();
        });
        when(detalleRepository.contarCuposOcupados(anyCollection(), eq("REGISTRADA"))).thenAnswer(inv -> new ArrayList<>(ocupados));
        when(requisitosRepository.findActivosPorPlanesYCursos(anyCollection(), anyCollection())).thenAnswer(inv -> new ArrayList<>(requisitos));
        when(reconocidoRepository.findByIdEstudiante(ID_ESTUDIANTE)).thenAnswer(inv -> new ArrayList<>(reconocidos));
        when(reconocidoRepository.findByIdEstudianteAndIdInstrumento(eq(ID_ESTUDIANTE), anyInt())).thenAnswer(inv ->
                reconocidos.stream().filter(r -> inv.getArgument(1).equals(r.getIdInstrumento())).toList());

        when(matriculaRepository.findByAutorizacion_Id(50L)).thenReturn(Optional.empty());
        when(matriculaRepository.saveAndFlush(any(Matricula.class))).thenAnswer(inv -> {
            Matricula m = inv.getArgument(0);
            m.setId(700L);
            return m;
        });
        EstadoMatricula registrada = new EstadoMatricula();
        registrada.setCodigo("REGISTRADA");
        when(estadoMatriculaRepository.findByCodigoAndActivoTrue("REGISTRADA")).thenReturn(Optional.of(registrada));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void oferta_muestraPorInstrumentoElNivelYSoloLosEspaciosLibresDeSuProfesor() {
        ocupados.add(new Object[]{1003L, 1L});

        OfertaMatriculaResponseDTO oferta = service.consultarMiOferta();

        // Los cursos de instrumento no salen como grupos normales.
        assertTrue(oferta.getOferta().stream().noneMatch(OfertaMatriculaResponseDTO.GrupoDTO::isEspacioInstrumento));
        assertEquals(List.of(2001L), oferta.getOferta().stream().map(OfertaMatriculaResponseDTO.GrupoDTO::getIdGrupo).toList());

        assertEquals(2, oferta.getLeccionesInstrumento().size());
        OfertaMatriculaResponseDTO.LeccionInstrumentoDTO piano = oferta.getLeccionesInstrumento().get(0);
        assertEquals("Piano", piano.getNombreInstrumento());
        assertTrue(piano.isDelPlan());
        assertEquals("Instrumento I", piano.getNombreNivel());
        assertTrue(piano.isRequerida());
        assertEquals(List.of(1001L), piano.getEspaciosLibres().stream().map(OfertaMatriculaResponseDTO.GrupoDTO::getIdGrupo).toList());

        OfertaMatriculaResponseDTO.LeccionInstrumentoDTO guitarra = oferta.getLeccionesInstrumento().get(1);
        assertFalse(guitarra.isDelPlan());
        // 1003 ya está tomado.
        assertEquals(List.of(1004L), guitarra.getEspaciosLibres().stream().map(OfertaMatriculaResponseDTO.GrupoDTO::getIdGrupo).toList());
    }

    @Test
    void confirmar_guardaElNivelCalculadoEnCadaEspacio() {

        confirmar(List.of(2001L), List.of(1001L, 1004L));

        ArgumentCaptor<MatriculaDetalle> detalles = ArgumentCaptor.forClass(MatriculaDetalle.class);
        verify(detalleRepository, times(3)).save(detalles.capture());

        MatriculaDetalle solfeo = detalle(detalles.getAllValues(), 2001L);
        MatriculaDetalle piano = detalle(detalles.getAllValues(), 1001L);
        MatriculaDetalle guitarra = detalle(detalles.getAllValues(), 1004L);

        assertNull(solfeo.getIdCurso());
        assertEquals(201L, piano.getIdCurso());
        assertEquals(201L, guitarra.getIdCurso());
    }

    @Test
    void confirmar_espacioDeOtroProfesor_seRechaza() {
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(), List.of(1002L, 1004L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("no es de su profesor asignado en Piano")));
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void confirmar_espacioYaTomado_seRechaza() {
        ocupados.add(new Object[]{1001L, 1L});

        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(), List.of(1001L, 1004L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("ya lo tomó otro estudiante")));
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void confirmar_choqueEntreLosDosInstrumentos_seRechaza() {
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(), List.of(1001L, 1003L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Choque de horario")
                && e.contains("Piano") && e.contains("Guitarra")));
    }

    @Test
    void confirmar_faltaElEspacioDeUnInstrumento_noConfirma() {
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(2001L), List.of(1001L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Elija el espacio de su lección de Guitarra")));
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void confirmar_cursoReconocidoCumpleElPrerrequisitoDelNivel() {
        // Instrumento II exige Instrumento I; en piano está convalidado por nivel de ingreso.
        requisitos.add(prerrequisito(202L, 201L));
        CursoReconocido convalidado = new CursoReconocido();
        convalidado.setIdEstudiante(ID_ESTUDIANTE);
        convalidado.setIdCurso(201L);
        convalidado.setIdInstrumento(PIANO);
        convalidado.setCondicion("CONVALIDADO");
        convalidado.setOrigen("NIVEL_INGRESO");
        convalidado.setFecha(LocalDate.of(2027, 1, 10));
        reconocidos.add(convalidado);

        confirmar(List.of(), List.of(1001L, 1004L));

        ArgumentCaptor<MatriculaDetalle> detalles = ArgumentCaptor.forClass(MatriculaDetalle.class);
        verify(detalleRepository, times(2)).save(detalles.capture());
        assertEquals(202L, detalle(detalles.getAllValues(), 1001L).getIdCurso());
        assertEquals(201L, detalle(detalles.getAllValues(), 1004L).getIdCurso());
    }

    @Test
    void confirmar_cursoNormalQueExigeUnNivelNoAprobado_seRechaza() {
        // Solfeo I exige haber aprobado Instrumento I, y no lo tiene en ningún instrumento.
        requisitos.add(prerrequisito(301L, 201L));

        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(2001L), List.of(1001L, 1004L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Prerrequisito incumplido en «Solfeo I»")));
    }

    /* ------------------------------------------------------------ */

    private void confirmar(List<Long> idsGrupo, List<Long> idsEspacio) {
        ConfirmarSeleccionMatriculaRequestDTO request = new ConfirmarSeleccionMatriculaRequestDTO();
        request.setIdsGrupo(new ArrayList<>(idsGrupo));
        request.setIdsEspacio(new ArrayList<>(idsEspacio));
        service.confirmar(request);
    }

    private List<Grupo> filtrarGrupos(Collection<?> ids) {
        return grupos.stream().filter(g -> ids.contains(g.getId())).toList();
    }

    private static MatriculaDetalle detalle(List<MatriculaDetalle> detalles, Long idGrupo) {
        return detalles.stream().filter(d -> d.getIdGrupo().equals(idGrupo)).findFirst().orElseThrow();
    }

    private RequisitosCurso prerrequisito(Long idCurso, Long idRequerido) {
        TiposRequisitosCurso tipo = new TiposRequisitosCurso();
        tipo.setCodigo("PRERREQUISITO");
        RequisitosCurso requisito = new RequisitosCurso();
        requisito.setIdCurso(cursos.stream().filter(c -> c.getId().equals(idCurso)).findFirst().orElseThrow());
        requisito.setIdCursoRequerido(cursos.stream().filter(c -> c.getId().equals(idRequerido)).findFirst().orElseThrow());
        requisito.setIdTipoRequisito(tipo);
        requisito.setActivo(true);
        return requisito;
    }

    private static Grupo espacio(Long id, int idInstrumento, Long idProfesor) {
        Grupo g = new Grupo();
        g.setId(id);
        g.setCodigo("ESP-" + id);
        g.setTipoGrupo(Grupo.TIPO_ESPACIO_INSTRUMENTO);
        g.setIdInstrumento(idInstrumento);
        g.setIdProfesor(idProfesor);
        g.setIdPeriodo(ID_PERIODO);
        g.setCupoMaximo((short) 1);
        g.setEstado("PUBLICADO");
        return g;
    }

    private static HorarioGrupo horario(Long idGrupo, int dia, String inicio, String fin) {
        HorarioGrupo h = new HorarioGrupo();
        h.setIdGrupo(idGrupo);
        h.setDiaSemana((short) dia);
        h.setHoraInicio(LocalTime.parse(inicio));
        h.setHoraFin(LocalTime.parse(fin));
        return h;
    }

    private static Curso curso(Long id, String nombre, boolean instrumento) {
        Curso c = new Curso();
        c.setId(id);
        c.setCodigo("C-" + id);
        c.setNombre(nombre);
        c.setActivo(true);
        c.setEsCursoInstrumento(instrumento);
        return c;
    }

    private static MallaCurricular malla(Long idCurso, int nivel, int orden) {
        MallaCurricular m = new MallaCurricular();
        m.setIdPlanEstudio(ID_PLAN);
        m.setIdCurso(idCurso);
        m.setNivel((short) nivel);
        m.setOrdenEnNivel((short) orden);
        m.setActivo(true);
        return m;
    }

    private static Instrumento instrumento(int id, String nombre) {
        Instrumento i = new Instrumento();
        i.setId(id);
        i.setNombre(nombre);
        return i;
    }

    private static Profesor profesor(Long id) {
        Profesor p = new Profesor();
        p.setId(id);
        p.setNombres("Prof");
        p.setPrimerApellido(String.valueOf(id));
        return p;
    }

    private static EstudiantesInstrumento asignacion(
            Long id, Estudiante estudiante, Instrumento instrumento, Profesor profesor, boolean delPlan) {
        EstudiantesInstrumento a = new EstudiantesInstrumento();
        a.setId(id);
        a.setEstudiante(estudiante);
        a.setInstrumento(instrumento);
        a.setProfesor(profesor);
        a.setFechaInicio(LocalDate.of(2026, 1, 10));
        a.setEsPrincipal(delPlan);
        return a;
    }
}
