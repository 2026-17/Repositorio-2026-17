package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Programa;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.ProgramaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.request.CursoUpdateRequestDTO;
import cr.acusab.una.sigac.presentation.dto.request.ModificarCursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoEdicionResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoListadoResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoResponseDTO;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CursoService {

    private final RequisitosCursoService requisitosCursoService;
    private final CursoRepository cursoRepository;
    private final PlanEstudioRepository planEstudioRepository;
    private final MallaCurricularRepository mallaCurricularRepository;
    private final GrupoRepository grupoRepository;
    private final ProgramaRepository programaRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProfesorRepository profesorRepository;

    public CursoService(
            CursoRepository cursoRepository,
            PlanEstudioRepository planEstudioRepository,
            MallaCurricularRepository mallaCurricularRepository,
            GrupoRepository grupoRepository,
            ProgramaRepository programaRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            UsuarioRepository usuarioRepository,
            ProfesorRepository profesorRepository,
            RequisitosCursoService requisitosCursoService
    ) {
        this.requisitosCursoService = requisitosCursoService;
        this.cursoRepository = cursoRepository;
        this.planEstudioRepository = planEstudioRepository;
        this.mallaCurricularRepository = mallaCurricularRepository;
        this.grupoRepository = grupoRepository;
        this.programaRepository = programaRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.usuarioRepository = usuarioRepository;
        this.profesorRepository = profesorRepository;
    }

    /*
     * HU-031 - Listado de cursos activos.
     *
     * Alimenta la pantalla de "Desactivar curso": el Director Académico
     * busca/filtra el curso en esta lista en lugar de digitar su ID.
     *
     * Por cada curso se muestra el docente y el cupo máximo del grupo
     * más reciente asociado, ya que esos datos viven en Grupo y no en
     * Curso (un curso puede tener varios grupos en distintos periodos).
     */
    @Transactional(readOnly = true)
    public List<CursoListadoResponseDTO> listarActivos() {

        List<Curso> cursosActivos =
                cursoRepository.findByActivoTrueOrderByNombreAsc();

        List<Long> idsCursos = cursosActivos.stream()
                .map(Curso::getId)
                .toList();

        // 1. Traer todos los grupos de esos cursos en una sola consulta
        // (evita N+1) y quedarse con el más reciente por curso.
        Map<Long, Grupo> grupoMasRecientePorCurso = new HashMap<>();

        if (!idsCursos.isEmpty()) {

            List<Grupo> grupos =
                    grupoRepository.findByIdCursoInOrderByCreadoEnDesc(
                            idsCursos
                    );

            for (Grupo grupo : grupos) {
                grupoMasRecientePorCurso.putIfAbsent(
                        grupo.getIdCurso(),
                        grupo
                );
            }
        }

        // 2. Traer los profesores referenciados por esos grupos,
        // también en una sola consulta.
        List<Long> idsProfesores =
                grupoMasRecientePorCurso.values().stream()
                        .map(Grupo::getIdProfesor)
                        .filter(id -> id != null)
                        .distinct()
                        .toList();

        Map<Long, Profesor> profesoresPorId =
                idsProfesores.isEmpty()
                        ? Map.of()
                        : profesorRepository.findAllById(idsProfesores)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Profesor::getId,
                                        p -> p
                                )
                        );

        // 3. Armar la respuesta.
        return cursosActivos.stream()
                .map(curso -> mapearAListadoResponse(
                        curso,
                        grupoMasRecientePorCurso.get(curso.getId()),
                        profesoresPorId
                ))
                .toList();
    }

    private CursoListadoResponseDTO mapearAListadoResponse(
            Curso curso,
            Grupo grupo,
            Map<Long, Profesor> profesoresPorId
    ) {

        CursoListadoResponseDTO dto =
                new CursoListadoResponseDTO();

        dto.setIdCurso(curso.getId());
        dto.setCodigo(curso.getCodigo());
        dto.setNombre(curso.getNombre());
        dto.setCategoria(curso.getDescripcion());
        dto.setHorasTotales(curso.getHorasTotales());

        if (grupo != null) {

            dto.setCupoMaximo(grupo.getCupoMaximo());

            Profesor profesor =
                    grupo.getIdProfesor() != null
                            ? profesoresPorId.get(grupo.getIdProfesor())
                            : null;

            if (profesor != null) {

                String nombreCompleto = String.join(
                        " ",
                        profesor.getNombres(),
                        profesor.getPrimerApellido(),
                        profesor.getSegundoApellido() != null
                                ? profesor.getSegundoApellido()
                                : ""
                ).trim().replaceAll("\\s+", " ");

                dto.setDocente(nombreCompleto);
            }
        }

        return dto;
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

        String descripcion =
                request.getDescripcion() != null
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

        Curso cursoGuardado =
                cursoRepository.save(curso);

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

        short siguienteOrden =
                (short) (ultimoOrden + 1);

        // 9. Incorporar el curso a la malla curricular.
        MallaCurricular malla =
                new MallaCurricular();

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
        grupo.setIdUsuarioUltimaModificacion(idUsuario);
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

        Grupo grupoGuardado =
                grupoRepository.save(grupo);

        // 11. Construir la respuesta para el frontend.
        if (request.getRequisitos() != null) {
            requisitosCursoService.guardarConfiguracion(plan.getId(), cursoGuardado.getId(), request.getRequisitos());
        }
        return mapearAResponse(
                cursoGuardado,
                mallaGuardada,
                grupoGuardado
        );
    }

    /*
     * HU-030 - Obtener los datos actuales del curso
     * para cargar el formulario de modificación.
     */
    @Transactional(readOnly = true)
    public CursoEdicionResponseDTO obtenerParaEdicion(
            Long idCurso
    ) {

        validarIdCurso(idCurso);

        Curso curso = cursoRepository
                .findById(idCurso)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El curso indicado no existe."
                ));

        if (!Boolean.TRUE.equals(curso.getActivo())) {
            throw new IllegalArgumentException(
                    "No se puede modificar un curso inactivo."
            );
        }

        MallaCurricular malla =
                obtenerMallaActivaUnica(idCurso);

        PlanEstudio plan = planEstudioRepository
                .findById(malla.getIdPlanEstudio())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el plan de estudio asociado al curso."
                ));

        Programa programa = programaRepository
                .findById(plan.getIdPrograma())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el programa académico asociado al curso."
                ));

        return mapearAEdicionResponse(
                curso,
                malla,
                plan,
                programa
        );
    }

    /*
     * HU-030 - Modificar curso de la malla curricular.
     *
     * Se modifican:
     * - nombre
     * - descripción
     * - cantidad de horas
     * - programa académico
     * - nivel académico
     *
     * No se modifican matrículas, detalles de matrícula,
     * grupos ni otra información histórica ya registrada.
     *
     * Los triggers de la base de datos registran automáticamente
     * las modificaciones sobre cursos y malla_curricular.
     */
    @Transactional
    public CursoEdicionResponseDTO modificar(
            Long idCurso,
            ModificarCursoRequestDTO request
    ) {

        // 1. Validar el identificador.
        validarIdCurso(idCurso);

        // 2. Buscar el curso.
        Curso curso = cursoRepository
                .findById(idCurso)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El curso indicado no existe."
                ));

        // 3. Solamente se pueden modificar cursos activos.
        if (!Boolean.TRUE.equals(curso.getActivo())) {
            throw new IllegalArgumentException(
                    "No se puede modificar un curso inactivo."
            );
        }

        // 4. Obtener la ubicación activa actual del curso.
        MallaCurricular mallaActual =
                obtenerMallaActivaUnica(idCurso);

        // 5. Obtener el plan actual para determinar
        // a qué programa pertenece actualmente el curso.
        PlanEstudio planActual = planEstudioRepository
                .findById(mallaActual.getIdPlanEstudio())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el plan de estudio asociado al curso."
                ));

        // 6. Validar el programa solicitado.
        Programa programaDestino = programaRepository
                .findById(request.getIdPrograma())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El programa académico seleccionado no existe."
                ));

        if (!Boolean.TRUE.equals(programaDestino.getActivo())) {
            throw new IllegalArgumentException(
                    "El programa académico seleccionado se encuentra inactivo."
            );
        }

        /*
         * 7. Determinar el plan que debe utilizarse.
         *
         * Si el Director selecciona el mismo programa que ya tiene
         * el curso, se conserva el plan actual. De esta forma,
         * modificar otro dato del curso no provoca accidentalmente
         * un cambio de versión del plan de estudio.
         *
         * Si cambia de programa, se utiliza el plan vigente más
         * reciente del nuevo programa.
         */
        boolean cambioPrograma =
                !planActual.getIdPrograma()
                        .equals(request.getIdPrograma());

        PlanEstudio planDestino;

        if (cambioPrograma) {

            planDestino = planEstudioRepository
                    .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
                            request.getIdPrograma()
                    )
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El programa seleccionado no tiene un plan de estudio vigente."
                    ));

        } else {

            planDestino = planActual;
        }

        // 8. Obtener el usuario autenticado para la bitácora.
        Long idUsuario =
                obtenerIdUsuarioAutenticado();

        Instant ahora =
                Instant.now();

        // 9. Modificar los datos propios del curso.
        String nombre =
                request.getNombre().trim();

        String descripcion =
                request.getDescripcion() != null
                        ? request.getDescripcion().trim()
                        : null;

        curso.setNombre(nombre);

        curso.setDescripcion(
                descripcion != null && !descripcion.isBlank()
                        ? descripcion
                        : null
        );

        curso.setHorasTotales(
                request.getHorasTotales()
        );

        curso.setIdUsuarioUltimaModificacion(
                idUsuario
        );

        curso.setActualizadoEn(
                ahora
        );

        Curso cursoGuardado =
                cursoRepository.save(curso);

        /*
         * trg_cursos_au_bitacora registra automáticamente
         * los valores anteriores y nuevos.
         */

        MallaCurricular mallaDestino;

        /*
         * 10. Si no cambió de programa, se mantiene la asociación
         * actual con su mismo plan.
         *
         * Si cambió de nivel, se actualiza la posición dentro
         * de ese mismo plan.
         */
        if (!cambioPrograma) {

            if (!mallaActual.getNivel()
                    .equals(request.getNivel())) {

                short siguienteOrden =
                        calcularSiguienteOrden(
                                planActual.getId(),
                                request.getNivel()
                        );

                mallaActual.setNivel(
                        request.getNivel()
                );

                mallaActual.setOrdenEnNivel(
                        siguienteOrden
                );
            }

            mallaActual.setIdUsuarioUltimaModificacion(
                    idUsuario
            );

            mallaActual.setActualizadoEn(
                    ahora
            );

            mallaDestino =
                    mallaCurricularRepository.save(
                            mallaActual
                    );

        } else {

            /*
             * 11. El programa cambió.
             *
             * La asociación anterior se conserva y solamente
             * se marca como inactiva.
             */
            mallaActual.setActivo(false);

            mallaActual.setIdUsuarioUltimaModificacion(
                    idUsuario
            );

            mallaActual.setActualizadoEn(
                    ahora
            );

            mallaCurricularRepository.save(
                    mallaActual
            );

            /*
             * 12. Comprobar si el curso ya tuvo anteriormente
             * una asociación con el plan de destino.
             *
             * Esto evita violar:
             *
             * UNIQUE (id_plan_estudio, id_curso)
             */
            var mallaDestinoExistente =
                    mallaCurricularRepository
                            .findByIdPlanEstudioAndIdCurso(
                                    planDestino.getId(),
                                    idCurso
                            );

            if (mallaDestinoExistente.isPresent()) {

                /*
                 * Ya existía una asociación histórica con ese plan.
                 * Se reutiliza y reactiva en lugar de crear un duplicado.
                 */
                mallaDestino =
                        mallaDestinoExistente.get();

                if (!mallaDestino.getNivel()
                        .equals(request.getNivel())) {

                    short siguienteOrden =
                            calcularSiguienteOrden(
                                    planDestino.getId(),
                                    request.getNivel()
                            );

                    mallaDestino.setNivel(
                            request.getNivel()
                    );

                    mallaDestino.setOrdenEnNivel(
                            siguienteOrden
                    );
                }

                mallaDestino.setActivo(true);

                mallaDestino.setIdUsuarioUltimaModificacion(
                        idUsuario
                );

                mallaDestino.setActualizadoEn(
                        ahora
                );

                mallaDestino =
                        mallaCurricularRepository.save(
                                mallaDestino
                        );

            } else {

                /*
                 * 13. Es la primera vez que el curso pertenece
                 * al plan de destino.
                 */
                short siguienteOrden =
                        calcularSiguienteOrden(
                                planDestino.getId(),
                                request.getNivel()
                        );

                MallaCurricular nuevaMalla =
                        new MallaCurricular();

                nuevaMalla.setIdPlanEstudio(
                        planDestino.getId()
                );

                nuevaMalla.setIdCurso(
                        idCurso
                );

                nuevaMalla.setNivel(
                        request.getNivel()
                );

                nuevaMalla.setOrdenEnNivel(
                        siguienteOrden
                );

                /*
                 * HU-030 no modifica si el curso es obligatorio
                 * u opcional, por lo que se conserva el valor actual.
                 */
                nuevaMalla.setObligatorio(
                        mallaActual.getObligatorio()
                );

                nuevaMalla.setActivo(true);

                nuevaMalla.setIdUsuarioUltimaModificacion(
                        idUsuario
                );

                nuevaMalla.setCreadoEn(
                        ahora
                );

                nuevaMalla.setActualizadoEn(
                        ahora
                );

                mallaDestino =
                        mallaCurricularRepository.save(
                                nuevaMalla
                        );
            }
        }

        /*
         * trg_malla_curricular_au_bitacora y
         * trg_malla_curricular_ai_bitacora registran automáticamente
         * los cambios correspondientes.
         */

        if (request.getRequisitos() != null) {
            requisitosCursoService.guardarConfiguracion(planDestino.getId(), cursoGuardado.getId(), request.getRequisitos());
        }
        // 14. Devolver los datos actualizados.
        return mapearAEdicionResponse(
                cursoGuardado,
                mallaDestino,
                planDestino,
                programaDestino
        );
    }

    /*
     * Compatibilidad con la implementación y las pruebas existentes de HU-030.
     *
     * La versión anterior de la historia de usuario utiliza
     * CursoUpdateRequestDTO y el método actualizar().
     *
     * Para no duplicar la lógica de negocio, este método convierte
     * la solicitud anterior al DTO actual y delega en modificar().
     */
    @Transactional
    public CursoEdicionResponseDTO actualizar(
            Long idCurso,
            CursoUpdateRequestDTO request
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Los datos para modificar el curso son obligatorios."
            );
        }

        ModificarCursoRequestDTO modificarRequest =
                new ModificarCursoRequestDTO();

        modificarRequest.setNombre(
                request.getNombre()
        );

        modificarRequest.setDescripcion(
                request.getDescripcion()
        );

        modificarRequest.setHorasTotales(
                request.getHorasTotales()
        );

        modificarRequest.setIdPrograma(
                request.getIdPrograma()
        );

        modificarRequest.setNivel(
                request.getNivel()
        );

        return modificar(
                idCurso,
                modificarRequest
        );
    }

    /*
     * HU-031 - Desactivar curso de la malla curricular.
     *
     * La desactivación es lógica (soft-delete).
     * No se elimina el curso ni la información histórica relacionada.
     *
     * Los triggers de la base de datos registran automáticamente
     * los UPDATE realizados sobre cursos y malla_curricular.
     */
    @Transactional
    public void desactivar(Long idCurso) {

        // 1. Validar que el identificador sea válido.
        validarIdCurso(idCurso);

        // 2. Buscar el curso.
        Curso curso = cursoRepository
                .findById(idCurso)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El curso indicado no existe."
                ));

        // 3. Evitar desactivar nuevamente un curso que ya está inactivo.
        if (!Boolean.TRUE.equals(curso.getActivo())) {
            throw new IllegalArgumentException(
                    "El curso ya se encuentra inactivo."
            );
        }

        // 4. Obtener el usuario autenticado que realiza la operación.
        Long idUsuario =
                obtenerIdUsuarioAutenticado();

        Instant ahora =
                Instant.now();

        // 5. Desactivar lógicamente el curso.
        curso.setActivo(false);
        curso.setIdUsuarioUltimaModificacion(idUsuario);
        curso.setActualizadoEn(ahora);

        cursoRepository.save(curso);

        /*
         * El trigger trg_cursos_au_bitacora registra automáticamente:
         *
         * activo:
         * true -> false
         *
         * junto con el usuario que realizó la modificación.
         */

        // 6. Buscar todas las asociaciones activas del curso
        // dentro de la malla curricular.
        List<MallaCurricular> mallasActivas =
                mallaCurricularRepository
                        .findByIdCursoAndActivoTrue(
                                idCurso
                        );

        // 7. Desactivar las asociaciones sin eliminarlas.
        for (MallaCurricular malla : mallasActivas) {

            malla.setActivo(false);

            malla.setIdUsuarioUltimaModificacion(
                    idUsuario
            );

            malla.setActualizadoEn(
                    ahora
            );
        }

        mallaCurricularRepository.saveAll(
                mallasActivas
        );

        /*
         * El trigger trg_malla_curricular_au_bitacora registra
         * automáticamente la desactivación de cada asociación.
         *
         * De esta manera se conserva íntegramente la información
         * histórica del curso y de su ubicación en la malla.
         */
    }

    /*
     * Valida el identificador recibido para operaciones
     * sobre un curso.
     */
    private void validarIdCurso(Long idCurso) {

        if (idCurso == null || idCurso <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del curso no es válido."
            );
        }
    }

    /*
     * HU-030 - Obtiene la única asociación activa
     * que representa la ubicación actual del curso.
     */
    private MallaCurricular obtenerMallaActivaUnica(
            Long idCurso
    ) {

        List<MallaCurricular> mallasActivas =
                mallaCurricularRepository
                        .findByIdCursoAndActivoTrue(
                                idCurso
                        );

        if (mallasActivas.isEmpty()) {
            throw new IllegalArgumentException(
                    "El curso no posee una ubicación activa en la malla curricular."
            );
        }

        if (mallasActivas.size() > 1) {
            throw new IllegalArgumentException(
                    "El curso posee más de una ubicación activa en la malla curricular."
            );
        }

        return mallasActivas.get(0);
    }

    /*
     * Calcula la siguiente posición disponible para un curso
     * dentro de un nivel determinado del plan de estudio.
     *
     * La consulta considera todas las filas existentes porque
     * la restricción UNIQUE de la BD también aplica a las
     * asociaciones inactivas.
     */
    private short calcularSiguienteOrden(
            Long idPlanEstudio,
            Short nivel
    ) {

        Short ultimoOrden = mallaCurricularRepository
                .findMaxOrdenEnNivel(
                        idPlanEstudio,
                        nivel
                )
                .orElse((short) 0);

        if (ultimoOrden == Short.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "No hay posiciones disponibles en el nivel seleccionado."
            );
        }

        return (short) (ultimoOrden + 1);
    }

    /*
     * Obtiene el identificador del usuario actualmente autenticado.
     *
     * Se utiliza para que los triggers de la base de datos puedan
     * registrar quién realizó cada modificación.
     */
    private Long obtenerIdUsuarioAutenticado() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new IllegalStateException(
                    "No se pudo identificar al usuario autenticado."
            );
        }

        String cedula =
                authentication.getName();

        Usuario usuario = usuarioRepository
                .findByCedula(cedula)
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario autenticado no existe en el sistema."
                ));

        return usuario.getId();
    }

    /*
     * Convierte las entidades creadas durante HU-028
     * en la respuesta enviada al frontend.
     */
    private CursoResponseDTO mapearAResponse(
            Curso curso,
            MallaCurricular malla,
            Grupo grupo
    ) {

        CursoResponseDTO dto =
                new CursoResponseDTO();

        dto.setIdCurso(
                curso.getId()
        );

        dto.setCodigo(
                curso.getCodigo()
        );

        dto.setNombre(
                curso.getNombre()
        );

        dto.setDescripcion(
                curso.getDescripcion()
        );

        dto.setHorasTotales(
                curso.getHorasTotales()
        );

        dto.setActivo(
                curso.getActivo()
        );

        dto.setIdMalla(
                malla.getId()
        );

        dto.setNivel(
                malla.getNivel()
        );

        dto.setIdGrupo(
                grupo.getId()
        );

        dto.setCodigoGrupo(
                grupo.getCodigo()
        );

        dto.setCupos(
                grupo.getCupoMaximo()
        );

        return dto;
    }

    /*
     * HU-030 - Construye la respuesta utilizada
     * por el formulario de modificación.
     */
    private CursoEdicionResponseDTO mapearAEdicionResponse(
            Curso curso,
            MallaCurricular malla,
            PlanEstudio plan,
            Programa programa
    ) {

        CursoEdicionResponseDTO dto =
                new CursoEdicionResponseDTO();

        dto.setIdCurso(
                curso.getId()
        );

        dto.setCodigo(
                curso.getCodigo()
        );

        dto.setNombre(
                curso.getNombre()
        );

        dto.setDescripcion(
                curso.getDescripcion()
        );

        dto.setHorasTotales(
                curso.getHorasTotales()
        );

        dto.setIdPrograma(
                programa.getId()
        );

        dto.setPrograma(
                programa.getNombre()
        );

        dto.setIdPlanEstudio(
                plan.getId()
        );

        dto.setNivel(
                malla.getNivel()
        );

        dto.setActivo(
                curso.getActivo()
        );

        return dto;
    }
}
