package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Bitacora;
import cr.acusab.una.sigac.data.entity.EstadoMatricula;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.Matricula;
import cr.acusab.una.sigac.data.entity.MatriculaDetalle;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.BitacoraRepository;
import cr.acusab.una.sigac.data.repository.EstadoMatriculaRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.MatriculaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.DarBajaEstudianteRequest;
import cr.acusab.una.sigac.presentation.dto.response.DarBajaEstudianteResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class BajaEstudianteService {

    private static final String ESTADO_REGISTRADA = "REGISTRADA";
    private static final String ESTADO_CANCELADA = "CANCELADA";

    private final EstudianteRepository estudianteRepository;
    private final MatriculaRepository matriculaRepository;
    private final MatriculaDetalleRepository matriculaDetalleRepository;
    private final EstadoMatriculaRepository estadoMatriculaRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraRepository bitacoraRepository;
    private final CuentaUsuarioService cuentaUsuarioService;
    private final ObjectMapper objectMapper;

    public BajaEstudianteService(
            EstudianteRepository estudianteRepository,
            MatriculaRepository matriculaRepository,
            MatriculaDetalleRepository matriculaDetalleRepository,
            EstadoMatriculaRepository estadoMatriculaRepository,
            UsuarioRepository usuarioRepository,
            BitacoraRepository bitacoraRepository,
            CuentaUsuarioService cuentaUsuarioService,
            ObjectMapper objectMapper
    ) {
        this.estudianteRepository = estudianteRepository;
        this.matriculaRepository = matriculaRepository;
        this.matriculaDetalleRepository = matriculaDetalleRepository;
        this.estadoMatriculaRepository = estadoMatriculaRepository;
        this.usuarioRepository = usuarioRepository;
        this.bitacoraRepository = bitacoraRepository;
        this.cuentaUsuarioService = cuentaUsuarioService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DarBajaEstudianteResponse darDeBaja(
            Long idEstudiante,
            DarBajaEstudianteRequest request
    ) {
        if (request == null || request.motivoBaja() == null || request.motivoBaja().isBlank()) {
            throw new BusinessException("El motivo de baja es obligatorio.");
        }

        String motivo = request.motivoBaja().trim();

        if (motivo.length() > 500) {
            throw new BusinessException("El motivo de baja no puede superar 500 caracteres.");
        }

        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe."));

        if (!Boolean.TRUE.equals(estudiante.getActivo())) {
            throw new BusinessException("El estudiante indicado ya se encuentra inactivo.");
        }

        Usuario usuario = obtenerUsuarioAutenticado();
        Instant fechaBaja = Instant.now();
        LocalDate fechaRetiro = fechaBaja.atZone(ZoneId.systemDefault()).toLocalDate();

        EstadoMatricula cancelada = estadoMatriculaRepository
                .findByCodigoAndActivoTrue(ESTADO_CANCELADA)
                .orElseThrow(() -> new BusinessException(
                        "No está configurado el estado CANCELADA de matrícula."
                ));

        List<Matricula> matriculasActivas = matriculaRepository
                .findByEstudianteAndEstado(idEstudiante, ESTADO_REGISTRADA);

        int detallesRetirados = 0;

        for (Matricula matricula : matriculasActivas) {
            List<MatriculaDetalle> detalles = matriculaDetalleRepository
                    .findByMatricula_IdAndRetiradoFalse(matricula.getId());

            for (MatriculaDetalle detalle : detalles) {
                detalle.setRetirado(true);
                detalle.setFechaRetiro(fechaRetiro);
                detalle.setMotivoRetiro(motivo);
                matriculaDetalleRepository.save(detalle);
                detallesRetirados++;
                registrarBitacora(
                        usuario.getId(),
                        "matriculas_detalles",
                        detalle.getId(),
                        Map.of("retirado", false),
                        Map.of("retirado", true, "motivo_retiro", motivo)
                );
            }

            matricula.setEstado(cancelada);
            matriculaRepository.save(matricula);

            registrarBitacora(
                    usuario.getId(),
                    "matriculas",
                    matricula.getId(),
                    Map.of("estado", ESTADO_REGISTRADA),
                    Map.of("estado", ESTADO_CANCELADA, "motivo_baja", motivo)
            );
        }

        estudiante.setActivo(false);
        estudiante.setMotivoBaja(motivo);
        estudiante.setFechaBaja(fechaBaja);
        estudiante.setIdUsuarioUltimaModificacion(usuario.getId());
        estudianteRepository.save(estudiante);

        // La cuenta del estudiante queda INACTIVA: ya no puede iniciar sesión
        // y sus tokens vigentes dejan de servir (ver JwtAuthenticationFilter).
        cuentaUsuarioService.inactivar(estudiante.getUsuario());

        return new DarBajaEstudianteResponse(
                estudiante.getId(),
                "INACTIVO",
                fechaBaja,
                motivo,
                matriculasActivas.size(),
                detallesRetirados
        );
    }

    private Usuario obtenerUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new BusinessException(
                    "No se pudo identificar al usuario autenticado."
            );
        }

        return usuarioRepository.findByCedula(authentication.getName())
                .orElseThrow(() -> new BusinessException(
                        "No se encontró el usuario administrativo autenticado."
                ));
    }

    private void registrarBitacora(
            Long idUsuario,
            String tabla,
            Long idRegistro,
            Map<String, ?> datosAnteriores,
            Map<String, ?> datosNuevos
    ) {
        Bitacora bitacora = new Bitacora();
        bitacora.setIdUsuario(idUsuario);
        bitacora.setTablaAfectada(tabla);
        bitacora.setIdRegistro(idRegistro);
        bitacora.setAccion("UPDATE");
        // ObjectMapper escapa cualquier carácter (incluidos los de control que
        // trae un texto pegado de Word), así el JSON siempre es válido.
        bitacora.setDatosAnteriores(objectMapper.writeValueAsString(datosAnteriores));
        bitacora.setDatosNuevos(objectMapper.writeValueAsString(datosNuevos));
        bitacora.setFechaEvento(Instant.now());
        bitacoraRepository.save(bitacora);
    }
}
