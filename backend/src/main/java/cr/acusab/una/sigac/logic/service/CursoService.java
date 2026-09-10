package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CursoService {

    private final CursoRepository cursoRepository;
    private final PlanEstudioRepository planEstudioRepository;
    private final MallaCurricularRepository mallaCurricularRepository;
    private final GrupoRepository grupoRepository;

    public CursoService(CursoRepository cursoRepository,
                        PlanEstudioRepository planEstudioRepository,
                        MallaCurricularRepository mallaCurricularRepository,
                        GrupoRepository grupoRepository) {
        this.cursoRepository = cursoRepository;
        this.planEstudioRepository = planEstudioRepository;
        this.mallaCurricularRepository = mallaCurricularRepository;
        this.grupoRepository = grupoRepository;
    }

    // @Transactional = si algo falla a la mitad, se revierte TODO (curso, malla y grupo)
    @Transactional
    public CursoResponseDTO crear(CursoRequestDTO request) {

        // 1) Validar código único (regla de HU-028)
        if (cursoRepository.existsByCodigo(request.getCodigo())) {
            throw new IllegalArgumentException("Ya existe un curso con este código.");
        }

        // 2) Buscar el plan de estudio vigente para el programa elegido
        PlanEstudio plan = planEstudioRepository
                .findFirstByIdProgramaAndVigenteTrue(request.getIdPrograma())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El programa seleccionado no tiene un plan de estudio vigente."));

        // 3) Crear el curso (tabla "cursos")
        Curso curso = new Curso();
        curso.setCodigo(request.getCodigo());
        curso.setNombre(request.getNombre());
        curso.setDescripcion(request.getDescripcion());
        curso.setHorasTotales(request.getHorasTotales());
        curso.setActivo(true); // todo curso se crea Activo
        curso.setCreadoEn(Instant.now());
        curso.setActualizadoEn(Instant.now());
        Curso cursoGuardado = cursoRepository.save(curso);
        // trg_cursos_ai_bitacora ya registra esto en la bitácora automáticamente

        // 4) Ubicarlo en la malla curricular (tabla "malla_curricular")
        // El "orden_en_nivel" se calcula solo: cuenta cuántos cursos hay
        // ya en ese plan+nivel y le pone el siguiente número.
        int cantidadEnNivel = mallaCurricularRepository
                .countByIdPlanEstudioAndNivel(plan.getId(), request.getNivel());

        MallaCurricular malla = new MallaCurricular();
        malla.setIdPlanEstudio(plan.getId());
        malla.setIdCurso(cursoGuardado.getId());
        malla.setNivel(request.getNivel());
        malla.setOrdenEnNivel((short) (cantidadEnNivel + 1));
        malla.setObligatorio(true);
        malla.setActivo(true);
        malla.setCreadoEn(Instant.now());
        malla.setActualizadoEn(Instant.now());
        MallaCurricular mallaGuardada = mallaCurricularRepository.save(malla);
        // trg_malla_curricular_ai_bitacora ya registra esto en la bitácora

        // 5) Abrir el primer grupo (tabla "grupos")
        // El código del grupo se genera automático: "{codigo del curso}-A"
        String codigoGrupo = request.getCodigo() + "-A";

        Grupo grupo = new Grupo();
        grupo.setIdPeriodo(request.getIdPeriodo());
        grupo.setIdCurso(cursoGuardado.getId());
        grupo.setCodigo(codigoGrupo);
        grupo.setCupoMaximo(request.getCupos());
        grupo.setModalidad("PRESENCIAL");
        grupo.setEstado("PLANIFICADO");
        grupo.setCreadoEn(Instant.now());
        grupo.setActualizadoEn(Instant.now());
        Grupo grupoGuardado = grupoRepository.save(grupo);
        // Nota: no hay trigger de bitácora para "grupos" en el .sql que revisamos;
        // confirmar con el equipo si hace falta agregarlo.

        // 6) Armar la respuesta para React
        return mapearAResponse(cursoGuardado, mallaGuardada, grupoGuardado);
    }

    private CursoResponseDTO mapearAResponse(Curso curso, MallaCurricular malla, Grupo grupo) {
        CursoResponseDTO dto = new CursoResponseDTO();
        dto.setIdCurso(curso.getId());
        dto.setCodigo(curso.getCodigo());
        dto.setNombre(curso.getNombre());
        dto.setDescripcion(curso.getDescripcion());
        dto.setHorasTotales(curso.getHorasTotales());
        dto.setActivo(curso.getActivo());

        dto.setIdMalla(malla.getId());
        dto.setNivel(malla.getNivel());

        dto.setIdGrupo(grupo.getId());
        dto.setCodigoGrupo(grupo.getCodigo());
        dto.setCupos(grupo.getCupoMaximo());

        return dto;
    }
}