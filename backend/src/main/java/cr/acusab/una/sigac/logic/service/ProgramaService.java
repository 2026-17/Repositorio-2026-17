package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Programa;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProgramaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.presentation.dto.request.PlanEstudioInicialRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProgramaCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProgramaUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaDetalleResponse;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaDisponibilidadResponse;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/*
 * Programas académicos (la "carrera" que agrupa planes de estudio y
 * cursos). No dependen de ningún período académico.
 *
 * Un programa nunca se elimina: planes, cursos e inscripciones de
 * estudiantes dependen de él. Solo se activa o desactiva.
 */
@Service
public class ProgramaService {

    private static final int LONGITUD_MAXIMA_CODIGO_PLAN = 40;
    private static final int LONGITUD_MAXIMA_NOMBRE_PLAN = 160;

    private final ProgramaRepository programaRepository;
    private final PlanEstudioRepository planEstudioRepository;
    private final MallaCurricularRepository mallaCurricularRepository;
    private final UsuarioRepository usuarioRepository;

    public ProgramaService(
            ProgramaRepository programaRepository,
            PlanEstudioRepository planEstudioRepository,
            MallaCurricularRepository mallaCurricularRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.programaRepository = programaRepository;
        this.planEstudioRepository = planEstudioRepository;
        this.mallaCurricularRepository = mallaCurricularRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /*
     * Opciones del selector "Programa académico" de Crear curso.
     */
    public List<ProgramaResponse> listarProgramasActivos() {

        return programaRepository
                .findByActivoTrueOrderByNombreAsc()
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    /*
     * Listado completo (activos e inactivos) con su resumen, para la
     * pantalla de gestión del Director Académico.
     */
    @Transactional(readOnly = true)
    public List<ProgramaDetalleResponse> listarParaGestion() {

        return programaRepository
                .findAllByOrderByNombreAsc()
                .stream()
                .map(this::mapearADetalle)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramaDetalleResponse obtener(Long idPrograma) {
        return mapearADetalle(buscarProgramaOLanzarError(idPrograma));
    }

    /*
     * Validación en vivo del asistente: ¿el código y el nombre escritos
     * ya están en uso? Un valor vacío se informa como disponible (el
     * campo obligatorio lo valida el formulario).
     */
    public ProgramaDisponibilidadResponse consultarDisponibilidad(
            String codigo,
            String nombre
    ) {
        String codigoNormalizado = normalizarCodigo(codigo);
        String nombreNormalizado = normalizarTexto(nombre);

        boolean codigoDisponible = codigoNormalizado == null
                || !programaRepository.existsByCodigoIgnoreCase(codigoNormalizado);

        boolean nombreDisponible = nombreNormalizado == null
                || !programaRepository.existsByNombreIgnoreCase(nombreNormalizado);

        return new ProgramaDisponibilidadResponse(codigoDisponible, nombreDisponible);
    }

    /*
     * Crear un programa académico y, opcionalmente, su primer plan de
     * estudio.
     *
     * @Transactional: si falla la creación del plan, tampoco queda el
     * programa creado (todo o nada).
     */
    @Transactional
    public ProgramaDetalleResponse crear(ProgramaCreateRequest request) {

        String codigo = normalizarCodigo(request.getCodigo());
        String nombre = normalizarTexto(request.getNombre());
        String descripcion = normalizarTexto(request.getDescripcion());

        if (codigo == null) {
            throw new IllegalArgumentException("El código del programa es obligatorio.");
        }

        if (nombre == null) {
            throw new IllegalArgumentException("El nombre del programa es obligatorio.");
        }

        // 1. Código y nombre únicos.
        if (programaRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new DuplicateResourceException(
                    "Ya existe un programa académico con el código " + codigo + "."
            );
        }

        if (programaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new DuplicateResourceException(
                    "Ya existe un programa académico con el nombre ingresado."
            );
        }

        // 2. Usuario autenticado, para los triggers de bitácora.
        Long idUsuario = obtenerIdUsuarioAutenticado();
        Instant ahora = Instant.now();

        // 3. Crear el programa (nace activo).
        Programa programa = new Programa();
        programa.setCodigo(codigo);
        programa.setNombre(nombre);
        programa.setDescripcion(descripcion);
        programa.setActivo(true);
        programa.setIdUsuarioUltimaModificacion(idUsuario);
        programa.setCreadoEn(ahora);
        programa.setActualizadoEn(ahora);

        Programa programaGuardado = programaRepository.save(programa);

        // 4. Primer plan de estudio, si se indicó.
        if (request.getPlanInicial() != null) {
            crearPlanEstudio(programaGuardado, request.getPlanInicial(), idUsuario, ahora);
        }

        return mapearADetalle(programaGuardado);
    }

    /*
     * Agregar un plan de estudio a un programa que no tiene uno vigente
     * (por ejemplo, si se creó sin plan). Mientras el programa no tenga
     * plan vigente no se le pueden crear cursos.
     */
    @Transactional
    public ProgramaDetalleResponse agregarPlanInicial(
            Long idPrograma,
            PlanEstudioInicialRequest request
    ) {
        Programa programa = buscarProgramaOLanzarError(idPrograma);

        if (planEstudioRepository
                .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(idPrograma)
                .isPresent()) {

            throw new IllegalArgumentException(
                    "El programa ya tiene un plan de estudio vigente."
            );
        }

        Long idUsuario = obtenerIdUsuarioAutenticado();
        crearPlanEstudio(programa, request, idUsuario, Instant.now());

        return mapearADetalle(programa);
    }

    /*
     * Modificar nombre y descripción. El código no cambia.
     */
    @Transactional
    public ProgramaDetalleResponse actualizar(
            Long idPrograma,
            ProgramaUpdateRequest request
    ) {
        Programa programa = buscarProgramaOLanzarError(idPrograma);

        String nombre = normalizarTexto(request.getNombre());
        String descripcion = normalizarTexto(request.getDescripcion());

        if (nombre == null) {
            throw new IllegalArgumentException("El nombre del programa es obligatorio.");
        }

        if (programaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, idPrograma)) {
            throw new DuplicateResourceException(
                    "Ya existe otro programa académico con el nombre ingresado."
            );
        }

        programa.setNombre(nombre);
        programa.setDescripcion(descripcion);
        programa.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());
        programa.setActualizadoEn(Instant.now());

        return mapearADetalle(programaRepository.save(programa));
    }

    /*
     * Activar o desactivar. Un programa inactivo deja de aparecer en
     * Crear curso; su información (planes, cursos, inscripciones) se
     * conserva.
     */
    @Transactional
    public ProgramaDetalleResponse cambiarEstado(Long idPrograma, boolean activo) {

        Programa programa = buscarProgramaOLanzarError(idPrograma);

        if (Boolean.valueOf(activo).equals(programa.getActivo())) {
            throw new IllegalArgumentException(
                    activo
                            ? "El programa ya se encuentra activo."
                            : "El programa ya se encuentra inactivo."
            );
        }

        programa.setActivo(activo);
        programa.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());
        programa.setActualizadoEn(Instant.now());

        return mapearADetalle(programaRepository.save(programa));
    }

    // ------------------------------------------------------------------

    private void crearPlanEstudio(
            Programa programa,
            PlanEstudioInicialRequest datos,
            Long idUsuario,
            Instant ahora
    ) {
        String version = normalizarTexto(datos.getVersion());

        if (version == null) {
            throw new IllegalArgumentException("La versión del plan de estudio es obligatoria.");
        }

        if (planEstudioRepository.existsByIdProgramaAndVersionIgnoreCase(programa.getId(), version)) {
            throw new DuplicateResourceException(
                    "El programa ya tiene un plan de estudio con la versión " + version + "."
            );
        }

        PlanEstudio plan = new PlanEstudio();
        plan.setIdPrograma(programa.getId());
        plan.setCodigo(generarCodigoPlan(programa.getCodigo(), version));
        plan.setNombre(recortar(
                "Plan de estudios " + programa.getNombre() + " v" + version,
                LONGITUD_MAXIMA_NOMBRE_PLAN
        ));
        plan.setVersion(version);
        plan.setFechaVigenciaDesde(datos.getFechaVigenciaDesde());
        plan.setNotaMinimaAprobacion(datos.getNotaMinimaAprobacion());
        plan.setVigente(true);
        plan.setIdUsuarioUltimaModificacion(idUsuario);
        plan.setCreadoEn(ahora);
        plan.setActualizadoEn(ahora);

        planEstudioRepository.save(plan);
    }

    /*
     * Código del plan: código del programa + versión (ej. FMI-V1.0).
     * Si por casualidad ya existe, se agrega un sufijo numérico.
     */
    private String generarCodigoPlan(String codigoPrograma, String version) {

        String base = recortar(
                codigoPrograma + "-V" + version.toUpperCase(Locale.ROOT),
                LONGITUD_MAXIMA_CODIGO_PLAN - 3
        );

        String codigo = base;
        int sufijo = 2;

        while (planEstudioRepository.existsByCodigo(codigo)) {
            codigo = base + "-" + sufijo;
            sufijo++;
        }

        return codigo;
    }

    private ProgramaDetalleResponse mapearADetalle(Programa programa) {

        List<PlanEstudio> planes =
                planEstudioRepository.findByIdProgramaOrderByFechaVigenciaDesdeDesc(programa.getId());

        PlanEstudio vigente = planes.stream()
                .filter(p -> Boolean.TRUE.equals(p.getVigente()))
                .findFirst()
                .orElse(null);

        ProgramaDetalleResponse.PlanResumen planResumen = null;
        long cantidadCursos = 0;

        if (vigente != null) {
            planResumen = new ProgramaDetalleResponse.PlanResumen(
                    vigente.getId(),
                    vigente.getCodigo(),
                    vigente.getNombre(),
                    vigente.getVersion(),
                    vigente.getFechaVigenciaDesde(),
                    vigente.getNotaMinimaAprobacion()
            );
            cantidadCursos = mallaCurricularRepository.countByIdPlanEstudioAndActivoTrue(vigente.getId());
        }

        return new ProgramaDetalleResponse(
                programa.getId(),
                programa.getCodigo(),
                programa.getNombre(),
                programa.getDescripcion(),
                programa.getActivo(),
                programa.getCreadoEn(),
                programa.getActualizadoEn(),
                planResumen,
                planes.size(),
                cantidadCursos
        );
    }

    private ProgramaResponse mapearAResponse(Programa programa) {

        return new ProgramaResponse(
                programa.getId(),
                programa.getCodigo(),
                programa.getNombre()
        );
    }

    private Programa buscarProgramaOLanzarError(Long idPrograma) {

        return programaRepository
                .findById(idPrograma)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "El programa académico indicado no existe."
                ));
    }

    // Código en mayúsculas y sin espacios alrededor; null si viene vacío.
    private static String normalizarCodigo(String codigo) {
        String texto = normalizarTexto(codigo);
        return texto == null ? null : texto.toUpperCase(Locale.ROOT);
    }

    // Quita espacios sobrantes (también los dobles internos); null si queda vacío.
    private static String normalizarTexto(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim().replaceAll("[ \\t]+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo).trim();
    }

    /*
     * Identificador del usuario autenticado, para que los triggers de la
     * base de datos registren quién hizo cada cambio.
     */
    private Long obtenerIdUsuarioAutenticado() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new IllegalStateException(
                    "No se pudo identificar al usuario autenticado."
            );
        }

        Usuario usuario = usuarioRepository
                .findByCedula(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario autenticado no existe en el sistema."
                ));

        return usuario.getId();
    }
}
