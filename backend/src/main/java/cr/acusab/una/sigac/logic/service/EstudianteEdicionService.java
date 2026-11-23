package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.DocumentosEstudiante;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.repository.CursoReconocidoRepository;
import cr.acusab.una.sigac.data.repository.DocumentoEstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.response.BecaResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteEdicionResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteEdicionResponse.BecaEdicion;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteEdicionResponse.InstrumentoEdicion;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteEdicionResponse.PolizaEdicion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/*
 * Consulta de solo lectura para la pantalla "Editar estudiante". También
 * funciona con estudiantes inactivos, porque la pantalla permite editarlos.
 */
@Service
public class EstudianteEdicionService {

    private static final Set<String> ESTADOS_SIN_VIGENCIA = Set.of("CANCELADO", "PLANIFICADO");

    private final EstudianteRepository estudianteRepository;
    private final EstudianteService estudianteService;
    private final EncargadoService encargadoService;
    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private final AvanceInstrumentoService avanceInstrumentoService;
    private final InscripcionPlanEstudioService inscripcionPlanService;
    private final DocumentoEstudianteRepository documentoEstudianteRepository;
    private final BecaService becaService;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final CursoReconocidoRepository cursoReconocidoRepository;

    public EstudianteEdicionService(EstudianteRepository estudianteRepository,
                                    EstudianteService estudianteService,
                                    EncargadoService encargadoService,
                                    EstudianteInstrumentoRepository estudianteInstrumentoRepository,
                                    AvanceInstrumentoService avanceInstrumentoService,
                                    InscripcionPlanEstudioService inscripcionPlanService,
                                    DocumentoEstudianteRepository documentoEstudianteRepository,
                                    BecaService becaService,
                                    PeriodoAcademicoRepository periodoAcademicoRepository,
                                    CursoReconocidoRepository cursoReconocidoRepository) {
        this.estudianteRepository = estudianteRepository;
        this.estudianteService = estudianteService;
        this.encargadoService = encargadoService;
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
        this.avanceInstrumentoService = avanceInstrumentoService;
        this.inscripcionPlanService = inscripcionPlanService;
        this.documentoEstudianteRepository = documentoEstudianteRepository;
        this.becaService = becaService;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.cursoReconocidoRepository = cursoReconocidoRepository;
    }

    @Transactional(readOnly = true)
    public EstudianteEdicionResponse obtener(Long id) {
        Estudiante estudiante = estudianteRepository.findById(id)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe"));

        Optional<Long> plan = avanceInstrumentoService.planConInstrumento(inscripcionPlanService.idsPlanesActivos(id));
        List<EstudiantesInstrumento> vigentes = estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndFechaFinIsNull(id).stream()
                .sorted(Comparator.comparing(EstudiantesInstrumento::getId))
                .toList();

        InstrumentoEdicion principal = vigentes.stream()
                .filter(v -> Boolean.TRUE.equals(v.getEsPrincipal()))
                .findFirst()
                .map(v -> aInstrumento(id, v, plan))
                .orElse(null);
        List<InstrumentoEdicion> extras = vigentes.stream()
                .filter(v -> !Boolean.TRUE.equals(v.getEsPrincipal()))
                .map(v -> aInstrumento(id, v, plan))
                .toList();

        return new EstudianteEdicionResponse(
                estudianteService.obtener(id),
                estudiante.getFechaIngreso(),
                Boolean.TRUE.equals(estudiante.getActivo()),
                principal,
                extras,
                encargadoService.listarParaEdicion(id),
                encargadoService.contarContactosSinEncargado(id),
                polizaVigente(id),
                becaDelPeriodoActual(id),
                cursoReconocidoRepository.findByIdEstudiante(id).size()
        );
    }

    private InstrumentoEdicion aInstrumento(Long idEstudiante, EstudiantesInstrumento relacion, Optional<Long> plan) {
        Integer idInstrumento = relacion.getInstrumento().getId();
        Profesor profesor = relacion.getProfesor();
        String nivel = plan
                .flatMap(idPlan -> avanceInstrumentoService.siguienteNivel(idEstudiante, idInstrumento, idPlan))
                .map(Curso::getNombre)
                .orElse(null);
        return new InstrumentoEdicion(
                relacion.getId(),
                idInstrumento,
                relacion.getInstrumento().getNombre(),
                profesor == null ? null : profesor.getId(),
                profesor == null ? null : nombreProfesor(profesor),
                profesor != null && Boolean.TRUE.equals(profesor.getActivo()),
                nivel
        );
    }

    private String nombreProfesor(Profesor profesor) {
        return String.join(" ",
                Objects.toString(profesor.getNombres(), ""),
                Objects.toString(profesor.getPrimerApellido(), ""),
                Objects.toString(profesor.getSegundoApellido(), "")
        ).trim().replaceAll("\\s+", " ");
    }

    private PolizaEdicion polizaVigente(Long id) {
        return documentoEstudianteRepository
                .findFirstByIdEstudiante_IdAndIdTipoDocumento_CodigoAndEsActualTrueOrderByCargadoEnDesc(id, "POLIZA")
                .map(this::aPoliza)
                .orElse(null);
    }

    private PolizaEdicion aPoliza(DocumentosEstudiante documento) {
        LocalDate vencimiento = documento.getFechaVencimiento();
        return new PolizaEdicion(
                documento.getNombreOriginal(),
                documento.getFechaEmision(),
                vencimiento,
                vencimiento != null && vencimiento.isBefore(LocalDate.now())
        );
    }

    private BecaEdicion becaDelPeriodoActual(Long id) {
        Optional<PeriodoAcademico> periodo = periodoActual();
        if (periodo.isEmpty()) return null;
        return becaService.listarPorEstudiante(id).stream()
                .filter(beca -> Boolean.TRUE.equals(beca.getActiva()))
                .filter(beca -> Objects.equals(beca.getIdPeriodo(), periodo.get().getId()))
                .findFirst()
                .map(this::aBeca)
                .orElse(null);
    }

    private BecaEdicion aBeca(BecaResponse beca) {
        return new BecaEdicion(beca.getCodigoPeriodo(), beca.getNombreTipoBeca(), beca.getPorcentaje());
    }

    // El período cuyas fechas incluyen hoy; si no hay, el que está en matrícula.
    private Optional<PeriodoAcademico> periodoActual() {
        LocalDate hoy = LocalDate.now();
        List<PeriodoAcademico> periodos = periodoAcademicoRepository.findAll();
        Optional<PeriodoAcademico> porFechas = periodos.stream()
                .filter(p -> !ESTADOS_SIN_VIGENCIA.contains(p.getEstado()))
                .filter(p -> p.getFechaInicio() != null && p.getFechaFin() != null)
                .filter(p -> !hoy.isBefore(p.getFechaInicio()) && !hoy.isAfter(p.getFechaFin()))
                .max(Comparator.comparing(PeriodoAcademico::getFechaInicio));
        if (porFechas.isPresent()) return porFechas;
        return periodos.stream()
                .filter(p -> "MATRICULA".equals(p.getEstado()))
                .filter(p -> p.getFechaInicio() != null)
                .max(Comparator.comparing(PeriodoAcademico::getFechaInicio));
    }
}
