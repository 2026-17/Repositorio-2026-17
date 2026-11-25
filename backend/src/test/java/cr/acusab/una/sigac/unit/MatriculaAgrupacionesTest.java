package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.MatriculaInvalidaException;
import cr.acusab.una.sigac.logic.service.AgrupacionService;
import cr.acusab.una.sigac.logic.service.AvanceAgrupacionService;
import cr.acusab.una.sigac.logic.service.AvanceInstrumentoService;
import cr.acusab.una.sigac.logic.service.MatriculaEstudianteService;
import cr.acusab.una.sigac.logic.service.MensualidadCalculoService;
import cr.acusab.una.sigac.presentation.dto.request.ConfirmarSeleccionMatriculaRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.AgrupacionesDisponiblesResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.HorarioEstudianteResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.MatriculaConfirmadaResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.OfertaMatriculaResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Matrícula de agrupaciones.
 *
 * Estudiante 1 (período 5, que inicia el 1/2/2027; plan 10) toca Violín (7)
 * y tiene 10 años. Su plan no tiene cursos de instrumento (no hay lecciones).
 *
 * Agrupaciones:
 *   1 Orquesta (instrumental, solo Violín)    2 Comercial (instrumental)
 *   3 Banda (instrumental, solo Trompeta)     4 Coro de niños (coral)
 *   5 Taller inclusivo (coral, solo inclusión)
 *   6 Coro pre solfeo (coral, edad máxima 5)
 * Grupos del período (todos publicados, cupo 20):
 *   3001 Orquesta lunes 14:00-16:00          3002 Comercial martes 14:00-16:00
 *   3003 Banda miércoles 14:00-16:00         3004 Coro de niños jueves 14:00-16:00
 *   3005 Comercial (otro grupo) viernes      3007 Coro de niños lunes 15:00-17:00 (choca con 3001)
 *   3008 Taller inclusivo sábado             3009 Coro pre solfeo domingo
 * Curso normal: 2001 Iniciación musical (301, edad máxima 5), miércoles 8:00.
 * Historial: aprobó Orquesta IV, reprobó Orquesta V y aprobó Comercial V.
 * Niveles: CI I..XII = 501..512; Coro I..XIV = 601..614.
 */
class MatriculaAgrupacionesTest {

    private static final long ID_ESTUDIANTE = 1L;
    private static final long ID_PERIODO = 5L;
    private static final long ID_PLAN = 10L;
    private static final int VIOLIN = 7;
    private static final int TROMPETA = 8;
    private static final LocalDate INICIO_PERIODO = LocalDate.of(2027, 2, 1);

    private MatriculaDetalleRepository detalleRepository;
    private MatriculaRepository matriculaRepository;
    private EstudianteRepository estudianteRepository;
    private EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private MatriculaEstudianteService service;
    private Estudiante estudiante;

    private final List<Grupo> grupos = new ArrayList<>();
    private final List<HorarioGrupo> horarios = new ArrayList<>();
    private final List<Object[]> ocupados = new ArrayList<>();
    private final List<Object[]> calificaciones = new ArrayList<>();
    private final Map<Long, Agrupacion> agrupaciones = new HashMap<>();
    private final List<Curso> cursos = new ArrayList<>(AvanceAgrupacionServiceTest.cursosDeLasFamilias());

    @BeforeEach
    void setUp() {
        estudianteRepository = mock(EstudianteRepository.class);
        PeriodoAcademicoRepository periodoRepository = mock(PeriodoAcademicoRepository.class);
        AutorizacionesMatriculaRepository autorizacionRepository = mock(AutorizacionesMatriculaRepository.class);
        EstudiantePlanEstudioRepository estudiantePlanRepository = mock(EstudiantePlanEstudioRepository.class);
        PlanEstudioRepository planRepository = mock(PlanEstudioRepository.class);
        MallaCurricularRepository mallaRepository = mock(MallaCurricularRepository.class);
        CursoRepository cursoRepository = mock(CursoRepository.class);
        GrupoRepository grupoRepository = mock(GrupoRepository.class);
        HorarioGrupoRepository horarioRepository = mock(HorarioGrupoRepository.class);
        EstadoMatriculaRepository estadoMatriculaRepository = mock(EstadoMatriculaRepository.class);
        CursoReconocidoRepository reconocidoRepository = mock(CursoReconocidoRepository.class);
        AgrupacionService agrupacionService = mock(AgrupacionService.class);
        estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);
        detalleRepository = mock(MatriculaDetalleRepository.class);
        matriculaRepository = mock(MatriculaRepository.class);

        service = new MatriculaEstudianteService(
                estudianteRepository, periodoRepository, autorizacionRepository, estudiantePlanRepository,
                planRepository, mallaRepository, cursoRepository, grupoRepository, horarioRepository,
                mock(ProfesorRepository.class), mock(RequisitosCursoRepository.class), matriculaRepository,
                detalleRepository, estadoMatriculaRepository, mock(JdbcTemplate.class),
                new AvanceInstrumentoService(detalleRepository, reconocidoRepository, mallaRepository, cursoRepository),
                estudianteInstrumentoRepository, mock(InstrumentoRepository.class),
                new AvanceAgrupacionService(detalleRepository, reconocidoRepository, cursoRepository, periodoRepository),
                agrupacionService
        );

        // Sesión del estudiante.
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("111");
        SecurityContext contexto = mock(SecurityContext.class);
        when(contexto.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(contexto);

        estudiante = new Estudiante();
        estudiante.setId(ID_ESTUDIANTE);
        estudiante.setActivo(true);
        estudiante.setNombres("Estudiante");
        estudiante.setPrimerApellido("Prueba");
        estudiante.setFechaNacimiento(INICIO_PERIODO.minusYears(10));
        when(estudianteRepository.findByUsuario_Cedula("111")).thenReturn(Optional.of(estudiante));
        when(estudianteRepository.findCodigoTipoEstudiante(ID_ESTUDIANTE)).thenReturn(Optional.of("REGULAR"));

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(ID_PERIODO);
        periodo.setNombre("I ciclo 2027");
        periodo.setEstado("MATRICULA");
        periodo.setFechaInicio(INICIO_PERIODO);
        periodo.setFechaFin(LocalDate.of(2027, 6, 30));
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
        when(autorizacionRepository.findByIdEstudiante_Id(ID_ESTUDIANTE)).thenReturn(List.of(autorizacion));

        EstudiantePlanEstudio inscripcion = new EstudiantePlanEstudio();
        inscripcion.setIdEstudiante(ID_ESTUDIANTE);
        inscripcion.setIdPlanEstudio(ID_PLAN);
        when(estudiantePlanRepository.findByIdEstudianteAndActivoTrue(ID_ESTUDIANTE)).thenReturn(List.of(inscripcion));

        PlanEstudio plan = new PlanEstudio();
        plan.setId(ID_PLAN);
        plan.setNombre("Plan 2027");
        when(planRepository.findAllById(anyCollection())).thenReturn(List.of(plan));

        // Cursos: los niveles de las familias y un curso normal con edad máxima 5.
        Curso iniciacion = new Curso();
        iniciacion.setId(301L);
        iniciacion.setCodigo("INI-1");
        iniciacion.setNombre("Iniciación musical");
        iniciacion.setActivo(true);
        iniciacion.setEdadMaxima(5);
        cursos.add(iniciacion);
        MallaCurricular malla = new MallaCurricular();
        malla.setIdPlanEstudio(ID_PLAN);
        malla.setIdCurso(301L);
        malla.setNivel((short) 1);
        malla.setOrdenEnNivel((short) 1);
        malla.setActivo(true);
        when(mallaRepository.findByIdPlanEstudioInAndActivoTrue(anyCollection())).thenReturn(List.of(malla));

        when(cursoRepository.findByCodigoIn(anyCollection())).thenReturn(AvanceAgrupacionServiceTest.cursosDeLasFamilias());
        when(cursoRepository.findAllById(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return cursos.stream().filter(c -> ids.contains(c.getId())).toList();
        });

        // Instrumento del estudiante: Violín.
        Instrumento violin = new Instrumento();
        violin.setId(VIOLIN);
        violin.setNombre("Violín");
        Instrumento trompeta = new Instrumento();
        trompeta.setId(TROMPETA);
        trompeta.setNombre("Trompeta");
        EstudiantesInstrumento asignacion = new EstudiantesInstrumento();
        asignacion.setId(60L);
        asignacion.setEstudiante(estudiante);
        asignacion.setInstrumento(violin);
        asignacion.setFechaInicio(LocalDate.of(2026, 1, 10));
        asignacion.setEsPrincipal(true);
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(ID_ESTUDIANTE))
                .thenReturn(List.of(asignacion));
        when(estudianteInstrumentoRepository.countByIdEstudiante_IdAndFechaFinIsNull(ID_ESTUDIANTE)).thenReturn(1L);

        // Agrupaciones del catálogo.
        agrupaciones.put(1L, agrupacion(1L, "Orquesta", Agrupacion.TIPO_INSTRUMENTAL));
        agrupaciones.put(2L, agrupacion(2L, "Comercial", Agrupacion.TIPO_INSTRUMENTAL));
        agrupaciones.put(3L, agrupacion(3L, "Banda", Agrupacion.TIPO_INSTRUMENTAL));
        agrupaciones.put(4L, agrupacion(4L, "Coro de niños", Agrupacion.TIPO_CORAL));
        Agrupacion inclusiva = agrupacion(5L, "Taller inclusivo", Agrupacion.TIPO_CORAL);
        inclusiva.setSoloInclusion(true);
        agrupaciones.put(5L, inclusiva);
        Agrupacion preSolfeo = agrupacion(6L, "Coro pre solfeo", Agrupacion.TIPO_CORAL);
        preSolfeo.setEdadMaxima(5);
        agrupaciones.put(6L, preSolfeo);
        when(agrupacionService.agrupacionesPorId(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return agrupaciones.values().stream().filter(a -> ids.contains(a.getId()))
                    .collect(Collectors.toMap(Agrupacion::getId, a -> a));
        });
        when(agrupacionService.instrumentosPermitidos(anyCollection()))
                .thenReturn(Map.of(1L, List.of(violin), 3L, List.of(trompeta)));
        when(agrupacionService.nombresProfesores(anyCollection()))
                .thenReturn(Map.of(3001L, List.of("Profesora Responsable", "Profesor Asistente")));

        // Grupos.
        grupos.add(grupoAgrupacion(3001L, 1L, "AGR-ORQ-1"));
        grupos.add(grupoAgrupacion(3002L, 2L, "AGR-COM-1"));
        grupos.add(grupoAgrupacion(3003L, 3L, "AGR-BANDA-1"));
        grupos.add(grupoAgrupacion(3004L, 4L, "AGR-CORO-1"));
        grupos.add(grupoAgrupacion(3005L, 2L, "AGR-COM-2"));
        grupos.add(grupoAgrupacion(3007L, 4L, "AGR-CORO-2"));
        grupos.add(grupoAgrupacion(3008L, 5L, "AGR-INC-1"));
        grupos.add(grupoAgrupacion(3009L, 6L, "AGR-PRE-1"));
        Grupo grupoIniciacion = new Grupo();
        grupoIniciacion.setId(2001L);
        grupoIniciacion.setCodigo("INI-1-A");
        grupoIniciacion.setIdCurso(301L);
        grupoIniciacion.setIdPeriodo(ID_PERIODO);
        grupoIniciacion.setCupoMaximo((short) 20);
        grupoIniciacion.setEstado("PUBLICADO");
        grupos.add(grupoIniciacion);

        horarios.add(horario(3001L, 1, "14:00", "16:00"));
        horarios.add(horario(3002L, 2, "14:00", "16:00"));
        horarios.add(horario(3003L, 3, "14:00", "16:00"));
        horarios.add(horario(3004L, 4, "14:00", "16:00"));
        horarios.add(horario(3005L, 5, "14:00", "16:00"));
        horarios.add(horario(3007L, 1, "15:00", "17:00"));
        horarios.add(horario(3008L, 6, "09:00", "10:00"));
        horarios.add(horario(3009L, 7, "09:00", "10:00"));
        horarios.add(horario(2001L, 3, "08:00", "09:00"));

        when(grupoRepository.findAllByIdParaActualizar(anyCollection())).thenAnswer(inv -> filtrarGrupos(inv.getArgument(0)));
        when(grupoRepository.findAllById(anyCollection())).thenAnswer(inv -> filtrarGrupos(inv.getArgument(0)));
        when(grupoRepository.findGruposAgrupacion(ID_PERIODO)).thenAnswer(inv -> grupos.stream().filter(Grupo::esAgrupacion).toList());
        when(grupoRepository.findByIdPeriodoAndEstadoIn(eq(ID_PERIODO), anyCollection())).thenAnswer(inv -> new ArrayList<>(grupos));
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return horarios.stream().filter(h -> ids.contains(h.getIdGrupo())).toList();
        });
        when(detalleRepository.contarCuposOcupados(anyCollection(), eq("REGISTRADA"))).thenAnswer(inv -> new ArrayList<>(ocupados));

        // Historial: Orquesta IV aprobada, Orquesta V reprobada, Comercial V aprobada.
        calificaciones.add(new Object[]{504L, 1L, "APROBADO", new BigDecimal("85"), 3L});
        calificaciones.add(new Object[]{505L, 1L, "REPROBADO", new BigDecimal("60"), 4L});
        calificaciones.add(new Object[]{505L, 2L, "APROBADO", new BigDecimal("90"), 4L});
        when(detalleRepository.findCalificacionesAgrupacion(ID_ESTUDIANTE)).thenAnswer(inv -> new ArrayList<>(calificaciones));

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

    /* ---------------- Matrícula ---------------- */

    @Test
    void variasAgrupacionesEnElMismoPeriodo_cadaUnaConSuDetalleSuNivelYSuOrden() {

        confirmar(3001L, 3002L, 3004L);

        List<MatriculaDetalle> detalles = detallesGuardados(3);

        assertEquals(505L, detalle(detalles, 3001L).getIdCurso(), "Reprobó Orquesta V: la repite");
        assertEquals(506L, detalle(detalles, 3002L).getIdCurso(), "Aprobó Comercial V: va en VI");
        assertEquals(601L, detalle(detalles, 3004L).getIdCurso(), "Primera vez en el coro: Coro I");

        assertEquals((short) 1, detalle(detalles, 3001L).getOrdenSeleccion());
        assertEquals((short) 2, detalle(detalles, 3002L).getOrdenSeleccion());
        assertEquals((short) 3, detalle(detalles, 3004L).getOrdenSeleccion());
    }

    @Test
    void choqueEntreDosAgrupaciones_seRechaza() {
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(3001L, 3007L));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.startsWith("Choque de horario entre")
                && e.contains("«Orquesta»") && e.contains("«Coro de niños»")), ex.getErrores().toString());
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void agrupacionSinCupo_seRechaza() {
        ocupados.add(new Object[]{3002L, 20L});

        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(3002L, 3004L));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("«Comercial»") && e.contains("está lleno")));
    }

    @Test
    void instrumentoNoPermitido_seRechaza() {
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(3003L, 3004L));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("«Banda»")
                && e.contains("es solo para estudiantes de Trompeta")), ex.getErrores().toString());
    }

    @Test
    void laMismaAgrupacionDosVeces_seRechaza() {
        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class,
                () -> confirmar(3002L, 3005L, 3004L));

        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("«Comercial» más de una vez")));
    }

    @Test
    void agrupacionCompletada_quedaBloqueada() {
        calificaciones.add(new Object[]{614L, 4L, "APROBADO", null, 4L}); // Coro XIV en Coro de niños

        AgrupacionesDisponiblesResponseDTO.AgrupacionDTO coro = disponible(3004L);
        assertTrue(coro.isBloqueada());
        assertEquals("Ya completó esta agrupación.", coro.getMotivoBloqueo());

        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class, () -> confirmar(3004L));
        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("ya completó esta agrupación")));
    }

    /* ---------------- D4: coro automático ---------------- */

    @Test
    void d4_unaSolaInstrumental_guardaConjuntoInstrumental() {

        confirmar(3001L);

        assertEquals(505L, detallesGuardados(1).get(0).getIdCurso());
    }

    @Test
    void d4_dosInstrumentalesSinCoral_laSegundaElegidaGuardaCoro() {

        // Se marcó primero Comercial y después Orquesta.
        confirmar(3002L, 3001L);

        List<MatriculaDetalle> detalles = detallesGuardados(2);
        assertEquals(506L, detalle(detalles, 3002L).getIdCurso(), "La primera sigue con Conjunto Instrumental VI");
        assertEquals(601L, detalle(detalles, 3001L).getIdCurso(), "La segunda se califica como Coro I");
        assertEquals((short) 2, detalle(detalles, 3001L).getOrdenSeleccion());
    }

    @Test
    void d4_unaInstrumentalYUnaCoral_ningunaInstrumentalPasaACoro() {

        confirmar(3001L, 3004L);

        List<MatriculaDetalle> detalles = detallesGuardados(2);
        assertEquals(505L, detalle(detalles, 3001L).getIdCurso());
        assertEquals(601L, detalle(detalles, 3004L).getIdCurso(), "La coral lleva su propio Coro I");
    }

    @Test
    void d4_laSegundaSeCuentaConLasYaMatriculadasDelPeriodo() {
        // Ya tenía Orquesta (orden 1) de una confirmación anterior.
        Matricula matricula = matriculaRegistrada();
        MatriculaDetalle orquesta = new MatriculaDetalle();
        orquesta.setMatricula(matricula);
        orquesta.setIdGrupo(3001L);
        orquesta.setIdCurso(505L);
        orquesta.setOrdenSeleccion((short) 1);
        when(detalleRepository.findByMatricula_IdAndRetiradoFalse(700L)).thenReturn(List.of(orquesta));

        confirmar(3002L);

        MatriculaDetalle comercial = detallesGuardados(1).get(0);
        assertEquals(601L, comercial.getIdCurso(), "Comercial es la segunda del período: Coro I");
        assertEquals((short) 2, comercial.getOrdenSeleccion());
    }

    @Test
    void d4_elEndpointYaNoAceptaElegirLaAgrupacionDeCoro() {
        for (Field campo : ConfirmarSeleccionMatriculaRequestDTO.class.getDeclaredFields()) {
            assertFalse(campo.getName().toLowerCase(Locale.ROOT).contains("coro"),
                    "La solicitud no debe permitir elegir el coro: " + campo.getName());
        }
    }

    /* ---------------- D6: solo inclusión ---------------- */

    @Test
    void d6_estudianteRegular_noPuedeMatricularUnaAgrupacionDeInclusion() {

        assertEquals("Es solo para estudiantes de inclusión.", disponible(3008L).getMotivoBloqueo());

        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class, () -> confirmar(3008L));
        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("es solo para estudiantes de inclusión")));
    }

    @Test
    void d6_estudianteDeInclusion_siPuede() {
        when(estudianteRepository.findCodigoTipoEstudiante(ID_ESTUDIANTE)).thenReturn(Optional.of("INCLUSION"));

        assertFalse(disponible(3008L).isBloqueada());

        confirmar(3008L);
        assertEquals(601L, detallesGuardados(1).get(0).getIdCurso());
    }

    /* ---------------- D7: edad ---------------- */

    @Test
    void d7_agrupacionConEdadMaxima5_unNinoDe5PuedeYUnoDe6No() {

        estudiante.setFechaNacimiento(INICIO_PERIODO.minusYears(6));
        assertEquals("Es solo para menores de 6 años.", disponible(3009L).getMotivoBloqueo());
        assertThrows(MatriculaInvalidaException.class, () -> confirmar(3009L));

        // Cumple 6 un día después del inicio del período: tiene 5.
        estudiante.setFechaNacimiento(INICIO_PERIODO.minusYears(6).plusDays(1));
        assertFalse(disponible(3009L).isBloqueada());
        confirmar(3009L);
        assertEquals(601L, detallesGuardados(1).get(0).getIdCurso());
    }

    @Test
    void d7_cursoConEdadMaxima5_unNinoDe5PuedeYUnoDe6No() {

        estudiante.setFechaNacimiento(INICIO_PERIODO.minusYears(6));
        OfertaMatriculaResponseDTO.GrupoDTO grupo = service.consultarMiOferta().getOferta().stream()
                .filter(g -> g.getIdGrupo() == 2001L).findFirst().orElseThrow();
        assertEquals("Es solo para menores de 6 años.", grupo.getMotivoNoDisponible());

        MatriculaInvalidaException ex = assertThrows(MatriculaInvalidaException.class, () -> confirmarCursos(2001L));
        assertTrue(ex.getErrores().stream().anyMatch(e -> e.contains("«Iniciación musical»") && e.contains("menores de 6 años")),
                ex.getErrores().toString());

        estudiante.setFechaNacimiento(INICIO_PERIODO.minusYears(5));
        assertNull(service.consultarMiOferta().getOferta().stream()
                .filter(g -> g.getIdGrupo() == 2001L).findFirst().orElseThrow().getMotivoNoDisponible());
        confirmarCursos(2001L);
        verify(detalleRepository, times(1)).save(any());
    }

    /* ---------------- Costo ---------------- */

    @Test
    void costo_elMontoDeLaMatriculaNoCambiaAlAgregarAgrupaciones() {
        MensualidadCalculoService mensualidad = new MensualidadCalculoService(estudianteInstrumentoRepository);
        BigDecimal antes = mensualidad.calcularMontoBase(ID_ESTUDIANTE);

        confirmar(3001L, 3002L, 3004L);
        detallesGuardados(3);

        assertEquals(antes, mensualidad.calcularMontoBase(ID_ESTUDIANTE));
        assertEquals(new BigDecimal("27000.00"), antes, "Un solo instrumento: solo la mensualidad base");
    }

    /* ---------------- El nivel es interno ---------------- */

    @Test
    void agrupacionesDisponibles_noIncluyenElNivel() {

        AgrupacionesDisponiblesResponseDTO disponibles = service.consultarMisAgrupaciones();

        for (Field campo : AgrupacionesDisponiblesResponseDTO.AgrupacionDTO.class.getDeclaredFields()) {
            String nombre = campo.getName().toLowerCase(Locale.ROOT);
            assertFalse(nombre.contains("nivel") || nombre.contains("curso"),
                    "Campo que revela el nivel: " + campo.getName());
        }

        AgrupacionesDisponiblesResponseDTO.AgrupacionDTO orquesta = disponible(3001L);
        assertEquals("Orquesta", orquesta.getNombre());
        assertFalse(orquesta.isBloqueada());
        assertEquals(List.of("Profesora Responsable", "Profesor Asistente"), orquesta.getProfesores());

        assertEquals("Es solo para estudiantes de Trompeta.", disponible(3003L).getMotivoBloqueo());
        assertEquals("INSTRUMENTAL", disponibles.getAgrupaciones().get(0).getTipo());
    }

    @Test
    void comprobanteYHorarioDelEstudiante_muestranSoloElNombreDeLaAgrupacion() {

        MatriculaConfirmadaResponseDTO comprobante = confirmar(3001L, 3004L);

        OfertaMatriculaResponseDTO.GrupoDTO orquesta = comprobante.getCursos().stream()
                .filter(c -> c.getIdGrupo() == 3001L).findFirst().orElseThrow();
        assertSinNivel(orquesta, "Orquesta");

        Matricula matricula = matriculaRegistrada();
        MatriculaDetalle detalle = new MatriculaDetalle();
        detalle.setMatricula(matricula);
        detalle.setIdGrupo(3001L);
        detalle.setIdCurso(505L);
        when(detalleRepository.findByMatricula_IdAndRetiradoFalse(700L)).thenReturn(List.of(detalle));

        HorarioEstudianteResponseDTO horario = service.consultarMiHorario(null);

        assertEquals(1, horario.getClases().size());
        assertSinNivel(horario.getClases().get(0), "Orquesta");
    }

    /* ------------------------------------------------------------ */

    private Matricula matriculaRegistrada() {
        Matricula matricula = new Matricula();
        matricula.setId(700L);
        EstadoMatricula registrada = new EstadoMatricula();
        registrada.setCodigo("REGISTRADA");
        matricula.setEstado(registrada);
        when(matriculaRepository.findByAutorizacion_Id(50L)).thenReturn(Optional.of(matricula));
        return matricula;
    }

    private AgrupacionesDisponiblesResponseDTO.AgrupacionDTO disponible(Long idGrupo) {
        return service.consultarMisAgrupaciones().getAgrupaciones().stream()
                .filter(a -> a.getIdGrupo().equals(idGrupo)).findFirst().orElseThrow();
    }

    private static void assertSinNivel(OfertaMatriculaResponseDTO.GrupoDTO dto, String nombre) {
        assertTrue(dto.isAgrupacion());
        assertEquals(nombre, dto.getNombreCurso());
        assertNull(dto.getIdCurso());
        assertNull(dto.getCodigoCurso());
        assertNull(dto.getNombreNivel());
        assertNull(dto.getNivel());
        assertFalse(dto.getNombreCurso().contains("Conjunto Instrumental"));
    }

    // Agrupaciones en el orden en que se marcaron.
    private MatriculaConfirmadaResponseDTO confirmar(Long... idsAgrupacion) {
        ConfirmarSeleccionMatriculaRequestDTO request = new ConfirmarSeleccionMatriculaRequestDTO();
        request.setIdsAgrupacion(new ArrayList<>(List.of(idsAgrupacion)));
        return service.confirmar(request);
    }

    private MatriculaConfirmadaResponseDTO confirmarCursos(Long... idsGrupo) {
        ConfirmarSeleccionMatriculaRequestDTO request = new ConfirmarSeleccionMatriculaRequestDTO();
        request.setIdsGrupo(new ArrayList<>(List.of(idsGrupo)));
        return service.confirmar(request);
    }

    private List<MatriculaDetalle> detallesGuardados(int cantidad) {
        ArgumentCaptor<MatriculaDetalle> captor = ArgumentCaptor.forClass(MatriculaDetalle.class);
        verify(detalleRepository, times(cantidad)).save(captor.capture());
        return captor.getAllValues();
    }

    private List<Grupo> filtrarGrupos(Collection<?> ids) {
        return grupos.stream().filter(g -> ids.contains(g.getId())).toList();
    }

    private static MatriculaDetalle detalle(List<MatriculaDetalle> detalles, Long idGrupo) {
        return detalles.stream().filter(d -> d.getIdGrupo().equals(idGrupo)).findFirst().orElseThrow();
    }

    private static Agrupacion agrupacion(Long id, String nombre, String tipo) {
        Agrupacion a = new Agrupacion();
        a.setId(id);
        a.setNombre(nombre);
        a.setCodigo("A" + id);
        a.setTipo(tipo);
        a.setActiva(true);
        return a;
    }

    private static Grupo grupoAgrupacion(Long id, Long idAgrupacion, String codigo) {
        Grupo g = new Grupo();
        g.setId(id);
        g.setCodigo(codigo);
        g.setTipoGrupo(Grupo.TIPO_AGRUPACION);
        g.setIdAgrupacion(idAgrupacion);
        g.setIdPeriodo(ID_PERIODO);
        g.setCupoMaximo((short) 20);
        g.setModalidad("PRESENCIAL");
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
}
