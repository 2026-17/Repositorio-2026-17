package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoResponseDTO;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CursoService {

    private final CursoRepository cursoRepository;

    public CursoService(CursoRepository cursoRepository) {
        this.cursoRepository = cursoRepository;
    }

    public CursoResponseDTO crear(@Valid CursoRequestDTO request) {
        // Validaciones de negocio
        if (cursoRepository.existsByCodigo(request.getCodigo())) {
            throw new IllegalArgumentException("Ya existe un curso con el código: " + request.getCodigo());
        }

        Curso curso = new Curso();
        curso.setCodigo(request.getCodigo());
        curso.setNombre(request.getNombre());
        curso.setDescripcion(request.getDescripcion());
        curso.setHorasTotales(request.getHorasTotales());
        curso.setActivo(true); // todo curso se crea Activo
        curso.setCreadoEn(Instant.now());
        curso.setActualizadoEn(Instant.now());
        // Nota: id_usuario_ultima_modificacion vendría del usuario autenticado
        // (HU-034), no del formulario. Eso se resuelve con Spring Security.

        Curso guardado = cursoRepository.save(curso);
        // El trigger trg_cursos_ai_bitacora ya deja el registro en bitácora
        // automáticamente al hacer este INSERT — no hace falta código extra aquí.

        return mapearAResponse(guardado);
    }

    private CursoResponseDTO mapearAResponse(Curso curso) {
        CursoResponseDTO dto = new CursoResponseDTO();
        dto.setId(curso.getId());
        dto.setCodigo(curso.getCodigo());
        dto.setNombre(curso.getNombre());
        dto.setDescripcion(curso.getDescripcion());
        dto.setHorasTotales(curso.getHorasTotales());
        dto.setActivo(curso.getActivo());
        return dto;
    }
}