package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.DocumentosEstudiante;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.TiposDocumento;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.DocumentoEstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.TipoDocumentoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.DocumentoEstudianteService;
import cr.acusab.una.sigac.presentation.dto.response.DocumentoEstudianteResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentoEstudianteServiceTest {

    private DocumentoEstudianteRepository documentoEstudianteRepository;
    private EstudianteRepository estudianteRepository;
    private TipoDocumentoRepository tipoDocumentoRepository;
    private UsuarioRepository usuarioRepository;

    private DocumentoEstudianteService documentoEstudianteService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {

        documentoEstudianteRepository =
                mock(DocumentoEstudianteRepository.class);

        estudianteRepository =
                mock(EstudianteRepository.class);

        tipoDocumentoRepository =
                mock(TipoDocumentoRepository.class);

        usuarioRepository =
                mock(UsuarioRepository.class);

        documentoEstudianteService =
                new DocumentoEstudianteService(
                        documentoEstudianteRepository,
                        estudianteRepository,
                        tipoDocumentoRepository,
                        usuarioRepository
                );

        ReflectionTestUtils.setField(
                documentoEstudianteService,
                "storageDirectory",
                tempDir.toString()
        );
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * HU-022 - Adjuntar documentos del estudiante
     * ============================================================
     */

    @Test
    void adjuntarPdfValidoGuardaDocumentoAsociadoAlEstudiante()
            throws IOException {

        Estudiante estudiante = estudianteActivo();
        TiposDocumento tipoDocumento = tipoDocumentoActivo();

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoDocumentoRepository.findById(10))
                .thenReturn(Optional.of(tipoDocumento));

        configurarUsuarioAutenticado();

        when(documentoEstudianteRepository
                .findFirstByIdEstudiante_IdAndIdTipoDocumento_IdAndEsActualTrueOrderByCargadoEnDesc(
                        1L,
                        10
                ))
                .thenReturn(Optional.empty());

        when(documentoEstudianteRepository
                .save(any(DocumentosEstudiante.class)))
                .thenAnswer(invocation -> {

                    DocumentosEstudiante documento =
                            invocation.getArgument(0);

                    documento.setId(100L);

                    return documento;
                });

        MockMultipartFile archivo = pdfValido(
                "certificado.pdf"
        );

        DocumentoEstudianteResponse respuesta =
                documentoEstudianteService.adjuntarOReemplazar(
                        1L,
                        10,
                        archivo,
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2027, 10, 1)
                );

        assertNotNull(respuesta);

        assertEquals(100L, respuesta.id());
        assertEquals(1L, respuesta.idEstudiante());
        assertEquals(10, respuesta.idTipoDocumento());
        assertEquals("CERTIFICADO", respuesta.codigoTipoDocumento());
        assertEquals("Certificado", respuesta.nombreTipoDocumento());
        assertEquals("certificado.pdf", respuesta.nombreOriginal());
        assertEquals("application/pdf", respuesta.tipoMime());
        assertTrue(respuesta.esActual());

        verify(documentoEstudianteRepository)
                .save(argThat(documento ->
                        documento.getIdEstudiante() == estudiante
                                && documento.getIdTipoDocumento() == tipoDocumento
                                && Boolean.TRUE.equals(documento.getEsActual())
                                && documento.getSha256() != null
                                && documento.getSha256().length() == 64
                                && documento.getIdUsuarioCarga() != null
                ));

        Path carpetaEstudiante =
                tempDir.resolve("1");

        assertTrue(Files.exists(carpetaEstudiante));

        try (var archivos = Files.list(carpetaEstudiante)) {
            assertEquals(1L, archivos.count());
        }
    }

    @Test
    void reemplazarDocumentoMarcaAnteriorComoNoActualYGuardaNuevo()
            throws IOException {

        Estudiante estudiante = estudianteActivo();
        TiposDocumento tipoDocumento = tipoDocumentoActivo();

        DocumentosEstudiante anterior =
                new DocumentosEstudiante();

        anterior.setId(50L);
        anterior.setEstudiante(estudiante);
        anterior.setIdTipoDocumento(tipoDocumento);
        anterior.setNombreOriginal("anterior.pdf");
        anterior.setNombreAlmacenado("anterior.pdf");
        anterior.setUbicacionArchivo("anterior.pdf");
        anterior.setTipoMime("application/pdf");
        anterior.setTamanoBytes(100L);
        anterior.setEsActual(true);
        anterior.setCargadoEn(Instant.now());

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoDocumentoRepository.findById(10))
                .thenReturn(Optional.of(tipoDocumento));

        configurarUsuarioAutenticado();

        when(documentoEstudianteRepository
                .findFirstByIdEstudiante_IdAndIdTipoDocumento_IdAndEsActualTrueOrderByCargadoEnDesc(
                        1L,
                        10
                ))
                .thenReturn(Optional.of(anterior));

        when(documentoEstudianteRepository
                .save(any(DocumentosEstudiante.class)))
                .thenAnswer(invocation -> {

                    DocumentosEstudiante documento =
                            invocation.getArgument(0);

                    if (documento.getId() == null) {
                        documento.setId(101L);
                    }

                    return documento;
                });

        MockMultipartFile archivo =
                pdfValido("nuevo.pdf");

        DocumentoEstudianteResponse respuesta =
                documentoEstudianteService.adjuntarOReemplazar(
                        1L,
                        10,
                        archivo,
                        null,
                        null
                );

        assertFalse(anterior.getEsActual());
        assertEquals(101L, respuesta.id());
        assertTrue(respuesta.esActual());
        assertEquals("nuevo.pdf", respuesta.nombreOriginal());

        verify(documentoEstudianteRepository)
                .save(anterior);

        verify(documentoEstudianteRepository)
                .save(argThat(documento ->
                        documento != anterior
                                && Boolean.TRUE.equals(documento.getEsActual())
                                && documento.getIdTipoDocumento() == tipoDocumento
                ));
    }

    @Test
    void rechazarArchivoQueNoSeaPdf() {

        Estudiante estudiante = estudianteActivo();
        TiposDocumento tipoDocumento = tipoDocumentoActivo();

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoDocumentoRepository.findById(10))
                .thenReturn(Optional.of(tipoDocumento));

        MockMultipartFile archivo =
                new MockMultipartFile(
                        "archivo",
                        "imagen.png",
                        "image/png",
                        "contenido".getBytes()
                );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> documentoEstudianteService
                                .adjuntarOReemplazar(
                                        1L,
                                        10,
                                        archivo,
                                        null,
                                        null
                                )
                );

        assertTrue(
                exception.getMessage()
                        .toLowerCase()
                        .contains("pdf")
        );

        verifyNoInteractions(usuarioRepository);

        verify(documentoEstudianteRepository, never())
                .save(any());
    }

    @Test
    void rechazarPdfDanado() {

        Estudiante estudiante = estudianteActivo();
        TiposDocumento tipoDocumento = tipoDocumentoActivo();

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoDocumentoRepository.findById(10))
                .thenReturn(Optional.of(tipoDocumento));

        /*
         * Tiene nombre y MIME de PDF e incluso comienza con %PDF,
         * pero su estructura interna no es un documento PDF válido.
         */
        MockMultipartFile archivo =
                new MockMultipartFile(
                        "archivo",
                        "danado.pdf",
                        "application/pdf",
                        "%PDF-1.4\narchivo corrupto"
                                .getBytes()
                );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> documentoEstudianteService
                                .adjuntarOReemplazar(
                                        1L,
                                        10,
                                        archivo,
                                        null,
                                        null
                                )
                );

        assertTrue(
                exception.getMessage()
                        .toLowerCase()
                        .contains("dañado")
                        ||
                        exception.getMessage()
                                .toLowerCase()
                                .contains("válido")
        );

        verify(documentoEstudianteRepository, never())
                .save(any());
    }

    @Test
    void rechazarCuandoEstudianteNoExiste()
            throws IOException {

        when(estudianteRepository.findByIdAndActivoTrue(99L))
                .thenReturn(Optional.empty());

        MockMultipartFile archivo =
                pdfValido("documento.pdf");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> documentoEstudianteService
                                .adjuntarOReemplazar(
                                        99L,
                                        10,
                                        archivo,
                                        null,
                                        null
                                )
                );

        assertTrue(
                exception.getMessage()
                        .toLowerCase()
                        .contains("estudiante")
        );

        verifyNoInteractions(tipoDocumentoRepository);
        verifyNoInteractions(usuarioRepository);

        verify(documentoEstudianteRepository, never())
                .save(any());
    }

    @Test
    void rechazarCuandoTipoDocumentoEstaInactivo()
            throws IOException {

        Estudiante estudiante = estudianteActivo();

        TiposDocumento tipoInactivo =
                tipoDocumentoActivo();

        tipoInactivo.setActivo(false);

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoDocumentoRepository.findById(10))
                .thenReturn(Optional.of(tipoInactivo));

        MockMultipartFile archivo =
                pdfValido("documento.pdf");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> documentoEstudianteService
                                .adjuntarOReemplazar(
                                        1L,
                                        10,
                                        archivo,
                                        null,
                                        null
                                )
                );

        assertTrue(
                exception.getMessage()
                        .toLowerCase()
                        .contains("tipo de documento")
        );

        verifyNoInteractions(usuarioRepository);

        verify(documentoEstudianteRepository, never())
                .save(any());
    }

    @Test
    void listarDocumentosActualesDelEstudiante() {

        Estudiante estudiante = estudianteActivo();
        TiposDocumento tipoDocumento = tipoDocumentoActivo();

        DocumentosEstudiante documento =
                new DocumentosEstudiante();

        documento.setId(200L);
        documento.setEstudiante(estudiante);
        documento.setIdTipoDocumento(tipoDocumento);
        documento.setNombreOriginal("expediente.pdf");
        documento.setNombreAlmacenado("uuid.pdf");
        documento.setUbicacionArchivo("ruta/uuid.pdf");
        documento.setTipoMime("application/pdf");
        documento.setTamanoBytes(500L);
        documento.setEsActual(true);
        documento.setCargadoEn(Instant.now());

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(documentoEstudianteRepository
                .findByIdEstudiante_IdAndEsActualTrueOrderByCargadoEnDesc(1L))
                .thenReturn(List.of(documento));

        List<DocumentoEstudianteResponse> documentos =
                documentoEstudianteService.listarActuales(1L);

        assertEquals(1, documentos.size());

        DocumentoEstudianteResponse respuesta =
                documentos.getFirst();

        assertEquals(200L, respuesta.id());
        assertEquals(1L, respuesta.idEstudiante());
        assertEquals("expediente.pdf", respuesta.nombreOriginal());
        assertEquals("CERTIFICADO", respuesta.codigoTipoDocumento());
        assertTrue(respuesta.esActual());
    }

    @Test
    void listarDocumentosRechazaEstudianteInexistente() {

        when(estudianteRepository.findByIdAndActivoTrue(99L))
                .thenReturn(Optional.empty());

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> documentoEstudianteService
                                .listarActuales(99L)
                );

        assertTrue(
                exception.getMessage()
                        .toLowerCase()
                        .contains("estudiante")
        );

        verify(documentoEstudianteRepository, never())
                .findByIdEstudiante_IdAndEsActualTrueOrderByCargadoEnDesc(
                        any()
                );
    }

    @Test
    void rechazarFechaVencimientoAnteriorAFechaEmision()
            throws IOException {

        Estudiante estudiante = estudianteActivo();
        TiposDocumento tipoDocumento = tipoDocumentoActivo();

        when(estudianteRepository.findByIdAndActivoTrue(1L))
                .thenReturn(Optional.of(estudiante));

        when(tipoDocumentoRepository.findById(10))
                .thenReturn(Optional.of(tipoDocumento));

        MockMultipartFile archivo =
                pdfValido("documento.pdf");

        LocalDate fechaEmision =
                LocalDate.of(2026, 10, 10);

        LocalDate fechaVencimiento =
                LocalDate.of(2026, 10, 1);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> documentoEstudianteService
                                .adjuntarOReemplazar(
                                        1L,
                                        10,
                                        archivo,
                                        fechaEmision,
                                        fechaVencimiento
                                )
                );

        assertTrue(
                exception.getMessage()
                        .toLowerCase()
                        .contains("vencimiento")
        );

        verifyNoInteractions(usuarioRepository);

        verify(documentoEstudianteRepository, never())
                .save(any());
    }

    /*
     * ============================================================
     * Métodos auxiliares
     * ============================================================
     */

    private Estudiante estudianteActivo() {

        Estudiante estudiante =
                new Estudiante();

        estudiante.setId(1L);
        estudiante.setActivo(true);
        estudiante.setNombres("Ana");
        estudiante.setPrimerApellido("Sánchez");

        return estudiante;
    }

    private TiposDocumento tipoDocumentoActivo() {

        TiposDocumento tipo =
                new TiposDocumento();

        tipo.setId(10);
        tipo.setCodigo("CERTIFICADO");
        tipo.setNombre("Certificado");
        tipo.setDescripcion(
                "Documento de prueba para expediente"
        );
        tipo.setActivo(true);

        return tipo;
    }

    private Usuario usuarioAutenticado() {

        Usuario usuario =
                new Usuario();

        usuario.setId(5L);
        usuario.setCedula("888888888");

        return usuario;
    }

    private void configurarUsuarioAutenticado() {

        Usuario usuario =
                usuarioAutenticado();

        when(usuarioRepository.findByCedula("888888888"))
                .thenReturn(Optional.of(usuario));

        Authentication authentication =
                mock(Authentication.class);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getName())
                .thenReturn("888888888");

        SecurityContext securityContext =
                mock(SecurityContext.class);

        when(securityContext.getAuthentication())
                .thenReturn(authentication);

        SecurityContextHolder.setContext(
                securityContext
        );
    }

    /**
     * Genera un PDF real y válido usando PDFBox.
     * Así la prueba no depende de bytes falsos.
     */
    private MockMultipartFile pdfValido(
            String nombre
    ) throws IOException {

        byte[] contenido;

        try (
                PDDocument documento =
                        new PDDocument();

                ByteArrayOutputStream salida =
                        new ByteArrayOutputStream()
        ) {
            documento.addPage(
                    new org.apache.pdfbox.pdmodel.PDPage()
            );

            documento.save(salida);

            contenido = salida.toByteArray();
        }

        return new MockMultipartFile(
                "archivo",
                nombre,
                "application/pdf",
                contenido
        );
    }
}