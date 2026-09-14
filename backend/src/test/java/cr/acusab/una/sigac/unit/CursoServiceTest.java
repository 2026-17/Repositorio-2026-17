package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Programa;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProgramaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.CursoService;
import cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CursoServiceTest {

    private CursoRepository cursoRepository;
    private PlanEstudioRepository planEstudioRepository;
    private MallaCurricularRepository mallaCurricularRepository;
    private GrupoRepository grupoRepository;
    private ProgramaRepository programaRepository;
    private PeriodoAcademicoRepository periodoAcademicoRepository;
    private UsuarioRepository usuarioRepository;

    private CursoService cursoService;

    @BeforeEach
    void setUp() {

        cursoRepository = mock(CursoRepository.class);
        planEstudioRepository = mock(PlanEstudioRepository.class);
        mallaCurricularRepository = mock(MallaCurricularRepository.class);
        grupoRepository = mock(GrupoRepository.class);
        programaRepository = mock(ProgramaRepository.class);
        periodoAcademicoRepository =
                mock(PeriodoAcademicoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);

        cursoService = new CursoService(
                cursoRepository,
                planEstudioRepository,
                mallaCurricularRepository,
                grupoRepository,
                programaRepository,
                periodoAcademicoRepository,
                usuarioRepository
        );
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * HU-028 - Crear curso en la malla curricular
     * ============================================================
     */
    @Test
    void crearCursoCorrectamente() {

        CursoRequestDTO request = crearRequestValido();

        when(cursoRepository.existsByCodigo("CUR-101"))
                .thenReturn(false);

        Programa programa = mock(Programa.class);
        when(programa.getActivo()).thenReturn(true);

        when(programaRepository.findById(1L))
                .thenReturn(Optional.of(programa));

        PlanEstudio plan = mock(PlanEstudio.class);
        when(plan.getId()).thenReturn(50L);

        when(planEstudioRepository
                .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
                        1L
                ))
                .thenReturn(Optional.of(plan));

        PeriodoAcademico periodo = mock(PeriodoAcademico.class);

        when(periodoAcademicoRepository.findById(2L))
                .thenReturn(Optional.of(periodo));

        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        configurarUsuarioAutenticado();

        when(mallaCurricularRepository.findMaxOrdenEnNivel(
                50L,
                (short) 2
        )).thenReturn(Optional.of((short) 3));

        Curso cursoGuardado = mock(Curso.class);
        when(cursoGuardado.getId()).thenReturn(100L);
        when(cursoGuardado.getCodigo()).thenReturn("CUR-101");
        when(cursoGuardado.getNombre()).thenReturn("Curso de prueba");
        when(cursoGuardado.getDescripcion())
                .thenReturn("Descripción de prueba");
        when(cursoGuardado.getHorasTotales()).thenReturn(40);
        when(cursoGuardado.getActivo()).thenReturn(true);

        when(cursoRepository.save(any(Curso.class)))
                .thenReturn(cursoGuardado);

        MallaCurricular mallaGuardada =
                mock(MallaCurricular.class);

        when(mallaGuardada.getId()).thenReturn(200L);
        when(mallaGuardada.getNivel()).thenReturn((short) 2);

        when(mallaCurricularRepository.save(
                any(MallaCurricular.class)
        )).thenReturn(mallaGuardada);

        Grupo grupoGuardado = mock(Grupo.class);

        when(grupoGuardado.getId()).thenReturn(300L);
        when(grupoGuardado.getCodigo()).thenReturn("CUR-101-A");
        when(grupoGuardado.getCupoMaximo()).thenReturn((short) 25);

        when(grupoRepository.save(any(Grupo.class)))
                .thenReturn(grupoGuardado);

        CursoResponseDTO respuesta =
                cursoService.crear(request);

        assertNotNull(respuesta);
        assertEquals(100L, respuesta.getIdCurso());
        assertEquals("CUR-101", respuesta.getCodigo());
        assertEquals("Curso de prueba", respuesta.getNombre());
        assertTrue(respuesta.getActivo());
        assertEquals((short) 2, respuesta.getNivel());
        assertEquals((short) 25, respuesta.getCupos());

        ArgumentCaptor<Curso> cursoCaptor =
                ArgumentCaptor.forClass(Curso.class);

        verify(cursoRepository).save(cursoCaptor.capture());

        Curso cursoCreado = cursoCaptor.getValue();

        assertEquals("CUR-101", cursoCreado.getCodigo());
        assertEquals("Curso de prueba", cursoCreado.getNombre());
        assertEquals(40, cursoCreado.getHorasTotales());
        assertTrue(cursoCreado.getActivo());

        assertEquals(
                7L,
                cursoCreado.getIdUsuarioUltimaModificacion()
        );

        ArgumentCaptor<MallaCurricular> mallaCaptor =
                ArgumentCaptor.forClass(MallaCurricular.class);

        verify(mallaCurricularRepository)
                .save(mallaCaptor.capture());

        MallaCurricular mallaCreada = mallaCaptor.getValue();

        assertEquals(50L, mallaCreada.getIdPlanEstudio());
        assertEquals((short) 2, mallaCreada.getNivel());
        assertEquals((short) 4, mallaCreada.getOrdenEnNivel());
        assertTrue(mallaCreada.getActivo());
        assertTrue(mallaCreada.getObligatorio());

        assertEquals(
                7L,
                mallaCreada.getIdUsuarioUltimaModificacion()
        );

        ArgumentCaptor<Grupo> grupoCaptor =
                ArgumentCaptor.forClass(Grupo.class);

        verify(grupoRepository).save(grupoCaptor.capture());

        Grupo grupoCreado = grupoCaptor.getValue();

        assertEquals(2L, grupoCreado.getIdPeriodo());
        assertEquals("CUR-101-A", grupoCreado.getCodigo());
        assertEquals((short) 25, grupoCreado.getCupoMaximo());
        assertEquals("PRESENCIAL", grupoCreado.getModalidad());
        assertEquals("PLANIFICADO", grupoCreado.getEstado());
    }

    @Test
    void noDebeCrearCursoConCodigoDuplicado() {

        CursoRequestDTO request = crearRequestValido();

        when(cursoRepository.existsByCodigo("CUR-101"))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.crear(request)
                );

        assertEquals(
                "Ya existe un curso con el código ingresado.",
                exception.getMessage()
        );

        verify(cursoRepository, never())
                .save(any(Curso.class));
    }

    @Test
    void noDebeCrearCursoSiProgramaEstaInactivo() {

        CursoRequestDTO request = crearRequestValido();

        when(cursoRepository.existsByCodigo("CUR-101"))
                .thenReturn(false);

        Programa programa = mock(Programa.class);
        when(programa.getActivo()).thenReturn(false);

        when(programaRepository.findById(1L))
                .thenReturn(Optional.of(programa));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.crear(request)
                );

        assertEquals(
                "El programa académico seleccionado se encuentra inactivo.",
                exception.getMessage()
        );

        verify(cursoRepository, never())
                .save(any(Curso.class));
    }

    @Test
    void noDebeCrearCursoSiPeriodoNoExiste() {

        CursoRequestDTO request = crearRequestValido();

        when(cursoRepository.existsByCodigo("CUR-101"))
                .thenReturn(false);

        Programa programa = mock(Programa.class);
        when(programa.getActivo()).thenReturn(true);

        when(programaRepository.findById(1L))
                .thenReturn(Optional.of(programa));

        PlanEstudio plan = mock(PlanEstudio.class);

        when(planEstudioRepository
                .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
                        1L
                ))
                .thenReturn(Optional.of(plan));

        when(periodoAcademicoRepository.findById(2L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.crear(request)
                );

        assertEquals(
                "El período lectivo seleccionado no existe.",
                exception.getMessage()
        );

        verify(cursoRepository, never())
                .save(any(Curso.class));
    }

    /*
     * ============================================================
     * HU-031 - Desactivar curso de la malla curricular
     * ============================================================
     */

    @Test
    void desactivarCursoCorrectamente() {

        Curso curso = new Curso();
        curso.setId(100L);
        curso.setCodigo("CUR-101");
        curso.setNombre("Curso de prueba");
        curso.setActivo(true);

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        configurarUsuarioAutenticado();

        MallaCurricular malla1 = new MallaCurricular();
        malla1.setId(200L);
        malla1.setIdCurso(100L);
        malla1.setActivo(true);

        MallaCurricular malla2 = new MallaCurricular();
        malla2.setId(201L);
        malla2.setIdCurso(100L);
        malla2.setActivo(true);

        List<MallaCurricular> mallasActivas =
                List.of(malla1, malla2);

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(mallasActivas);

        cursoService.desactivar(100L);

        /*
         * El curso no debe eliminarse.
         * Únicamente debe quedar marcado como inactivo.
         */
        assertFalse(curso.getActivo());

        assertEquals(
                7L,
                curso.getIdUsuarioUltimaModificacion()
        );

        assertNotNull(curso.getActualizadoEn());

        /*
         * Las asociaciones activas con la malla curricular
         * también deben quedar inactivas.
         */
        assertFalse(malla1.getActivo());
        assertFalse(malla2.getActivo());

        assertEquals(
                7L,
                malla1.getIdUsuarioUltimaModificacion()
        );

        assertEquals(
                7L,
                malla2.getIdUsuarioUltimaModificacion()
        );

        assertNotNull(malla1.getActualizadoEn());
        assertNotNull(malla2.getActualizadoEn());

        /*
         * Comprobar que los cambios se guardaron.
         */
        verify(cursoRepository).save(curso);

        verify(mallaCurricularRepository)
                .findByIdCursoAndActivoTrue(100L);

        verify(mallaCurricularRepository)
                .saveAll(mallasActivas);

        /*
         * HU-031 exige soft-delete.
         * Por lo tanto, nunca debe eliminarse físicamente
         * el curso de la base de datos.
         */
        verify(cursoRepository, never())
                .delete(any(Curso.class));

        verify(cursoRepository, never())
                .deleteById(anyLong());
    }

    @Test
    void noDebeDesactivarCursoInexistente() {

        when(cursoRepository.findById(999L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.desactivar(999L)
                );

        assertEquals(
                "El curso indicado no existe.",
                exception.getMessage()
        );

        /*
         * Si el curso no existe, no debe modificarse nada.
         */
        verify(cursoRepository, never())
                .save(any(Curso.class));

        verify(mallaCurricularRepository, never())
                .findByIdCursoAndActivoTrue(anyLong());

        verify(mallaCurricularRepository, never())
                .saveAll(anyList());
    }

    @Test
    void noDebeDesactivarCursoYaInactivo() {

        Curso curso = new Curso();
        curso.setId(100L);
        curso.setActivo(false);

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.desactivar(100L)
                );

        assertEquals(
                "El curso ya se encuentra inactivo.",
                exception.getMessage()
        );

        /*
         * Si ya está inactivo, no debe volver a guardarse
         * ni modificar sus asociaciones con la malla.
         */
        verify(cursoRepository, never())
                .save(any(Curso.class));

        verify(mallaCurricularRepository, never())
                .findByIdCursoAndActivoTrue(anyLong());

        verify(mallaCurricularRepository, never())
                .saveAll(anyList());
    }

    /*
     * ============================================================
     * Métodos auxiliares para las pruebas
     * ============================================================
     */

    private CursoRequestDTO crearRequestValido() {

        CursoRequestDTO request = new CursoRequestDTO();

        request.setCodigo("CUR-101");
        request.setNombre("Curso de prueba");
        request.setDescripcion("Descripción de prueba");
        request.setHorasTotales(40);
        request.setIdPrograma(1L);
        request.setNivel((short) 2);
        request.setCupos((short) 25);
        request.setIdPeriodo(2L);

        return request;
    }

    private void configurarUsuarioAutenticado() {

        Authentication authentication =
                mock(Authentication.class);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getName())
                .thenReturn("123456789");

        SecurityContext securityContext =
                mock(SecurityContext.class);

        when(securityContext.getAuthentication())
                .thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }
}