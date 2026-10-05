package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.logic.service.RequisitosCursoService;
import cr.acusab.una.sigac.presentation.dto.request.RequisitosCursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.request.ConfiguracionRequisitosCursoRequest;
import cr.acusab.una.sigac.presentation.dto.response.RequisitosCursoResponseDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrerrequisitosCursoServiceTest {
    @Test
    void guardarConfiguracionCompletaDeHu32() {
        var service = mock(RequisitosCursoService.class, CALLS_REAL_METHODS);
        var actual = new ConfiguracionRequisitosCursoRequest();
        actual.setPrerrequisitos(List.of(3L));
        actual.setCorrequisitos(List.of(4L));
        actual.setEdadMinima(10);
        actual.setEdadMaxima(17);
        doReturn(new RequisitosCursoResponseDTO()).when(service).guardar(any());

        service.guardarConfiguracion(1L, 2L, actual);

        var captor = ArgumentCaptor.forClass(RequisitosCursoRequestDTO.class);
        verify(service).guardar(captor.capture());
        var request = captor.getValue();
        assertEquals(1L, request.getIdPlanEstudio());
        assertEquals(2L, request.getIdCurso());
        assertEquals(List.of(3L), request.getPrerrequisitos());
        assertEquals(List.of(4L), request.getCorrequisitos());
        assertEquals(10, request.getEdadMinima());
        assertEquals(17, request.getEdadMaxima());
    }

    @Test
    void permiteQuitarTodosLosRequisitosYLimitesDeEdad() {
        var service = mock(RequisitosCursoService.class, CALLS_REAL_METHODS);
        doReturn(new RequisitosCursoResponseDTO()).when(service).guardar(any());
        service.guardarConfiguracion(1L, 2L, new ConfiguracionRequisitosCursoRequest());
        var captor = ArgumentCaptor.forClass(RequisitosCursoRequestDTO.class);
        verify(service).guardar(captor.capture());
        assertTrue(captor.getValue().getPrerrequisitos().isEmpty());
        assertTrue(captor.getValue().getCorrequisitos().isEmpty());
        assertNull(captor.getValue().getEdadMinima());
        assertNull(captor.getValue().getEdadMaxima());
    }
}
