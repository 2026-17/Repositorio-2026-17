package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.EspaciosInvalidosException;
import cr.acusab.una.sigac.logic.service.EspacioInstrumentoService;
import cr.acusab.una.sigac.logic.service.ValidacionHorarioProfesorService;
import cr.acusab.una.sigac.presentation.dto.request.EspaciosInstrumentoRequest;
import cr.acusab.una.sigac.presentation.dto.request.HorarioGrupoRequestDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Espacios de instrumento - Crear en lote.
 *
 * Escenario: período 4, instrumento Piano (5), profesora 12 con Piano
 * como especialidad.
 */
class EspacioInstrumentoServiceTest {

    private GrupoRepository grupoRepository;
    private HorarioGrupoRepository horarioRepository;
    private ProfesorRepository profesorRepository;

    private EspacioInstrumentoService service;

    private final List<Grupo> gruposDelProfesor = new ArrayList<>();
    private final List<HorarioGrupo> horariosDelProfesor = new ArrayList<>();
    private Profesor profesora;
    private long siguienteId = 100;

    @BeforeEach
    void setUp() {
        grupoRepository = mock(GrupoRepository.class);
        horarioRepository = mock(HorarioGrupoRepository.class);
        profesorRepository = mock(ProfesorRepository.class);
        PeriodoAcademicoRepository periodoRepository = mock(PeriodoAcademicoRepository.class);
        InstrumentoRepository instrumentoRepository = mock(InstrumentoRepository.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);

        service = new EspacioInstrumentoService(
                grupoRepository,
                horarioRepository,
                periodoRepository,
                instrumentoRepository,
                profesorRepository,
                mock(CursoRepository.class),
                mock(EstudianteRepository.class),
                mock(MatriculaDetalleRepository.class),
                new ValidacionHorarioProfesorService(grupoRepository, horarioRepository),
                usuarioRepository
        );

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(4L);
        periodo.setNombre("I ciclo 2027");
        periodo.setEstado("PLANIFICADO");
        periodo.setFechaInicio(LocalDate.of(2027, 2, 1));
        periodo.setFechaFin(LocalDate.of(2027, 6, 30));
        when(periodoRepository.findById(4L)).thenReturn(Optional.of(periodo));

        Instrumento piano = new Instrumento();
        piano.setId(5);
        piano.setCodigo("PIANO");
        piano.setNombre("Piano");
        piano.setActivo(true);
        when(instrumentoRepository.findByIdAndActivoTrue(5)).thenReturn(Optional.of(piano));

        profesora = new Profesor();
        profesora.setId(12L);
        profesora.setNombres("Laura");
        profesora.setPrimerApellido("Méndez");
        profesora.setActivo(true);
        profesora.setEspecialidad("Piano, Solfeo");
        when(profesorRepository.findById(12L)).thenReturn(Optional.of(profesora));

        when(grupoRepository.findGruposProfesorEnPeriodo(eq(12L), eq(4L), any(), any()))
                .thenAnswer(inv -> new ArrayList<>(gruposDelProfesor));
        when(horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(anyCollection()))
                .thenAnswer(inv -> new ArrayList<>(horariosDelProfesor));
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> {
            Grupo g = inv.getArgument(0);
            g.setId(siguienteId++);
            return g;
        });

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
    void crear_loteValido_creaUnEspacioPorBloqueConCupoUnoYPublicado() {

        EspaciosInstrumentoRequest request = request(
                bloque(1, "07:10", "07:50", null),
                bloque(1, "07:50", "08:30", null),
                bloque(2, "13:00", "13:40", "Aula 5"));

        service.crear(request);

        ArgumentCaptor<Grupo> grupos = ArgumentCaptor.forClass(Grupo.class);
        verify(grupoRepository, times(3)).save(grupos.capture());

        for (Grupo espacio : grupos.getAllValues()) {
            assertEquals(Grupo.TIPO_ESPACIO_INSTRUMENTO, espacio.getTipoGrupo());
            assertNull(espacio.getIdCurso());
            assertEquals(5, espacio.getIdInstrumento());
            assertEquals(12L, espacio.getIdProfesor());
            assertEquals((short) 1, espacio.getCupoMaximo());
            assertEquals("PUBLICADO", espacio.getEstado());
        }

        assertEquals("ESP-PIA-12-1", grupos.getAllValues().get(0).getCodigo());
        assertEquals("ESP-PIA-12-3", grupos.getAllValues().get(2).getCodigo());
        // Aula por defecto, salvo el bloque que trae la suya.
        assertEquals("Aula 3", grupos.getAllValues().get(0).getAula());
        assertEquals("Aula 5", grupos.getAllValues().get(2).getAula());

        verify(horarioRepository, times(3)).save(any(HorarioGrupo.class));
    }

    @Test
    void crear_choqueConOtroGrupoDelProfesor_noCreaNinguno() {

        Grupo solfeo = new Grupo();
        solfeo.setId(2L);
        solfeo.setCodigo("SOL-I-A");
        gruposDelProfesor.add(solfeo);

        HorarioGrupo ocupado = new HorarioGrupo();
        ocupado.setIdGrupo(2L);
        ocupado.setDiaSemana((short) 4);
        ocupado.setHoraInicio(LocalTime.of(16, 0));
        ocupado.setHoraFin(LocalTime.of(16, 40));
        horariosDelProfesor.add(ocupado);

        EspaciosInstrumentoRequest request = request(
                bloque(1, "07:10", "07:50", null),
                bloque(4, "16:20", "17:00", null));

        EspaciosInvalidosException error = assertThrows(EspaciosInvalidosException.class,
                () -> service.crear(request));

        assertEquals(1, error.getErrores().size());
        assertEquals(1, error.getErrores().get(0).indice());
        assertTrue(error.getErrores().get(0).mensaje().contains("SOL-I-A"));
        assertTrue(error.getErrores().get(0).mensaje().contains("4:00 p. m."));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void crear_choqueDentroDelLote_informaElBloqueYNoCreaNinguno() {

        EspaciosInstrumentoRequest request = request(
                bloque(1, "07:10", "07:50", null),
                bloque(1, "07:30", "08:10", null));

        EspaciosInvalidosException error = assertThrows(EspaciosInvalidosException.class,
                () -> service.crear(request));

        assertEquals(1, error.getErrores().get(0).indice());
        assertTrue(error.getErrores().get(0).mensaje().contains("espacio 1 de esta misma lista"));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void crear_profesorSinLaEspecialidad_seRechaza() {

        profesora.setEspecialidad("Guitarra");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.crear(request(bloque(1, "07:10", "07:50", null))));

        assertTrue(error.getMessage().contains("no tiene Piano como especialidad"));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    @Test
    void crear_cupoDistintoDeUno_seRechaza() {

        EspaciosInstrumentoRequest request = request(bloque(1, "07:10", "07:50", null));
        request.setCupoMaximo(3);

        BusinessException error = assertThrows(BusinessException.class, () -> service.crear(request));

        assertTrue(error.getMessage().contains("siempre es 1"));
        verify(grupoRepository, never()).save(any(Grupo.class));
    }

    private EspaciosInstrumentoRequest request(HorarioGrupoRequestDTO... bloques) {
        EspaciosInstrumentoRequest request = new EspaciosInstrumentoRequest();
        request.setIdPeriodo(4L);
        request.setIdInstrumento(5);
        request.setIdProfesor(12L);
        request.setAula("Aula 3");
        request.setBloques(List.of(bloques));
        return request;
    }

    private HorarioGrupoRequestDTO bloque(int dia, String inicio, String fin, String aula) {
        HorarioGrupoRequestDTO bloque = new HorarioGrupoRequestDTO();
        bloque.setDiaSemana((short) dia);
        bloque.setHoraInicio(LocalTime.parse(inicio));
        bloque.setHoraFin(LocalTime.parse(fin));
        bloque.setAula(aula);
        return bloque;
    }
}
