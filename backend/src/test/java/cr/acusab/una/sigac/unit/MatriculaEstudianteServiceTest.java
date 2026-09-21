package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.AutorizacionesMatricula;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.EstadoMatricula;
import cr.acusab.una.sigac.data.entity.EstadosAutorizacionesMatricula;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantePlanEstudio;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.Matricula;
import cr.acusab.una.sigac.data.entity.MatriculaDetalle;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.RequisitosCurso;
import cr.acusab.una.sigac.data.entity.TiposRequisitosCurso;

import cr.acusab.una.sigac.data.repository.AutorizacionesMatriculaRepository;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.EstadoMatriculaRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstudiantePlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.MatriculaRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.RequisitosCursoRepository;

import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.MatriculaInvalidaException;
import cr.acusab.una.sigac.logic.service.MatriculaEstudianteService;

import cr.acusab.una.sigac.presentation.dto.request.ConfirmarSeleccionMatriculaRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.MatriculaConfirmadaResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.OfertaMatriculaResponseDTO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Historia 2 - Matricular cursos previamente autorizados.
 *
 * Escenario base: estudiante activo (id 1) autorizado para el período 5 e
 * inscrito en el plan 10, cuya malla incluye los cursos 101 (nivel 1) y
 * 102 (nivel 2). El curso 999 no pertenece a ese plan.
 */
class MatriculaEstudianteServiceTest {

    private static final long ID_ESTUDIANTE = 1L;
    private static final long ID_PERIODO = 5L;
    private static final long ID_PLAN = 10L;

    private EstudianteRepository estudianteRepository;
    private PeriodoAcademicoRepository periodoRepository;
    private AutorizacionesMatriculaRepository autorizacionRepository;
    private EstudiantePlanEstudioRepository estudiantePlanRepository;
    private PlanEstudioRepository planRepository;
    private MallaCurricularRepository mallaRepository;
    private CursoRepository cursoRepository;
    private GrupoRepository grupoRepository;
    private HorarioGrupoRepository horarioRepository;
    private ProfesorRepository profesorRepository;
    private RequisitosCursoRepository requisitosRepository;
    private MatriculaRepository matriculaRepository;
    private MatriculaDetalleRepository detalleRepository;
    private EstadoMatriculaRepository estadoMatriculaRepository;

    private MatriculaEstudianteService service;

    private Estudiante estudiante;
    private AutorizacionesMatricula autorizacion;

    private final List<Grupo> grupos = new ArrayList<>();
    private final List<RequisitosCurso> requisitos = new ArrayList<>();
    private final List<HorarioGrupo> horarios = new ArrayList<>();
    private final List<Object[]> aprobados = new ArrayList<>();
    private final List<Object[]> ocupados = new ArrayList<>();

    @BeforeEach
    void setUp() {

        estudianteRepository = mock(EstudianteRepository.class);
        periodoRepository = mock(PeriodoAcademicoRepository.class);
        autorizacionRepository = mock(AutorizacionesMatriculaRepository.class);
        estudiantePlanRepository = mock(EstudiantePlanEstudioRepository.class);
        planRepository = mock(PlanEstudioRepository.class);
        mallaRepository = mock(MallaCurricularRepository.class);
        cursoRepository = mock(CursoRepository.class);
        grupoRepository = mock(GrupoRepository.class);
        horarioRepository = mock(HorarioGrupoRepository.class);
        profesorRepository = mock(ProfesorRepository.class);
        requisitosRepository = mock(RequisitosCursoRepository.class);
        matriculaRepository = mock(MatriculaRepository.class);
        detalleRepository = mock(MatriculaDetalleRepository.class);
        estadoMatriculaRepository = mock(EstadoMatriculaRepository.class);

        service = new MatriculaEstudianteService(
                estudianteRepository,
                periodoRepository,
                autorizacionRepository,
                estudiantePlanRepository,
                planRepository,
                mallaRepository,
                cursoRepository,
                grupoRepository,
                horarioRepository,
                profesorRepository,
                requisitosRepository,
                matriculaRepository,
                detalleRepository,
                estadoMatriculaRepository
        );

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("111111111");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        estudiante = new Estudiante();
        estudiante.setId(ID_ESTUDIANTE);
        estudiante.setActivo(true);
        estudiante.setNombres("Ana");
        estudiante.setPrimerApellido("Prueba");
        when(estudianteRepository.findByUsuario_Cedula("111111111"))
                .thenReturn(Optional.of(estudiante));

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(ID_PERIODO);
        periodo.setCodigo("2026-II");
        periodo.setNombre("II Ciclo 2026");
        when(periodoRepository.findByEstadoInOrderByFechaInicioDesc(anyCollection()))
                .thenReturn(List.of(periodo));

        autorizacion = new AutorizacionesMatricula();
        autorizacion.setId(50L);
        autorizacion.setIdEstudiante(estudiante);
        autorizacion.setIdPeriodo(periodo);
        autorizacion.setIdEstadoAutorizacion(estadoAutorizacion("AUTORIZADA"));
        when(autorizacionRepository.findByIdEstudiante_IdAndIdPeriodo_Id(ID_ESTUDIANTE, ID_PERIODO))
                .thenReturn(Optional.of(autorizacion));

        EstudiantePlanEstudio inscripcion = new EstudiantePlanEstudio();
        inscripcion.setIdEstudiante(ID_ESTUDIANTE);
        inscripcion.setIdPlanEstudio(ID_PLAN);
        when(estudiantePlanRepository.findByIdEstudianteAndActivoTrue(ID_ESTUDIANTE))
                .thenReturn(List.of(inscripcion));

        when(planRepository.findAllById(anyCollection()))
                .thenReturn(List.of(plan()));

        when(mallaRepository.findByIdPlanEstudioInAndActivoTrue(anyCollection()))
                .thenReturn(List.of(malla(101L, 1), malla(102L, 2)));

        when(cursoRepository.findAllById(anyCollection()))
                .thenAnswer(inv -> List.of(
                        curso(101L, "Guitarra I"),
                        curso(102L, "Guitarra II"),
                        curso(999L, "Piano I")
                ));

        when(grupoRepository.findAllByIdParaActualizar(anyCollection()))
                .thenAnswer(inv -> grupos.stream()
                        .filter(g -> ((java.util.Collection<?>) inv.getArgument(0)).contains(g.getId()))
                        .toList());
        when(grupoRepository.findByIdPeriodoAndEstadoIn(eq(ID_PERIODO), anyCollection()))
                .thenAnswer(inv -> new ArrayList<>(grupos));
        when(grupoRepository.findAllById(anyCollection()))
                .thenAnswer(inv -> grupos.stream()
                        .filter(g -> ((java.util.Collection<?>) inv.getArgument(0)).contains(g.getId()))
                        .toList());

        when(matriculaRepository.findByAutorizacion_Id(50L)).thenReturn(Optional.empty());
        when(matriculaRepository.saveAndFlush(any(Matricula.class)))
                .thenAnswer(inv -> {
                    Matricula m = inv.getArgument(0);
                    m.setId(700L);
                    return m;
                });

        when(estadoMatriculaRepository.findByCodigoAndActivoTrue("REGISTRADA"))
                .thenReturn(Optional.of(estadoMatricula("REGISTRADA")));

        when(requisitosRepository.findActivosPorPlanesYCursos(anyCollection(), anyCollection()))
                .thenAnswer(inv -> new ArrayList<>(requisitos));
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection()))
                .thenAnswer(inv -> new ArrayList<>(horarios));
        when(detalleRepository.findCursosAprobados(ID_ESTUDIANTE))
                .thenAnswer(inv -> new ArrayList<>(aprobados));
        when(detalleRepository.contarCuposOcupados(anyCollection(), eq("REGISTRADA")))
                .thenAnswer(inv -> new ArrayList<>(ocupados));
        when(detalleRepository.findByMatricula_IdAndRetiradoFalse(anyLong()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * Consultar oferta
     * ============================================================
     */

    @Test
    void consultarMiOferta_sinAutorizacion_noHabilita() {

        when(autorizacionRepository.findByIdEstudiante_IdAndIdPeriodo_Id(ID_ESTUDIANTE, ID_PERIODO))
                .thenReturn(Optional.empty());

        OfertaMatriculaResponseDTO oferta = service.consultarMiOferta();

        assertFalse(oferta.isHabilitado());
        assertTrue(oferta.getMotivoNoHabilitado().contains("revisión previa del administrador"));
        assertTrue(oferta.getOferta().isEmpty());
    }

    @Test
    void consultarMiOferta_autorizacionPendiente_noHabilita() {

        autorizacion.setIdEstadoAutorizacion(estadoAutorizacion("PENDIENTE"));

        OfertaMatriculaResponseDTO oferta = service.consultarMiOferta();

        assertFalse(oferta.isHabilitado());
        assertTrue(oferta.getMotivoNoHabilitado().contains("pendiente"));
    }

    @Test
    void consultarMiOferta_estudianteInactivo_noHabilita() {

        estudiante.setActivo(false);

        OfertaMatriculaResponseDTO oferta = service.consultarMiOferta();

        assertFalse(oferta.isHabilitado());
        assertTrue(oferta.getMotivoNoHabilitado().contains("Activo"));
    }

    @Test
    void consultarMiOferta_sinPlanInscrito_noHabilita() {

        when(estudiantePlanRepository.findByIdEstudianteAndActivoTrue(ID_ESTUDIANTE))
                .thenReturn(List.of());

        OfertaMatriculaResponseDTO oferta = service.consultarMiOferta();

        assertFalse(oferta.isHabilitado());
        assertTrue(oferta.getMotivoNoHabilitado().contains("plan de estudio"));
    }

    @Test
    void consultarMiOferta_soloOfreceCursosDeSusPlanes() {

        grupos.add(grupo(1L, 101L, 10));
        grupos.add(grupo(2L, 999L, 10));

        OfertaMatriculaResponseDTO oferta = service.consultarMiOferta();

        assertTrue(oferta.isHabilitado());
        assertEquals(1, oferta.getOferta().size());
        assertEquals(101L, oferta.getOferta().get(0).getIdCurso());
        assertEquals(10, oferta.getOferta().get(0).getCupoDisponible());
        assertNull(oferta.getOferta().get(0).getProfesor());
    }

    /*
     * ============================================================
     * Confirmar - casos exitosos
     * ============================================================
     */

    @Test
    void confirmar_registraMatriculaYDescuentaCupo() {

        grupos.add(grupo(1L, 101L, 10));

        MatriculaConfirmadaResponseDTO respuesta = service.confirmar(solicitud(1L));

        assertEquals(700L, respuesta.getIdMatricula());
        assertEquals("REGISTRADA", respuesta.getEstado());
        assertEquals(1, respuesta.getCursos().size());

        verify(matriculaRepository).saveAndFlush(argThat(m ->
                m.getIdPlanEstudio().equals(ID_PLAN)
                        && "REGISTRADA".equals(m.getEstado().getCodigo())));
        verify(detalleRepository).save(argThat((MatriculaDetalle d) -> d.getIdGrupo().equals(1L)));
    }

    @Test
    void confirmar_conMatriculaExistente_agregaGruposSinCrearOtra() {

        grupos.add(grupo(1L, 101L, 10));

        Matricula existente = new Matricula();
        existente.setId(700L);
        existente.setEstado(estadoMatricula("REGISTRADA"));
        when(matriculaRepository.findByAutorizacion_Id(50L)).thenReturn(Optional.of(existente));

        service.confirmar(solicitud(1L));

        verify(matriculaRepository, never()).saveAndFlush(any());
        verify(detalleRepository).save(argThat((MatriculaDetalle d) -> d.getMatricula() == existente));
    }

    /*
     * ============================================================
     * Confirmar - rechazos
     * ============================================================
     */

    @Test
    void confirmar_sinSeleccion_seRechaza() {

        ConfirmarSeleccionMatriculaRequestDTO vacia = new ConfirmarSeleccionMatriculaRequestDTO();
        vacia.setIdsGrupo(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirmar(vacia));

        assertTrue(ex.getMessage().contains("al menos un curso"));
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void confirmar_sinAutorizacion_seRechaza() {

        grupos.add(grupo(1L, 101L, 10));
        when(autorizacionRepository.findByIdEstudiante_IdAndIdPeriodo_Id(ID_ESTUDIANTE, ID_PERIODO))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirmar(solicitud(1L)));

        assertTrue(ex.getMessage().contains("administrador"));
        verify(matriculaRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmar_cursoDeUnPlanEnElQueNoEstaInscrito_seRechaza() {

        grupos.add(grupo(2L, 999L, 10));

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(2L)));

        assertTrue(ex.getErrores().stream()
                .anyMatch(e -> e.contains("no pertenece a ningún plan de estudio en el que usted está inscrito")));
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void confirmar_grupoDeOtroPeriodo_seRechaza() {

        Grupo otroPeriodo = grupo(1L, 101L, 10);
        otroPeriodo.setIdPeriodo(99L);
        grupos.add(otroPeriodo);

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(1L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("no pertenece al período vigente")));
    }

    @Test
    void confirmar_grupoSinCupos_seRechaza() {

        grupos.add(grupo(1L, 101L, 2));
        ocupados.add(new Object[]{1L, 2L});

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(1L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("no tiene cupos disponibles")));
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void confirmar_cursoYaMatriculadoEnElPeriodo_seRechaza() {

        grupos.add(grupo(1L, 101L, 10));
        grupos.add(grupo(2L, 101L, 10));

        Matricula existente = new Matricula();
        existente.setId(700L);
        existente.setEstado(estadoMatricula("REGISTRADA"));
        when(matriculaRepository.findByAutorizacion_Id(50L)).thenReturn(Optional.of(existente));

        MatriculaDetalle detalle = new MatriculaDetalle();
        detalle.setIdGrupo(1L);
        when(detalleRepository.findByMatricula_IdAndRetiradoFalse(700L)).thenReturn(List.of(detalle));

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(2L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Ya está matriculado")));
    }

    @Test
    void confirmar_dosGruposDelMismoCurso_seRechaza() {

        grupos.add(grupo(1L, 101L, 10));
        grupos.add(grupo(2L, 101L, 10));

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(1L, 2L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("más de un grupo del curso")));
    }

    @Test
    void confirmar_choqueDeHorarioEntreGruposSeleccionados_seRechaza() {

        grupos.add(grupo(1L, 101L, 10));
        grupos.add(grupo(2L, 102L, 10));
        horarios.add(horario(1L, 1, "08:00", "09:30"));
        horarios.add(horario(2L, 1, "09:00", "10:30"));

        aprobarCurso(101L, "85");

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(1L, 2L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Choque de horario")));
    }

    @Test
    void confirmar_horariosContiguosSinSolape_seAceptan() {

        grupos.add(grupo(1L, 101L, 10));
        grupos.add(grupo(2L, 102L, 10));
        horarios.add(horario(1L, 1, "08:00", "09:30"));
        horarios.add(horario(2L, 1, "09:30", "11:00"));

        aprobarCurso(101L, "85");

        assertDoesNotThrow(() -> service.confirmar(solicitud(1L, 2L)));
    }

    /*
     * ============================================================
     * Prerrequisitos y correquisitos
     * ============================================================
     */

    @Test
    void confirmar_prerrequisitoNoAprobado_seRechaza() {

        grupos.add(grupo(2L, 102L, 10));
        requisitos.add(requisito("PRERREQUISITO", 102L, 101L, null));

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(2L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Prerrequisito incumplido")));
    }

    @Test
    void confirmar_prerrequisitoAprobado_seAcepta() {

        grupos.add(grupo(2L, 102L, 10));
        requisitos.add(requisito("PRERREQUISITO", 102L, 101L, null));
        aprobarCurso(101L, "85");

        assertDoesNotThrow(() -> service.confirmar(solicitud(2L)));
    }

    @Test
    void confirmar_prerrequisitoAprobadoPeroBajoLaNotaMinima_seRechaza() {

        grupos.add(grupo(2L, 102L, 10));
        requisitos.add(requisito("PRERREQUISITO", 102L, 101L, new BigDecimal("70")));
        aprobarCurso(101L, "65");

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(2L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Prerrequisito incumplido")));
    }

    @Test
    void confirmar_correquisitoIncluidoEnLaSeleccion_seAcepta() {

        grupos.add(grupo(1L, 101L, 10));
        grupos.add(grupo(2L, 102L, 10));
        requisitos.add(requisito("CORREQUISITO", 102L, 101L, null));

        assertDoesNotThrow(() -> service.confirmar(solicitud(1L, 2L)));
    }

    @Test
    void confirmar_correquisitoAusente_seRechaza() {

        grupos.add(grupo(2L, 102L, 10));
        requisitos.add(requisito("CORREQUISITO", 102L, 101L, null));

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(2L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Correquisito incumplido")));
    }

    @Test
    void confirmar_notaMinimaExigidaPeroCursoSinNota_seRechaza() {

        grupos.add(grupo(2L, 102L, 10));
        requisitos.add(requisito("PRERREQUISITO", 102L, 101L, new BigDecimal("70")));
        aprobados.add(new Object[]{101L, null});

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(2L)));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("Prerrequisito incumplido")));
    }

    /*
     * La base de datos valida el correquisito al insertar cada curso, así
     * que el curso requerido debe guardarse primero aunque el estudiante
     * lo haya elegido después.
     */
    @Test
    void confirmar_guardaPrimeroElCorrequisito_sinImportarElOrdenElegido() {

        grupos.add(grupo(1L, 101L, 10));
        grupos.add(grupo(2L, 102L, 10));
        requisitos.add(requisito("CORREQUISITO", 102L, 101L, null));

        service.confirmar(solicitud(2L, 1L));

        ArgumentCaptor<MatriculaDetalle> guardados = ArgumentCaptor.forClass(MatriculaDetalle.class);
        verify(detalleRepository, times(2)).save(guardados.capture());

        assertEquals(
                List.of(1L, 2L),
                guardados.getAllValues().stream().map(MatriculaDetalle::getIdGrupo).toList()
        );
    }

    @Test
    void confirmar_reglaRechazadaPorLaBaseDeDatos_seMuestraAlEstudiante() {

        grupos.add(grupo(1L, 101L, 10));

        when(detalleRepository.save(any(MatriculaDetalle.class)))
                .thenThrow(new JpaSystemException(new RuntimeException(
                        "no se pudo insertar",
                        new SQLException("El estudiante no cumple los prerrequisitos del curso", "45000")
                )));

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(1L)));

        assertEquals("El estudiante no cumple los prerrequisitos del curso", ex.getMessage());
    }

    @Test
    void confirmar_errorDeBaseDeDatosDeOtraNaturaleza_noSeOculta() {

        grupos.add(grupo(1L, 101L, 10));

        when(detalleRepository.save(any(MatriculaDetalle.class)))
                .thenThrow(new JpaSystemException(new RuntimeException("conexión perdida")));

        assertThrows(JpaSystemException.class, () -> service.confirmar(solicitud(1L)));
    }

    @Test
    void confirmar_informaTodosLosMotivosJuntos() {

        grupos.add(grupo(1L, 101L, 2));
        grupos.add(grupo(2L, 102L, 10));
        ocupados.add(new Object[]{1L, 2L});
        requisitos.add(requisito("PRERREQUISITO", 102L, 101L, null));
        horarios.add(horario(1L, 1, "08:00", "09:30"));
        horarios.add(horario(2L, 1, "08:00", "09:30"));

        MatriculaInvalidaException ex =
                assertThrows(MatriculaInvalidaException.class, () -> service.confirmar(solicitud(1L, 2L)));

        assertTrue(ex.getErrores().size() >= 3, "errores: " + ex.getErrores());
    }

    /*
     * ============================================================
     * Utilidades
     * ============================================================
     */

    private ConfirmarSeleccionMatriculaRequestDTO solicitud(Long... idsGrupo) {

        ConfirmarSeleccionMatriculaRequestDTO request = new ConfirmarSeleccionMatriculaRequestDTO();
        request.setIdsGrupo(List.of(idsGrupo));

        return request;
    }

    private Grupo grupo(Long id, Long idCurso, int cupoMaximo) {

        Grupo grupo = new Grupo();
        grupo.setId(id);
        grupo.setIdPeriodo(ID_PERIODO);
        grupo.setIdCurso(idCurso);
        grupo.setCodigo("G-" + id);
        grupo.setCupoMaximo((short) cupoMaximo);
        grupo.setModalidad("PRESENCIAL");
        grupo.setEstado("PUBLICADO");

        return grupo;
    }

    private Curso curso(Long id, String nombre) {

        Curso curso = new Curso();
        curso.setId(id);
        curso.setCodigo("C-" + id);
        curso.setNombre(nombre);
        curso.setActivo(true);

        return curso;
    }

    private PlanEstudio plan() {

        PlanEstudio plan = new PlanEstudio();
        plan.setId(ID_PLAN);
        plan.setCodigo("PLAN-1");
        plan.setNombre("Plan de Guitarra");

        return plan;
    }

    private MallaCurricular malla(Long idCurso, int nivel) {

        MallaCurricular malla = new MallaCurricular();
        malla.setIdPlanEstudio(ID_PLAN);
        malla.setIdCurso(idCurso);
        malla.setNivel((short) nivel);

        return malla;
    }

    private HorarioGrupo horario(Long idGrupo, int dia, String inicio, String fin) {

        HorarioGrupo horario = new HorarioGrupo();
        horario.setIdGrupo(idGrupo);
        horario.setDiaSemana((short) dia);
        horario.setHoraInicio(LocalTime.parse(inicio));
        horario.setHoraFin(LocalTime.parse(fin));

        return horario;
    }

    private RequisitosCurso requisito(String tipo, Long idCurso, Long idRequerido, BigDecimal notaMinima) {

        TiposRequisitosCurso tipoRequisito = new TiposRequisitosCurso();
        tipoRequisito.setCodigo(tipo);

        RequisitosCurso requisito = new RequisitosCurso();
        requisito.setIdPlanEstudio(plan());
        requisito.setIdCurso(curso(idCurso, "Curso " + idCurso));
        requisito.setIdCursoRequerido(curso(idRequerido, "Curso " + idRequerido));
        requisito.setIdTipoRequisito(tipoRequisito);
        requisito.setNotaMinima(notaMinima);
        requisito.setActivo(true);

        return requisito;
    }

    private void aprobarCurso(Long idCurso, String nota) {
        aprobados.add(new Object[]{idCurso, new BigDecimal(nota)});
    }

    private EstadosAutorizacionesMatricula estadoAutorizacion(String codigo) {

        EstadosAutorizacionesMatricula estado = new EstadosAutorizacionesMatricula();
        estado.setCodigo(codigo);

        return estado;
    }

    private EstadoMatricula estadoMatricula(String codigo) {

        EstadoMatricula estado = new EstadoMatricula();
        estado.setCodigo(codigo);

        return estado;
    }
}
