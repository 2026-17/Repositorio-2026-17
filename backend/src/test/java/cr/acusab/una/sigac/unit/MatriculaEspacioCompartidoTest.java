package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.MatriculaInvalidaException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Matrícula en espacios de "cualquier instrumento del profesor".
 *
 * Profesora 12 da "Violín, Viola". Profesor 13 da "Violín".
 * Espacios del período 5:
 *   1001 sin instrumento, profesora 12, lunes 7:10-7:50
 *   1002 Violín, profesor 13, martes 8:00-8:40
 *
 * Estudiantes (datos ficticios):
 *   1 Ana   - Viola con la profesora 12
 *   2 Luis  - Violín con la profesora 12
 *   3 Marta - Violín con el profesor 13
 *   4 Sofía - Violín y Viola, las dos con la profesora 12
 */
class MatriculaEspacioCompartidoTest {

    private static final long ID_PERIODO = 5L;
    private static final long ID_PLAN = 10L;
    private static final int VIOLIN = 7;
    private static final int VIOLA = 8;

    private MatriculaDetalleRepository detalleRepository;
    private MatriculaEstudianteService service;
    private Authentication authentication;
    private Profesor profesora;

    private final List<Grupo> grupos = new ArrayList<>();
    private final List<HorarioGrupo> horarios = new ArrayList<>();
    private final List<Object[]> ocupados = new ArrayList<>();
    private final Map<Long, List<Object[]>> aprobadosPorEstudiante = new HashMap<>();

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
        MatriculaRepository matriculaRepository = mock(MatriculaRepository.class);
        EstadoMatriculaRepository estadoMatriculaRepository = mock(EstadoMatriculaRepository.class);
        EstudianteInstrumentoRepository estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);
        InstrumentoRepository instrumentoRepository = mock(InstrumentoRepository.class);
        CursoReconocidoRepository reconocidoRepository = mock(CursoReconocidoRepository.class);
        detalleRepository = mock(MatriculaDetalleRepository.class);

        service = new MatriculaEstudianteService(
                estudianteRepository, periodoRepository, autorizacionRepository, estudiantePlanRepository,
                planRepository, mallaRepository, cursoRepository, grupoRepository, horarioRepository,
                mock(ProfesorRepository.class), mock(RequisitosCursoRepository.class), matriculaRepository,
                detalleRepository, estadoMatriculaRepository, mock(JdbcTemplate.class),
                new AvanceInstrumentoService(detalleRepository, reconocidoRepository, mallaRepository, cursoRepository),
                estudianteInstrumentoRepository, instrumentoRepository
        );

        authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityContext contexto = mock(SecurityContext.class);
        when(contexto.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(contexto);

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(ID_PERIODO);
        periodo.setNombre("I ciclo 2027");
        periodo.setEstado("MATRICULA");
        when(periodoRepository.findByEstadoInOrderByFechaInicioDesc(anyCollection())).thenReturn(List.of(periodo));
        when(periodoRepository.findById(anyLong())).thenReturn(Optional.of(periodo));

        PlanEstudio plan = new PlanEstudio();
        plan.setId(ID_PLAN);
        plan.setNombre("Plan 2027");
        when(planRepository.findAllById(anyCollection())).thenReturn(List.of(plan));

        EstudiantePlanEstudio inscripcion = new EstudiantePlanEstudio();
        inscripcion.setIdPlanEstudio(ID_PLAN);
        when(estudiantePlanRepository.findByIdEstudianteAndActivoTrue(anyLong())).thenReturn(List.of(inscripcion));

        // Malla: Instrumento I (201) e Instrumento II (202).
        List<Curso> cursos = List.of(curso(201L, "Instrumento I"), curso(202L, "Instrumento II"));
        List<MallaCurricular> malla = List.of(malla(201L, 1), malla(202L, 2));
        when(mallaRepository.findByIdPlanEstudioInAndActivoTrue(anyCollection())).thenReturn(malla);
        when(mallaRepository.findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc(ID_PLAN)).thenReturn(malla);
        when(cursoRepository.findAllById(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return cursos.stream().filter(c -> ids.contains(c.getId())).toList();
        });

        Instrumento violin = instrumento(VIOLIN, "Violín");
        Instrumento viola = instrumento(VIOLA, "Viola");
        when(instrumentoRepository.findAllById(anyCollection())).thenReturn(List.of(violin, viola));

        profesora = profesor(12L, "Violín, Viola");
        Profesor profesor = profesor(13L, "Violín");

        // Estudiantes, cada uno con su autorización y sus instrumentos.
        Map<Long, List<EstudiantesInstrumento>> asignaciones = new HashMap<>();
        registrar(1L, "Ana", estudianteRepository, autorizacionRepository, asignaciones, periodo,
                new Object[][]{{viola, profesora}});
        registrar(2L, "Luis", estudianteRepository, autorizacionRepository, asignaciones, periodo,
                new Object[][]{{violin, profesora}});
        registrar(3L, "Marta", estudianteRepository, autorizacionRepository, asignaciones, periodo,
                new Object[][]{{violin, profesor}});
        registrar(4L, "Sofía", estudianteRepository, autorizacionRepository, asignaciones, periodo,
                new Object[][]{{violin, profesora}, {viola, profesora}});
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(anyLong()))
                .thenAnswer(inv -> asignaciones.getOrDefault((Long) inv.getArgument(0), List.of()));

        grupos.add(espacio(1001L, null, 12L));
        grupos.add(espacio(1002L, VIOLIN, 13L));
        horarios.add(horario(1001L, 1, "07:10", "07:50"));
        horarios.add(horario(1002L, 2, "08:00", "08:40"));

        when(grupoRepository.findAllByIdParaActualizar(anyCollection())).thenAnswer(inv -> filtrar(inv.getArgument(0)));
        when(grupoRepository.findAllById(anyCollection())).thenAnswer(inv -> filtrar(inv.getArgument(0)));
        // Igual que la consulta: del profesor, y del instrumento o sin instrumento.
        when(grupoRepository.findEspacios(eq(ID_PERIODO), anyInt(), anyLong())).thenAnswer(inv -> grupos.stream()
                .filter(g -> g.getIdProfesor().equals(inv.getArgument(2)))
                .filter(g -> g.getIdInstrumento() == null || g.getIdInstrumento().equals(inv.getArgument(1)))
                .toList());
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return horarios.stream().filter(h -> ids.contains(h.getIdGrupo())).toList();
        });
        when(detalleRepository.contarCuposOcupados(anyCollection(), eq("REGISTRADA"))).thenAnswer(inv -> new ArrayList<>(ocupados));
        when(detalleRepository.findNivelesInstrumentoAprobados(anyLong()))
                .thenAnswer(inv -> aprobadosPorEstudiante.getOrDefault((Long) inv.getArgument(0), List.of()));

        when(matriculaRepository.findByAutorizacion_Id(anyLong())).thenReturn(Optional.empty());
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
    void oferta_estudiantesDeViolaYDeViolinConLaMismaProfesora_venElMismoEspacio() {

        comoEstudiante(1L);
        OfertaMatriculaResponseDTO.LeccionInstrumentoDTO viola = leccion(service.consultarMiOferta(), "Viola");
        assertEquals(List.of(1001L), idsLibres(viola));
        assertEquals(VIOLA, viola.getEspaciosLibres().get(0).getIdInstrumento());
        assertEquals("Instrumento I · Viola", viola.getEspaciosLibres().get(0).getNombreCurso());

        comoEstudiante(2L);
        OfertaMatriculaResponseDTO.LeccionInstrumentoDTO violin = leccion(service.consultarMiOferta(), "Violín");
        assertEquals(List.of(1001L), idsLibres(violin));
        assertEquals(VIOLIN, violin.getEspaciosLibres().get(0).getIdInstrumento());
    }

    @Test
    void oferta_estudianteConOtroProfesor_noVeElEspacioCompartido() {

        comoEstudiante(3L);
        OfertaMatriculaResponseDTO.LeccionInstrumentoDTO violin = leccion(service.consultarMiOferta(), "Violín");

        assertEquals(List.of(1002L), idsLibres(violin));
    }

    @Test
    void oferta_siLaProfesoraYaNoDaElInstrumento_noLeOfreceElEspacioCompartido() {
        // La asignación sigue con la profesora 12, pero su especialidad ya no incluye Viola.
        profesora.setEspecialidad("Violín");

        comoEstudiante(1L);
        OfertaMatriculaResponseDTO.LeccionInstrumentoDTO viola = leccion(service.consultarMiOferta(), "Viola");

        assertTrue(idsLibres(viola).isEmpty());
    }

    @Test
    void confirmar_espacioCompartido_guardaElInstrumentoYElNivelEnElDetalle() {

        comoEstudiante(1L);
        confirmar(List.of(1001L), List.of());

        ArgumentCaptor<MatriculaDetalle> detalle = ArgumentCaptor.forClass(MatriculaDetalle.class);
        verify(detalleRepository).save(detalle.capture());
        assertEquals(1001L, detalle.getValue().getIdGrupo());
        assertEquals(VIOLA, detalle.getValue().getIdInstrumento());
        assertEquals(201L, detalle.getValue().getIdCurso());
    }

    @Test
    void confirmar_espacioDeUnInstrumento_tambienGuardaElInstrumento() {

        comoEstudiante(3L);
        confirmar(List.of(1002L), List.of());

        ArgumentCaptor<MatriculaDetalle> detalle = ArgumentCaptor.forClass(MatriculaDetalle.class);
        verify(detalleRepository).save(detalle.capture());
        assertEquals(VIOLIN, detalle.getValue().getIdInstrumento());
    }

    @Test
    void cupoUno_siUnoLoToma_elOtroYaNoLoVeNiLoPuedeConfirmar() {

        comoEstudiante(1L);
        confirmar(List.of(1001L), List.of());
        ocupados.add(new Object[]{1001L, 1L});

        comoEstudiante(2L);
        OfertaMatriculaResponseDTO.LeccionInstrumentoDTO violin = leccion(service.consultarMiOferta(), "Violín");
        assertTrue(idsLibres(violin).isEmpty());

        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(1001L), List.of()));
        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("ya lo tomó otro estudiante")));
    }

    @Test
    void confirmar_estudianteConOtroProfesor_noPuedeTomarElEspacioCompartido() {

        comoEstudiante(3L);
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(1001L, 1002L), List.of()));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("no es de su profesor asignado")));
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void confirmar_dosLeccionesConLaMismaProfesora_sinIndicarLaLeccion_pideIndicarla() {

        comoEstudiante(4L);
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(1001L), List.of()));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Indique para cuál lección")
                && e.contains("Violín o Viola")));
    }

    @Test
    void confirmar_dosLeccionesConLaMismaProfesora_indicandoLaLeccion_usaEsaYPideLaOtra() {

        comoEstudiante(4L);
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(), List.<Object[]>of(new Object[]{1001L, VIOLA})));

        // El espacio quedó para Viola; falta el de Violín.
        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Elija el espacio de su lección de Violín")));
        assertTrue(ex.getErrores().stream().noneMatch(e -> e.contains("Indique para cuál")));
    }

    @Test
    void confirmar_unSoloEspacioPorInstrumentoPorPeriodo() {
        grupos.add(espacio(1003L, null, 12L));
        horarios.add(horario(1003L, 3, "09:00", "09:40"));

        comoEstudiante(1L);
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(1001L, 1003L), List.of()));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Solo puede tener un espacio de Viola por período")));
    }

    @Test
    void confirmar_instrumentoIndicadoNoCoincideConElEspacioDeUnInstrumento_seRechaza() {

        comoEstudiante(3L);
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(List.of(), List.<Object[]>of(new Object[]{1002L, VIOLA})));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("no es del instrumento indicado")));
    }

    @Test
    void nivel_seCalculaConElInstrumentoDelDetalle() {
        // Sofía aprobó Instrumento I en Viola en un espacio compartido: la
        // consulta devuelve el instrumento del detalle (Viola), no el del grupo.
        aprobadosPorEstudiante.put(4L, List.<Object[]>of(new Object[]{201L, VIOLA, new BigDecimal("90"), 4L}));

        comoEstudiante(4L);
        OfertaMatriculaResponseDTO oferta = service.consultarMiOferta();

        assertEquals("Instrumento II", leccion(oferta, "Viola").getNombreNivel());
        // En Violín no cuenta: sigue en Instrumento I.
        assertEquals("Instrumento I", leccion(oferta, "Violín").getNombreNivel());
    }

    /* ------------------------------------------------------------ */

    private void comoEstudiante(long id) {
        when(authentication.getName()).thenReturn("cedula-" + id);
    }

    // espacios: pares {idEspacio, idInstrumento}
    private void confirmar(List<Long> idsEspacio, List<Object[]> espacios) {
        ConfirmarSeleccionMatriculaRequestDTO request = new ConfirmarSeleccionMatriculaRequestDTO();
        request.setIdsEspacio(new ArrayList<>(idsEspacio));
        for (Object[] par : espacios) {
            ConfirmarSeleccionMatriculaRequestDTO.EspacioElegido elegido = new ConfirmarSeleccionMatriculaRequestDTO.EspacioElegido();
            elegido.setIdEspacio((Long) par[0]);
            elegido.setIdInstrumento((Integer) par[1]);
            request.getEspacios().add(elegido);
        }
        service.confirmar(request);
    }

    private static OfertaMatriculaResponseDTO.LeccionInstrumentoDTO leccion(OfertaMatriculaResponseDTO oferta, String instrumento) {
        return oferta.getLeccionesInstrumento().stream()
                .filter(l -> instrumento.equals(l.getNombreInstrumento()))
                .findFirst()
                .orElseThrow();
    }

    private static List<Long> idsLibres(OfertaMatriculaResponseDTO.LeccionInstrumentoDTO leccion) {
        return leccion.getEspaciosLibres().stream().map(OfertaMatriculaResponseDTO.GrupoDTO::getIdGrupo).toList();
    }

    private List<Grupo> filtrar(Collection<?> ids) {
        return grupos.stream().filter(g -> ids.contains(g.getId())).toList();
    }

    private static void registrar(
            long id, String nombre,
            EstudianteRepository estudianteRepository,
            AutorizacionesMatriculaRepository autorizacionRepository,
            Map<Long, List<EstudiantesInstrumento>> asignaciones,
            PeriodoAcademico periodo,
            Object[][] instrumentoYProfesor) {

        Estudiante estudiante = new Estudiante();
        estudiante.setId(id);
        estudiante.setActivo(true);
        estudiante.setNombres(nombre);
        estudiante.setPrimerApellido("Prueba");
        when(estudianteRepository.findByUsuario_Cedula("cedula-" + id)).thenReturn(Optional.of(estudiante));

        EstadosAutorizacionesMatricula autorizada = new EstadosAutorizacionesMatricula();
        autorizada.setCodigo("AUTORIZADA");
        AutorizacionesMatricula autorizacion = new AutorizacionesMatricula();
        autorizacion.setId(50L + id);
        autorizacion.setIdEstudiante(estudiante);
        autorizacion.setIdPeriodo(periodo);
        autorizacion.setIdEstadoAutorizacion(autorizada);
        when(autorizacionRepository.findByIdEstudiante_IdAndIdPeriodo_Id(id, ID_PERIODO)).thenReturn(Optional.of(autorizacion));

        List<EstudiantesInstrumento> lista = new ArrayList<>();
        for (int i = 0; i < instrumentoYProfesor.length; i++) {
            EstudiantesInstrumento a = new EstudiantesInstrumento();
            a.setId(id * 10 + i);
            a.setEstudiante(estudiante);
            a.setInstrumento((Instrumento) instrumentoYProfesor[i][0]);
            a.setProfesor((Profesor) instrumentoYProfesor[i][1]);
            a.setFechaInicio(LocalDate.of(2026, 1, 10).plusDays(i));
            a.setEsPrincipal(i == 0);
            lista.add(a);
        }
        asignaciones.put(id, lista);
    }

    private static Grupo espacio(Long id, Integer idInstrumento, Long idProfesor) {
        Grupo g = new Grupo();
        g.setId(id);
        g.setCodigo(idInstrumento == null ? "ESP-VAR-" + idProfesor + "-" + id : "ESP-VIO-" + idProfesor + "-" + id);
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

    private static Curso curso(Long id, String nombre) {
        Curso c = new Curso();
        c.setId(id);
        c.setCodigo("C-" + id);
        c.setNombre(nombre);
        c.setActivo(true);
        c.setEsCursoInstrumento(true);
        return c;
    }

    private static MallaCurricular malla(Long idCurso, int nivel) {
        MallaCurricular m = new MallaCurricular();
        m.setIdPlanEstudio(ID_PLAN);
        m.setIdCurso(idCurso);
        m.setNivel((short) nivel);
        m.setOrdenEnNivel((short) 1);
        m.setActivo(true);
        return m;
    }

    private static Instrumento instrumento(int id, String nombre) {
        Instrumento i = new Instrumento();
        i.setId(id);
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
