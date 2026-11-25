package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.CupoGrupo;
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
    private UsuarioRepository usuarioRepository;
    private MatriculaDetalleRepository matriculaDetalleRepository;
    private GrupoService service;

    @BeforeEach
    void setUp() {
        grupoRepository = mock(GrupoRepository.class);
        horarioRepository = mock(HorarioGrupoRepository.class);
        cursoRepository = mock(CursoRepository.class);
        periodoRepository = mock(PeriodoAcademicoRepository.class);
        profesorRepository = mock(ProfesorRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        matriculaDetalleRepository = mock(MatriculaDetalleRepository.class);
        service = new GrupoService(
                grupoRepository, horarioRepository, cursoRepository,
                periodoRepository, profesorRepository,
                new ValidacionHorarioProfesorService(grupoRepository, horarioRepository),
                usuarioRepository,
                matriculaDetalleRepository
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
    void crear_rechazaCursoDeInstrumento() {
        Curso instrumentoV = new Curso();
        instrumentoV.setId(3L);
        instrumentoV.setNombre("Instrumento V");
        instrumentoV.setActivo(true);
        instrumentoV.setEsCursoInstrumento(true);
        when(cursoRepository.findById(3L)).thenReturn(Optional.of(instrumentoV));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.crear(request(LocalTime.of(8, 0), LocalTime.of(9, 0)))
        );

        assertTrue(error.getMessage().contains("Espacios de instrumento"));
        verify(grupoRepository, never()).save(any(Grupo.class));
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

    /* ============================================================
     * Gestión: sugerir código, modificar y eliminar
     * ============================================================ */

    @Test
    void sugerirCodigo_usaLaPrimeraLetraLibreDelPeriodo() {
        Curso curso = cursoRepository.findById(3L).orElseThrow();
        curso.setCodigo("mus-101");
        when(grupoRepository.existsByIdPeriodoAndCodigo(4L, "MUS-101-A")).thenReturn(true);
        when(grupoRepository.existsByIdPeriodoAndCodigo(4L, "MUS-101-B")).thenReturn(true);

        assertEquals("MUS-101-C", service.sugerirCodigo(3L, 4L));
    }

    @Test
    void actualizar_rechazaGrupoCancelado() {
        Grupo grupo = grupoExistente();
        grupo.setEstado("CANCELADO");

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.actualizar(20L, request(LocalTime.of(9, 0), LocalTime.of(10, 0)))
        );

        assertTrue(error.getMessage().contains("cerrado o cancelado"));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void actualizar_noCambiaElCursoSiHayMatriculas() {
        grupoExistente().setIdCurso(99L);
        when(matriculaDetalleRepository.contarDetallesPorGrupo(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 1L}));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.actualizar(20L, request(LocalTime.of(9, 0), LocalTime.of(10, 0)))
        );

        assertTrue(error.getMessage().contains("no se puede cambiar su curso"));
    }

    @Test
    void actualizar_noDejaElCupoPorDebajoDeLosInscritos() {
        grupoExistente();
        when(matriculaDetalleRepository.contarCuposOcupados(anyCollection(), eq("REGISTRADA")))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 18L}));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.actualizar(20L, request(LocalTime.of(9, 0), LocalTime.of(10, 0)))
        );

        assertTrue(error.getMessage().contains("18 estudiantes"));
    }

    @Test
    void actualizar_guardaYReemplazaElHorario() {
        Grupo grupo = grupoExistente();
        autenticar();
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> inv.getArgument(0));

        GrupoRequestDTO request = request(LocalTime.of(9, 0), LocalTime.of(10, 0));
        request.setCupoMaximo((short) 25);
        request.setAula("Aula 2");

        service.actualizar(20L, request);

        assertEquals((short) 25, grupo.getCupoMaximo());
        assertEquals("MUS-01-B", grupo.getCodigo());
        assertEquals(7L, grupo.getIdUsuarioUltimaModificacion());
        verify(horarioRepository).eliminarPorGrupo(20L);
        verify(horarioRepository).saveAll(argThat(lista ->
                lista instanceof List<?> l && l.size() == 1
                        && "Aula 2".equals(((HorarioGrupo) l.get(0)).getAula())));
    }

    @Test
    void eliminar_rechazaGrupoConMatriculas() {
        grupoExistente();
        when(matriculaDetalleRepository.contarDetallesPorGrupo(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 3L}));

        BusinessException error = assertThrows(BusinessException.class, () -> service.eliminar(20L));

        assertTrue(error.getMessage().contains("Cancelado"));
        verify(grupoRepository, never()).delete(any(Grupo.class));
    }

    @Test
    void eliminar_rechazaGrupoConClasesRegistradas() {
        grupoExistente();
        when(grupoRepository.contarSesionesPorGrupo(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 2L}));

        assertThrows(BusinessException.class, () -> service.eliminar(20L));
        verify(grupoRepository, never()).delete(any(Grupo.class));
    }

    @Test
    void eliminar_borraGrupoSinHistorial() {
        Grupo grupo = grupoExistente();
        autenticar();

        service.eliminar(20L);

        assertEquals(7L, grupo.getIdUsuarioUltimaModificacion());
        verify(grupoRepository).delete(grupo);
    }

    private Grupo grupoExistente() {
        Grupo grupo = new Grupo();
        grupo.setId(20L);
        grupo.setIdCurso(3L);
        grupo.setIdPeriodo(4L);
        grupo.setCodigo("MUS-01-B");
        grupo.setEstado("PLANIFICADO");
        when(grupoRepository.findById(20L)).thenReturn(Optional.of(grupo));
        return grupo;
    }

    private void autenticar() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        when(usuarioRepository.findByCedula("123")).thenReturn(Optional.of(usuario));

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "123", null, List.of())
        );
    }

    /* =================================================================
     * Cursos a coordinar en Dirección
     * ================================================================= */

    private Curso cursoACoordinar() {
        Curso camara = new Curso();
        camara.setId(3L);
        camara.setNombre("Música de Cámara III");
        camara.setActivo(true);
        camara.setACoordinar(true);
        when(cursoRepository.findById(3L)).thenReturn(Optional.of(camara));
        when(cursoRepository.findAllById(anyCollection())).thenReturn(List.of(camara));
        return camara;
    }

    private GrupoRequestDTO requestSinHorario(String estado) {
        GrupoRequestDTO request = request(LocalTime.of(8, 0), LocalTime.of(9, 0));
        request.setHorarios(List.of());
        request.setEstado(estado);
        return request;
    }

    @Test
    void crear_cursoNormalSinHorario_daElErrorDeSiempre() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.crear(requestSinHorario("PLANIFICADO")));

        assertEquals("Agregue al menos un horario para el grupo.", error.getMessage());
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void crear_cursoNormalPublicadoSinProfesor_daError() {
        GrupoRequestDTO request = request(LocalTime.of(8, 0), LocalTime.of(9, 0));
        request.setEstado("PUBLICADO");

        BusinessException error = assertThrows(BusinessException.class, () -> service.crear(request));

        assertTrue(error.getMessage().contains("profesor activo"));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void crear_horariosNull_enCursoNormal_daElErrorDeSiempre() {
        GrupoRequestDTO request = requestSinHorario("PLANIFICADO");
        request.setHorarios(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.crear(request));

        assertEquals("Agregue al menos un horario para el grupo.", error.getMessage());
    }

    @Test
    void crear_cursoACoordinar_sinHorarioNiProfesor_seCreaPublicado() {
        cursoACoordinar();
        autenticar();
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> {
            Grupo g = inv.getArgument(0);
            g.setId(50L);
            return g;
        });

        var respuesta = service.crear(requestSinHorario("PUBLICADO"));

        assertEquals("PUBLICADO", respuesta.getEstado());
        assertNull(respuesta.getIdProfesor());
        assertTrue(respuesta.getHorarios().isEmpty());
        verify(grupoRepository).save(argThat(g -> g.getIdProfesor() == null && "PUBLICADO".equals(g.getEstado())));
        // Sin horario ni profesor no se buscan choques.
        verify(grupoRepository, never()).findGruposProfesorEnPeriodo(any(), any(), any(), any());
    }

    @Test
    void crear_cursoACoordinarSinCupo_seGuardaConCupoSinLimite() {
        cursoACoordinar();
        autenticar();
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> inv.getArgument(0));

        GrupoRequestDTO request = requestSinHorario("PUBLICADO");
        request.setCupoMaximo(null);

        var respuesta = service.crear(request);

        verify(grupoRepository).save(argThat(g -> g.getCupoMaximo() == CupoGrupo.CUPO_SIN_LIMITE));
        assertTrue(respuesta.getSinCupo());
        assertNull(respuesta.getCupoMaximo());
    }

    @Test
    void crear_cursoACoordinar_ignoraElCupoQueVenga() {
        cursoACoordinar();
        autenticar();
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> inv.getArgument(0));

        GrupoRequestDTO request = requestSinHorario("PUBLICADO");
        request.setCupoMaximo((short) 12);

        service.crear(request);

        verify(grupoRepository).save(argThat(g -> g.getCupoMaximo() == CupoGrupo.CUPO_SIN_LIMITE));
    }

    @Test
    void crear_cursoNormalSinCupo_daElErrorDeSiempre() {
        GrupoRequestDTO request = request(LocalTime.of(8, 0), LocalTime.of(9, 0));
        request.setCupoMaximo(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.crear(request));

        assertEquals("El cupo máximo es obligatorio.", error.getMessage());
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void actualizar_cursoACoordinar_conMuchosInscritos_noAplicaCupoMenorQueInscritos() {
        cursoACoordinar();
        Grupo grupo = grupoExistente();
        autenticar();
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(matriculaDetalleRepository.contarCuposOcupados(anyCollection(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 1500L}));

        GrupoRequestDTO request = requestSinHorario("PUBLICADO");
        request.setCupoMaximo(null);

        var respuesta = service.actualizar(20L, request);

        assertEquals(CupoGrupo.CUPO_SIN_LIMITE, grupo.getCupoMaximo());
        assertTrue(respuesta.getSinCupo());
        assertNull(respuesta.getCupoMaximo());
        assertEquals(1500L, respuesta.getInscritos());
    }

    @Test
    void actualizar_cursoNormalSinCupo_daElErrorDeSiempre() {
        grupoExistente();
        GrupoRequestDTO request = request(LocalTime.of(9, 0), LocalTime.of(10, 0));
        request.setCupoMaximo(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.actualizar(20L, request));

        assertEquals("El cupo máximo es obligatorio.", error.getMessage());
    }

    @Test
    void crear_cursoACoordinar_conProfesorYSinHorario_noDaFalsoChoque() {
        cursoACoordinar();
        autenticar();
        Profesor profesor = new Profesor();
        profesor.setId(9L);
        profesor.setActivo(true);
        when(profesorRepository.findByIdParaAsignacion(9L)).thenReturn(Optional.of(profesor));
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> inv.getArgument(0));

        GrupoRequestDTO request = requestSinHorario("PUBLICADO");
        request.setIdProfesor(9L);

        var respuesta = service.crear(request);

        assertEquals(9L, respuesta.getIdProfesor());
        verify(grupoRepository, never()).findGruposProfesorEnPeriodo(any(), any(), any(), any());
    }

    @Test
    void crear_cursoACoordinar_conHorario_sigueValidandoLasHoras() {
        cursoACoordinar();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.crear(request(LocalTime.of(5, 0), LocalTime.of(6, 0))));

        assertTrue(error.getMessage().contains("7:00 a. m."));
    }

    @Test
    void actualizar_cursoACoordinar_publicaSinHorarioNiProfesor() {
        cursoACoordinar();
        Grupo grupo = grupoExistente();
        autenticar();
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = service.actualizar(20L, requestSinHorario("PUBLICADO"));

        assertEquals("PUBLICADO", grupo.getEstado());
        assertNull(grupo.getIdProfesor());
        assertTrue(respuesta.isACoordinar());
        verify(horarioRepository).eliminarPorGrupo(20L);
        verify(horarioRepository).saveAll(argThat(lista -> lista instanceof List<?> l && l.isEmpty()));
    }

    @Test
    void actualizar_cursoNormal_publicadoSinProfesor_daError() {
        grupoExistente();
        GrupoRequestDTO request = request(LocalTime.of(9, 0), LocalTime.of(10, 0));
        request.setEstado("PUBLICADO");

        BusinessException error = assertThrows(BusinessException.class, () -> service.actualizar(20L, request));

        assertTrue(error.getMessage().contains("profesor activo"));
    }

    @Test
    void actualizar_cursoNormal_sinHorario_daElErrorDeSiempre() {
        grupoExistente();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.actualizar(20L, requestSinHorario("PLANIFICADO")));

        assertEquals("Agregue al menos un horario para el grupo.", error.getMessage());
    }

    @org.junit.jupiter.api.AfterEach
    void limpiarSeguridad() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
