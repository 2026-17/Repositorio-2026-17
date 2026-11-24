package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.AgrupacionService;
import cr.acusab.una.sigac.logic.service.AvanceAgrupacionService;
import cr.acusab.una.sigac.logic.service.ValidacionHorarioProfesorService;
import cr.acusab.una.sigac.presentation.dto.request.GrupoAgrupacionRequest;
import cr.acusab.una.sigac.presentation.dto.request.HorarioGrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteAgrupacionResponse;
import cr.acusab.una.sigac.presentation.dto.response.GrupoAgrupacionResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Grupos de agrupación: profesores (un responsable), publicación, código
 * AGR-<código>-<n>, avisos de choque y la vista interna con el nivel.
 *
 * Agrupación 1: Orquesta (ORQ). Período 5 abierto. Profesores 12 (activo),
 * 13 (activo) y 14 (inactivo).
 */
class AgrupacionServiceTest {

    private GrupoRepository grupoRepository;
    private GrupoProfesorRepository grupoProfesorRepository;
    private ValidacionHorarioProfesorService validacionHorario;
    private MatriculaDetalleRepository detalleRepository;
    private Authentication authentication;
    private AgrupacionService service;

    private final Map<Long, Grupo> guardados = new HashMap<>();

    @BeforeEach
    void setUp() {
        AgrupacionRepository agrupacionRepository = mock(AgrupacionRepository.class);
        AgrupacionInstrumentoRepository agrupacionInstrumentoRepository = mock(AgrupacionInstrumentoRepository.class);
        HorarioGrupoRepository horarioRepository = mock(HorarioGrupoRepository.class);
        PeriodoAcademicoRepository periodoRepository = mock(PeriodoAcademicoRepository.class);
        ProfesorRepository profesorRepository = mock(ProfesorRepository.class);
        CursoRepository cursoRepository = mock(CursoRepository.class);
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        grupoRepository = mock(GrupoRepository.class);
        grupoProfesorRepository = mock(GrupoProfesorRepository.class);
        validacionHorario = mock(ValidacionHorarioProfesorService.class);
        detalleRepository = mock(MatriculaDetalleRepository.class);

        service = new AgrupacionService(
                agrupacionRepository, agrupacionInstrumentoRepository, grupoProfesorRepository, grupoRepository,
                horarioRepository, periodoRepository, mock(InstrumentoRepository.class), profesorRepository,
                cursoRepository, estudianteRepository, detalleRepository, validacionHorario,
                mock(AvanceAgrupacionService.class), usuarioRepository);

        // Sesión del director académico.
        authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("director");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_DIRECTOR_ACADEMICO"))).when(authentication).getAuthorities();
        SecurityContext contexto = mock(SecurityContext.class);
        when(contexto.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(contexto);

        Usuario usuario = new Usuario();
        usuario.setId(900L);
        when(usuarioRepository.findByCedula("director")).thenReturn(Optional.of(usuario));

        Agrupacion orquesta = new Agrupacion();
        orquesta.setId(1L);
        orquesta.setCodigo("ORQ");
        orquesta.setNombre("Orquesta");
        orquesta.setTipo(Agrupacion.TIPO_INSTRUMENTAL);
        orquesta.setActiva(true);
        when(agrupacionRepository.findById(1L)).thenReturn(Optional.of(orquesta));
        when(agrupacionRepository.findAllById(anyCollection())).thenReturn(List.of(orquesta));

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(5L);
        periodo.setNombre("I ciclo 2027");
        periodo.setEstado("PLANIFICADO");
        periodo.setFechaInicio(LocalDate.of(2027, 2, 1));
        periodo.setFechaFin(LocalDate.of(2027, 6, 30));
        when(periodoRepository.findById(5L)).thenReturn(Optional.of(periodo));
        when(periodoRepository.findAllById(anyCollection())).thenReturn(List.of(periodo));

        List<Profesor> profesores = List.of(profesor(12L, true), profesor(13L, true), profesor(14L, false));
        when(profesorRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            Set<Object> ids = new HashSet<>();
            ((Iterable<?>) inv.getArgument(0)).forEach(ids::add);
            return profesores.stream().filter(p -> ids.contains(p.getId())).toList();
        });
        when(profesorRepository.findByIdUsuario_Cedula("profe13")).thenReturn(Optional.of(profesores.get(1)));

        when(grupoRepository.existsByIdPeriodoAndCodigo(5L, "AGR-ORQ-1")).thenReturn(true);
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> {
            Grupo g = inv.getArgument(0);
            if (g.getId() == null) g.setId(4000L);
            guardados.put(g.getId(), g);
            return g;
        });
        when(grupoRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(guardados.get(inv.<Long>getArgument(0))));
        when(validacionHorario.buscarConflicto(anyLong(), anyLong(), any(), any(), any(), anyList()))
                .thenReturn(Optional.empty());

        // Niveles para la vista interna.
        Curso ciV = new Curso();
        ciV.setId(505L);
        ciV.setCodigo("CI-V");
        ciV.setNombre("Conjunto Instrumental V");
        Curso coroI = new Curso();
        coroI.setId(601L);
        coroI.setCodigo("CORO-1");
        coroI.setNombre("Coro I");
        when(cursoRepository.findAllById(anyCollection())).thenReturn(List.of(ciV, coroI));

        Estudiante ana = new Estudiante();
        ana.setId(1L);
        ana.setNombres("Estudiante");
        ana.setPrimerApellido("Uno");
        Estudiante beto = new Estudiante();
        beto.setId(2L);
        beto.setNombres("Estudiante");
        beto.setPrimerApellido("Dos");
        when(estudianteRepository.findAllById(anyCollection())).thenReturn(List.of(ana, beto));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noSePublicaSinResponsable() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.crearGrupo(request("PUBLICADO")));

        assertTrue(ex.getMessage().contains("responsable activo"));
        verify(grupoRepository, never()).save(any());
    }

    @Test
    void noSePublicaConResponsableInactivo() {
        GrupoAgrupacionRequest request = request("PUBLICADO", profesorRequest(14L, true));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.crearGrupo(request));
        assertTrue(ex.getMessage().contains("responsable activo"));
    }

    @Test
    void sinProfesores_sePuedeGuardarComoPlanificada() {
        GrupoAgrupacionResponse grupo = service.crearGrupo(request("PLANIFICADO"));

        assertEquals("PLANIFICADO", grupo.estado());
        assertNull(guardados.get(4000L).getIdProfesor());
    }

    @Test
    void soloUnResponsable() {
        GrupoAgrupacionRequest request = request("PLANIFICADO", profesorRequest(12L, true), profesorRequest(13L, true));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.crearGrupo(request));
        assertEquals("Solo puede haber un profesor responsable.", ex.getMessage());
    }

    @Test
    void conProfesoresSinResponsable_seRechaza() {
        GrupoAgrupacionRequest request = request("PLANIFICADO", profesorRequest(12L, false));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.crearGrupo(request));
        assertTrue(ex.getMessage().contains("responsable"));
    }

    @Test
    void publicar_guardaIdProfesorIgualAlResponsableYTodosLosProfesores() {
        GrupoAgrupacionRequest request = request("PUBLICADO", profesorRequest(12L, false), profesorRequest(13L, true));

        service.crearGrupo(request);

        Grupo grupo = guardados.get(4000L);
        assertEquals(Grupo.TIPO_AGRUPACION, grupo.getTipoGrupo());
        assertEquals(13L, grupo.getIdProfesor(), "grupos.id_profesor = responsable");
        assertNull(grupo.getIdCurso());
        assertNull(grupo.getIdInstrumento());
        assertEquals("AGR-ORQ-2", grupo.getCodigo(), "AGR-ORQ-1 ya existe en el período");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GrupoProfesor>> captor = ArgumentCaptor.forClass(List.class);
        verify(grupoProfesorRepository).saveAll(captor.capture());
        List<GrupoProfesor> filas = captor.getValue();
        assertEquals(2, filas.size());
        assertEquals(1, filas.stream().filter(GrupoProfesor::getEsResponsable).count());
        assertEquals(13L, filas.stream().filter(GrupoProfesor::getEsResponsable).findFirst().orElseThrow().getIdProfesor());
    }

    @Test
    void choqueConOtroGrupoDelProfesor_seAvisaPeroNoBloquea() {
        Grupo espacio = new Grupo();
        espacio.setId(77L);
        espacio.setCodigo("ESP-VIO-12-1");
        espacio.setTipoGrupo(Grupo.TIPO_ESPACIO_INSTRUMENTO);
        HorarioGrupo ocupado = new HorarioGrupo();
        ocupado.setIdGrupo(77L);
        ocupado.setDiaSemana((short) 1);
        ocupado.setHoraInicio(LocalTime.of(14, 30));
        ocupado.setHoraFin(LocalTime.of(15, 10));
        when(validacionHorario.buscarConflicto(eq(12L), anyLong(), any(), any(), any(), anyList()))
                .thenReturn(Optional.of(new ValidacionHorarioProfesorService.Conflicto(null, espacio, ocupado)));

        GrupoAgrupacionResponse grupo = service.crearGrupo(request("PUBLICADO", profesorRequest(12L, true)));

        assertEquals("PUBLICADO", grupo.estado());
        assertEquals(1, grupo.choques().size());
        GrupoAgrupacionResponse.Choque choque = grupo.choques().get(0);
        assertEquals(0, choque.indiceBloque());
        assertEquals("ESP-VIO-12-1", choque.codigoGrupo());
        assertTrue(choque.mensaje().contains("el espacio ESP-VIO-12-1"));
    }

    @Test
    void vistaInterna_muestraLaAgrupacionConSuNivel() {
        Grupo grupo = grupoGuardado();
        when(detalleRepository.findOcupantesDeGrupos(List.of(grupo.getId()), "REGISTRADA"))
                .thenReturn(List.of(new Object[]{grupo.getId(), 505L, 1L}, new Object[]{grupo.getId(), 601L, 2L}));

        List<EstudianteAgrupacionResponse> estudiantes = service.estudiantesDelGrupo(grupo.getId());

        assertEquals(2, estudiantes.size());
        EstudianteAgrupacionResponse dos = estudiantes.get(0);
        EstudianteAgrupacionResponse uno = estudiantes.get(1);
        assertEquals("Orquesta · Conjunto Instrumental V", uno.etiqueta());
        assertFalse(uno.calificaComoCoro());
        assertEquals("Orquesta · Coro I (se califica como coro)", dos.etiqueta());
        assertTrue(dos.calificaComoCoro(), "Instrumental calificada como coro (D4)");
    }

    @Test
    void vistaDelProfesor_soloSusGruposYConElNivel() {
        Grupo grupo = grupoGuardado();
        when(detalleRepository.findOcupantesDeGrupos(List.of(grupo.getId()), "REGISTRADA"))
                .thenReturn(List.<Object[]>of(new Object[]{grupo.getId(), 505L, 1L}));
        when(authentication.getName()).thenReturn("profe13");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_PROFESOR"))).when(authentication).getAuthorities();

        // 13 no es profesor del grupo.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.estudiantesDelGrupo(grupo.getId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());

        // Ahora sí lo es.
        when(grupoProfesorRepository.existsByIdGrupoAndIdProfesor(grupo.getId(), 13L)).thenReturn(true);
        assertEquals("Orquesta · Conjunto Instrumental V",
                service.estudiantesDelGrupo(grupo.getId()).get(0).etiqueta());
    }

    @Test
    void profesorNoResponsable_veElGrupoYSuListaPeroNoPuedeCalificar() {
        Grupo grupo = grupoGuardado(); // responsable: 12
        when(detalleRepository.findOcupantesDeGrupos(List.of(grupo.getId()), "REGISTRADA"))
                .thenReturn(List.<Object[]>of(new Object[]{grupo.getId(), 505L, 1L}));
        when(grupoProfesorRepository.existsByIdGrupoAndIdProfesor(grupo.getId(), 13L)).thenReturn(true);
        when(authentication.getName()).thenReturn("profe13");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_PROFESOR"))).when(authentication).getAuthorities();

        // Ve la lista con el nivel.
        assertEquals(1, service.estudiantesDelGrupo(grupo.getId()).size());

        // Pero no califica: solo el responsable.
        assertFalse(service.puedeCalificar(grupo.getId()));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validarPuedeCalificar(grupo.getId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());

        // El responsable sí.
        grupo.setIdProfesor(13L);
        assertTrue(service.puedeCalificar(grupo.getId()));
    }

    /* ------------------------------------------------------------ */

    private Grupo grupoGuardado() {
        Grupo grupo = new Grupo();
        grupo.setId(4100L);
        grupo.setTipoGrupo(Grupo.TIPO_AGRUPACION);
        grupo.setIdAgrupacion(1L);
        grupo.setIdPeriodo(5L);
        grupo.setIdProfesor(12L);
        grupo.setCodigo("AGR-ORQ-1");
        grupo.setCupoMaximo((short) 30);
        grupo.setEstado("PUBLICADO");
        guardados.put(grupo.getId(), grupo);
        return grupo;
    }

    private static GrupoAgrupacionRequest request(String estado, GrupoAgrupacionRequest.ProfesorGrupo... profesores) {
        GrupoAgrupacionRequest request = new GrupoAgrupacionRequest();
        request.setIdAgrupacion(1L);
        request.setIdPeriodo(5L);
        request.setCupoMaximo(30);
        request.setEstado(estado);
        HorarioGrupoRequestDTO bloque = new HorarioGrupoRequestDTO();
        bloque.setDiaSemana((short) 1);
        bloque.setHoraInicio(LocalTime.of(14, 0));
        bloque.setHoraFin(LocalTime.of(16, 0));
        request.setBloques(new ArrayList<>(List.of(bloque)));
        request.setProfesores(new ArrayList<>(List.of(profesores)));
        return request;
    }

    private static GrupoAgrupacionRequest.ProfesorGrupo profesorRequest(Long id, boolean responsable) {
        GrupoAgrupacionRequest.ProfesorGrupo p = new GrupoAgrupacionRequest.ProfesorGrupo();
        p.setIdProfesor(id);
        p.setResponsable(responsable);
        return p;
    }

    private static Profesor profesor(Long id, boolean activo) {
        Profesor p = new Profesor();
        p.setId(id);
        p.setNombres("Profesor");
        p.setPrimerApellido(String.valueOf(id));
        p.setActivo(activo);
        return p;
    }
}
