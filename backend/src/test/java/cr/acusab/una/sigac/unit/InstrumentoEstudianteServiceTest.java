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
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/*
 * HU-026 - Registrar instrumentos que aprende el estudiante.
 *
 * Cubre los criterios de aceptación principales:
 * - Validar que el estudiante exista.
 * - Validar que se haya seleccionado un instrumento (activo).
 * - Registrar la fecha de asignación.
 * - Conservar el historial al cambiar de instrumento principal.
 * - Permitir varios instrumentos vigentes a la vez.
 * - Consultar el instrumento vigente y el historial completo.
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
    void asignar_debeCrearLaPrimeraAsignacionComoPrincipalConFechaDeHoy() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));
        when(instrumentoRepository.findByIdAndActivoTrue(5))
                .thenReturn(Optional.of(instrumento(5, "PIA", "Piano")));
        when(estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndIdInstrumento_IdAndFechaFinIsNull(1L, 5))
                .thenReturn(Optional.empty());
        when(estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndEsPrincipalTrueAndFechaFinIsNull(1L))
                .thenReturn(Optional.empty());
        when(estudianteInstrumentoRepository.save(any(EstudiantesInstrumento.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InstrumentoEstudianteRequest request = new InstrumentoEstudianteRequest();
        request.setIdInstrumento(5);

        InstrumentoEstudianteResponse response = service.asignar(1L, request);

        assertEquals(1L, response.idEstudiante());
        assertEquals(5, response.idInstrumento());
        assertEquals(LocalDate.now(), response.fechaInicio());
        assertTrue(response.esPrincipal());
        assertTrue(response.vigente());
    }

    @Test
    void asignar_alCambiarDeInstrumentoPrincipal_debeCerrarElAnteriorSinBorrarlo() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));
        when(instrumentoRepository.findByIdAndActivoTrue(9))
                .thenReturn(Optional.of(instrumento(9, "VIO", "Violín")));
        when(estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndIdInstrumento_IdAndFechaFinIsNull(1L, 9))
                .thenReturn(Optional.empty());

        EstudiantesInstrumento anterior = new EstudiantesInstrumento();
        anterior.setEstudiante(estudiante(1L));
        anterior.setInstrumento(instrumento(5, "PIA", "Piano"));
        anterior.setFechaInicio(LocalDate.now().minusMonths(6));
        anterior.setEsPrincipal(true);

        when(estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndEsPrincipalTrueAndFechaFinIsNull(1L))
                .thenReturn(Optional.of(anterior));
        when(estudianteInstrumentoRepository.save(any(EstudiantesInstrumento.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InstrumentoEstudianteRequest request = new InstrumentoEstudianteRequest();
        request.setIdInstrumento(9);

        service.cambiarPrincipal(1L, request);

        // El registro anterior se cierra (fecha_fin = hoy) pero se sigue guardando,
        // nunca se elimina: así se conserva el historial pedido por la HU.
        assertEquals(LocalDate.now(), anterior.getFechaFin());

        ArgumentCaptor<EstudiantesInstrumento> captor =
                ArgumentCaptor.forClass(EstudiantesInstrumento.class);
        verify(estudianteInstrumentoRepository, times(2)).save(captor.capture());
        verify(estudianteInstrumentoRepository, never()).delete(any());
        verify(estudianteInstrumentoRepository, never()).deleteById(any());
    }

    @Test
    void asignar_debePermitirVariosInstrumentosVigentesSiNoEsPrincipal() {
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
        request.setEsPrincipal(false);

        InstrumentoEstudianteResponse response = service.asignar(1L, request);

        assertFalse(response.esPrincipal());
        // No debe tocar ni buscar el instrumento principal vigente.
        verify(estudianteInstrumentoRepository, never())
                .findByIdEstudiante_IdAndEsPrincipalTrueAndFechaFinIsNull(any());
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
    void listarVigentes_debeRetornarSoloLosInstrumentosSinFechaFin() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante(1L)));

        EstudiantesInstrumento vigente = new EstudiantesInstrumento();
        vigente.setEstudiante(estudiante(1L));
        vigente.setInstrumento(instrumento(5, "PIA", "Piano"));
        vigente.setFechaInicio(LocalDate.now());
        vigente.setEsPrincipal(true);

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
