package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Beca;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.TiposBeca;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.BecaRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.TiposBecaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.presentation.dto.request.BecaActualizarRequest;
import cr.acusab.una.sigac.presentation.dto.request.BecaAsignarRequest;
import cr.acusab.una.sigac.presentation.dto.response.BecaResponse;
import cr.acusab.una.sigac.presentation.dto.response.TipoBecaResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/*
 * HU-015 - Asignar beca a un estudiante.
 *
 * Calcula el descuento y el monto a pagar a partir de la mensualidad
 * del estudiante (base + instrumentos adicionales), pero no procesa
 * pagos: SIGAC únicamente registra el porcentaje de beca vigente y
 * expone el monto resultante para que otras pantallas (mensualidades)
 * lo utilicen.
 */
@Service
public class BecaService {

    private static final List<BigDecimal> PORCENTAJES_VALIDOS = List.of(
            BigDecimal.valueOf(0),
            BigDecimal.valueOf(25),
            BigDecimal.valueOf(50),
            BigDecimal.valueOf(75),
            BigDecimal.valueOf(100)
    );

    public static final String MENSAJE_INCLUSION_YA_BECADO =
            "Los estudiantes de inclusión ya tienen beca del 100%";
    public static final String MOTIVO_BECA_INCLUSION = "Estudiante de inclusión";

    private final BecaRepository becaRepository;
    private final EstudianteRepository estudianteRepository;
    private final TiposBecaRepository tiposBecaRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final MensualidadCalculoService mensualidadCalculoService;
    private final UsuarioRepository usuarioRepository;
    private final BecaVigenteService becaVigenteService;

    public BecaService(
            BecaRepository becaRepository,
            EstudianteRepository estudianteRepository,
            TiposBecaRepository tiposBecaRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            MensualidadCalculoService mensualidadCalculoService,
            UsuarioRepository usuarioRepository,
            BecaVigenteService becaVigenteService
    ) {
        this.becaRepository = becaRepository;
        this.estudianteRepository = estudianteRepository;
        this.tiposBecaRepository = tiposBecaRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.mensualidadCalculoService = mensualidadCalculoService;
        this.usuarioRepository = usuarioRepository;
        this.becaVigenteService = becaVigenteService;
    }

    /*
     * HU-015 - Asignar una beca a un estudiante para un período académico.
     */
    @Transactional
    public BecaResponse asignar(BecaAsignarRequest request) {

        Estudiante estudiante = estudianteRepository.findById(request.getIdEstudiante())
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe."));

        if (!Boolean.TRUE.equals(estudiante.getActivo())) {
            throw new BusinessException("El estudiante indicado se encuentra inactivo.");
        }

        // Un estudiante de inclusión ya tiene la beca permanente del 100%.
        if (esInclusion(estudiante)) {
            throw new BusinessException(MENSAJE_INCLUSION_YA_BECADO);
        }

        TiposBeca tipoBeca = tiposBecaRepository.findByIdAndActivoTrue(request.getIdTipoBeca())
                .orElseThrow(() -> new BusinessException("El tipo de beca indicado no existe o está inactivo."));
        validarTipoBecaAsignable(tipoBeca);

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(request.getIdPeriodo())
                .orElseThrow(() -> new BusinessException("El período académico indicado no existe."));

        BigDecimal porcentaje = validarPorcentaje(request.getPorcentaje());

        if (becaRepository.existsByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(
                estudiante.getId(), periodo.getId())) {
            throw new DuplicateResourceException(
                    "El estudiante ya tiene una beca activa asignada para este período académico. "
                            + "Modifique la beca existente en lugar de asignar una nueva.");
        }

        LocalDate fechaInicio = request.getFechaInicio() != null
                ? request.getFechaInicio()
                : LocalDate.now();

        Usuario usuarioAsigna = obtenerUsuarioAutenticado();

        Instant ahora = Instant.now();

        Beca beca = new Beca();
        beca.setIdEstudiante(estudiante);
        beca.setIdTipoBeca(tipoBeca);
        beca.setIdPeriodo(periodo);
        beca.setPorcentaje(porcentaje);
        beca.setFechaInicio(fechaInicio);
        beca.setMotivo(normalizarNullable(request.getMotivo()));
        beca.setActiva(true);
        beca.setIdUsuarioAsigna(usuarioAsigna);
        beca.setCreadoEn(ahora);
        beca.setActualizadoEn(ahora);

        beca = becaRepository.save(beca);

        return construirRespuesta(beca);
    }

    /*
     * HU-015 - Modificar una beca previamente asignada.
     */
    @Transactional
    public BecaResponse actualizar(Long idBeca, BecaActualizarRequest request) {

        Beca beca = becaRepository.findById(idBeca)
                .orElseThrow(() -> new BusinessException("La beca indicada no existe."));

        validarNoEsPermanente(beca, "modificar");

        TiposBeca tipoBeca = tiposBecaRepository.findByIdAndActivoTrue(request.getIdTipoBeca())
                .orElseThrow(() -> new BusinessException("El tipo de beca indicado no existe o está inactivo."));
        validarTipoBecaAsignable(tipoBeca);

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(request.getIdPeriodo())
                .orElseThrow(() -> new BusinessException("El período académico indicado no existe."));

        BigDecimal porcentaje = validarPorcentaje(request.getPorcentaje());

        // Si la beca quedó sin período (se borró el período: ON DELETE SET
        // NULL), al editarla se le asigna el período indicado.
        boolean cambiaPeriodo = beca.getIdPeriodo() == null
                || !Objects.equals(beca.getIdPeriodo().getId(), periodo.getId());

        if (cambiaPeriodo && becaRepository.existsByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(
                beca.getIdEstudiante().getId(), periodo.getId())) {
            throw new DuplicateResourceException(
                    "El estudiante ya tiene otra beca activa asignada para ese período académico.");
        }

        if (request.getFechaFin() != null && request.getFechaFin().isBefore(beca.getFechaInicio())) {
            throw new BusinessException(
                    "La fecha de fin no puede ser anterior a la fecha de inicio de la beca.");
        }

        beca.setIdTipoBeca(tipoBeca);
        beca.setIdPeriodo(periodo);
        beca.setPorcentaje(porcentaje);
        beca.setFechaFin(request.getFechaFin());
        beca.setMotivo(normalizarNullable(request.getMotivo()));
        beca.setActualizadoEn(Instant.now());

        beca = becaRepository.save(beca);

        return construirRespuesta(beca);
    }

    /*
     * HU-015 - Eliminar una beca asignada a un estudiante.
     */
    @Transactional
    public void eliminar(Long idBeca) {

        Beca beca = becaRepository.findById(idBeca)
                .orElseThrow(() -> new BusinessException("La beca indicada no existe."));

        validarNoEsPermanente(beca, "eliminar");

        becaRepository.delete(beca);
    }

    @Transactional(readOnly = true)
    public BecaResponse obtener(Long idBeca) {
        Beca beca = becaRepository.findById(idBeca)
                .orElseThrow(() -> new BusinessException("La beca indicada no existe."));
        return construirRespuesta(beca);
    }

    @Transactional(readOnly = true)
    public List<BecaResponse> listarPorEstudiante(Long idEstudiante) {

        if (!estudianteRepository.existsById(idEstudiante)) {
            throw new BusinessException("El estudiante indicado no existe.");
        }

        return becaRepository.findByIdEstudiante_IdOrderByFechaInicioDesc(idEstudiante)
                .stream()
                .map(this::construirRespuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TipoBecaResponse> listarTiposBeca() {
        // La beca de inclusión no se elige a mano: depende del tipo de estudiante.
        return tiposBecaRepository.findByActivoTrueOrderByNombreAsc()
                .stream()
                .filter(t -> !BecaVigenteService.CODIGO_TIPO_BECA_INCLUSION.equalsIgnoreCase(t.getCodigo()))
                .map(t -> new TipoBecaResponse(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion()))
                .toList();
    }

    /*
     * Beca permanente de inclusión: 100%, sin período, desde hoy y sin fecha
     * de fin. Se llama al registrar un estudiante de inclusión o al cambiarlo
     * a ese tipo. Si ya tiene una activa se devuelve esa (máximo una).
     */
    @Transactional
    public BecaResponse asignarBecaPermanenteInclusion(Estudiante estudiante) {

        Beca existente = becaVigenteService.obtenerBecaPermanenteActiva(estudiante.getId()).orElse(null);
        if (existente != null) {
            return construirRespuesta(existente);
        }

        TiposBeca tipoInclusion = tiposBecaRepository
                .findByCodigo(BecaVigenteService.CODIGO_TIPO_BECA_INCLUSION)
                .orElseThrow(() -> new BusinessException(
                        "No está configurado el tipo de beca INCLUSION. "
                                + "Ejecute el script 2026-11-17_tipo_estudiante_inclusion.sql."));

        Instant ahora = Instant.now();

        Beca beca = new Beca();
        beca.setIdEstudiante(estudiante);
        beca.setIdTipoBeca(tipoInclusion);
        beca.setIdPeriodo(null);
        beca.setPorcentaje(BigDecimal.valueOf(100));
        beca.setFechaInicio(LocalDate.now());
        beca.setFechaFin(null);
        beca.setMotivo(MOTIVO_BECA_INCLUSION);
        beca.setActiva(true);
        beca.setIdUsuarioAsigna(usuarioAutenticadoOpcional());
        beca.setCreadoEn(ahora);
        beca.setActualizadoEn(ahora);

        return construirRespuesta(becaRepository.save(beca));
    }

    /*
     * Al pasar un estudiante de inclusión a regular se cierra su beca
     * permanente (activa = false, fecha_fin = hoy). Nunca se borra, para
     * conservar el historial.
     */
    @Transactional
    public void cerrarBecaPermanenteInclusion(Long idEstudiante) {
        LocalDate hoy = LocalDate.now();
        List<Beca> activas = becaRepository
                .findByIdEstudiante_IdAndIdTipoBeca_CodigoAndIdPeriodoIsNullAndActivaTrue(
                        idEstudiante, BecaVigenteService.CODIGO_TIPO_BECA_INCLUSION);
        for (Beca beca : activas) {
            beca.setActiva(false);
            // ck_becas_fechas: la fecha de fin no puede ser anterior al inicio
            boolean iniciaDespues = beca.getFechaInicio() != null && beca.getFechaInicio().isAfter(hoy);
            beca.setFechaFin(iniciaDespues ? beca.getFechaInicio() : hoy);
            beca.setActualizadoEn(Instant.now());
            becaRepository.save(beca);
        }
    }

    private boolean esInclusion(Estudiante estudiante) {
        return estudiante.getIdTipoEstudiante() != null && estudiante.getIdTipoEstudiante().esInclusion();
    }

    private void validarNoEsPermanente(Beca beca, String accion) {
        if (BecaVigenteService.esBecaPermanenteInclusion(beca)) {
            throw new BusinessException("La beca permanente de inclusión no se puede " + accion
                    + ": se maneja con el tipo de estudiante.");
        }
    }

    private void validarTipoBecaAsignable(TiposBeca tipoBeca) {
        if (BecaVigenteService.CODIGO_TIPO_BECA_INCLUSION.equalsIgnoreCase(tipoBeca.getCodigo())) {
            throw new BusinessException("La beca de inclusión se asigna automáticamente "
                    + "al marcar al estudiante como de inclusión.");
        }
    }

    /*
     * El porcentaje de beca solo admite los valores institucionales
     * definidos: 0 (sin beca), 25, 50, 75 y 100 (exoneración de créditos).
     */
    private BigDecimal validarPorcentaje(BigDecimal porcentaje) {
        if (porcentaje == null) {
            throw new BusinessException("El porcentaje de beca es obligatorio.");
        }

        if (porcentaje.compareTo(BigDecimal.ZERO) < 0
                || porcentaje.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(
                    "El porcentaje de beca no puede ser menor a 0% ni superior a 100%.");
        }

        boolean valido = PORCENTAJES_VALIDOS.stream()
                .anyMatch(valor -> valor.compareTo(porcentaje) == 0);

        if (!valido) {
            throw new BusinessException(
                    "El porcentaje de beca debe ser 0%, 25%, 50%, 75% o 100%.");
        }

        return porcentaje;
    }

    private BecaResponse construirRespuesta(Beca beca) {

        Estudiante estudiante = beca.getIdEstudiante();

        BigDecimal montoBase = mensualidadCalculoService.calcularMontoBase(estudiante.getId());
        BigDecimal montoDescuento = mensualidadCalculoService.calcularDescuento(montoBase, beca.getPorcentaje());
        BigDecimal montoAPagar = montoBase.subtract(montoDescuento);

        BecaResponse response = new BecaResponse();
        response.setIdBeca(beca.getId());
        response.setIdEstudiante(estudiante.getId());
        response.setNombreEstudiante(nombreCompleto(estudiante));

        response.setIdTipoBeca(beca.getIdTipoBeca().getId());
        response.setCodigoTipoBeca(beca.getIdTipoBeca().getCodigo());
        response.setNombreTipoBeca(beca.getIdTipoBeca().getNombre());

        if (beca.getIdPeriodo() != null) {
            response.setIdPeriodo(beca.getIdPeriodo().getId());
            response.setCodigoPeriodo(beca.getIdPeriodo().getCodigo());
        }

        response.setPorcentaje(beca.getPorcentaje());
        response.setMontoMensualidadBase(montoBase);
        response.setMontoDescuento(montoDescuento);
        response.setMontoAPagar(montoAPagar);

        response.setFechaInicio(beca.getFechaInicio());
        response.setFechaFin(beca.getFechaFin());
        response.setActiva(beca.getActiva());
        response.setMotivo(beca.getMotivo());
        response.setPermanente(BecaVigenteService.esBecaPermanenteInclusion(beca));

        return response;
    }

    private String nombreCompleto(Estudiante estudiante) {
        return String.join(
                " ",
                estudiante.getNombres(),
                estudiante.getPrimerApellido(),
                estudiante.getSegundoApellido() != null ? estudiante.getSegundoApellido() : ""
        ).trim().replaceAll("\\s+", " ");
    }

    /*
     * Obtiene el usuario autenticado para registrar quién asignó la beca.
     */
    private Usuario obtenerUsuarioAutenticado() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }

        String cedula = authentication.getName();

        return usuarioRepository.findByCedula(cedula)
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario autenticado no existe en el sistema."));
    }

    /*
     * Igual que obtenerUsuarioAutenticado, pero sin fallar: la beca de
     * inclusión se crea como parte de otra operación y no debe impedirla.
     */
    private Usuario usuarioAutenticadoOpcional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return null;
        }
        return usuarioRepository.findByCedula(authentication.getName()).orElse(null);
    }

    private String normalizarNullable(String value) {
        if (value == null) return null;
        String normalizado = value.trim().replaceAll("\\s+", " ");
        return normalizado.isBlank() ? null : normalizado;
    }
}
