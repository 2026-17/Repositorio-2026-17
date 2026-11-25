package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.AutorizacionesMatricula;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.EstadoMatricula;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.Matricula;
import cr.acusab.una.sigac.data.entity.MatriculaDetalle;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.CursosACoordinarService;
import cr.acusab.una.sigac.presentation.dto.request.CoordinacionDetalleRequest;
import cr.acusab.una.sigac.presentation.dto.response.CursoACoordinarResponse;
import cr.acusab.una.sigac.presentation.dto.response.GrupoGestionResponse;
import cr.acusab.una.sigac.presentation.dto.response.OfertaMatriculaResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Cursos a coordinar en Dirección: la Dirección Académica asigna (o no) un
 * profesor a cada estudiante y anota cómo cumplirá el curso.
 */
class CursosACoordinarServiceTest {

    private GrupoRepository grupoRepository;
    private CursoRepository cursoRepository;
    private PeriodoAcademicoRepository periodoRepository;
    private MatriculaDetalleRepository detalleRepository;
    private ProfesorRepository profesorRepository;
    private EstudianteInstrumentoRepository estudianteInstrumentoRepository;

    private CursosACoordinarService service;

    private Curso camara;
    private Curso solfeo;
    private Grupo grupoCamara;
    private Grupo grupoSolfeo;

    @BeforeEach
    void setUp() {
        grupoRepository = mock(GrupoRepository.class);
        cursoRepository = mock(CursoRepository.class);
        periodoRepository = mock(PeriodoAcademicoRepository.class);
        detalleRepository = mock(MatriculaDetalleRepository.class);
        profesorRepository = mock(ProfesorRepository.class);
        estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);

        service = new CursosACoordinarService(grupoRepository, cursoRepository, periodoRepository,
                detalleRepository, profesorRepository, estudianteInstrumentoRepository);

        camara = curso(103L, "MC-III", "Música de Cámara III", true);
        solfeo = curso(101L, "SOL-I", "Solfeo I", false);
        when(cursoRepository.findById(103L)).thenReturn(Optional.of(camara));
        when(cursoRepository.findById(101L)).thenReturn(Optional.of(solfeo));
        when(cursoRepository.findAllById(anyCollection())).thenReturn(List.of(camara, solfeo));

        grupoCamara = grupo(30L, 103L, "MC-III-1");
        grupoSolfeo = grupo(10L, 101L, "SOL-I-A");
        when(grupoRepository.findById(30L)).thenReturn(Optional.of(grupoCamara));
        when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupoSolfeo));

        when(detalleRepository.save(any(MatriculaDetalle.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /* =================================================================
     * PATCH /cursos-a-coordinar/detalles/{id}
     * ================================================================= */

    @Test
    void actualizarDetalle_asignaProfesorYNotas() {
        MatriculaDetalle detalle = detalle(500L, 30L, "REGISTRADA");
        when(detalleRepository.findById(500L)).thenReturn(Optional.of(detalle));
        Profesor laura = profesor(9L, "Laura", "Mora", true);
        when(profesorRepository.findById(9L)).thenReturn(Optional.of(laura));
        when(profesorRepository.findAllById(anyCollection())).thenReturn(List.of(laura));

        CursoACoordinarResponse.Estudiante fila = service.actualizarDetalle(500L,
                new CoordinacionDetalleRequest(9L, "  En Orquesta · Acompaña a violín  "));

        assertEquals(9L, detalle.getIdProfesorAsignado());
        assertEquals("En Orquesta · Acompaña a violín", detalle.getNotasCoordinacion());
        assertEquals(9L, fila.idProfesorAsignado());
        assertEquals("Laura Mora", fila.nombreProfesorAsignado());
        assertEquals("Ana Prueba", fila.nombreCompleto());
        verify(detalleRepository).save(detalle);
    }

    @Test
    void actualizarDetalle_conNull_dejaSinAsignarYSinNotas() {
        MatriculaDetalle detalle = detalle(500L, 30L, "REGISTRADA");
        detalle.setIdProfesorAsignado(9L);
        detalle.setNotasCoordinacion("Algo");
        when(detalleRepository.findById(500L)).thenReturn(Optional.of(detalle));

        CursoACoordinarResponse.Estudiante fila =
                service.actualizarDetalle(500L, new CoordinacionDetalleRequest(null, "   "));

        assertNull(detalle.getIdProfesorAsignado());
        assertNull(detalle.getNotasCoordinacion());
        assertNull(fila.nombreProfesorAsignado());
    }

    @Test
    void actualizarDetalle_deUnCursoNormal_daError() {
        MatriculaDetalle detalle = detalle(501L, 10L, "REGISTRADA");
        when(detalleRepository.findById(501L)).thenReturn(Optional.of(detalle));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.actualizarDetalle(501L, new CoordinacionDetalleRequest(9L, null)));

        assertTrue(error.getMessage().contains("no se coordina en Dirección"));
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void actualizarDetalle_retirado_daError() {
        MatriculaDetalle detalle = detalle(500L, 30L, "REGISTRADA");
        detalle.setRetirado(true);
        when(detalleRepository.findById(500L)).thenReturn(Optional.of(detalle));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.actualizarDetalle(500L, new CoordinacionDetalleRequest(9L, null)));

        assertTrue(error.getMessage().contains("se retiró"));
    }

    @Test
    void actualizarDetalle_profesorInactivo_daError() {
        MatriculaDetalle detalle = detalle(500L, 30L, "REGISTRADA");
        when(detalleRepository.findById(500L)).thenReturn(Optional.of(detalle));
        when(profesorRepository.findById(9L)).thenReturn(Optional.of(profesor(9L, "Laura", "Mora", false)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.actualizarDetalle(500L, new CoordinacionDetalleRequest(9L, null)));

        assertTrue(error.getMessage().contains("inactivo"));
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void actualizarDetalle_profesorQueNoExiste_daError() {
        MatriculaDetalle detalle = detalle(500L, 30L, "REGISTRADA");
        when(detalleRepository.findById(500L)).thenReturn(Optional.of(detalle));
        when(profesorRepository.findById(77L)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.actualizarDetalle(500L, new CoordinacionDetalleRequest(77L, null)));

        assertTrue(error.getMessage().contains("no existe"));
    }

    /* =================================================================
     * GET /cursos-a-coordinar
     * ================================================================= */

    @Test
    void listar_sinPeriodo_usaElDeMatriculaYDevuelveLosEstudiantes() {
        PeriodoAcademico matricula = periodo(5L, "MATRICULA");
        PeriodoAcademico enCurso = periodo(4L, "EN_CURSO");
        when(periodoRepository.findByEstadoInOrderByFechaInicioDesc(anyCollection()))
                .thenReturn(List.of(enCurso, matricula));
        when(grupoRepository.findGruposACoordinar(5L)).thenReturn(List.of(grupoCamara));

        MatriculaDetalle detalle = detalle(500L, 30L, "REGISTRADA");
        when(detalleRepository.findDetallesActivosDeGrupos(anyCollection(), eq("REGISTRADA")))
                .thenReturn(List.of(detalle));

        Instrumento violin = new Instrumento();
        violin.setNombre("Violín");
        EstudiantesInstrumento asignacion = new EstudiantesInstrumento();
        asignacion.setInstrumento(violin);
        asignacion.setEsPrincipal(true);
        asignacion.setFechaInicio(LocalDate.of(2026, 1, 1));
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(1L))
                .thenReturn(List.of(asignacion));

        List<CursoACoordinarResponse> grupos = service.listar(null);

        assertEquals(1, grupos.size());
        CursoACoordinarResponse g = grupos.get(0);
        assertEquals("MC-III-1", g.codigo());
        assertEquals("Música de Cámara III", g.nombreCurso());
        // Sin cupo: no se manda el valor guardado; los matriculados sí se cuentan.
        assertNull(g.cupoMaximo());
        assertTrue(g.sinCupo());
        assertEquals(1, g.matriculados());
        assertEquals("Violín", g.estudiantes().get(0).instrumento());
        assertEquals("111111111", g.estudiantes().get(0).cedula());
        assertNull(g.estudiantes().get(0).idProfesorAsignado());
    }

    @Test
    void listar_sinPeriodoVigente_devuelveVacio() {
        when(periodoRepository.findByEstadoInOrderByFechaInicioDesc(anyCollection())).thenReturn(List.of());

        assertTrue(service.listar(null).isEmpty());
        verify(grupoRepository, never()).findGruposACoordinar(any());
    }

    /* =================================================================
     * JSON: el campo se llama "aCoordinar" (no "acoordinar")
     * ================================================================= */

    @Test
    void json_usaElNombreACoordinar() {
        JsonMapper mapper = JsonMapper.builder().build();

        OfertaMatriculaResponseDTO.GrupoDTO dto = new OfertaMatriculaResponseDTO.GrupoDTO();
        dto.setACoordinar(true);
        dto.setProfesorAsignado("Laura Mora");
        String json = mapper.writeValueAsString(dto);
        assertTrue(json.contains("\"aCoordinar\":true"), json);
        assertFalse(json.contains("\"acoordinar\""), json);
        assertTrue(json.contains("\"profesorAsignado\":\"Laura Mora\""), json);

        GrupoGestionResponse gestion = new GrupoGestionResponse();
        gestion.setACoordinar(true);
        String jsonGestion = mapper.writeValueAsString(gestion);
        assertTrue(jsonGestion.contains("\"aCoordinar\":true"), jsonGestion);
        assertFalse(jsonGestion.contains("\"acoordinar\""), jsonGestion);

        cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO request = mapper.readValue(
                "{\"aCoordinar\":true}", cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO.class);
        assertEquals(Boolean.TRUE, request.getACoordinar());
    }

    /* =================================================================
     * Apoyo
     * ================================================================= */

    private static Curso curso(Long id, String codigo, String nombre, boolean aCoordinar) {
        Curso c = new Curso();
        c.setId(id);
        c.setCodigo(codigo);
        c.setNombre(nombre);
        c.setActivo(true);
        c.setACoordinar(aCoordinar);
        return c;
    }

    private static Grupo grupo(Long id, Long idCurso, String codigo) {
        Grupo g = new Grupo();
        g.setId(id);
        g.setIdCurso(idCurso);
        g.setIdPeriodo(5L);
        g.setCodigo(codigo);
        g.setCupoMaximo((short) 40);
        g.setEstado("PUBLICADO");
        return g;
    }

    private static PeriodoAcademico periodo(Long id, String estado) {
        PeriodoAcademico p = new PeriodoAcademico();
        p.setId(id);
        p.setEstado(estado);
        return p;
    }

    private static Profesor profesor(Long id, String nombres, String apellido, boolean activo) {
        Profesor p = new Profesor();
        p.setId(id);
        p.setNombres(nombres);
        p.setPrimerApellido(apellido);
        p.setActivo(activo);
        return p;
    }

    private static MatriculaDetalle detalle(Long id, Long idGrupo, String estadoMatricula) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(1L);
        estudiante.setNumeroIdentificacion("111111111");
        estudiante.setNombres("Ana");
        estudiante.setPrimerApellido("Prueba");

        AutorizacionesMatricula autorizacion = new AutorizacionesMatricula();
        autorizacion.setIdEstudiante(estudiante);

        EstadoMatricula estado = new EstadoMatricula();
        estado.setCodigo(estadoMatricula);

        Matricula matricula = new Matricula();
        matricula.setId(700L);
        matricula.setAutorizacion(autorizacion);
        matricula.setEstado(estado);

        MatriculaDetalle d = new MatriculaDetalle();
        d.setId(id);
        d.setIdGrupo(idGrupo);
        d.setMatricula(matricula);
        return d;
    }
}
