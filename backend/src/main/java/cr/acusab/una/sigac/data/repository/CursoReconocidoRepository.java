package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.CursoReconocido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CursoReconocidoRepository extends JpaRepository<CursoReconocido, Long> {

    // Todo el avance reconocido de un estudiante (cursos normales y de instrumento).
    List<CursoReconocido> findByIdEstudiante(Long idEstudiante);

    // Avance reconocido en una agrupación (Orquesta IV de la carga inicial).
    List<CursoReconocido> findByIdEstudianteAndIdAgrupacion(Long idEstudiante, Long idAgrupacion);

    // Avance reconocido en un instrumento.
    List<CursoReconocido> findByIdEstudianteAndIdInstrumento(Long idEstudiante, Integer idInstrumento);

    // Nivel de ingreso anterior de un instrumento (se reemplaza al registrar uno nuevo).
    List<CursoReconocido> findByIdEstudianteAndIdInstrumentoAndOrigen(
            Long idEstudiante, Integer idInstrumento, String origen);
}
