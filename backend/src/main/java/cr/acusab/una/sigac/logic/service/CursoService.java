package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Programa;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProgramaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoResponseDTO;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CursoService {

    private final CursoRepository cursoRepository;
    private final PlanEstudioRepository planEstudioRepository;
    private final MallaCurricularRepository mallaCurricularRepository;
    private final GrupoRepository grupoRepository;
    private final ProgramaRepository programaRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final UsuarioRepository usuarioRepository;

    public CursoService(
            CursoRepository cursoRepository,
            PlanEstudioRepository planEstudioRepository,
            MallaCurricularRepository mallaCurricularRepository,
            GrupoRepository grupoRepository,
            ProgramaRepository programaRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.cursoRepository = cursoRepository;
        this.planEstudioRepository = planEstudioRepository;
        this.mallaCurricularRepository = mallaCurricularRepository;
        this.grupoRepository = grupoRepository;
        this.programaRepository = programaRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /*
     * HU-028 - Crear curso en la malla curricular.
     *
     * @Transactional garantiza que si falla la creación del curso,
     * la malla o el grupo, se revierta toda la operación.
     */
    @Transactional
    public CursoResponseDTO crear(CursoRequestDTO request) {

        String codigo = request.getCodigo().trim();
        String nombre = request.getNombre().trim();

        String descripcion = request.getDescripcion() != null
                ? request.getDescripcion().trim()
                : null;

        // 1. Validar que el código del curso sea único.
        if (cursoRepository.existsByCodigo(codigo)) {
            throw new IllegalArgumentException(
                    "Ya existe un curso con el código ingresado."
            );
        }

        // 2. Validar que el programa académico exista.
        Programa programa = programaRepository
                .findById(request.getIdPrograma())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El programa académico seleccionado no existe."
                ));

        // 3. El programa debe estar activo.
        if (!Boolean.TRUE.equals(programa.getActivo())) {
            throw new IllegalArgumentException(
                    "El programa académico seleccionado se encuentra inactivo."
            );
        }

        // 4. Buscar el plan de estudio vigente más reciente del programa.
        PlanEstudio plan = planEstudioRepository
                .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
                        request.getIdPrograma()
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "El programa seleccionado no tiene un plan de estudio vigente."
                ));

        // 5. Validar que el período lectivo exista.
        periodoAcademicoRepository
                .findById(request.getIdPeriodo())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El período lectivo seleccionado no existe."
                ));

        // 6. Obtener el usuario autenticado.
        // Este ID será utilizado por los triggers de bitácora.
        Long idUsuario = obtenerIdUsuarioAutenticado();

        Instant ahora = Instant.now();

        // 7. Crear el curso.
        Curso curso = new Curso();
        curso.setCodigo(codigo);
        curso.setNombre(nombre);
        curso.setDescripcion(
                descripcion != null && !descripcion.isBlank()
                        ? descripcion
                        : null
        );
        curso.setHorasTotales(request.getHorasTotales());
        curso.setActivo(true);

        // Importante para la bitácora.
        curso.setIdUsuarioUltimaModificacion(idUsuario);

        curso.setCreadoEn(ahora);
        curso.setActualizadoEn(ahora);

        Curso cursoGuardado = cursoRepository.save(curso);

        /*
         * El trigger trg_cursos_ai_bitacora registra automáticamente
         * la creación utilizando id_usuario_ultima_modificacion.
         */

        // 8. Calcular el siguiente orden disponible dentro del nivel.
        Short ultimoOrden = mallaCurricularRepository
                .findMaxOrdenEnNivel(
                        plan.getId(),
                        request.getNivel()
                )
                .orElse((short) 0);

        short siguienteOrden = (short) (ultimoOrden + 1);

        // 9. Incorporar el curso a la malla curricular.
        MallaCurricular malla = new MallaCurricular();
        malla.setIdPlanEstudio(plan.getId());
        malla.setIdCurso(cursoGuardado.getId());
        malla.setNivel(request.getNivel());
        malla.setOrdenEnNivel(siguienteOrden);
        malla.setObligatorio(true);
        malla.setActivo(true);

        // Importante para la bitácora.
        malla.setIdUsuarioUltimaModificacion(idUsuario);

        malla.setCreadoEn(ahora);
        malla.setActualizadoEn(ahora);

        MallaCurricular mallaGuardada =
                mallaCurricularRepository.save(malla);

        /*
         * El trigger trg_malla_curricular_ai_bitacora registra
         * automáticamente la incorporación a la malla.
         */

        // 10. Crear el primer grupo del curso para el período indicado.
        String codigoGrupo = codigo + "-A";

        Grupo grupo = new Grupo();
        grupo.setIdPeriodo(request.getIdPeriodo());
        grupo.setIdCurso(cursoGuardado.getId());
        grupo.setCodigo(codigoGrupo);
        grupo.setCupoMaximo(request.getCupos());

        /*
         * La BD permite PRESENCIAL, VIRTUAL e HIBRIDA.
         * HU-028 no solicita seleccionar modalidad, por lo que
         * se conserva PRESENCIAL como valor inicial.
         */
        grupo.setModalidad("PRESENCIAL");
        grupo.setEstado("PLANIFICADO");
        grupo.setCreadoEn(ahora);
        grupo.setActualizadoEn(ahora);

        Grupo grupoGuardado = grupoRepository.save(grupo);

        // 11. Construir la respuesta para el frontend.
        return mapearAResponse(
                cursoGuardado,
                mallaGuardada,
                grupoGuardado
        );
    }

    private Long obtenerIdUsuarioAutenticado() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new IllegalStateException(
                    "No se pudo identificar al usuario autenticado."
            );
        }

        String cedula = authentication.getName();

        Usuario usuario = usuarioRepository
                .findByCedula(cedula)
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario autenticado no existe en el sistema."
                ));

        return usuario.getId();
    }

    private CursoResponseDTO mapearAResponse(
            Curso curso,
            MallaCurricular malla,
            Grupo grupo
    ) {

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