package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.EstudiantePlanEstudio;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.repository.EstudiantePlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.logic.service.InscripcionPlanEstudioService;
import cr.acusab.una.sigac.presentation.dto.response.PlanEstudioOpcionResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Inscripción de estudiantes a planes de estudio (obligatoria al registrar).
 */
class InscripcionPlanEstudioServiceTest {

    private static final long ID_ESTUDIANTE = 7L;

    private EstudiantePlanEstudioRepository inscripcionRepository;
    private PlanEstudioRepository planRepository;
    private InscripcionPlanEstudioService service;

    private final List<PlanEstudio> planes = new ArrayList<>();
    private final List<EstudiantePlanEstudio> existentes = new ArrayList<>();

    @BeforeEach
    void setUp() {

        inscripcionRepository = mock(EstudiantePlanEstudioRepository.class);
        planRepository = mock(PlanEstudioRepository.class);

        service = new InscripcionPlanEstudioService(inscripcionRepository, planRepository);

        planes.add(plan(1L, "Plan de Guitarra", true));
        planes.add(plan(2L, "Plan de Teoría", true));
        planes.add(plan(3L, "Plan antiguo", false));

        when(planRepository.findAllById(anyCollection()))
                .thenAnswer(inv -> {
                    java.util.Collection<?> ids = inv.getArgument(0);
                    return planes.stream().filter(p -> ids.contains(p.getId())).toList();
                });
        when(planRepository.findByVigenteTrueOrderByNombreAsc())
                .thenAnswer(inv -> planes.stream().filter(PlanEstudio::getVigente).toList());
        when(inscripcionRepository.findByIdEstudiante(ID_ESTUDIANTE))
                .thenAnswer(inv -> new ArrayList<>(existentes));
        when(inscripcionRepository.findByIdEstudianteAndActivoTrue(ID_ESTUDIANTE))
                .thenAnswer(inv -> existentes.stream().filter(EstudiantePlanEstudio::getActivo).toList());
    }

    @Test
    void listarPlanesVigentes_soloDevuelveLosVigentes() {

        List<PlanEstudioOpcionResponse> opciones = service.listarPlanesVigentes();

        assertEquals(List.of(1L, 2L), opciones.stream().map(PlanEstudioOpcionResponse::id).toList());
    }

    @Test
    void validarParaRegistro_sinPlanes_seRechaza() {

        assertThrows(IllegalArgumentException.class, () -> service.validarParaRegistro(null));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.validarParaRegistro(List.of()));

        assertTrue(ex.getMessage().contains("al menos un plan de estudio"));
    }

    @Test
    void validarParaRegistro_planInexistente_seRechaza() {

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.validarParaRegistro(List.of(1L, 99L)));

        assertTrue(ex.getMessage().contains("no existen"));
    }

    @Test
    void validarParaRegistro_planNoVigente_seRechaza() {

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.validarParaRegistro(List.of(3L)));

        assertTrue(ex.getMessage().contains("no se encuentra vigente"));
    }

    @Test
    void validarParaRegistro_idNulo_seRechaza() {

        assertThrows(IllegalArgumentException.class,
                () -> service.validarParaRegistro(Arrays.asList(1L, null)));
    }

    @Test
    void sincronizar_inscribeEnLosPlanesIndicados() {

        List<Long> resultado = service.sincronizar(ID_ESTUDIANTE, List.of(2L, 1L));

        assertEquals(List.of(1L, 2L), resultado);

        ArgumentCaptor<EstudiantePlanEstudio> guardadas = ArgumentCaptor.forClass(EstudiantePlanEstudio.class);
        verify(inscripcionRepository, times(2)).save(guardadas.capture());

        assertTrue(guardadas.getAllValues().stream().allMatch(i ->
                i.getIdEstudiante().equals(ID_ESTUDIANTE) && Boolean.TRUE.equals(i.getActivo())));
    }

    @Test
    void sincronizar_ignoraRepetidos() {

        List<Long> resultado = service.sincronizar(ID_ESTUDIANTE, List.of(1L, 1L, 1L));

        assertEquals(List.of(1L), resultado);
        verify(inscripcionRepository, times(1)).save(any(EstudiantePlanEstudio.class));
    }

    @Test
    void sincronizar_planYaInscrito_noDuplica() {

        existentes.add(inscripcion(1L, true));

        service.sincronizar(ID_ESTUDIANTE, List.of(1L));

        verify(inscripcionRepository, never()).save(any());
    }

    @Test
    void sincronizar_reactivaUnaInscripcionInactiva() {

        EstudiantePlanEstudio inactiva = inscripcion(1L, false);
        existentes.add(inactiva);

        service.sincronizar(ID_ESTUDIANTE, List.of(1L));

        assertTrue(inactiva.getActivo());
        verify(inscripcionRepository).save(inactiva);
    }

    @Test
    void sincronizar_desactivaSinBorrarLosPlanesQueYaNoSeSeleccionan() {

        EstudiantePlanEstudio guitarra = inscripcion(1L, true);
        EstudiantePlanEstudio teoria = inscripcion(2L, true);
        existentes.add(guitarra);
        existentes.add(teoria);

        List<Long> resultado = service.sincronizar(ID_ESTUDIANTE, List.of(1L));

        assertEquals(List.of(1L), resultado);
        assertTrue(guitarra.getActivo());
        assertFalse(teoria.getActivo());
        verify(inscripcionRepository, never()).delete(any());
        verify(inscripcionRepository, never()).deleteAll();
    }

    @Test
    void sincronizar_planQueDejoDeSerVigente_seConservaSiYaEstabaInscrito() {

        existentes.add(inscripcion(3L, true));

        assertDoesNotThrow(() -> service.sincronizar(ID_ESTUDIANTE, List.of(3L, 1L)));
    }

    @Test
    void sincronizar_planNoVigenteNuevo_seRechazaSinGuardarNada() {

        assertThrows(IllegalArgumentException.class, () -> service.sincronizar(ID_ESTUDIANTE, List.of(1L, 3L)));

        verify(inscripcionRepository, never()).save(any());
    }

    @Test
    void sincronizar_sinPlanes_seRechazaYNoDesactivaLosExistentes() {

        EstudiantePlanEstudio guitarra = inscripcion(1L, true);
        existentes.add(guitarra);

        assertThrows(IllegalArgumentException.class, () -> service.sincronizar(ID_ESTUDIANTE, List.of()));

        assertTrue(guitarra.getActivo());
        verify(inscripcionRepository, never()).save(any());
    }

    @Test
    void idsPlanesActivos_soloIncluyeLasActivasYOrdenadas() {

        existentes.add(inscripcion(2L, true));
        existentes.add(inscripcion(1L, true));
        existentes.add(inscripcion(3L, false));

        assertEquals(List.of(1L, 2L), service.idsPlanesActivos(ID_ESTUDIANTE));
    }

    private PlanEstudio plan(Long id, String nombre, boolean vigente) {

        PlanEstudio plan = new PlanEstudio();
        plan.setId(id);
        plan.setCodigo("PLAN-" + id);
        plan.setNombre(nombre);
        plan.setVersion("1.0");
        plan.setVigente(vigente);

        return plan;
    }

    private EstudiantePlanEstudio inscripcion(Long idPlan, boolean activo) {

        EstudiantePlanEstudio inscripcion = new EstudiantePlanEstudio();
        inscripcion.setIdEstudiante(ID_ESTUDIANTE);
        inscripcion.setIdPlanEstudio(idPlan);
        inscripcion.setActivo(activo);

        return inscripcion;
    }
}
