package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.PeriodoAcademicoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.PeriodoAcademicoResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class PeriodoAcademicoService {

    /*
     * Estados permitidos al crear un período.
     * CANCELADO no aplica a la creación: un período nace planificado
     * y solo llega a ese estado por una acción posterior explícita.
     */
    private static final Set<String> ESTADOS_VALIDOS_CREACION = Set.of(
            "PLANIFICADO",
            "MATRICULA",
            "EN_CURSO",
            "CERRADO"
    );

    /*
     * Estados permitidos al modificar un período ya existente.
     * Aquí sí se permite CANCELADO, ya que un período planificado
     * puede cancelarse antes de iniciar.
     */
    private static final Set<String> ESTADOS_VALIDOS_ACTUALIZACION = Set.of(
            "PLANIFICADO",
            "MATRICULA",
            "EN_CURSO",
            "CERRADO",
            "CANCELADO"
    );

    private static final int LONGITUD_MAXIMA_CODIGO = 30;

    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final UsuarioRepository usuarioRepository;

    public PeriodoAcademicoService(
            PeriodoAcademicoRepository periodoAcademicoRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<PeriodoAcademicoResponse> listarPeriodosDisponibles() {

        List<String> estadosDisponibles = List.of(
                "PLANIFICADO",
                "MATRICULA",
                "EN_CURSO"
        );

        return periodoAcademicoRepository
                .findByEstadoInOrderByFechaInicioDesc(estadosDisponibles)
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    /*
     * Listado completo de períodos académicos (incluye cerrados y
     * cancelados), para la pantalla de consulta/modificación.
     */
    public List<PeriodoAcademicoResponse> listarTodos() {

        return periodoAcademicoRepository
                .findAll()
                .stream()
                .sorted((a, b) -> b.getFechaInicio().compareTo(a.getFechaInicio()))
                .map(this::mapearAResponse)
                .toList();
    }

    public PeriodoAcademicoResponse obtener(Long idPeriodo) {

        PeriodoAcademico periodo = buscarPeriodoOLanzarError(idPeriodo);

        return mapearAResponse(periodo);
    }

    /*
     * Creación de un período académico.
     *
     * @Transactional garantiza que si falla cualquier validación
     * posterior a la persistencia, no quede un período a medio crear.
     */
    @Transactional
    public PeriodoAcademicoResponse crear(PeriodoAcademicoRequestDTO request) {

        String nombre = request.getNombre().trim();
        LocalDate fechaInicio = request.getFechaInicio();
        LocalDate fechaFin = request.getFechaFin();
        LocalDate fechaInicioMatricula = request.getFechaInicioMatricula();
        LocalDate fechaFinMatricula = request.getFechaFinMatricula();
        String estado = request.getEstado().trim().toUpperCase();

        // 1. La fecha de fin debe ser posterior a la fecha de inicio.
        validarFechas(fechaInicio, fechaFin);

        // 2. Si se definen fechas de matrícula, la de fin no puede ser
        // anterior a la de inicio.
        validarFechasMatricula(fechaInicioMatricula, fechaFinMatricula, fechaFin);

        // 3. El estado indicado debe ser uno de los permitidos al crear.
        if (!ESTADOS_VALIDOS_CREACION.contains(estado)) {
            throw new IllegalArgumentException(
                    "El estado indicado no es válido."
            );
        }

        // 4. No puede existir otro período con el mismo nombre.
        if (periodoAcademicoRepository.existsByNombre(nombre)) {
            throw new IllegalArgumentException(
                    "Ya existe un período académico con el nombre ingresado."
            );
        }

        // 5. Obtener el usuario autenticado.
        // Este ID será utilizado por los triggers de bitácora.
        Long idUsuario = obtenerIdUsuarioAutenticado();

        Instant ahora = Instant.now();

        // 6. Crear el período académico.
        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setCodigo(generarCodigoUnico(nombre));
        periodo.setNombre(nombre);
        periodo.setFechaInicio(fechaInicio);
        periodo.setFechaFin(fechaFin);
        periodo.setFechaInicioMatricula(fechaInicioMatricula);
        periodo.setFechaFinMatricula(fechaFinMatricula);
        periodo.setEstado(estado);

        // Importante para la bitácora.
        periodo.setIdUsuarioUltimaModificacion(idUsuario);

        periodo.setCreadoEn(ahora);
        periodo.setActualizadoEn(ahora);

        PeriodoAcademico periodoGuardado =
                periodoAcademicoRepository.save(periodo);

        /*
         * El trigger trg_periodos_academicos_ai_bitacora registra
         * automáticamente la creación utilizando
         * id_usuario_ultima_modificacion.
         */

        return mapearAResponse(periodoGuardado);
    }

    /*
     * Modificación de un período académico existente.
     *
     * El código no se regenera: identifica al período desde su
     * creación y no forma parte de los datos editables por el HU.
     */
    @Transactional
    public PeriodoAcademicoResponse actualizar(
            Long idPeriodo,
            PeriodoAcademicoRequestDTO request
    ) {

        PeriodoAcademico periodo = buscarPeriodoOLanzarError(idPeriodo);

        String nombre = request.getNombre().trim();
        LocalDate fechaInicio = request.getFechaInicio();
        LocalDate fechaFin = request.getFechaFin();
        LocalDate fechaInicioMatricula = request.getFechaInicioMatricula();
        LocalDate fechaFinMatricula = request.getFechaFinMatricula();
        String estado = request.getEstado().trim().toUpperCase();

        // 1. La fecha de fin debe ser posterior a la fecha de inicio.
        validarFechas(fechaInicio, fechaFin);

        // 2. Si se definen fechas de matrícula, la de fin no puede ser
        // anterior a la de inicio.
        validarFechasMatricula(fechaInicioMatricula, fechaFinMatricula, fechaFin);

        // 3. El estado indicado debe ser uno de los permitidos.
        if (!ESTADOS_VALIDOS_ACTUALIZACION.contains(estado)) {
            throw new IllegalArgumentException(
                    "El estado indicado no es válido."
            );
        }

        // 3b. El estado solo avanza (ver validarCambioEstado).
        validarCambioEstado(periodo.getEstado(), estado);

        // 4. No puede existir otro período (distinto a este) con el
        // mismo nombre.
        if (periodoAcademicoRepository.existsByNombreAndIdNot(nombre, idPeriodo)) {
            throw new IllegalArgumentException(
                    "Ya existe un período académico con el nombre ingresado."
            );
        }

        // 5. Obtener el usuario autenticado.
        // Este ID será utilizado por los triggers de bitácora.
        Long idUsuario = obtenerIdUsuarioAutenticado();

        periodo.setNombre(nombre);
        periodo.setFechaInicio(fechaInicio);
        periodo.setFechaFin(fechaFin);
        periodo.setFechaInicioMatricula(fechaInicioMatricula);
        periodo.setFechaFinMatricula(fechaFinMatricula);
        periodo.setEstado(estado);

        // Importante para la bitácora.
        periodo.setIdUsuarioUltimaModificacion(idUsuario);

        periodo.setActualizadoEn(Instant.now());

        PeriodoAcademico periodoActualizado =
                periodoAcademicoRepository.save(periodo);

        /*
         * El trigger trg_periodos_academicos_au_bitacora registra
         * automáticamente la modificación utilizando
         * id_usuario_ultima_modificacion.
         */

        return mapearAResponse(periodoActualizado);
    }

    private void validarFechas(LocalDate fechaInicio, LocalDate fechaFin) {

        if (!fechaFin.isAfter(fechaInicio)) {
            throw new IllegalArgumentException(
                    "La fecha de fin debe ser posterior a la fecha de inicio."
            );
        }
    }

    /*
     * Orden del ciclo de vida de un período. El estado solo avanza
     * (se puede saltar un paso, pero no volver atrás). CANCELADO solo
     * se permite desde PLANIFICADO o MATRICULA, y CERRADO y CANCELADO
     * son finales.
     */
    private static final List<String> ORDEN_ESTADOS = List.of(
            "PLANIFICADO",
            "MATRICULA",
            "EN_CURSO",
            "CERRADO"
    );

    private static final Set<String> ESTADOS_QUE_PERMITEN_CANCELAR = Set.of(
            "PLANIFICADO",
            "MATRICULA"
    );

    private void validarCambioEstado(String actual, String nuevo) {

        if (actual == null || actual.equalsIgnoreCase(nuevo)) {
            return;
        }

        String estadoActual = actual.toUpperCase();

        if ("CANCELADO".equals(estadoActual)) {
            throw new IllegalArgumentException(
                    "Un período cancelado no puede cambiar de estado."
            );
        }

        if ("CANCELADO".equals(nuevo)) {
            if (!ESTADOS_QUE_PERMITEN_CANCELAR.contains(estadoActual)) {
                throw new IllegalArgumentException(
                        "Solo se puede cancelar un período en estado PLANIFICADO o MATRICULA."
                );
            }
            return;
        }

        if (ORDEN_ESTADOS.indexOf(nuevo) < ORDEN_ESTADOS.indexOf(estadoActual)) {
            throw new IllegalArgumentException(
                    "El estado del período solo puede avanzar: PLANIFICADO → MATRICULA → EN_CURSO → CERRADO."
            );
        }
    }

    private void validarFechasMatricula(
            LocalDate fechaInicioMatricula,
            LocalDate fechaFinMatricula,
            LocalDate fechaFinPeriodo
    ) {

        // Las fechas de matrícula van las dos o ninguna.
        if ((fechaInicioMatricula == null) != (fechaFinMatricula == null)) {
            throw new IllegalArgumentException(
                    "Indique la fecha de inicio y la de fin de matrícula, o deje ambas vacías."
            );
        }

        if (fechaFinMatricula != null
                && fechaFinPeriodo != null
                && fechaFinMatricula.isAfter(fechaFinPeriodo)) {

            throw new IllegalArgumentException(
                    "La fecha de fin de matrícula no puede ser posterior a la fecha de fin del período."
            );
        }

        if (fechaInicioMatricula != null
                && fechaFinMatricula != null
                && fechaFinMatricula.isBefore(fechaInicioMatricula)) {

            throw new IllegalArgumentException(
                    "La fecha de fin de matrícula no puede ser anterior "
                            + "a la fecha de inicio de matrícula."
            );
        }
    }

    private PeriodoAcademico buscarPeriodoOLanzarError(Long idPeriodo) {

        return periodoAcademicoRepository
                .findById(idPeriodo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El período académico indicado no existe."
                ));
    }

    /*
     * Genera un código único a partir del nombre del período, ya que
     * la tabla lo requiere pero la creación del período académico
     * (HU) no solicita que el usuario lo digite.
     */
    private String generarCodigoUnico(String nombre) {

        String base = Normalizer
                .normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (base.isEmpty()) {
            base = "PERIODO";
        }

        if (base.length() > LONGITUD_MAXIMA_CODIGO - 4) {
            base = base.substring(0, LONGITUD_MAXIMA_CODIGO - 4);
        }

        String codigo = base;
        int sufijo = 2;

        while (periodoAcademicoRepository.existsByCodigo(codigo)) {
            codigo = base + "-" + sufijo;
            sufijo++;
        }

        return codigo;
    }

    /*
     * Obtiene el identificador del usuario actualmente autenticado.
     *
     * Se utiliza para que los triggers de la base de datos puedan
     * registrar quién realizó cada modificación.
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

        String cedula = authentication.getName();

        Usuario usuario = usuarioRepository
                .findByCedula(cedula)
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario autenticado no existe en el sistema."
                ));

        return usuario.getId();
    }

    private PeriodoAcademicoResponse mapearAResponse(
            PeriodoAcademico periodo
    ) {
        return new PeriodoAcademicoResponse(
                periodo.getId(),
                periodo.getCodigo(),
                periodo.getNombre(),
                periodo.getFechaInicio(),
                periodo.getFechaFin(),
                periodo.getFechaInicioMatricula(),
                periodo.getFechaFinMatricula(),
                periodo.getEstado()
        );
    }
}
