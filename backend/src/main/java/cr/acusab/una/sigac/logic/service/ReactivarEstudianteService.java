package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.response.ReactivarEstudianteResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReactivarEstudianteService {

    private final EstudianteRepository estudianteRepository;
    private final UsuarioRepository usuarioRepository;

    public ReactivarEstudianteService(
            EstudianteRepository estudianteRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.estudianteRepository = estudianteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public ReactivarEstudianteResponse reactivar(Long idEstudiante) {
        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
                .orElseThrow(() -> new BusinessException(
                        "El estudiante indicado no existe."
                ));

        if (Boolean.TRUE.equals(estudiante.getActivo())) {
            throw new BusinessException(
                    "El estudiante ya se encuentra activo."
            );
        }

        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new BusinessException(
                    "No se pudo identificar al usuario autenticado."
            );
        }

        Usuario usuario = usuarioRepository.findByCedula(authentication.getName())
                .orElseThrow(() -> new BusinessException(
                        "No se encontró el usuario administrativo autenticado."
                ));

        estudiante.setActivo(true);
        estudiante.setMotivoBaja(null);
        estudiante.setFechaBaja(null);
        estudiante.setIdUsuarioUltimaModificacion(usuario.getId());
        estudianteRepository.save(estudiante);

        return new ReactivarEstudianteResponse(
                estudiante.getId(),
                "ACTIVO",
                "Estudiante reactivado correctamente. Las matrículas y cursos cancelados no se restauran automáticamente."
        );
    }
}
