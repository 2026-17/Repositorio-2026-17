// backend/src/main/java/cr/acusab/una/sigac/logic/service/UsuarioService.java
package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstadoUsuarioRepository;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.UsuarioCreateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstadoUsuarioResponse;
import cr.acusab.una.sigac.presentation.dto.response.RoleResponse;
import cr.acusab.una.sigac.presentation.dto.response.UsuarioResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final EstadoUsuarioRepository estadoUsuarioRepository;
    private final ContrasenaTemporalService contrasenaTemporalService;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            RoleRepository roleRepository,
            EstadoUsuarioRepository estadoUsuarioRepository,
            ContrasenaTemporalService contrasenaTemporalService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.estadoUsuarioRepository = estadoUsuarioRepository;
        this.contrasenaTemporalService = contrasenaTemporalService;
    }

    @Transactional
    public UsuarioResponse crearUsuario(UsuarioCreateRequest request) {
        if (usuarioRepository.findByCedula(request.getCedula()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cédula ya está registrada");
        }

        if (usuarioRepository.findByCorreo(request.getCorreo()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        Role rol = roleRepository.findById(request.getIdRol())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no existe"));

        EstadosUsuario estado = estadoUsuarioRepository.findById(request.getIdEstadoUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estado de usuario no existe"));

        Instant now = Instant.now();

        Usuario usuario = new Usuario();
        usuario.setCedula(request.getCedula());
        usuario.setNombre(request.getNombre());
        usuario.setCorreo(request.getCorreo());
        contrasenaTemporalService.asignar(usuario);
        usuario.setIdRol(rol);
        usuario.setIdEstadoUsuario(estado);
        usuario.setIntentosFallidos((short) 0);
        usuario.setBloqueadoHasta(null);
        usuario.setUltimoAcceso(null);
        usuario.setCreadoEn(now);
        usuario.setActualizadoEn(now);

        Usuario saved = usuarioRepository.save(usuario);

        return new UsuarioResponse(
                saved.getId(),
                saved.getNombre(),
                saved.getCorreo(),
                new RoleResponse(rol.getId(), rol.getCodigo(), rol.getNombre()),
                new EstadoUsuarioResponse(estado.getId(), estado.getCodigo(), estado.getNombre()),
                saved.getRequiereCambioContrasena(),
                saved.getUltimoAcceso(),
                saved.getCreadoEn()
        );
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(usuario -> new UsuarioResponse(
                        usuario.getId(),
                        usuario.getNombre(),
                        usuario.getCorreo(),
                        new RoleResponse(usuario.getIdRol().getId(), usuario.getIdRol().getCodigo(), usuario.getIdRol().getNombre()),
                        new EstadoUsuarioResponse(usuario.getIdEstadoUsuario().getId(), usuario.getIdEstadoUsuario().getCodigo(), usuario.getIdEstadoUsuario().getNombre()),
                        usuario.getRequiereCambioContrasena(),
                        usuario.getUltimoAcceso(),
                        usuario.getCreadoEn()
                ))
                .toList();
    }
}