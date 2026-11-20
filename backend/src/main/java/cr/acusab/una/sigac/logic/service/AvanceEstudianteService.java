package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Bitacora;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.CursoReconocido;
import cr.acusab.una.sigac.data.entity.EstudiantePlanEstudio;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.BitacoraRepository;
import cr.acusab.una.sigac.data.repository.CursoReconocidoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.EstudiantePlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.NivelIngresoRequest;
import cr.acusab.una.sigac.presentation.dto.response.AvanceInstrumentosResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/*
 * Avance por instrumento (consulta) y nivel de ingreso (prueba de
 * ubicación). La regla de qué está aprobado y cuál es el siguiente nivel
 * vive en AvanceInstrumentoService.
 */
@Service
public class AvanceEstudianteService {

    public static final String ESTADO_APROBADO = "APROBADO";
    public static final String ESTADO_CONVALIDADO = "CONVALIDADO";
    public static final String ESTADO_SIGUIENTE = "SIGUIENTE";
    public static final String ESTADO_PENDIENTE = "PENDIENTE";

    private final AvanceInstrumentoService avanceInstrumentoService;
    private final EstudianteRepository estudianteRepository;
    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private final EstudiantePlanEstudioRepository estudiantePlanRepository;
    private final PlanEstudioRepository planEstudioRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final CursoReconocidoRepository cursoReconocidoRepository;
    private final BitacoraRepository bitacoraRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    public AvanceEstudianteService(
            AvanceInstrumentoService avanceInstrumentoService,
            EstudianteRepository estudianteRepository,
            EstudianteInstrumentoRepository estudianteInstrumentoRepository,
            EstudiantePlanEstudioRepository estudiantePlanRepository,
            PlanEstudioRepository planEstudioRepository,
            PeriodoAcademicoRepository periodoRepository,
            CursoReconocidoRepository cursoReconocidoRepository,
            BitacoraRepository bitacoraRepository,
            UsuarioRepository usuarioRepository,
            ObjectMapper objectMapper
    ) {
        this.avanceInstrumentoService = avanceInstrumentoService;
        this.estudianteRepository = estudianteRepository;
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
        this.estudiantePlanRepository = estudiantePlanRepository;
        this.planEstudioRepository = planEstudioRepository;
        this.periodoRepository = periodoRepository;
        this.cursoReconocidoRepository = cursoReconocidoRepository;
        this.bitacoraRepository = bitacoraRepository;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper;
    }

    /*
     * Por cada instrumento vigente: profesor, si es del plan o extra, y
     * los niveles de la malla con su estado.
     */
    @Transactional(readOnly = true)
    public AvanceInstrumentosResponse consultar(Long idEstudiante) {

        validarEstudiante(idEstudiante);

        Optional<Long> idPlan = planDelEstudiante(idEstudiante);
        List<Curso> nivelesPlan = idPlan.map(avanceInstrumentoService::nivelesDelPlan).orElse(List.of());

        String nombrePlan = idPlan.flatMap(planEstudioRepository::findById).map(PlanEstudio::getNombre).orElse(null);

        Map<Long, String> nombresPeriodo = new HashMap<>();

        List<AvanceInstrumentosResponse.Instrumento> instrumentos = new ArrayList<>();

        for (EstudiantesInstrumento asignacion : vigentesOrdenados(idEstudiante)) {

            Integer idInstrumento = asignacion.getInstrumento().getId();
            Map<Long, AvanceInstrumentoService.NivelAprobado> aprobados =
                    avanceInstrumentoService.nivelesAprobados(idEstudiante, idInstrumento);

            Curso siguiente = nivelesPlan.stream()
                    .filter(c -> !aprobados.containsKey(c.getId()))
                    .findFirst()
                    .orElse(null);

            List<AvanceInstrumentosResponse.Nivel> niveles = new ArrayList<>();
            int numero = 1;

            for (Curso nivel : nivelesPlan) {
                AvanceInstrumentoService.NivelAprobado aprobado = aprobados.get(nivel.getId());
                String estado;
                if (aprobado != null) {
                    estado = CursoReconocido.CONDICION_CONVALIDADO.equals(aprobado.condicion())
                            ? ESTADO_CONVALIDADO : ESTADO_APROBADO;
                } else {
                    estado = siguiente != null && siguiente.getId().equals(nivel.getId())
                            ? ESTADO_SIGUIENTE : ESTADO_PENDIENTE;
                }

                Long idPeriodo = aprobado == null ? null : aprobado.idPeriodo();
                String nombrePeriodo = idPeriodo == null ? null : nombresPeriodo.computeIfAbsent(idPeriodo,
                        id -> periodoRepository.findById(id).map(PeriodoAcademico::getNombre).orElse(null));

                niveles.add(new AvanceInstrumentosResponse.Nivel(
                        nivel.getId(),
                        nivel.getCodigo(),
                        nivel.getNombre(),
                        numero++,
                        estado,
                        aprobado == null ? null : aprobado.origen(),
                        idPeriodo,
                        nombrePeriodo,
                        aprobado == null ? null : aprobado.fecha()
                ));
            }

            Profesor profesor = asignacion.getProfesor();

            instrumentos.add(new AvanceInstrumentosResponse.Instrumento(
                    asignacion.getId(),
                    idInstrumento,
                    asignacion.getInstrumento().getNombre(),
                    profesor != null ? profesor.getId() : null,
                    profesor != null ? EspacioInstrumentoService.nombreCompleto(
                            profesor.getNombres(), profesor.getPrimerApellido(), profesor.getSegundoApellido()) : null,
                    Boolean.TRUE.equals(asignacion.getEsPrincipal()),
                    siguiente != null ? siguiente.getId() : null,
                    siguiente != null ? siguiente.getNombre() : null,
                    !nivelesPlan.isEmpty() && siguiente == null,
                    aprobados.values().stream()
                            .anyMatch(a -> AvanceInstrumentoService.ORIGEN_SIGAC.equals(a.origen())),
                    niveles
            ));
        }

        return new AvanceInstrumentosResponse(idEstudiante, idPlan.orElse(null), nombrePlan, instrumentos);
    }

    /*
     * Nivel de ingreso: los niveles anteriores al indicado quedan
     * CONVALIDADOS (origen NIVEL_INGRESO) y se registra en la bitácora.
     * Si el estudiante ya tiene niveles de ese instrumento aprobados en
     * SIGAC, se rechaza. Un nivel de ingreso anterior se reemplaza.
     */
    @Transactional
    public AvanceInstrumentosResponse registrarNivelIngreso(
            Long idEstudiante, Integer idInstrumento, NivelIngresoRequest request) {

        validarEstudiante(idEstudiante);

        EstudiantesInstrumento asignacion = estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndIdInstrumento_IdAndFechaFinIsNull(idEstudiante, idInstrumento)
                .orElseThrow(() -> new BusinessException(
                        "El estudiante no tiene ese instrumento asignado actualmente."));

        if (avanceInstrumentoService.tieneNivelesAprobadosEnSigac(idEstudiante, idInstrumento)) {
            throw new BusinessException(
                    "El estudiante ya tiene niveles de " + asignacion.getInstrumento().getNombre()
                            + " aprobados en SIGAC: el nivel de ingreso solo se registra antes de su primer nivel aprobado."
            );
        }

        Long idPlan = planDelEstudiante(idEstudiante)
                .orElseThrow(() -> new BusinessException(
                        "El estudiante no está inscrito en un plan de estudio con cursos de instrumento."));

        List<Curso> nivelesPlan = avanceInstrumentoService.nivelesDelPlan(idPlan);

        int posicion = -1;
        for (int i = 0; i < nivelesPlan.size(); i++) {
            if (nivelesPlan.get(i).getId().equals(request.getIdCurso())) {
                posicion = i;
                break;
            }
        }

        if (posicion < 0) {
            throw new BusinessException("El nivel de ingreso debe ser un curso de instrumento del plan del estudiante.");
        }

        Long idUsuario = obtenerIdUsuarioAutenticado();
        String motivo = request.getMotivo().trim();
        LocalDate hoy = LocalDate.now();

        // 1. El nivel de ingreso anterior (si había) se reemplaza.
        List<CursoReconocido> anteriores = cursoReconocidoRepository
                .findByIdEstudianteAndIdInstrumentoAndOrigen(idEstudiante, idInstrumento,
                        CursoReconocido.ORIGEN_NIVEL_INGRESO);
        List<String> convalidadosAntes = anteriores.stream()
                .map(r -> nombreNivel(nivelesPlan, r.getIdCurso()))
                .toList();
        cursoReconocidoRepository.deleteAll(anteriores);
        cursoReconocidoRepository.flush();

        // 2. Lo que ya estaba reconocido por otra vía (certificaciones) se conserva.
        List<Long> yaReconocidos = cursoReconocidoRepository
                .findByIdEstudianteAndIdInstrumento(idEstudiante, idInstrumento)
                .stream()
                .map(CursoReconocido::getIdCurso)
                .toList();

        List<String> convalidados = new ArrayList<>();

        for (Curso nivel : nivelesPlan.subList(0, posicion)) {
            if (yaReconocidos.contains(nivel.getId())) {
                continue;
            }
            CursoReconocido reconocido = new CursoReconocido();
            reconocido.setIdEstudiante(idEstudiante);
            reconocido.setIdCurso(nivel.getId());
            reconocido.setIdInstrumento(idInstrumento);
            reconocido.setCondicion(CursoReconocido.CONDICION_CONVALIDADO);
            reconocido.setOrigen(CursoReconocido.ORIGEN_NIVEL_INGRESO);
            reconocido.setFecha(hoy);
            reconocido.setMotivo(motivo);
            reconocido.setIdUsuarioRegistra(idUsuario);
            reconocido.setCreadoEn(Instant.now());
            cursoReconocidoRepository.save(reconocido);
            convalidados.add(nivel.getNombre());
        }

        // 3. Bitácora (sobre la asignación del instrumento).
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("convalidados_nivel_ingreso", convalidadosAntes);

        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("accion", "NIVEL_INGRESO");
        despues.put("id_instrumento", idInstrumento);
        despues.put("nivel_ingreso", nivelesPlan.get(posicion).getNombre());
        despues.put("id_curso_nivel_ingreso", nivelesPlan.get(posicion).getId());
        despues.put("convalidados_nivel_ingreso", convalidados);
        despues.put("motivo", motivo);

        Bitacora bitacora = new Bitacora();
        bitacora.setIdUsuario(idUsuario);
        bitacora.setTablaAfectada("estudiantes_instrumentos");
        bitacora.setIdRegistro(asignacion.getId());
        bitacora.setAccion("UPDATE");
        bitacora.setDatosAnteriores(objectMapper.writeValueAsString(antes));
        bitacora.setDatosNuevos(objectMapper.writeValueAsString(despues));
        bitacora.setFechaEvento(Instant.now());
        bitacoraRepository.save(bitacora);

        return consultar(idEstudiante);
    }

    /* ----------------------------------------------------------------- */

    private void validarEstudiante(Long idEstudiante) {
        if (idEstudiante == null || !estudianteRepository.existsById(idEstudiante)) {
            throw new BusinessException("El estudiante indicado no existe.");
        }
    }

    private Optional<Long> planDelEstudiante(Long idEstudiante) {
        List<Long> idsPlan = estudiantePlanRepository.findByIdEstudianteAndActivoTrue(idEstudiante)
                .stream()
                .map(EstudiantePlanEstudio::getIdPlanEstudio)
                .distinct()
                .toList();
        return avanceInstrumentoService.planConInstrumento(idsPlan);
    }

    // El del plan primero; luego por fecha de inicio.
    private List<EstudiantesInstrumento> vigentesOrdenados(Long idEstudiante) {
        return estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(idEstudiante)
                .stream()
                .sorted(Comparator
                        .comparing((EstudiantesInstrumento e) -> !Boolean.TRUE.equals(e.getEsPrincipal()))
                        .thenComparing(EstudiantesInstrumento::getFechaInicio)
                        .thenComparing(EstudiantesInstrumento::getId))
                .toList();
    }

    private static String nombreNivel(List<Curso> niveles, Long idCurso) {
        return niveles.stream()
                .filter(c -> Objects.equals(c.getId(), idCurso))
                .map(Curso::getNombre)
                .findFirst()
                .orElse(String.valueOf(idCurso));
    }

    private Long obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }
        return usuarioRepository.findByCedula(authentication.getName())
                .map(Usuario::getId)
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe en el sistema."));
    }
}
