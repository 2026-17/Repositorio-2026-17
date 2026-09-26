package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Programa;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.ProgramaRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.ConsultaCursosService;
import cr.acusab.una.sigac.presentation.dto.response.CursoDisponibleResponseDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/*
 * HU-10 - Consultar cursos disponibles de un período académico.
 *
 * Pruebas con JUnit puro: los repositorios se sustituyen por dobles de
 * prueba mínimos (repositorioFalso), sin librerías de mocking. Un
 * repositorio en null significa que la prueba espera que ese método
 * nunca se invoque.
 */
class ConsultaCursosServiceTest {

    @Test
    void noDebeConsultarSiElPeriodoNoExiste() {

        PeriodoAcademicoRepository periodos = repositorioFalso(
                PeriodoAcademicoRepository.class,
                Map.of("findById", Optional.empty())
        );

        ConsultaCursosService service = new ConsultaCursosService(
                periodos, null, null, null, null, null, null, null, null
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.consultarDisponibles(2L, 1L, null)
        );

        assertEquals("El período académico indicado no existe.", exception.getMessage());
    }

    @Test
    void noDebeConsultarSiElProgramaNoExiste() {

        PeriodoAcademicoRepository periodos = repositorioFalso(
                PeriodoAcademicoRepository.class,
                Map.of("findById", Optional.of(new PeriodoAcademico()))
        );

        ProgramaRepository programas = repositorioFalso(
                ProgramaRepository.class,
                Map.of("findById", Optional.empty())
        );

        ConsultaCursosService service = new ConsultaCursosService(
                periodos, programas, null, null, null, null, null, null, null
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.consultarDisponibles(2L, 1L, null)
        );

        assertEquals("El programa académico indicado no existe.", exception.getMessage());
    }

    @Test
    void noDebeConsultarSiElProgramaEstaInactivo() {

        Programa programaInactivo = new Programa();
        programaInactivo.setActivo(false);

        PeriodoAcademicoRepository periodos = repositorioFalso(
                PeriodoAcademicoRepository.class,
                Map.of("findById", Optional.of(new PeriodoAcademico()))
        );

        ProgramaRepository programas = repositorioFalso(
                ProgramaRepository.class,
                Map.of("findById", Optional.of(programaInactivo))
        );

        ConsultaCursosService service = new ConsultaCursosService(
                periodos, programas, null, null, null, null, null, null, null
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.consultarDisponibles(2L, 1L, null)
        );

        assertEquals(
                "El programa académico seleccionado se encuentra inactivo.",
                exception.getMessage()
        );
    }

    @Test
    void debeRetornarListaVaciaSiElProgramaNoTienePlanVigente() {

        PlanEstudioRepository planes = repositorioFalso(
                PlanEstudioRepository.class,
                Map.of("findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc", Optional.empty())
        );

        ConsultaCursosService service = new ConsultaCursosService(
                periodoRepositoryValido(), programaRepositoryValido(), planes,
                null, null, null, null, null, null
        );

        List<CursoDisponibleResponseDTO> resultado =
                service.consultarDisponibles(2L, 1L, null);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void debeRetornarListaVaciaSiNoHayGruposOfertadosEnElPeriodo() {

        MallaCurricularRepository mallas = repositorioFalso(
                MallaCurricularRepository.class,
                Map.of("findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc",
                        List.of(crearMalla(100L, (short) 1)))
        );

        CursoRepository cursos = repositorioFalso(
                CursoRepository.class,
                Map.of("findAllById", List.of(crearCurso(100L, "PIA-101", "Piano Individual I")))
        );

        GrupoRepository grupos = repositorioFalso(
                GrupoRepository.class,
                Map.of("findByIdPeriodoAndEstadoIn", List.of())
        );

        ConsultaCursosService service = new ConsultaCursosService(
                periodoRepositoryValido(), programaRepositoryValido(), planRepositoryValido(),
                mallas, cursos, grupos, null, null, null
        );

        List<CursoDisponibleResponseDTO> resultado =
                service.consultarDisponibles(2L, 1L, null);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void debeListarCursosDisponiblesConHorarioGrupoYProfesor() {

        MallaCurricularRepository mallas = repositorioFalso(
                MallaCurricularRepository.class,
                Map.of("findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc",
                        List.of(crearMalla(100L, (short) 1)))
        );

        CursoRepository cursos = repositorioFalso(
                CursoRepository.class,
                Map.of("findAllById", List.of(crearCurso(100L, "PIA-101", "Piano Individual I")))
        );

        Grupo grupo = new Grupo();
        grupo.setId(300L);
        grupo.setIdCurso(100L);
        grupo.setIdProfesor(9L);
        grupo.setCodigo("PIA-101-A");
        grupo.setModalidad("PRESENCIAL");
        grupo.setAula("A-1");
        grupo.setCupoMaximo((short) 10);

        GrupoRepository grupoRepo = repositorioFalso(
                GrupoRepository.class,
                Map.of("findByIdPeriodoAndEstadoIn", List.of(grupo))
        );

        Profesor profesor = new Profesor();
        profesor.setId(9L);
        profesor.setNombres("Marlene");
        profesor.setPrimerApellido("Solano");

        ProfesorRepository profesores = repositorioFalso(
                ProfesorRepository.class,
                Map.of("findAllById", List.of(profesor))
        );

        HorarioGrupo horario = new HorarioGrupo();
        horario.setIdGrupo(300L);
        horario.setDiaSemana((short) 1);
        horario.setHoraInicio(LocalTime.of(8, 0));
        horario.setHoraFin(LocalTime.of(10, 0));
        horario.setAula("A-1");

        HorarioGrupoRepository horarios = repositorioFalso(
                HorarioGrupoRepository.class,
                Map.of("findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc", List.of(horario))
        );

        // 3 cupos ocupados de un máximo de 10 -> 7 disponibles.
        MatriculaDetalleRepository matriculaDetalles = repositorioFalso(
                MatriculaDetalleRepository.class,
                Map.of("contarCuposOcupados", List.<Object[]>of(new Object[]{300L, 3L}))
        );

        ConsultaCursosService service = new ConsultaCursosService(
                periodoRepositoryValido(), programaRepositoryValido(), planRepositoryValido(),
                mallas, cursos, grupoRepo, profesores, horarios, matriculaDetalles
        );

        List<CursoDisponibleResponseDTO> resultado =
                service.consultarDisponibles(2L, 1L, null);

        assertEquals(1, resultado.size());

        CursoDisponibleResponseDTO dto = resultado.get(0);

        assertEquals(100L, dto.getIdCurso());
        assertEquals("PIA-101", dto.getCodigoCurso());
        assertEquals("Piano Individual I", dto.getNombreCurso());
        assertEquals((short) 1, dto.getNivel());
        assertEquals(300L, dto.getIdGrupo());
        assertEquals("PIA-101-A", dto.getCodigoGrupo());
        assertEquals("Marlene Solano", dto.getProfesor());
        assertEquals(10, dto.getCupoMaximo());
        assertEquals(7, dto.getCupoDisponible());

        assertEquals(1, dto.getHorarios().size());
        assertEquals("08:00", dto.getHorarios().get(0).getHoraInicio());
        assertEquals("10:00", dto.getHorarios().get(0).getHoraFin());
    }

    @Test
    void debeMostrarCupoDisponibleEnCeroSinNegativos() {

        MallaCurricularRepository mallas = repositorioFalso(
                MallaCurricularRepository.class,
                Map.of("findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc",
                        List.of(crearMalla(100L, (short) 1)))
        );

        CursoRepository cursos = repositorioFalso(
                CursoRepository.class,
                Map.of("findAllById", List.of(crearCurso(100L, "PIA-101", "Piano Individual I")))
        );

        Grupo grupo = new Grupo();
        grupo.setId(300L);
        grupo.setIdCurso(100L);
        grupo.setCodigo("PIA-101-A");
        grupo.setCupoMaximo((short) 5);

        GrupoRepository grupoRepo = repositorioFalso(
                GrupoRepository.class,
                Map.of("findByIdPeriodoAndEstadoIn", List.of(grupo))
        );

        HorarioGrupoRepository horarios = repositorioFalso(
                HorarioGrupoRepository.class,
                Map.of("findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc", List.of())
        );

        // Más matriculados que cupos (caso límite): nunca debe quedar negativo.
        MatriculaDetalleRepository matriculaDetalles = repositorioFalso(
                MatriculaDetalleRepository.class,
                Map.of("contarCuposOcupados", List.<Object[]>of(new Object[]{300L, 8L}))
        );

        ConsultaCursosService service = new ConsultaCursosService(
                periodoRepositoryValido(), programaRepositoryValido(), planRepositoryValido(),
                mallas, cursos, grupoRepo, null, horarios, matriculaDetalles
        );

        List<CursoDisponibleResponseDTO> resultado =
                service.consultarDisponibles(2L, 1L, null);

        assertEquals(0, resultado.get(0).getCupoDisponible());
    }

    @Test
    void debeFiltrarPorTextoDeBusquedaEnNombreOCodigo() {

        MallaCurricularRepository mallas = repositorioFalso(
                MallaCurricularRepository.class,
                Map.of("findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc",
                        List.of(crearMalla(100L, (short) 1), crearMalla(200L, (short) 1)))
        );

        CursoRepository cursos = repositorioFalso(
                CursoRepository.class,
                Map.of("findAllById", List.of(
                        crearCurso(100L, "PIA-101", "Piano Individual I"),
                        crearCurso(200L, "TEO-201", "Teoría Musical II")
                ))
        );

        Grupo grupoPiano = new Grupo();
        grupoPiano.setId(300L);
        grupoPiano.setIdCurso(100L);
        grupoPiano.setCodigo("PIA-101-A");
        grupoPiano.setCupoMaximo((short) 10);

        Grupo grupoTeoria = new Grupo();
        grupoTeoria.setId(400L);
        grupoTeoria.setIdCurso(200L);
        grupoTeoria.setCodigo("TEO-201-A");
        grupoTeoria.setCupoMaximo((short) 10);

        GrupoRepository grupoRepo = repositorioFalso(
                GrupoRepository.class,
                Map.of("findByIdPeriodoAndEstadoIn", List.of(grupoPiano, grupoTeoria))
        );

        HorarioGrupoRepository horarios = repositorioFalso(
                HorarioGrupoRepository.class,
                Map.of("findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc", List.of())
        );

        MatriculaDetalleRepository matriculaDetalles = repositorioFalso(
                MatriculaDetalleRepository.class,
                Map.of("contarCuposOcupados", List.of())
        );

        ConsultaCursosService service = new ConsultaCursosService(
                periodoRepositoryValido(), programaRepositoryValido(), planRepositoryValido(),
                mallas, cursos, grupoRepo, null, horarios, matriculaDetalles
        );

        // Solo el curso de Piano debe pasar el filtro de texto.
        List<CursoDisponibleResponseDTO> resultado =
                service.consultarDisponibles(2L, 1L, "piano");

        assertEquals(1, resultado.size());
        assertEquals("PIA-101", resultado.get(0).getCodigoCurso());
    }

    /*
     * ============================================================
     * Métodos auxiliares para las pruebas
     * ============================================================
     */

    private PeriodoAcademicoRepository periodoRepositoryValido() {
        return repositorioFalso(
                PeriodoAcademicoRepository.class,
                Map.of("findById", Optional.of(new PeriodoAcademico()))
        );
    }

    private ProgramaRepository programaRepositoryValido() {
        Programa programa = new Programa();
        programa.setActivo(true);

        return repositorioFalso(
                ProgramaRepository.class,
                Map.of("findById", Optional.of(programa))
        );
    }

    private PlanEstudioRepository planRepositoryValido() {
        PlanEstudio plan = new PlanEstudio();
        plan.setId(50L);

        return repositorioFalso(
                PlanEstudioRepository.class,
                Map.of("findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc", Optional.of(plan))
        );
    }

    private MallaCurricular crearMalla(Long idCurso, Short nivel) {
        MallaCurricular malla = new MallaCurricular();
        malla.setIdCurso(idCurso);
        malla.setNivel(nivel);
        malla.setActivo(true);
        return malla;
    }

    private Curso crearCurso(Long id, String codigo, String nombre) {
        Curso curso = new Curso();
        curso.setId(id);
        curso.setCodigo(codigo);
        curso.setNombre(nombre);
        curso.setActivo(true);
        return curso;
    }

    /*
     * Doble de prueba mínimo: responde con el valor indicado para cada
     * nombre de método (sin distinguir sobrecargas, que no se usan aquí).
     * Cualquier otro método invocado lanza una excepción, para detectar
     * llamadas que la prueba no esperaba.
     */
    @SuppressWarnings("unchecked")
    private static <T> T repositorioFalso(Class<T> tipo, Map<String, Object> respuestas) {
        return (T) Proxy.newProxyInstance(
                tipo.getClassLoader(),
                new Class<?>[]{tipo},
                (proxy, method, args) -> {
                    if (respuestas.containsKey(method.getName())) {
                        return respuestas.get(method.getName());
                    }

                    switch (method.getName()) {
                        case "toString": return "RepositorioFalso<" + tipo.getSimpleName() + ">";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                    }

                    throw new UnsupportedOperationException(
                            tipo.getSimpleName() + "." + method.getName() + " no estaba previsto en esta prueba."
                    );
                }
        );
    }
}
