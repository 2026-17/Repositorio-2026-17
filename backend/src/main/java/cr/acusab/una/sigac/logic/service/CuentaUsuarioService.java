package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstadoUsuarioRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * Mantiene la cuenta de usuario de una persona (estudiante o profesor) al
 * día con sus datos: estado ACTIVO/INACTIVO al dar de baja o reactivar, y
 * cédula y correo al editar sus datos personales.
 */
@Service
public class CuentaUsuarioService {

    public static final String ESTADO_ACTIVO = "ACTIVO";
    public static final String ESTADO_INACTIVO = "INACTIVO";

    private final UsuarioRepository usuarioRepository;
    private final EstadoUsuarioRepository estadoUsuarioRepository;

    public CuentaUsuarioService(UsuarioRepository usuarioRepository,
                                EstadoUsuarioRepository estadoUsuarioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.estadoUsuarioRepository = estadoUsuarioRepository;
    }

    public void activar(Usuario usuario) {
        cambiarEstado(usuario, ESTADO_ACTIVO);
    }

    public void inactivar(Usuario usuario) {
        cambiarEstado(usuario, ESTADO_INACTIVO);
    }

    private void cambiarEstado(Usuario usuario, String codigo) {
        if (usuario == null) return;
        EstadosUsuario estado = estadoUsuarioRepository.findByCodigoIgnoreCase(codigo)
                .orElseThrow(() -> new BusinessException(
                        "No está configurado el estado " + codigo + " de usuario."));
        usuario.setIdEstadoUsuario(estado);
        usuario.setActualizadoEn(Instant.now());
        usuarioRepository.save(usuario);
    }

    /**
     * Copia la cédula y el correo nuevos a la cuenta. Antes revisa que
     * ninguna OTRA cuenta los tenga. Un valor null no se modifica.
     */
    public void sincronizarIdentidad(Usuario usuario, String cedula, String correo) {
        if (usuario == null) return;

        boolean cambiaCedula = cedula != null && !cedula.equals(usuario.getCedula());
        boolean cambiaCorreo = correo != null && !correo.equalsIgnoreCase(usuario.getCorreo());
        if (!cambiaCedula && !cambiaCorreo) return;

        if (cambiaCedula) {
            usuarioRepository.findByCedula(cedula)
                    .filter(otro -> !Objects.equals(otro.getId(), usuario.getId()))
                    .ifPresent(otro -> {
                        throw new DuplicateResourceException("La cédula ya está registrada en otra cuenta.");
                    });
            usuario.setCedula(cedula);
        }
        if (cambiaCorreo) {
            usuarioRepository.findByCorreo(correo)
                    .filter(otro -> !Objects.equals(otro.getId(), usuario.getId()))
                    .ifPresent(otro -> {
                        throw new DuplicateResourceException("El correo ya está registrado en otra cuenta.");
                    });
            usuario.setCorreo(correo);
        }
        usuario.setActualizadoEn(Instant.now());
        usuarioRepository.save(usuario);
    }
}
