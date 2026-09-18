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

    private final BecaRepository becaRepository;
    private final EstudianteRepository estudianteRepository;
    private final TiposBecaRepository tiposBecaRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final MensualidadCalculoService mensualidadCalculoService;
    private final UsuarioRepository usuarioRepository;

    public BecaService(
            BecaRepository becaRepository,
            EstudianteRepository estudianteRepository,
            TiposBecaRepository tiposBecaRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            MensualidadCalculoService mensualidadCalculoService,
            UsuarioRepository usuarioRepository
    ) {
        this.becaRepository = becaRepository;
        this.estudianteRepository = estudianteRepository;
        this.tiposBecaRepository = tiposBecaRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.mensualidadCalculoService = mensualidadCalculoService;
        this.usuarioRepository = usuarioRepository;
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

        TiposBeca tipoBeca = tiposBecaRepository.findByIdAndActivoTrue(request.getIdTipoBeca())
                .orElseThrow(() -> new BusinessException("El tipo de beca indicado no existe o está inactivo."));

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

        TiposBeca tipoBeca = tiposBecaRepository.findByIdAndActivoTrue(request.getIdTipoBeca())
                .orElseThrow(() -> new BusinessException("El tipo de beca indicado no existe o está inactivo."));

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(request.getIdPeriodo())
                .orElseThrow(() -> new BusinessException("El período académico indicado no existe."));

        BigDecimal porcentaje = validarPorcentaje(request.getPorcentaje());

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
        return tiposBecaRepository.findByActivoTrueOrderByNombreAsc()
                .stream()
                .map(t -> new TipoBecaResponse(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion()))
                .toList();
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

    private String normalizarNullable(String value) {
        if (value == null) return null;
        String normalizado = value.trim().replaceAll("\\s+", " ");
        return normalizado.isBlank() ? null : normalizado;
    }
}
