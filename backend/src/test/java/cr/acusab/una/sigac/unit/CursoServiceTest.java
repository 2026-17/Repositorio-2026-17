package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Programa;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.ProgramaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.CursoService;
import cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.request.CursoUpdateRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoEdicionResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoListadoResponseDTO;
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
    private ProfesorRepository profesorRepository;

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
        profesorRepository = mock(ProfesorRepository.class);

        cursoService = new CursoService(
                cursoRepository,
                planEstudioRepository,
                mallaCurricularRepository,
                grupoRepository,
                programaRepository,
                periodoAcademicoRepository,
                usuarioRepository,
                profesorRepository
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

        MallaCurricular mallaCreada =
                mallaCaptor.getValue();

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

        Grupo grupoCreado =
                grupoCaptor.getValue();

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
     * HU-030 - Modificar curso de la malla curricular
     * ============================================================
     */

    @Test
    void debeObtenerCursoParaEdicionCorrectamente() {

        Curso curso = crearCursoActivo();

        MallaCurricular malla =
                crearMallaActiva(
                        200L,
                        100L,
                        50L,
                        (short) 2,
                        (short) 3
                );

        PlanEstudio plan = mock(PlanEstudio.class);

        when(plan.getId()).thenReturn(50L);
        when(plan.getIdPrograma()).thenReturn(1L);

        Programa programa = mock(Programa.class);

        when(programa.getId()).thenReturn(1L);
        when(programa.getNombre())
                .thenReturn("Educación Musical");

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(List.of(malla));

        when(planEstudioRepository.findById(50L))
                .thenReturn(Optional.of(plan));

        when(programaRepository.findById(1L))
                .thenReturn(Optional.of(programa));

        CursoEdicionResponseDTO respuesta =
                cursoService.obtenerParaEdicion(100L);

        assertNotNull(respuesta);
        assertEquals(100L, respuesta.getIdCurso());
        assertEquals("CUR-101", respuesta.getCodigo());
        assertEquals("Curso original", respuesta.getNombre());
        assertEquals("Descripción original", respuesta.getDescripcion());
        assertEquals(40, respuesta.getHorasTotales());
        assertEquals(1L, respuesta.getIdPrograma());
        assertEquals("Educación Musical", respuesta.getPrograma());
        assertEquals((short) 2, respuesta.getNivel());
        assertTrue(respuesta.getActivo());
    }

    @Test
    void debeActualizarDatosCursoSinCambiarProgramaNiNivel() {

        Curso curso = crearCursoActivo();

        MallaCurricular malla =
                crearMallaActiva(
                        200L,
                        100L,
                        50L,
                        (short) 2,
                        (short) 3
                );

        PlanEstudio planActual = mock(PlanEstudio.class);

        when(planActual.getId()).thenReturn(50L);
        when(planActual.getIdPrograma()).thenReturn(1L);

        Programa programa = mock(Programa.class);

        when(programa.getId()).thenReturn(1L);
        when(programa.getNombre())
                .thenReturn("Educación Musical");
        when(programa.getActivo()).thenReturn(true);

        Usuario usuario = mock(Usuario.class);

        when(usuario.getId()).thenReturn(7L);

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(List.of(malla));

        when(planEstudioRepository.findById(50L))
                .thenReturn(Optional.of(planActual));

        when(programaRepository.findById(1L))
                .thenReturn(Optional.of(programa));

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        when(cursoRepository.save(any(Curso.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(mallaCurricularRepository
                .save(any(MallaCurricular.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        configurarUsuarioAutenticado();

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        request.setIdPrograma(1L);
        request.setNivel((short) 2);

        CursoEdicionResponseDTO respuesta =
                cursoService.actualizar(
                        100L,
                        request
                );

        assertNotNull(respuesta);

        assertEquals(
                "Curso actualizado",
                curso.getNombre()
        );

        assertEquals(
                "Descripción actualizada",
                curso.getDescripcion()
        );

        assertEquals(
                60,
                curso.getHorasTotales()
        );

        assertEquals(
                7L,
                curso.getIdUsuarioUltimaModificacion()
        );

        assertNotNull(
                curso.getActualizadoEn()
        );

        assertEquals(
                50L,
                malla.getIdPlanEstudio()
        );

        assertEquals(
                (short) 2,
                malla.getNivel()
        );

        assertEquals(
                (short) 3,
                malla.getOrdenEnNivel()
        );

        assertTrue(
                malla.getActivo()
        );

        assertEquals(
                7L,
                malla.getIdUsuarioUltimaModificacion()
        );

        assertEquals(
                "Curso actualizado",
                respuesta.getNombre()
        );

        assertEquals(
                1L,
                respuesta.getIdPrograma()
        );

        assertEquals(
                (short) 2,
                respuesta.getNivel()
        );

        /*
         * Al continuar en el mismo programa no debe buscar
         * otro plan vigente ni crear una asociación nueva.
         */
        verify(planEstudioRepository, never())
                .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
                        anyLong()
                );

        verify(mallaCurricularRepository, never())
                .findByIdPlanEstudioAndIdCurso(
                        anyLong(),
                        anyLong()
                );

        /*
         * HU-030 no modifica grupos ni matrículas.
         */
        verify(grupoRepository, never())
                .save(any(Grupo.class));
    }

    @Test
    void debeActualizarNivelDentroDelMismoPrograma() {

        Curso curso = crearCursoActivo();

        MallaCurricular malla =
                crearMallaActiva(
                        200L,
                        100L,
                        50L,
                        (short) 2,
                        (short) 3
                );

        PlanEstudio planActual = mock(PlanEstudio.class);

        when(planActual.getId()).thenReturn(50L);
        when(planActual.getIdPrograma()).thenReturn(1L);

        Programa programa = mock(Programa.class);

        when(programa.getId()).thenReturn(1L);
        when(programa.getNombre())
                .thenReturn("Educación Musical");
        when(programa.getActivo()).thenReturn(true);

        Usuario usuario = mock(Usuario.class);

        when(usuario.getId()).thenReturn(7L);

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(List.of(malla));

        when(planEstudioRepository.findById(50L))
                .thenReturn(Optional.of(planActual));

        when(programaRepository.findById(1L))
                .thenReturn(Optional.of(programa));

        when(mallaCurricularRepository
                .findMaxOrdenEnNivel(
                        50L,
                        (short) 3
                ))
                .thenReturn(Optional.of((short) 5));

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        when(cursoRepository.save(any(Curso.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(mallaCurricularRepository
                .save(any(MallaCurricular.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        configurarUsuarioAutenticado();

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        request.setIdPrograma(1L);
        request.setNivel((short) 3);

        CursoEdicionResponseDTO respuesta =
                cursoService.actualizar(
                        100L,
                        request
                );

        assertNotNull(respuesta);

        assertEquals(
                50L,
                malla.getIdPlanEstudio()
        );

        assertEquals(
                (short) 3,
                malla.getNivel()
        );

        assertEquals(
                (short) 6,
                malla.getOrdenEnNivel()
        );

        assertTrue(
                malla.getActivo()
        );

        assertEquals(
                7L,
                malla.getIdUsuarioUltimaModificacion()
        );

        assertEquals(
                (short) 3,
                respuesta.getNivel()
        );

        verify(mallaCurricularRepository)
                .findMaxOrdenEnNivel(
                        50L,
                        (short) 3
                );

        verify(mallaCurricularRepository, never())
                .findByIdPlanEstudioAndIdCurso(
                        anyLong(),
                        anyLong()
                );
    }

    @Test
    void debeCambiarCursoDeProgramaCreandoNuevaMalla() {

        Curso curso = crearCursoActivo();

        MallaCurricular mallaActual =
                crearMallaActiva(
                        200L,
                        100L,
                        50L,
                        (short) 2,
                        (short) 3
                );

        PlanEstudio planActual = mock(PlanEstudio.class);

        when(planActual.getId()).thenReturn(50L);
        when(planActual.getIdPrograma()).thenReturn(1L);

        Programa programaDestino = mock(Programa.class);

        when(programaDestino.getId()).thenReturn(2L);
        when(programaDestino.getNombre())
                .thenReturn("Artes Musicales");
        when(programaDestino.getActivo())
                .thenReturn(true);

        PlanEstudio planDestino = mock(PlanEstudio.class);

        when(planDestino.getId()).thenReturn(60L);
        when(planDestino.getIdPrograma()).thenReturn(2L);

        Usuario usuario = mock(Usuario.class);

        when(usuario.getId()).thenReturn(7L);

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(List.of(mallaActual));

        when(planEstudioRepository.findById(50L))
                .thenReturn(Optional.of(planActual));

        when(programaRepository.findById(2L))
                .thenReturn(Optional.of(programaDestino));

        when(planEstudioRepository
                .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
                        2L
                ))
                .thenReturn(Optional.of(planDestino));

        when(mallaCurricularRepository
                .findByIdPlanEstudioAndIdCurso(
                        60L,
                        100L
                ))
                .thenReturn(Optional.empty());

        when(mallaCurricularRepository
                .findMaxOrdenEnNivel(
                        60L,
                        (short) 4
                ))
                .thenReturn(Optional.of((short) 2));

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        when(cursoRepository.save(any(Curso.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(mallaCurricularRepository
                .save(any(MallaCurricular.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        configurarUsuarioAutenticado();

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        request.setIdPrograma(2L);
        request.setNivel((short) 4);

        CursoEdicionResponseDTO respuesta =
                cursoService.actualizar(
                        100L,
                        request
                );

        assertNotNull(respuesta);

        /*
         * La asociación anterior debe conservarse,
         * pero marcada como inactiva.
         */
        assertFalse(
                mallaActual.getActivo()
        );

        assertEquals(
                7L,
                mallaActual.getIdUsuarioUltimaModificacion()
        );

        ArgumentCaptor<MallaCurricular> captor =
                ArgumentCaptor.forClass(
                        MallaCurricular.class
                );

        verify(mallaCurricularRepository, times(2))
                .save(captor.capture());

        List<MallaCurricular> mallasGuardadas =
                captor.getAllValues();

        MallaCurricular nuevaMalla =
                mallasGuardadas.get(1);

        assertNotSame(
                mallaActual,
                nuevaMalla
        );

        assertEquals(
                60L,
                nuevaMalla.getIdPlanEstudio()
        );

        assertEquals(
                100L,
                nuevaMalla.getIdCurso()
        );

        assertEquals(
                (short) 4,
                nuevaMalla.getNivel()
        );

        assertEquals(
                (short) 3,
                nuevaMalla.getOrdenEnNivel()
        );

        assertTrue(
                nuevaMalla.getActivo()
        );

        assertTrue(
                nuevaMalla.getObligatorio()
        );

        assertEquals(
                7L,
                nuevaMalla.getIdUsuarioUltimaModificacion()
        );

        assertNotNull(
                nuevaMalla.getCreadoEn()
        );

        assertNotNull(
                nuevaMalla.getActualizadoEn()
        );

        assertEquals(
                2L,
                respuesta.getIdPrograma()
        );

        assertEquals(
                "Artes Musicales",
                respuesta.getPrograma()
        );

        assertEquals(
                (short) 4,
                respuesta.getNivel()
        );

        /*
         * No se elimina la asociación histórica.
         */
        verify(mallaCurricularRepository, never())
                .delete(any(MallaCurricular.class));

        verify(mallaCurricularRepository, never())
                .deleteById(anyLong());

        /*
         * Tampoco se modifican grupos existentes.
         */
        verify(grupoRepository, never())
                .save(any(Grupo.class));
    }

    @Test
    void debeCambiarCursoDeProgramaReutilizandoMallaExistente() {

        Curso curso = crearCursoActivo();

        MallaCurricular mallaActual =
                crearMallaActiva(
                        200L,
                        100L,
                        50L,
                        (short) 2,
                        (short) 3
                );

        MallaCurricular mallaDestino =
                crearMallaActiva(
                        300L,
                        100L,
                        60L,
                        (short) 1,
                        (short) 2
                );

        mallaDestino.setActivo(false);

        PlanEstudio planActual = mock(PlanEstudio.class);

        when(planActual.getId()).thenReturn(50L);
        when(planActual.getIdPrograma()).thenReturn(1L);

        PlanEstudio planDestino = mock(PlanEstudio.class);

        when(planDestino.getId()).thenReturn(60L);
        when(planDestino.getIdPrograma()).thenReturn(2L);

        Programa programaDestino = mock(Programa.class);

        when(programaDestino.getId()).thenReturn(2L);
        when(programaDestino.getNombre())
                .thenReturn("Artes Musicales");
        when(programaDestino.getActivo())
                .thenReturn(true);

        Usuario usuario = mock(Usuario.class);

        when(usuario.getId()).thenReturn(7L);

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(List.of(mallaActual));

        when(planEstudioRepository.findById(50L))
                .thenReturn(Optional.of(planActual));

        when(programaRepository.findById(2L))
                .thenReturn(Optional.of(programaDestino));

        when(planEstudioRepository
                .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
                        2L
                ))
                .thenReturn(Optional.of(planDestino));

        when(mallaCurricularRepository
                .findByIdPlanEstudioAndIdCurso(
                        60L,
                        100L
                ))
                .thenReturn(Optional.of(mallaDestino));

        when(mallaCurricularRepository
                .findMaxOrdenEnNivel(
                        60L,
                        (short) 4
                ))
                .thenReturn(Optional.of((short) 5));

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        when(cursoRepository.save(any(Curso.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(mallaCurricularRepository
                .save(any(MallaCurricular.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        configurarUsuarioAutenticado();

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        request.setIdPrograma(2L);
        request.setNivel((short) 4);

        CursoEdicionResponseDTO respuesta =
                cursoService.actualizar(
                        100L,
                        request
                );

        assertNotNull(respuesta);

        assertFalse(
                mallaActual.getActivo()
        );

        /*
         * La asociación histórica con el plan destino
         * debe reactivarse en lugar de crear un duplicado.
         */
        assertTrue(
                mallaDestino.getActivo()
        );

        assertEquals(
                60L,
                mallaDestino.getIdPlanEstudio()
        );

        assertEquals(
                (short) 4,
                mallaDestino.getNivel()
        );

        assertEquals(
                (short) 6,
                mallaDestino.getOrdenEnNivel()
        );

        assertEquals(
                7L,
                mallaDestino.getIdUsuarioUltimaModificacion()
        );

        assertEquals(
                2L,
                respuesta.getIdPrograma()
        );

        assertEquals(
                (short) 4,
                respuesta.getNivel()
        );

        verify(mallaCurricularRepository)
                .findByIdPlanEstudioAndIdCurso(
                        60L,
                        100L
                );
    }

    @Test
    void noDebeModificarCursoInexistente() {

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        when(cursoRepository.findById(999L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.actualizar(
                                999L,
                                request
                        )
                );

        assertEquals(
                "El curso indicado no existe.",
                exception.getMessage()
        );

        verify(cursoRepository, never())
                .save(any(Curso.class));

        verify(mallaCurricularRepository, never())
                .save(any(MallaCurricular.class));
    }

    @Test
    void noDebeModificarCursoInactivo() {

        Curso curso = crearCursoActivo();
        curso.setActivo(false);

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.actualizar(
                                100L,
                                request
                        )
                );

        assertEquals(
                "No se puede modificar un curso inactivo.",
                exception.getMessage()
        );

        verify(mallaCurricularRepository, never())
                .findByIdCursoAndActivoTrue(anyLong());

        verify(cursoRepository, never())
                .save(any(Curso.class));
    }

    @Test
    void noDebeModificarCursoSiProgramaDestinoEstaInactivo() {

        Curso curso = crearCursoActivo();

        MallaCurricular malla =
                crearMallaActiva(
                        200L,
                        100L,
                        50L,
                        (short) 2,
                        (short) 3
                );

        PlanEstudio planActual = mock(PlanEstudio.class);

        when(planActual.getId()).thenReturn(50L);
        when(planActual.getIdPrograma()).thenReturn(1L);

        Programa programaDestino = mock(Programa.class);

        when(programaDestino.getActivo())
                .thenReturn(false);

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(List.of(malla));

        when(planEstudioRepository.findById(50L))
                .thenReturn(Optional.of(planActual));

        when(programaRepository.findById(2L))
                .thenReturn(Optional.of(programaDestino));

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        request.setIdPrograma(2L);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.actualizar(
                                100L,
                                request
                        )
                );

        assertEquals(
                "El programa académico seleccionado se encuentra inactivo.",
                exception.getMessage()
        );

        verify(cursoRepository, never())
                .save(any(Curso.class));

        verify(mallaCurricularRepository, never())
                .save(any(MallaCurricular.class));
    }

    @Test
    void noDebeModificarSiNuevoProgramaNoTienePlanVigente() {

        Curso curso = crearCursoActivo();

        MallaCurricular malla =
                crearMallaActiva(
                        200L,
                        100L,
                        50L,
                        (short) 2,
                        (short) 3
                );

        PlanEstudio planActual = mock(PlanEstudio.class);

        when(planActual.getId()).thenReturn(50L);
        when(planActual.getIdPrograma()).thenReturn(1L);

        Programa programaDestino = mock(Programa.class);

        when(programaDestino.getActivo())
                .thenReturn(true);

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(List.of(malla));

        when(planEstudioRepository.findById(50L))
                .thenReturn(Optional.of(planActual));

        when(programaRepository.findById(2L))
                .thenReturn(Optional.of(programaDestino));

        when(planEstudioRepository
                .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
                        2L
                ))
                .thenReturn(Optional.empty());

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        request.setIdPrograma(2L);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.actualizar(
                                100L,
                                request
                        )
                );

        assertEquals(
                "El programa seleccionado no tiene un plan de estudio vigente.",
                exception.getMessage()
        );

        verify(cursoRepository, never())
                .save(any(Curso.class));

        verify(mallaCurricularRepository, never())
                .save(any(MallaCurricular.class));
    }

    @Test
    void noDebeModificarCursoSinMallaActiva() {

        Curso curso = crearCursoActivo();

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(List.of());

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.actualizar(
                                100L,
                                request
                        )
                );

        assertEquals(
                "El curso no posee una ubicación activa en la malla curricular.",
                exception.getMessage()
        );

        verify(cursoRepository, never())
                .save(any(Curso.class));
    }

    @Test
    void noDebeModificarCursoConMasDeUnaMallaActiva() {

        Curso curso = crearCursoActivo();

        MallaCurricular malla1 =
                crearMallaActiva(
                        200L,
                        100L,
                        50L,
                        (short) 2,
                        (short) 3
                );

        MallaCurricular malla2 =
                crearMallaActiva(
                        201L,
                        100L,
                        60L,
                        (short) 2,
                        (short) 4
                );

        when(cursoRepository.findById(100L))
                .thenReturn(Optional.of(curso));

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(List.of(
                        malla1,
                        malla2
                ));

        CursoUpdateRequestDTO request =
                crearUpdateRequestValido();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cursoService.actualizar(
                                100L,
                                request
                        )
                );

        assertEquals(
                "El curso posee más de una ubicación activa en la malla curricular.",
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

        when(usuario.getId())
                .thenReturn(7L);

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        configurarUsuarioAutenticado();

        MallaCurricular malla1 =
                new MallaCurricular();

        malla1.setId(200L);
        malla1.setIdCurso(100L);
        malla1.setActivo(true);

        MallaCurricular malla2 =
                new MallaCurricular();

        malla2.setId(201L);
        malla2.setIdCurso(100L);
        malla2.setActivo(true);

        List<MallaCurricular> mallasActivas =
                List.of(
                        malla1,
                        malla2
                );

        when(mallaCurricularRepository
                .findByIdCursoAndActivoTrue(100L))
                .thenReturn(mallasActivas);

        cursoService.desactivar(100L);

        /*
         * El curso no debe eliminarse.
         * Únicamente debe quedar marcado como inactivo.
         */
        assertFalse(
                curso.getActivo()
        );

        assertEquals(
                7L,
                curso.getIdUsuarioUltimaModificacion()
        );

        assertNotNull(
                curso.getActualizadoEn()
        );

        /*
         * Las asociaciones activas con la malla curricular
         * también deben quedar inactivas.
         */
        assertFalse(
                malla1.getActivo()
        );

        assertFalse(
                malla2.getActivo()
        );

        assertEquals(
                7L,
                malla1.getIdUsuarioUltimaModificacion()
        );

        assertEquals(
                7L,
                malla2.getIdUsuarioUltimaModificacion()
        );

        assertNotNull(
                malla1.getActualizadoEn()
        );

        assertNotNull(
                malla2.getActualizadoEn()
        );

        /*
         * Comprobar que los cambios se guardaron.
         */
        verify(cursoRepository)
                .save(curso);

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

        verify(cursoRepository, never())
                .save(any(Curso.class));

        verify(mallaCurricularRepository, never())
                .findByIdCursoAndActivoTrue(anyLong());

        verify(mallaCurricularRepository, never())
                .saveAll(anyList());
    }

    /*
     * ============================================================
     * HU-031 - Listado de cursos activos
     * ============================================================
     */

    @Test
    void debeListarCursosActivosConDocenteYCupoDelGrupoMasReciente() {

        Curso curso1 = new Curso();

        curso1.setId(100L);
        curso1.setCodigo("PIA-101");
        curso1.setNombre("Piano Individual I");
        curso1.setDescripcion("Instrumento");
        curso1.setHorasTotales(40);
        curso1.setActivo(true);

        Curso curso2 = new Curso();

        curso2.setId(200L);
        curso2.setCodigo("TEO-201");
        curso2.setNombre("Teoría Musical II");
        curso2.setDescripcion("Teoría");
        curso2.setHorasTotales(60);
        curso2.setActivo(true);

        when(cursoRepository
                .findByActivoTrueOrderByNombreAsc())
                .thenReturn(
                        List.of(
                                curso1,
                                curso2
                        )
                );

        Grupo grupoCurso1 = new Grupo();

        grupoCurso1.setId(300L);
        grupoCurso1.setIdCurso(100L);
        grupoCurso1.setIdProfesor(9L);
        grupoCurso1.setCupoMaximo((short) 10);

        when(grupoRepository
                .findByIdCursoInOrderByCreadoEnDesc(
                        List.of(
                                100L,
                                200L
                        )
                ))
                .thenReturn(
                        List.of(grupoCurso1)
                );

        Profesor profesor = new Profesor();

        profesor.setId(9L);
        profesor.setNombres("Marlene");
        profesor.setPrimerApellido("Solano");
        profesor.setSegundoApellido(null);

        when(profesorRepository.findAllById(
                List.of(9L)
        )).thenReturn(
                List.of(profesor)
        );

        List<CursoListadoResponseDTO> resultado =
                cursoService.listarActivos();

        assertEquals(
                2,
                resultado.size()
        );

        CursoListadoResponseDTO dto1 =
                resultado.get(0);

        assertEquals(
                100L,
                dto1.getIdCurso()
        );

        assertEquals(
                "PIA-101",
                dto1.getCodigo()
        );

        assertEquals(
                "Piano Individual I",
                dto1.getNombre()
        );

        assertEquals(
                "Instrumento",
                dto1.getCategoria()
        );

        assertEquals(
                "Marlene Solano",
                dto1.getDocente()
        );

        assertEquals(
                (short) 10,
                dto1.getCupoMaximo()
        );

        CursoListadoResponseDTO dto2 =
                resultado.get(1);

        assertEquals(
                200L,
                dto2.getIdCurso()
        );

        assertNull(
                dto2.getDocente()
        );

        assertNull(
                dto2.getCupoMaximo()
        );
    }

    @Test
    void debeRetornarListaVaciaSiNoHayCursosActivos() {

        when(cursoRepository
                .findByActivoTrueOrderByNombreAsc())
                .thenReturn(List.of());

        List<CursoListadoResponseDTO> resultado =
                cursoService.listarActivos();

        assertTrue(
                resultado.isEmpty()
        );

        verify(grupoRepository, never())
                .findByIdCursoInOrderByCreadoEnDesc(
                        anyList()
                );
    }

    /*
     * ============================================================
     * Métodos auxiliares para las pruebas
     * ============================================================
     */

    private CursoRequestDTO crearRequestValido() {

        CursoRequestDTO request =
                new CursoRequestDTO();

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

    private CursoUpdateRequestDTO crearUpdateRequestValido() {

        CursoUpdateRequestDTO request =
                new CursoUpdateRequestDTO();

        request.setNombre(
                "Curso actualizado"
        );

        request.setDescripcion(
                "Descripción actualizada"
        );

        request.setHorasTotales(
                60
        );

        request.setIdPrograma(
                1L
        );

        request.setNivel(
                (short) 2
        );

        return request;
    }

    private Curso crearCursoActivo() {

        Curso curso =
                new Curso();

        curso.setId(100L);
        curso.setCodigo("CUR-101");
        curso.setNombre("Curso original");
        curso.setDescripcion("Descripción original");
        curso.setHorasTotales(40);
        curso.setActivo(true);

        return curso;
    }

    private MallaCurricular crearMallaActiva(
            Long idMalla,
            Long idCurso,
            Long idPlanEstudio,
            Short nivel,
            Short orden
    ) {

        MallaCurricular malla =
                new MallaCurricular();

        malla.setId(idMalla);
        malla.setIdCurso(idCurso);
        malla.setIdPlanEstudio(idPlanEstudio);
        malla.setNivel(nivel);
        malla.setOrdenEnNivel(orden);
        malla.setObligatorio(true);
        malla.setActivo(true);

        return malla;
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

        SecurityContextHolder.setContext(
                securityContext
        );
    }
}