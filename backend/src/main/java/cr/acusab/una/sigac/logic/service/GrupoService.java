package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.GrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.request.HorarioGrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.GrupoResponseDTO;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class GrupoService {

    private static final Set<String> MODALIDADES_VALIDAS = Set.of(
            "PRESENCIAL",
            "VIRTUAL",
            "HIBRIDA"
    );

    /*
     * Estados permitidos al crear un grupo.
     * EN_CURSO, CERRADO y CANCELADO llegan después, por acciones
     * explícitas sobre un grupo ya existente.
     */
    private static final Set<String> ESTADOS_VALIDOS_CREACION = Set.of(
            "PLANIFICADO",
            "PUBLICADO"
    );

    // En períodos terminados no tiene sentido abrir grupos nuevos.
    private static final Set<String> ESTADOS_PERIODO_CERRADOS = Set.of(
            "CERRADO",
            "CANCELADO"
    );

    // Franja permitida para las lecciones: no se imparten en la madrugada.
    private static final LocalTime HORA_MINIMA = LocalTime.of(7, 0);
    private static final LocalTime HORA_MAXIMA = LocalTime.of(22, 0);

    private final GrupoRepository grupoRepository;
    private final HorarioGrupoRepository horarioGrupoRepository;
    private final CursoRepository cursoRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final ProfesorRepository profesorRepository;
    private final ValidacionHorarioProfesorService validacionHorarioProfesorService;
    private final UsuarioRepository usuarioRepository;

    public GrupoService(
            GrupoRepository grupoRepository,
            HorarioGrupoRepository horarioGrupoRepository,
            CursoRepository cursoRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            ProfesorRepository profesorRepository,
            ValidacionHorarioProfesorService validacionHorarioProfesorService,
            UsuarioRepository usuarioRepository
    ) {
        this.grupoRepository = grupoRepository;
        this.horarioGrupoRepository = horarioGrupoRepository;
        this.cursoRepository = cursoRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.profesorRepository = profesorRepository;
        this.validacionHorarioProfesorService = validacionHorarioProfesorService;
        this.usuarioRepository = usuarioRepository;
    }

    /*
     * Creación de un grupo con su horario semanal.
     *
     * @Transactional garantiza que si falla el guardado de algún
     * horario, no quede el grupo creado sin horario.
     */
    @Transactional
    public GrupoResponseDTO crear(GrupoRequestDTO request) {

        String codigo = request.getCodigo().trim().toUpperCase();
        String modalidad = request.getModalidad().trim().toUpperCase();
        String estado = request.getEstado().trim().toUpperCase();
        String aula = textoOpcional(request.getAula());
        LocalDate fechaInicio = request.getFechaInicio();
        LocalDate fechaFin = request.getFechaFin();

        // 1. El curso debe existir y estar activo.
        Curso curso = cursoRepository
                .findById(request.getIdCurso())
                .orElseThrow(() -> new BusinessException(
                        "El curso seleccionado no existe."
                ));

        if (!Boolean.TRUE.equals(curso.getActivo())) {
            throw new BusinessException(
                    "El curso seleccionado se encuentra inactivo."
            );
        }

        // 2. El período debe existir y no estar cerrado ni cancelado.
        PeriodoAcademico periodo = periodoAcademicoRepository
                .findById(request.getIdPeriodo())
                .orElseThrow(() -> new BusinessException(
                        "El período académico seleccionado no existe."
                ));

        if (ESTADOS_PERIODO_CERRADOS.contains(periodo.getEstado())) {
            throw new BusinessException(
                    "No se pueden crear grupos en un período cerrado o cancelado."
            );
        }

        // 3. Si se indica profesor, debe existir y estar activo.
        Profesor profesor = null;

        if (request.getIdProfesor() != null) {

            profesor = profesorRepository
                    .findByIdParaAsignacion(request.getIdProfesor())
                    .orElseThrow(() -> new BusinessException(
                            "El profesor seleccionado no existe."
                    ));

            if (!Boolean.TRUE.equals(profesor.getActivo())) {
                throw new BusinessException(
                        "El profesor seleccionado se encuentra inactivo."
                );
            }
        }

        // 4. Modalidad y estado deben ser de los permitidos.
        if (!MODALIDADES_VALIDAS.contains(modalidad)) {
            throw new BusinessException(
                    "La modalidad indicada no es válida."
            );
        }

        if (!ESTADOS_VALIDOS_CREACION.contains(estado)) {
            throw new BusinessException(
                    "El estado indicado no es válido."
            );
        }

        if ("PUBLICADO".equals(estado) && profesor == null) {
            throw new BusinessException(
                    "Un grupo solo puede publicarse cuando tiene un profesor activo asignado."
            );
        }

        // 5. Fechas del grupo: opcionales, pero coherentes entre sí
        // y dentro del período académico.
        validarFechas(fechaInicio, fechaFin, periodo);

        // 6. El código no se puede repetir dentro del período.
        if (grupoRepository.existsByIdPeriodoAndCodigo(periodo.getId(), codigo)) {
            throw new BusinessException(
                    "Ya existe un grupo con ese código en el período seleccionado."
            );
        }

        // 7. El horario no puede tener bloques inválidos ni traslapes.
        validarHorarios(request.getHorarios());

        if (profesor != null) {
            List<ValidacionHorarioProfesorService.BloqueHorario> horariosPropuestos =
                    request.getHorarios().stream()
                            .map(horario -> new ValidacionHorarioProfesorService.BloqueHorario(
                                    horario.getDiaSemana(),
                                    horario.getHoraInicio(),
                                    horario.getHoraFin()
                            ))
                            .toList();

            LocalDate fechaInicioEfectiva = fechaInicio != null
                    ? fechaInicio
                    : periodo.getFechaInicio();
            LocalDate fechaFinEfectiva = fechaFin != null
                    ? fechaFin
                    : periodo.getFechaFin();

            validacionHorarioProfesorService.validarDisponibilidad(
                    profesor.getId(),
                    periodo.getId(),
                    fechaInicioEfectiva,
                    fechaFinEfectiva,
                    null,
                    horariosPropuestos
            );
        }

        Instant ahora = Instant.now();

        // 8. Crear el grupo.
        Long idUsuario = obtenerIdUsuarioAutenticado();

        Grupo grupo = new Grupo();
        grupo.setIdUsuarioUltimaModificacion(idUsuario);
        grupo.setIdCurso(curso.getId());
        grupo.setIdPeriodo(periodo.getId());
        grupo.setIdProfesor(profesor != null ? profesor.getId() : null);
        grupo.setCodigo(codigo);
        grupo.setCupoMaximo(request.getCupoMaximo());
        grupo.setModalidad(modalidad);
        grupo.setAula(aula);
        grupo.setFechaInicio(fechaInicio);
        grupo.setFechaFin(fechaFin);
        grupo.setEstado(estado);
        grupo.setCreadoEn(ahora);
        grupo.setActualizadoEn(ahora);

        Grupo grupoGuardado = grupoRepository.save(grupo);

        // 9. Guardar el horario semanal. Un bloque sin aula propia
        // usa el aula del grupo.
        List<HorarioGrupo> horarios = request.getHorarios()
                .stream()
                .map(h -> {
                    HorarioGrupo horario = new HorarioGrupo();
                    horario.setIdGrupo(grupoGuardado.getId());
                    horario.setDiaSemana(h.getDiaSemana());
                    horario.setHoraInicio(h.getHoraInicio());
                    horario.setHoraFin(h.getHoraFin());

                    String aulaHorario = textoOpcional(h.getAula());
                    horario.setAula(aulaHorario != null ? aulaHorario : aula);

                    return horario;
                })
                .toList();

        List<HorarioGrupo> horariosGuardados =
                horarioGrupoRepository.saveAll(horarios);

        return mapearAResponse(
                grupoGuardado,
                curso,
                periodo,
                profesor,
                horariosGuardados
        );
    }

    private void validarFechas(
            LocalDate fechaInicio,
            LocalDate fechaFin,
            PeriodoAcademico periodo
    ) {

        if (fechaInicio != null
                && fechaFin != null
                && fechaFin.isBefore(fechaInicio)) {

            throw new BusinessException(
                    "La fecha de fin del grupo no puede ser anterior a la fecha de inicio."
            );
        }

        if (fechaInicio != null
                && (fechaInicio.isBefore(periodo.getFechaInicio())
                || fechaInicio.isAfter(periodo.getFechaFin()))) {

            throw new BusinessException(
                    "La fecha de inicio del grupo debe estar dentro del período académico."
            );
        }

        if (fechaFin != null
                && (fechaFin.isBefore(periodo.getFechaInicio())
                || fechaFin.isAfter(periodo.getFechaFin()))) {

            throw new BusinessException(
                    "La fecha de fin del grupo debe estar dentro del período académico."
            );
        }
    }

    /*
     * Revisa cada bloque y que dos bloques del mismo día no se traslapen.
     * Ej.: lunes 8:00-10:00 y lunes 9:00-11:00 chocan entre sí.
     */
    private void validarHorarios(List<HorarioGrupoRequestDTO> horarios) {

        for (HorarioGrupoRequestDTO horario : horarios) {

            if (!horario.getHoraFin().isAfter(horario.getHoraInicio())) {
                throw new BusinessException(
                        "En cada horario, la hora de fin debe ser posterior a la hora de inicio."
                );
            }

            if (horario.getHoraInicio().isBefore(HORA_MINIMA)
                    || horario.getHoraFin().isAfter(HORA_MAXIMA)) {
                throw new BusinessException(
                        "Los horarios deben estar entre las 7:00 a. m. y las 10:00 p. m."
                );
            }
        }

        List<HorarioGrupoRequestDTO> ordenados = horarios.stream()
                .sorted(Comparator
                        .comparing(HorarioGrupoRequestDTO::getDiaSemana)
                        .thenComparing(HorarioGrupoRequestDTO::getHoraInicio))
                .toList();

        for (int i = 1; i < ordenados.size(); i++) {

            HorarioGrupoRequestDTO anterior = ordenados.get(i - 1);
            HorarioGrupoRequestDTO actual = ordenados.get(i);

            if (anterior.getDiaSemana().equals(actual.getDiaSemana())
                    && actual.getHoraInicio().isBefore(anterior.getHoraFin())) {

                throw new BusinessException(
                        "Hay horarios que se traslapan el mismo día."
                );
            }
        }
    }

    private Long obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }

        Usuario usuario = usuarioRepository.findByCedula(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario autenticado no existe en el sistema."
                ));
        return usuario.getId();
    }

    private String textoOpcional(String texto) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return texto.trim();
    }

    private GrupoResponseDTO mapearAResponse(
            Grupo grupo,
            Curso curso,
            PeriodoAcademico periodo,
            Profesor profesor,
            List<HorarioGrupo> horarios
    ) {

        String nombreProfesor = profesor == null
                ? null
                : String.join(
                        " ",
                        profesor.getNombres(),
                        profesor.getPrimerApellido(),
                        profesor.getSegundoApellido() != null
                                ? profesor.getSegundoApellido()
                                : ""
                ).trim().replaceAll("\\s+", " ");

        List<GrupoResponseDTO.Horario> horariosResponse = horarios.stream()
                .sorted(Comparator
                        .comparing(HorarioGrupo::getDiaSemana)
                        .thenComparing(HorarioGrupo::getHoraInicio))
                .map(h -> new GrupoResponseDTO.Horario(
                        h.getId(),
                        h.getDiaSemana(),
                        h.getHoraInicio(),
                        h.getHoraFin(),
                        h.getAula()
                ))
                .toList();

        return new GrupoResponseDTO(
                grupo.getId(),
                grupo.getCodigo(),
                curso.getId(),
                curso.getNombre(),
                periodo.getId(),
                periodo.getNombre(),
                grupo.getIdProfesor(),
                nombreProfesor,
                grupo.getCupoMaximo(),
                grupo.getModalidad(),
                grupo.getAula(),
                grupo.getFechaInicio(),
                grupo.getFechaFin(),
                grupo.getEstado(),
                horariosResponse
        );
    }
}
