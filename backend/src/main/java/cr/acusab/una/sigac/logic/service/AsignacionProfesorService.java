package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Bitacora;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.Horario;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.BitacoraRepository;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.AsignarProfesorRequest;
import cr.acusab.una.sigac.presentation.dto.request.HorarioRequest;
import cr.acusab.una.sigac.presentation.dto.response.AsignacionGrupoResponse;
import cr.acusab.una.sigac.presentation.dto.response.GrupoDeProfesorResponse;
import cr.acusab.una.sigac.presentation.dto.response.HorarioResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AsignacionProfesorService {

    private final GrupoRepository grupoRepository;
    private final HorarioRepository horarioRepository;
    private final ProfesorRepository profesorRepository;
    private final CursoRepository cursoRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final BitacoraRepository bitacoraRepository;
    private final UsuarioRepository usuarioRepository;

    public AsignacionProfesorService(
            GrupoRepository grupoRepository,
            HorarioRepository horarioRepository,
            ProfesorRepository profesorRepository,
            CursoRepository cursoRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            BitacoraRepository bitacoraRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.grupoRepository = grupoRepository;
        this.horarioRepository = horarioRepository;
        this.profesorRepository = profesorRepository;
        this.cursoRepository = cursoRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.bitacoraRepository = bitacoraRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /*
     * HU-014 - Asignar (o reasignar) un profesor a un grupo existente.
     */
    @Transactional
    public AsignacionGrupoResponse asignar(Long idGrupo, AsignarProfesorRequest request) {

        Grupo grupo = buscarGrupoOFallar(idGrupo);

        Profesor profesor = profesorRepository.findById(request.getIdProfesor())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El profesor indicado no existe"));

        if (!Boolean.TRUE.equals(profesor.getActivo())) {
            throw new ResponseStatusException(BAD_REQUEST, "Solo se pueden asignar profesores activos");
        }

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(grupo.getIdPeriodo())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El periodo academico del grupo no existe"));

        validarPeriodoNoIniciado(periodo);
        validarHorarios(request.getHorarios());
        validarSinConflictoDeHorario(grupo, profesor, request.getHorarios());

        Long profesorAnterior = grupo.getIdProfesor();

        grupo.setIdProfesor(profesor.getId());
        grupo.setActualizadoEn(Instant.now());
        Grupo grupoGuardado = grupoRepository.save(grupo);

        List<Horario> horarios = reemplazarHorarios(grupoGuardado, request.getHorarios());

        registrarBitacora(
                idGrupo,
                mapaDe("idProfesor", profesorAnterior),
                mapaDe("idProfesor", profesor.getId(), "horarios", resumenHorarios(horarios))
        );

        Curso curso = buscarCursoOFallar(grupoGuardado.getIdCurso());

        return construirAsignacionResponse(
                grupoGuardado, curso, periodo, profesor, horarios,
                "Profesor asignado correctamente al grupo."
        );
    }

    /*
     * HU-014 - Quitar la asignacion de un profesor de un grupo.
     */
    @Transactional
    public void eliminarAsignacion(Long idGrupo) {

        Grupo grupo = buscarGrupoOFallar(idGrupo);

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(grupo.getIdPeriodo())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El periodo academico del grupo no existe"));

        validarPeriodoNoIniciado(periodo);

        Long profesorAnterior = grupo.getIdProfesor();

        grupo.setIdProfesor(null);
        grupo.setActualizadoEn(Instant.now());
        grupoRepository.save(grupo);

        horarioRepository.deleteByIdGrupo_Id(idGrupo);

        registrarBitacora(
                idGrupo,
                mapaDe("idProfesor", profesorAnterior),
                mapaDe("idProfesor", null)
        );
    }

    @Transactional(readOnly = true)
    public AsignacionGrupoResponse obtenerAsignacion(Long idGrupo) {

        Grupo grupo = buscarGrupoOFallar(idGrupo);
        Curso curso = buscarCursoOFallar(grupo.getIdCurso());

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(grupo.getIdPeriodo())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El periodo academico del grupo no existe"));

        Profesor profesor = grupo.getIdProfesor() != null
                ? profesorRepository.findById(grupo.getIdProfesor()).orElse(null)
                : null;

        List<Horario> horarios = horarioRepository.findByIdGrupo_Id(idGrupo);

        return construirAsignacionResponse(grupo, curso, periodo, profesor, horarios, null);
    }

    /*
     * HU-014 - "Actualizacion de cursos asignados al profesor".
     */
    @Transactional(readOnly = true)
    public List<GrupoDeProfesorResponse> listarGruposDeProfesor(Long idProfesor, Long idPeriodo) {

        if (!profesorRepository.existsById(idProfesor)) {
            throw new ResponseStatusException(NOT_FOUND, "El profesor indicado no existe");
        }

        List<Grupo> grupos = grupoRepository.findByIdProfesor(idProfesor).stream()
                .filter(g -> idPeriodo == null || idPeriodo.equals(g.getIdPeriodo()))
                .toList();

        if (grupos.isEmpty()) {
            return List.of();
        }

        List<Long> idsGrupo = grupos.stream().map(Grupo::getId).toList();
        List<Long> idsCurso = grupos.stream().map(Grupo::getIdCurso).distinct().toList();
        List<Long> idsPeriodo = grupos.stream().map(Grupo::getIdPeriodo).distinct().toList();

        Map<Long, Curso> cursosPorId = cursoRepository.findAllById(idsCurso).stream()
                .collect(java.util.stream.Collectors.toMap(Curso::getId, c -> c));

        Map<Long, PeriodoAcademico> periodosPorId = periodoAcademicoRepository.findAllById(idsPeriodo).stream()
                .collect(java.util.stream.Collectors.toMap(PeriodoAcademico::getId, p -> p));

        Map<Long, List<Horario>> horariosPorGrupo = horarioRepository.findByIdGrupo_IdIn(idsGrupo).stream()
                .collect(java.util.stream.Collectors.groupingBy(h -> h.getIdGrupo().getId()));

        return grupos.stream()
                .map(grupo -> {
                    Curso curso = cursosPorId.get(grupo.getIdCurso());
                    PeriodoAcademico periodo = periodosPorId.get(grupo.getIdPeriodo());
                    List<Horario> horarios = horariosPorGrupo.getOrDefault(grupo.getId(), List.of());

                    return new GrupoDeProfesorResponse(
                            grupo.getId(),
                            grupo.getCodigo(),
                            curso != null ? curso.getId() : null,
                            curso != null ? curso.getCodigo() : null,
                            curso != null ? curso.getNombre() : null,
                            periodo != null ? periodo.getId() : null,
                            periodo != null ? periodo.getCodigo() : null,
                            grupo.getEstado(),
                            horarios.stream().map(this::toHorarioResponse).toList()
                    );
                })
                .toList();
    }

    /*
     * HU-014 - Un grupo solo puede habilitarse ("publicarse") si tiene
     * al menos un profesor responsable asignado.
     */
    @Transactional
    public AsignacionGrupoResponse publicar(Long idGrupo) {

        Grupo grupo = buscarGrupoOFallar(idGrupo);

        if (grupo.getIdProfesor() == null) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "El grupo debe tener un profesor asignado antes de poder habilitarse."
            );
        }

        grupo.setEstado("PUBLICADO");
        grupo.setActualizadoEn(Instant.now());
        Grupo grupoGuardado = grupoRepository.save(grupo);

        Curso curso = buscarCursoOFallar(grupoGuardado.getIdCurso());
        PeriodoAcademico periodo = periodoAcademicoRepository.findById(grupoGuardado.getIdPeriodo()).orElse(null);
        Profesor profesor = profesorRepository.findById(grupoGuardado.getIdProfesor()).orElse(null);
        List<Horario> horarios = horarioRepository.findByIdGrupo_Id(idGrupo);

        return construirAsignacionResponse(
                grupoGuardado, curso, periodo, profesor, horarios,
                "Grupo habilitado correctamente."
        );
    }

    // ------------------------------------------------------------------

    private void validarPeriodoNoIniciado(PeriodoAcademico periodo) {
        if (!periodo.getFechaInicio().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "No es posible modificar la asignacion: el periodo academico ya inicio."
            );
        }
    }

    private void validarHorarios(List<HorarioRequest> horarios) {
        for (HorarioRequest horario : horarios) {
            if (!horario.getHoraFin().isAfter(horario.getHoraInicio())) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "La hora de fin del horario debe ser posterior a la hora de inicio."
                );
            }
        }
    }

    private void validarSinConflictoDeHorario(Grupo grupo, Profesor profesor, List<HorarioRequest> horarios) {

        List<Grupo> otrosGrupos = grupoRepository.findByIdProfesorAndIdPeriodoAndIdNot(
                profesor.getId(), grupo.getIdPeriodo(), grupo.getId());

        if (otrosGrupos.isEmpty()) {
            return;
        }

        List<Long> idsOtrosGrupos = otrosGrupos.stream().map(Grupo::getId).toList();
        Map<Long, Grupo> gruposPorId = otrosGrupos.stream()
                .collect(java.util.stream.Collectors.toMap(Grupo::getId, g -> g));

        List<Horario> horariosExistentes = horarioRepository.findByIdGrupo_IdIn(idsOtrosGrupos);

        for (Horario existente : horariosExistentes) {
            for (HorarioRequest nuevo : horarios) {
                boolean mismoDia = existente.getDiaSemana().equals(nuevo.getDiaSemana());
                boolean seSolapan = nuevo.getHoraInicio().isBefore(existente.getHoraFin())
                        && existente.getHoraInicio().isBefore(nuevo.getHoraFin());

                if (mismoDia && seSolapan) {
                    Grupo grupoEnConflicto = gruposPorId.get(existente.getIdGrupo().getId());
                    String codigoGrupo = grupoEnConflicto != null ? grupoEnConflicto.getCodigo() : "otro grupo";
                    throw new ResponseStatusException(
                            CONFLICT,
                            "El profesor ya tiene un curso asignado en ese horario (grupo " + codigoGrupo + ")."
                    );
                }
            }
        }
    }

    private List<Horario> reemplazarHorarios(Grupo grupo, List<HorarioRequest> solicitados) {

        horarioRepository.deleteByIdGrupo_Id(grupo.getId());

        List<Horario> nuevos = solicitados.stream().map(req -> {
            Horario horario = new Horario();
            horario.setIdGrupo(grupo);
            horario.setDiaSemana(req.getDiaSemana());
            horario.setHoraInicio(req.getHoraInicio());
            horario.setHoraFin(req.getHoraFin());
            horario.setAula(req.getAula());
            return horario;
        }).toList();

        return horarioRepository.saveAll(nuevos);
    }

    /*
     * HU-014 - Registro de la asignacion en el historial academico
     * y administrativo (tabla generica de bitacora, sin trigger de BD
     * porque "grupos" no tiene columna id_usuario_ultima_modificacion).
     */
    private void registrarBitacora(Long idGrupo, Map<String, Object> datosAnteriores, Map<String, Object> datosNuevos) {

        Bitacora bitacora = new Bitacora();
        bitacora.setIdUsuario(obtenerIdUsuarioAutenticado());
        bitacora.setTablaAfectada("grupos");
        bitacora.setIdRegistro(idGrupo);
        bitacora.setAccion("UPDATE");
        bitacora.setDatosAnteriores(aJson(datosAnteriores));
        bitacora.setDatosNuevos(aJson(datosNuevos));
        bitacora.setFechaEvento(Instant.now());

        bitacoraRepository.save(bitacora);
    }

    // Map.of no acepta valores null (p. ej. "sin profesor anterior"),
    // por eso se construye con un HashMap explicito.
    private Map<String, Object> mapaDe(String clave, Object valor) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put(clave, valor);
        return mapa;
    }

    private Map<String, Object> mapaDe(String clave1, Object valor1, String clave2, Object valor2) {
        Map<String, Object> mapa = mapaDe(clave1, valor1);
        mapa.put(clave2, valor2);
        return mapa;
    }

    // Serializacion manual a JSON: no hay jackson-databind en el classpath
    // (el proyecto usa spring-boot-starter-webmvc, no -web) y los datos aqui
    // son simples (ids, strings, listas de mapas), por lo que no amerita
    // agregar una nueva dependencia solo para esto.
    private String aJson(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribirJson(valor, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void escribirJson(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                sb.append('"').append(escaparJson(entrada.getKey().toString())).append("\":");
                escribirJson(entrada.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof List<?> lista) {
            sb.append('[');
            for (int i = 0; i < lista.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                escribirJson(lista.get(i), sb);
            }
            sb.append(']');
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else {
            sb.append('"').append(escaparJson(valor.toString())).append('"');
        }
    }

    private String escaparJson(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<Map<String, Object>> resumenHorarios(List<Horario> horarios) {
        return horarios.stream().map(h -> {
            Map<String, Object> resumen = new HashMap<>();
            resumen.put("diaSemana", h.getDiaSemana());
            resumen.put("horaInicio", h.getHoraInicio().toString());
            resumen.put("horaFin", h.getHoraFin().toString());
            return resumen;
        }).toList();
    }

    private Grupo buscarGrupoOFallar(Long idGrupo) {
        return grupoRepository.findById(idGrupo)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El grupo indicado no existe"));
    }

    private Curso buscarCursoOFallar(Long idCurso) {
        return cursoRepository.findById(idCurso)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El curso del grupo no existe"));
    }

    private Long obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }

        Usuario usuario = usuarioRepository.findByCedula(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe en el sistema."));

        return usuario.getId();
    }

    private HorarioResponse toHorarioResponse(Horario h) {
        return new HorarioResponse(h.getId(), h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin(), h.getAula());
    }

    private AsignacionGrupoResponse construirAsignacionResponse(
            Grupo grupo, Curso curso, PeriodoAcademico periodo, Profesor profesor, List<Horario> horarios, String mensaje
    ) {
        String nombreProfesor = profesor != null
                ? String.join(" ", profesor.getNombres(), profesor.getPrimerApellido(),
                        profesor.getSegundoApellido() != null ? profesor.getSegundoApellido() : "")
                        .trim().replaceAll("\\s+", " ")
                : null;

        return new AsignacionGrupoResponse(
                grupo.getId(),
                grupo.getCodigo(),
                curso != null ? curso.getId() : null,
                curso != null ? curso.getCodigo() : null,
                curso != null ? curso.getNombre() : null,
                periodo != null ? periodo.getId() : null,
                periodo != null ? periodo.getCodigo() : null,
                profesor != null ? profesor.getId() : null,
                nombreProfesor,
                grupo.getEstado(),
                horarios.stream().map(this::toHorarioResponse).toList(),
                mensaje
        );
    }
}
