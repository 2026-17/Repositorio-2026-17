package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Agrupacion;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.CursoReconocido;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.repository.CursoReconocidoRepository;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.logic.service.AvanceAgrupacionService;
import cr.acusab.una.sigac.logic.service.AvanceInstrumentoService;
import cr.acusab.una.sigac.logic.service.FamiliaAgrupacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Avance por agrupación (reglas D1, D2 y D3).
 *
 * Niveles: Conjunto Instrumental I..XII (ids 501..512) y Coro I..XIV
 * (ids 601..614), identificados por código (CI-I.., CORO-1, CORO-II..).
 * Agrupaciones: Orquesta (1), Comercial (2), Banda (3), instrumentales.
 */
class AvanceAgrupacionServiceTest {

    private static final long ESTUDIANTE = 1L;

    private final List<Object[]> calificaciones = new ArrayList<>();
    private final List<CursoReconocido> reconocidos = new ArrayList<>();
    private AvanceAgrupacionService service;

    private final Agrupacion orquesta = agrupacion(1L, "Orquesta");
    private final Agrupacion comercial = agrupacion(2L, "Comercial");
    private final Agrupacion banda = agrupacion(3L, "Banda");

    @BeforeEach
    void setUp() {
        MatriculaDetalleRepository detalleRepository = mock(MatriculaDetalleRepository.class);
        CursoReconocidoRepository reconocidoRepository = mock(CursoReconocidoRepository.class);
        CursoRepository cursoRepository = mock(CursoRepository.class);
        PeriodoAcademicoRepository periodoRepository = mock(PeriodoAcademicoRepository.class);

        service = new AvanceAgrupacionService(detalleRepository, reconocidoRepository, cursoRepository, periodoRepository);

        when(detalleRepository.findCalificacionesAgrupacion(ESTUDIANTE)).thenAnswer(inv -> new ArrayList<>(calificaciones));
        when(reconocidoRepository.findByIdEstudiante(ESTUDIANTE)).thenAnswer(inv -> new ArrayList<>(reconocidos));
        when(cursoRepository.findByCodigoIn(anyCollection())).thenReturn(cursosDeLasFamilias());

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(4L);
        periodo.setNombre("I ciclo 2026");
        when(periodoRepository.findById(4L)).thenReturn(Optional.of(periodo));
    }

    @Test
    void primeraVezSinHistorial_empiezaEnI() {
        AvanceAgrupacionService.SiguienteNivel nivel =
                service.siguienteNivel(ESTUDIANTE, banda, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL);

        assertEquals(501L, nivel.curso().getId());
        assertEquals(1, nivel.numero());
        assertFalse(nivel.completado());
        assertEquals("Primera vez en Banda.", nivel.motivo());
    }

    @Test
    void historialSinAgrupacionCiIV_empiezaEnV() {
        reconocidos.add(reconocido(504L, null));

        AvanceAgrupacionService.SiguienteNivel nivel =
                service.siguienteNivel(ESTUDIANTE, banda, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL);

        assertEquals(505L, nivel.curso().getId());
        assertEquals("Empieza en CI V por su historial de la certificación.", nivel.motivo());
    }

    @Test
    void reproboOrquestaV_laRepite_mientrasEnComercialVaEnVI() {
        calificaciones.add(new Object[]{504L, 1L, "APROBADO", new BigDecimal("85"), 3L}); // Orquesta IV
        calificaciones.add(new Object[]{505L, 1L, "REPROBADO", new BigDecimal("60"), 4L}); // Orquesta V
        calificaciones.add(new Object[]{505L, 2L, "APROBADO", new BigDecimal("90"), 4L}); // Comercial V

        AvanceAgrupacionService.SiguienteNivel enOrquesta =
                service.siguienteNivel(ESTUDIANTE, orquesta, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL);
        AvanceAgrupacionService.SiguienteNivel enComercial =
                service.siguienteNivel(ESTUDIANTE, comercial, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL);

        assertEquals(505L, enOrquesta.curso().getId());
        assertEquals("No aprobó Orquesta V: la repite.", enOrquesta.motivo());

        assertEquals(506L, enComercial.curso().getId());
        assertEquals("Aprobó Comercial V en I ciclo 2026.", enComercial.motivo());
    }

    @Test
    void completoXII_quedaCompletadoSinNivel() {
        calificaciones.add(new Object[]{512L, 1L, "APROBADO", new BigDecimal("95"), 4L});

        AvanceAgrupacionService.SiguienteNivel nivel =
                service.siguienteNivel(ESTUDIANTE, orquesta, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL);

        assertTrue(nivel.completado());
        assertNull(nivel.curso());
        assertFalse(nivel.disponible());
    }

    @Test
    void elAvancePropioTienePrioridadSobreElHistorialSinAgrupacion() {
        reconocidos.add(reconocido(508L, null));                                          // CI VIII sin agrupación
        calificaciones.add(new Object[]{503L, 1L, "APROBADO", new BigDecimal("80"), 4L}); // Orquesta III

        AvanceAgrupacionService.SiguienteNivel nivel =
                service.siguienteNivel(ESTUDIANTE, orquesta, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL);

        assertEquals(504L, nivel.curso().getId());
    }

    @Test
    void reconocidoConLaAgrupacion_cuentaComoAvancePropio() {
        reconocidos.add(reconocido(504L, 1L)); // Orquesta IV de la carga inicial

        assertEquals(505L, service.siguienteNivel(ESTUDIANTE, orquesta, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL)
                .curso().getId());
        // No cuenta en otra agrupación.
        assertEquals(501L, service.siguienteNivel(ESTUDIANTE, comercial, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL)
                .curso().getId());
    }

    @Test
    void nivelesAprobados_soloDeEsaAgrupacionYFamilia() {
        calificaciones.add(new Object[]{504L, 1L, "APROBADO", null, 4L}); // Orquesta IV
        calificaciones.add(new Object[]{601L, 1L, "APROBADO", null, 4L}); // Coro I en Orquesta (D4)
        calificaciones.add(new Object[]{505L, 2L, "APROBADO", null, 4L}); // Comercial V

        assertEquals(List.of(504L), List.copyOf(
                service.nivelesAprobados(ESTUDIANTE, 1L, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL).keySet()));
        assertEquals(List.of(601L), List.copyOf(
                service.nivelesAprobados(ESTUDIANTE, 1L, FamiliaAgrupacion.CORO).keySet()));
    }

    /* ---------------- D5: plan y graduación ---------------- */

    @Test
    void d5_todosLosNivelesEnUnaSolaAgrupacion_familiaCompleta() {
        for (long id = 501L; id <= 512L; id++) {
            calificaciones.add(new Object[]{id, 1L, "APROBADO", null, 4L}); // Orquesta I..XII
        }

        assertTrue(service.familiaCompleta(ESTUDIANTE, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL));
        assertFalse(service.familiaCompleta(ESTUDIANTE, FamiliaAgrupacion.CORO));
    }

    @Test
    void d5_nivelesRepartidosEntreDosAgrupaciones_noEstaCompleta() {
        for (long id = 501L; id <= 506L; id++) {
            calificaciones.add(new Object[]{id, 1L, "APROBADO", null, 4L}); // Orquesta I..VI
        }
        for (long id = 507L; id <= 512L; id++) {
            calificaciones.add(new Object[]{id, 2L, "APROBADO", null, 4L}); // Comercial VII..XII
        }

        assertFalse(service.familiaCompleta(ESTUDIANTE, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL));
    }

    @Test
    void d5_historialSinAgrupacionCuentaComoParteDeLaAgrupacion() {
        for (long id = 501L; id <= 504L; id++) {
            reconocidos.add(reconocido(id, null)); // CI I..IV de la certificación
        }
        for (long id = 505L; id <= 512L; id++) {
            calificaciones.add(new Object[]{id, 1L, "APROBADO", null, 4L}); // Orquesta V..XII
        }

        assertTrue(service.familiaCompleta(ESTUDIANTE, FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL));
    }

    @Test
    void d5_elMismoNivelEnDosAgrupacionesNoCuentaDoble() {
        // I..XI en Orquesta y XI otra vez en Comercial: siguen faltando niveles en una misma agrupación.
        for (long id = 501L; id <= 511L; id++) {
            calificaciones.add(new Object[]{id, 1L, "APROBADO", null, 4L});
        }
        calificaciones.add(new Object[]{511L, 2L, "APROBADO", null, 4L});
        calificaciones.add(new Object[]{501L, 2L, "APROBADO", null, 4L});

        AvanceAgrupacionService.AvanceFamilia avance =
                service.avanceFamilia(service.cargarHistorial(ESTUDIANTE), FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL);

        assertFalse(avance.completa());
        assertEquals(1L, avance.idAgrupacion(), "Cuenta la agrupación más adelantada (Orquesta)");
        assertEquals(11, avance.cursosAprobados().size(), "Once niveles distintos, no trece");
        assertEquals(11, avance.ultimoNivel());

        // Para el plan cuentan los de la más adelantada; XII no está aprobado.
        Map<Long, AvanceInstrumentoService.CursoAprobado> plan = service.cursosAprobadosDelPlan(ESTUDIANTE);
        assertTrue(plan.containsKey(511L));
        assertFalse(plan.containsKey(512L));
    }

    /* ------------------------------------------------------------ */

    static List<Curso> cursosDeLasFamilias() {
        List<Curso> cursos = new ArrayList<>();
        List<String> ci = FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL.codigos();
        for (int i = 0; i < ci.size(); i++) {
            cursos.add(curso(501L + i, ci.get(i), "Conjunto Instrumental " + FamiliaAgrupacion.romano(i + 1)));
        }
        List<String> coro = FamiliaAgrupacion.CORO.codigos();
        for (int i = 0; i < coro.size(); i++) {
            cursos.add(curso(601L + i, coro.get(i), "Coro " + FamiliaAgrupacion.romano(i + 1)));
        }
        return cursos;
    }

    private static Curso curso(Long id, String codigo, String nombre) {
        Curso c = new Curso();
        c.setId(id);
        c.setCodigo(codigo);
        c.setNombre(nombre);
        c.setActivo(true);
        return c;
    }

    private static Agrupacion agrupacion(Long id, String nombre) {
        Agrupacion a = new Agrupacion();
        a.setId(id);
        a.setNombre(nombre);
        a.setCodigo(nombre.toUpperCase());
        a.setTipo(Agrupacion.TIPO_INSTRUMENTAL);
        a.setActiva(true);
        return a;
    }

    private static CursoReconocido reconocido(Long idCurso, Long idAgrupacion) {
        CursoReconocido r = new CursoReconocido();
        r.setIdEstudiante(ESTUDIANTE);
        r.setIdCurso(idCurso);
        r.setIdAgrupacion(idAgrupacion);
        r.setCondicion(CursoReconocido.CONDICION_APROBADO);
        r.setOrigen(CursoReconocido.ORIGEN_CERTIFICACION);
        r.setFecha(LocalDate.of(2025, 12, 1));
        return r;
    }
}
