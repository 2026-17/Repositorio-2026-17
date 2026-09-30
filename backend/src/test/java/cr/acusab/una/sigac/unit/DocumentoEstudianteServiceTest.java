package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.DocumentoEstudiante;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.TipoDocumento;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.DocumentoEstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.TipoDocumentoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.DocumentoEstudianteService;
import cr.acusab.una.sigac.presentation.dto.response.DocumentoEstudianteResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentoEstudianteServiceTest {

    @TempDir
    Path tempDir;

    private DocumentoEstudianteRepository documentoEstudianteRepository;
    private EstudianteRepository estudianteRepository;
    private TipoDocumentoRepository tipoDocumentoRepository;
    private UsuarioRepository usuarioRepository;

    private DocumentoEstudianteService documentoEstudianteService;

    @BeforeEach
    void setUp() {
        documentoEstudianteRepository = mock(DocumentoEstudianteRepository.class);
        estudianteRepository = mock(EstudianteRepository.class);
        tipoDocumentoRepository = mock(TipoDocumentoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);

        documentoEstudianteService = new DocumentoEstudianteService(
                documentoEstudianteRepository,
                estudianteRepository,
                tipoDocumentoRepository,
                usuarioRepository,
                tempDir.toString()
        );
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * HU-022 - Adjuntar documento del estudiante
     * ============================================================
     */

    @Test
    void debeAdjuntarDocumentoCorrectamente() throws IOException {

        Estudiante estudiante = estudianteConId(1L);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        TipoDocumento tipo = tipoDocumentoActivo(5, "IDENTIFICACION");
        when(tipoDocumentoRepository.findById(5)).thenReturn(Optional.of(tipo));

        when(documentoEstudianteRepository
                .findByIdEstudiante_IdAndIdTipoDocumento_IdAndEsActualTrue(1L, 5))
                .thenReturn(Optional.empty());

        configurarUsuarioAutenticado();
        Usuario usuario = mock(Usuario.class);
        when(usuarioRepository.findByCedula("123456789")).thenReturn(Optional.of(usuario));

        when(documentoEstudianteRepository.save(any(DocumentoEstudiante.class)))
                .thenAnswer(invocation -> {
                    DocumentoEstudiante doc = invocation.getArgument(0);
                    doc.setId(200L);
                    return doc;
                });

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "cedula.pdf", "application/pdf", pdfValido());

        DocumentoEstudianteResponse respuesta = documentoEstudianteService.adjuntar(
                1L, 5, archivo, null, null);

        assertNotNull(respuesta);
        assertEquals(200L, respuesta.getId());
        assertTrue(respuesta.getEsActual());
        assertEquals("cedula.pdf", respuesta.getNombreOriginal());

        ArgumentCaptor<DocumentoEstudiante> captor = ArgumentCaptor.forClass(DocumentoEstudiante.class);
        verify(documentoEstudianteRepository).save(captor.capture());

        DocumentoEstudiante guardado = captor.getValue();
        assertTrue(guardado.getEsActual());
        assertNotNull(guardado.getSha256());
        assertEquals(64, guardado.getSha256().length());

        // El archivo debe haberse escrito realmente en disco.
        assertTrue(Path.of(guardado.getUbicacionArchivo()).toFile().exists());
    }

    @Test
    void debeRechazarArchivoQueNoSeaPdf() {

        Estudiante estudiante = estudianteConId(1L);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        TipoDocumento tipo = tipoDocumentoActivo(5, "IDENTIFICACION");
        when(tipoDocumentoRepository.findById(5)).thenReturn(Optional.of(tipo));

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "foto.png", "image/png", "no es un pdf".getBytes());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> documentoEstudianteService.adjuntar(1L, 5, archivo, null, null)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(documentoEstudianteRepository, never()).save(any(DocumentoEstudiante.class));
    }

    @Test
    void debeRechazarPdfDanado() {

        Estudiante estudiante = estudianteConId(1L);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        TipoDocumento tipo = tipoDocumentoActivo(5, "IDENTIFICACION");
        when(tipoDocumentoRepository.findById(5)).thenReturn(Optional.of(tipo));

        // Content-type correcto, pero el contenido no es un PDF valido (esta truncado/corrupto).
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "cedula.pdf", "application/pdf", "%PDF-1.4 esto no es un pdf real".getBytes());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> documentoEstudianteService.adjuntar(1L, 5, archivo, null, null)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(documentoEstudianteRepository, never()).save(any(DocumentoEstudiante.class));
    }

    @Test
    void debeReemplazarDocumentoVigenteDelMismoTipo() throws IOException {

        Estudiante estudiante = estudianteConId(1L);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        TipoDocumento tipo = tipoDocumentoActivo(5, "IDENTIFICACION");
        when(tipoDocumentoRepository.findById(5)).thenReturn(Optional.of(tipo));

        DocumentoEstudiante vigente = new DocumentoEstudiante();
        vigente.setId(99L);
        vigente.setEsActual(true);

        when(documentoEstudianteRepository
                .findByIdEstudiante_IdAndIdTipoDocumento_IdAndEsActualTrue(1L, 5))
                .thenReturn(Optional.of(vigente));

        configurarUsuarioAutenticado();
        when(usuarioRepository.findByCedula("123456789")).thenReturn(Optional.of(mock(Usuario.class)));

        when(documentoEstudianteRepository.save(any(DocumentoEstudiante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "cedula-nueva.pdf", "application/pdf", pdfValido());

        documentoEstudianteService.adjuntar(1L, 5, archivo, null, null);

        assertFalse(vigente.getEsActual());

        ArgumentCaptor<DocumentoEstudiante> captor = ArgumentCaptor.forClass(DocumentoEstudiante.class);
        verify(documentoEstudianteRepository, times(2)).save(captor.capture());

        DocumentoEstudiante nuevo = captor.getAllValues().get(1);
        assertTrue(nuevo.getEsActual());
        assertEquals("cedula-nueva.pdf", nuevo.getNombreOriginal());
    }

    @Test
    void debeRechazarAdjuntarSiEstudianteNoExiste() throws IOException {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.empty());

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "cedula.pdf", "application/pdf", pdfValido());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> documentoEstudianteService.adjuntar(1L, 5, archivo, null, null)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(tipoDocumentoRepository, never()).findById(anyInt());
    }

    @Test
    void debeRechazarAdjuntarSiTipoDocumentoNoExiste() throws IOException {

        Estudiante estudiante = estudianteConId(1L);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        when(tipoDocumentoRepository.findById(5)).thenReturn(Optional.empty());

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "cedula.pdf", "application/pdf", pdfValido());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> documentoEstudianteService.adjuntar(1L, 5, archivo, null, null)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(documentoEstudianteRepository, never()).save(any(DocumentoEstudiante.class));
    }

    @Test
    void debeListarSoloDocumentosVigentesDelEstudiante() {

        when(estudianteRepository.existsById(1L)).thenReturn(true);

        DocumentoEstudiante doc = new DocumentoEstudiante();
        doc.setId(10L);
        doc.setIdEstudiante(estudianteConId(1L));
        doc.setIdTipoDocumento(tipoDocumentoActivo(5, "IDENTIFICACION"));
        doc.setNombreOriginal("cedula.pdf");
        doc.setTipoMime("application/pdf");
        doc.setTamanoBytes(1024L);
        doc.setEsActual(true);

        when(documentoEstudianteRepository.findByIdEstudiante_IdAndEsActualTrue(1L))
                .thenReturn(java.util.List.of(doc));

        var resultado = documentoEstudianteService.listarPorEstudiante(1L);

        assertEquals(1, resultado.size());
        assertEquals("cedula.pdf", resultado.get(0).getNombreOriginal());
    }

    /*
     * ============================================================
     * Métodos auxiliares para las pruebas
     * ============================================================
     */

    private Estudiante estudianteConId(Long id) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(id);
        return estudiante;
    }

    private TipoDocumento tipoDocumentoActivo(Integer id, String codigo) {
        TipoDocumento tipo = new TipoDocumento();
        tipo.setId(id);
        tipo.setCodigo(codigo);
        tipo.setNombre(codigo);
        tipo.setActivo(true);
        return tipo;
    }

    private byte[] pdfValido() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private void configurarUsuarioAutenticado() {

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("123456789");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }
}
