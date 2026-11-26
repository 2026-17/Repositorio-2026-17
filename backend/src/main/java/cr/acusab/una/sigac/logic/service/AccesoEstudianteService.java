package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.response.ContrasenaTemporalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Acceso a SIGAC de un estudiante cuando el correo no le llega (por ejemplo,
 * el código de «Olvidé mi contraseña» cae en Correo no deseado): la
 * administración genera una contraseña temporal y se la entrega en persona.
 */
@Service
public class AccesoEstudianteService {

    private static final Logger log = LoggerFactory.getLogger(AccesoEstudianteService.class);

    private final EstudianteRepository estudianteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContrasenaTemporalService contrasenaTemporalService;

    public AccesoEstudianteService(EstudianteRepository estudianteRepository,
                                   UsuarioRepository usuarioRepository,
                                   ContrasenaTemporalService contrasenaTemporalService) {
        this.estudianteRepository = estudianteRepository;
        this.usuarioRepository = usuarioRepository;
        this.contrasenaTemporalService = contrasenaTemporalService;
    }

    /**
     * Genera la contraseña temporal del estudiante (sin correo). La anterior
     * deja de funcionar y al iniciar sesión se le pide cambiarla.
     *
     * @param cedulaAdministrativo cédula del usuario que lo hace (para el registro)
     */
    @Transactional
    public ContrasenaTemporalResponse generarContrasenaTemporal(Long idEstudiante, String cedulaAdministrativo) {
        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe."));

        Usuario usuario = estudiante.getUsuario();
        if (usuario == null) {
            throw new BusinessException("El estudiante no tiene una cuenta de acceso a SIGAC.");
        }
        String estado = usuario.getIdEstadoUsuario() != null ? usuario.getIdEstadoUsuario().getCodigo() : null;
        if (!CuentaUsuarioService.ESTADO_ACTIVO.equalsIgnoreCase(estado)) {
            throw new BusinessException("La cuenta del estudiante no está activa. "
                    + "Reactívela antes de generar una contraseña temporal.");
        }

        String contrasena = contrasenaTemporalService.asignarSinCorreo(usuario);
        usuarioRepository.save(usuario);

        Long idAdministrativo = cedulaAdministrativo == null ? null
                : usuarioRepository.findByCedula(cedulaAdministrativo).map(Usuario::getId).orElse(null);
        // Nunca se registra la contraseña.
        log.info("Contraseña temporal generada sin correo: estudiante {} (usuario {}), por el usuario administrativo {}",
                idEstudiante, usuario.getId(), idAdministrativo);

        return new ContrasenaTemporalResponse(contrasena, Instant.now());
    }
}
