package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.InstrumentoEstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.InstrumentoEstudianteRequest;
import cr.acusab.una.sigac.presentation.dto.response.InstrumentoEstudianteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * HU-026 - Registrar instrumentos que aprende el estudiante.
 *
 * Cubre los criterios de aceptación principales:
 * - Validar que el estudiante exista.
 * - Validar que se haya seleccionado un instrumento (activo).
 * - Registrar la fecha de asignación.
 * - Permitir varios instrumentos vigentes a la vez (no hay "principal").
 * - Deshabilitar (finalizar) un instrumento vigente conservando el historial.
 * - Consultar los instrumentos vigentes y el historial completo.
 */
class InstrumentoEstudianteServiceTest {

    private EstudianteRepository estudianteRepository;
    private InstrumentoRepository instrumentoRepository;
    private EstudianteInstrumentoRepository estudianteInstrumentoRepository;

    private InstrumentoEstudianteService service;

    @BeforeEach
    void setUp() {
        estudianteRepository = mock(EstudianteRepository.class);
        instrumentoRepository = mock(InstrumentoRepository.class);
        estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);

        service = new InstrumentoEstudianteService(
                estudianteRepository,
                instrumentoRepository,
                estudianteInstrumentoRepository
        );
    }

    private Estudiante estudiante(Long id) {
        Estudiante e = new Estudiante();
        e.setId(id);
        return e;
    }

    private Instrumento instrumento(Integer id, String codigo, String nombre) {
        Instrumento i = new Instrumento();
        i.setId(id);
        i.setCodigo(codigo);
        i.setNombre(nombre);
        i.setActivo(true);
        return i;
    }

    @Test
    void asignar_debeRechazarSiElEstudianteNoExiste() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.empty());

        InstrumentoEstudianteRequest request = new InstrumentoEstudianteRequest();
        request.setIdInstrumento(5);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.asignar(1L, request));

        assertTrue(ex.getMessage().toLowerCase().contains("estudiante"));
        verifyNoInteractions(instrumentoRepository);
        verify(estudianteInstrumentoRepository, never()).save(any());
    }

    @Test
    void asignar_debeRechazarSiElInstrumentoNoExisteOEstaInactivo() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));
        when(instrumentoRepository.findByIdAndActivoTrue(5)).thenReturn(Optional.empty());

        InstrumentoEstudianteRequest request = new InstrumentoEstudianteRequest();
        request.setIdInstrumento(5);

        assertThrows(BusinessException.class, () -> service.asignar(1L, request));
        verify(estudianteInstrumentoRepository, never()).save(any());
    }

    @Test
    void asignar_debeCrearLaAsignacionVigenteConFechaDeHoy() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));
        when(instrumentoRepository.findByIdAndActivoTrue(5))
                .thenReturn(Optional.of(instrumento(5, "PIA", "Piano")));
        when(estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndIdInstrumento_IdAndFechaFinIsNull(1L, 5))
                .thenReturn(Optional.empty());
        when(estudianteInstrumentoRepository.save(any(EstudiantesInstrumento.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InstrumentoEstudianteRequest request = new InstrumentoEstudianteRequest();
        request.setIdInstrumento(5);

        InstrumentoEstudianteResponse response = service.asignar(1L, request);

        assertEquals(1L, response.idEstudiante());
        assertEquals(5, response.idInstrumento());
        assertEquals(LocalDate.now(), response.fechaInicio());
        assertTrue(response.vigente());
    }

    @Test
    void asignar_debePermitirVariosInstrumentosVigentesALaVez() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));
        when(instrumentoRepository.findByIdAndActivoTrue(7))
                .thenReturn(Optional.of(instrumento(7, "GUI", "Guitarra")));
        when(estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndIdInstrumento_IdAndFechaFinIsNull(1L, 7))
                .thenReturn(Optional.empty());
        when(estudianteInstrumentoRepository.save(any(EstudiantesInstrumento.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InstrumentoEstudianteRequest request = new InstrumentoEstudianteRequest();
        request.setIdInstrumento(7);

        InstrumentoEstudianteResponse response = service.asignar(1L, request);

        assertTrue(response.vigente());
        // Ya no existe el concepto de instrumento principal: no debe
        // buscarse ni cerrarse ningún registro anterior al asignar.
        verify(estudianteInstrumentoRepository, times(1)).save(any());
    }

    @Test
    void asignar_debeRechazarInstrumentoRepetidoMientrasEsteVigente() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));
        when(instrumentoRepository.findByIdAndActivoTrue(5))
                .thenReturn(Optional.of(instrumento(5, "PIA", "Piano")));
        when(estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndIdInstrumento_IdAndFechaFinIsNull(1L, 5))
                .thenReturn(Optional.of(new EstudiantesInstrumento()));

        InstrumentoEstudianteRequest request = new InstrumentoEstudianteRequest();
        request.setIdInstrumento(5);

        assertThrows(BusinessException.class, () -> service.asignar(1L, request));
        verify(estudianteInstrumentoRepository, never()).save(any());
    }

    @Test
    void deshabilitar_debeRegistrarFechaFinConLaFechaDeHoyYConservarElRegistro() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));

        EstudiantesInstrumento asignacion = new EstudiantesInstrumento();
        asignacion.setId(10L);
        asignacion.setEstudiante(estudiante(1L));
        asignacion.setInstrumento(instrumento(5, "PIA", "Piano"));
        asignacion.setFechaInicio(LocalDate.now().minusMonths(3));

        when(estudianteInstrumentoRepository.findById(10L)).thenReturn(Optional.of(asignacion));
        when(estudianteInstrumentoRepository.save(any(EstudiantesInstrumento.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InstrumentoEstudianteResponse response = service.deshabilitar(1L, 10L);

        assertEquals(LocalDate.now(), asignacion.getFechaFin());
        assertFalse(response.vigente());
        verify(estudianteInstrumentoRepository, times(1)).save(asignacion);
        verify(estudianteInstrumentoRepository, never()).delete(any());
        verify(estudianteInstrumentoRepository, never()).deleteById(any());
    }

    @Test
    void deshabilitar_debeRechazarSiLaAsignacionYaEstaFinalizada() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));

        EstudiantesInstrumento asignacion = new EstudiantesInstrumento();
        asignacion.setId(10L);
        asignacion.setEstudiante(estudiante(1L));
        asignacion.setInstrumento(instrumento(5, "PIA", "Piano"));
        asignacion.setFechaInicio(LocalDate.now().minusMonths(3));
        asignacion.setFechaFin(LocalDate.now().minusDays(1));

        when(estudianteInstrumentoRepository.findById(10L)).thenReturn(Optional.of(asignacion));

        assertThrows(BusinessException.class, () -> service.deshabilitar(1L, 10L));
        verify(estudianteInstrumentoRepository, never()).save(any());
    }

    @Test
    void deshabilitar_debeRechazarSiLaAsignacionNoPerteneceAlEstudiante() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));

        EstudiantesInstrumento asignacion = new EstudiantesInstrumento();
        asignacion.setId(10L);
        asignacion.setEstudiante(estudiante(2L));
        asignacion.setInstrumento(instrumento(5, "PIA", "Piano"));
        asignacion.setFechaInicio(LocalDate.now().minusMonths(3));

        when(estudianteInstrumentoRepository.findById(10L)).thenReturn(Optional.of(asignacion));

        assertThrows(BusinessException.class, () -> service.deshabilitar(1L, 10L));
        verify(estudianteInstrumentoRepository, never()).save(any());
    }

    @Test
    void listarVigentes_debeRetornarSoloLosInstrumentosSinFechaFin() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));

        EstudiantesInstrumento vigente = new EstudiantesInstrumento();
        vigente.setEstudiante(estudiante(1L));
        vigente.setInstrumento(instrumento(5, "PIA", "Piano"));
        vigente.setFechaInicio(LocalDate.now());

        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(1L))
                .thenReturn(List.of(vigente));

        List<InstrumentoEstudianteResponse> resultado = service.listarVigentes(1L);

        assertEquals(1, resultado.size());
        assertTrue(resultado.get(0).vigente());
    }

    @Test
    void listarHistorial_debeRechazarSiElEstudianteNoExiste() {
        when(estudianteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.listarHistorial(99L));
        verifyNoInteractions(estudianteInstrumentoRepository);
    }
}
