package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.EdicionEstudianteException;
import cr.acusab.una.sigac.logic.service.*;
import cr.acusab.una.sigac.presentation.dto.request.ContactoEmergenciaUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EncargadoUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteEdicionCompletaRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteEdicionCompletaRequest.EncargadoEdicion;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * Guardado completo de "Editar estudiante". Usa EncargadoService real con
 * repositorios simulados. Todos los datos son ficticios.
 */
class EstudianteEdicionCompletaTest {

    private static final Long ID_ESTUDIANTE = 10L;
    private static final String MARCA = EncargadoService.OBSERVACION_CARGA_INICIAL;

    private EstudianteRepository estudianteRepository;
    private EncargadoRepository encargadoRepository;
    private EstudianteEncargadoRepository relacionRepository;
    private ContactoEmergenciaRepository contactoRepository;
    private InstrumentoRepository instrumentoRepository;
    private EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private ProfesorRepository profesorRepository;
    private TipoDocumentoRepository tipoDocumentoRepository;
    private InscripcionPlanEstudioService planService;
    private DocumentoEstudianteService documentoService;
    private EstudianteService service;

    private Estudiante estudiante;
    private TiposIdentificacion cedula;
    private Instrumento piano;
    private Instrumento violin;

    @BeforeEach
    void setUp() {
        estudianteRepository = mock(EstudianteRepository.class);
        encargadoRepository = mock(EncargadoRepository.class);
        relacionRepository = mock(EstudianteEncargadoRepository.class);
        contactoRepository = mock(ContactoEmergenciaRepository.class);
        instrumentoRepository = mock(InstrumentoRepository.class);
        estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);
        profesorRepository = mock(ProfesorRepository.class);
        tipoDocumentoRepository = mock(TipoDocumentoRepository.class);
        planService = mock(InscripcionPlanEstudioService.class);
        documentoService = mock(DocumentoEstudianteService.class);
        TipoIdentificacionRepository tipoRepository = mock(TipoIdentificacionRepository.class);
        DistritoRepository distritoRepository = mock(DistritoRepository.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);

        EncargadoService encargadoService = new EncargadoService(
                estudianteRepository, encargadoRepository, relacionRepository, contactoRepository, tipoRepository);

        service = new EstudianteService(
                estudianteRepository, encargadoRepository, relacionRepository, instrumentoRepository,
                estudianteInstrumentoRepository, profesorRepository, mock(DocumentoEstudianteRepository.class),
                tipoRepository, tipoDocumentoRepository, mock(CentroEducativoRepository.class), usuarioRepository,
                mock(RoleRepository.class), mock(EstadoUsuarioRepository.class), mock(ContrasenaTemporalService.class),
                mock(BecaService.class), planService, distritoRepository,
                new CuentaUsuarioService(usuarioRepository, mock(EstadoUsuarioRepository.class)),
                encargadoService, documentoService);

        cedula = new TiposIdentificacion();
        cedula.setId(1);
        cedula.setCodigo("CEDULA");
        cedula.setNombre("Cédula nacional");
        cedula.setActivo(true);
        when(tipoRepository.findByIdAndActivoTrue(1)).thenReturn(Optional.of(cedula));
        when(tipoRepository.findById(1)).thenReturn(Optional.of(cedula));

        estudiante = new Estudiante();
        estudiante.setId(ID_ESTUDIANTE);
        estudiante.setActivo(true);
        estudiante.setIdTipoIdentificacion(cedula);
        estudiante.setNumeroIdentificacion("100000001");
        when(estudianteRepository.findById(ID_ESTUDIANTE)).thenReturn(Optional.of(estudiante));
        when(estudianteRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(1, "100000001"))
                .thenReturn(Optional.of(estudiante));

        Provincia provincia = new Provincia();
        provincia.setId(1);
        Canton canton = new Canton();
        canton.setCodigo(101);
        canton.setProvincia(provincia);
        Distrito distrito = new Distrito();
        distrito.setCodigo(10101);
        distrito.setCanton(canton);
        when(distritoRepository.findById(10101)).thenReturn(Optional.of(distrito));

        piano = instrumento(1, "Piano");
        violin = instrumento(2, "Violín");
        when(instrumentoRepository.findByIdAndActivoTrue(1)).thenReturn(Optional.of(piano));
        when(instrumentoRepository.findByIdAndActivoTrue(2)).thenReturn(Optional.of(violin));
        when(planService.sincronizar(any(), any())).thenReturn(List.of(1L));

        when(encargadoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(relacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(contactoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(estudianteInstrumentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- Todo o nada ----------

    @Test
    void siFallaUnEncargado_noSeGuardaNada() {
        relacion(encargadoExistente(20L, "200000002"), "MADRE", true, null);
        relacion(encargadoExistente(21L, "200000003"), "PADRE", false, null);

        EncargadoUpdateRequest malo = datosEncargado("200000003", "PADRE", false);
        malo.setParentesco("VECINO");

        var error = assertThrows(EdicionEstudianteException.class, () -> guardar(menor(List.of(
                new EncargadoEdicion(20L, datosEncargado("200000002", "MADRE", true)),
                new EncargadoEdicion(21L, malo)))));

        assertEquals(EdicionEstudianteException.ENCARGADOS, error.getErrores().get(0).seccion());
        assertEquals(1, error.getErrores().get(0).indiceEncargado());
        verify(estudianteRepository, never()).save(any());
        verify(encargadoRepository, never()).save(any());
        verify(relacionRepository, never()).save(any());
        verify(contactoRepository, never()).save(any());
        verify(estudianteInstrumentoRepository, never()).save(any());
        verify(planService, never()).sincronizar(any(), any());
    }

    @Test
    void reportaErroresDeVariasSecciones() {
        var datos = datosEstudiante(LocalDate.now().minusYears(12), 1, "OTRO_GENERO", 99999);

        var error = assertThrows(EdicionEstudianteException.class, () -> guardar(new EstudianteEdicionCompletaRequest(
                datos, LocalDate.now(), null, List.of())));

        var secciones = error.getErrores().stream().map(EdicionEstudianteException.ErrorEdicion::seccion).toList();
        assertTrue(secciones.containsAll(List.of("personal", "contacto", "encargados")));
    }

    // ---------- Encargados ----------

    @Test
    void quedaUnSoloResponsablePrincipal() {
        var madre = relacion(encargadoExistente(20L, "200000002"), "MADRE", true, null);
        var padre = relacion(encargadoExistente(21L, "200000003"), "PADRE", false, null);

        guardar(menor(List.of(
                new EncargadoEdicion(20L, datosEncargado("200000002", "MADRE", false)),
                new EncargadoEdicion(21L, datosEncargado("200000003", "PADRE", true)))));

        assertFalse(madre.getEsResponsablePrincipal());
        assertTrue(padre.getEsResponsablePrincipal());
    }

    @Test
    void rechazaDosResponsablesPrincipales() {
        relacion(encargadoExistente(20L, "200000002"), "MADRE", true, null);
        relacion(encargadoExistente(21L, "200000003"), "PADRE", false, null);

        var error = assertThrows(EdicionEstudianteException.class, () -> guardar(menor(List.of(
                new EncargadoEdicion(20L, datosEncargado("200000002", "MADRE", true)),
                new EncargadoEdicion(21L, datosEncargado("200000003", "PADRE", true))))));

        assertTieneMensaje(error, "Debe existir exactamente un responsable principal.");
    }

    @Test
    void rechazaMasDeDosEncargados() {
        var error = assertThrows(EdicionEstudianteException.class, () -> guardar(menor(List.of(
                new EncargadoEdicion(null, datosEncargado("200000002", "MADRE", true)),
                new EncargadoEdicion(null, datosEncargado("200000003", "PADRE", false)),
                new EncargadoEdicion(null, datosEncargado("200000004", "TUTOR", false))))));

        assertTieneMensaje(error, "Un menor de edad debe tener uno o dos encargados.");
    }

    @Test
    void rechazaMasDeDosContactosContandoTodosLosEncargados() {
        relacion(encargadoExistente(20L, "200000002"), "MADRE", true, null);
        relacion(encargadoExistente(21L, "200000003"), "PADRE", false, null);
        EncargadoUpdateRequest madre = datosEncargado("200000002", "MADRE", true);
        madre.setContactosEmergencia(List.of(contacto(null), contacto(null)));
        EncargadoUpdateRequest padre = datosEncargado("200000003", "PADRE", false);
        padre.setContactosEmergencia(List.of(contacto(null)));

        var error = assertThrows(EdicionEstudianteException.class, () -> guardar(menor(List.of(
                new EncargadoEdicion(20L, madre), new EncargadoEdicion(21L, padre)))));

        assertTieneMensaje(error, "El estudiante puede tener como máximo dos contactos de emergencia.");
    }

    @Test
    void elContactoQueNoVieneQuedaInactivoYNoSeBorra() {
        Encargado madre = encargadoExistente(20L, "200000002");
        relacion(madre, "MADRE", true, null);
        ContactoEmergencia existente = new ContactoEmergencia();
        existente.setId(70L);
        existente.setEstudiante(estudiante);
        existente.setEncargado(madre);
        existente.setActivo(true);
        when(contactoRepository.findByEstudiante_IdAndActivoTrue(ID_ESTUDIANTE)).thenReturn(List.of(existente));
        when(contactoRepository.findByEstudiante_IdAndEncargado_IdAndActivoTrue(ID_ESTUDIANTE, 20L))
                .thenReturn(List.of(existente));

        guardar(menor(List.of(new EncargadoEdicion(20L, datosEncargado("200000002", "MADRE", true)))));

        assertFalse(existente.getActivo());
        verify(contactoRepository, never()).delete(any());
    }

    @Test
    void parentescoPorConfirmarImpideGuardar() {
        relacion(encargadoExistente(20L, "200000002"), "OTRO", true, MARCA);
        EncargadoUpdateRequest datos = datosEncargado("200000002", "OTRO", true);
        datos.setObservaciones(MARCA);

        var error = assertThrows(EdicionEstudianteException.class,
                () -> guardar(menor(List.of(new EncargadoEdicion(20L, datos)))));

        assertEquals(0, error.getErrores().get(0).indiceEncargado());
        verify(relacionRepository, never()).save(any());
    }

    @Test
    void alCambiarElParentescoSeLimpiaLaObservacionDeCargaInicial() {
        var relacion = relacion(encargadoExistente(20L, "200000002"), "OTRO", true, MARCA);
        EncargadoUpdateRequest datos = datosEncargado("200000002", "MADRE", true);
        datos.setObservaciones(MARCA);

        guardar(menor(List.of(new EncargadoEdicion(20L, datos))));

        assertEquals("MADRE", relacion.getParentesco());
        assertNull(relacion.getObservaciones());
    }

    @Test
    void encargadoNuevoConIdentificacionExistenteSeReutiliza() {
        Encargado existente = encargadoExistente(30L, "200000009");
        when(encargadoRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(1, "200000009"))
                .thenReturn(Optional.of(existente));

        guardar(menor(List.of(new EncargadoEdicion(null, datosEncargado("200000009", "TUTOR", true)))));

        verify(encargadoRepository, never()).save(any());
        verify(relacionRepository).save(argThat(r -> r.getEncargado() == existente));
    }

    @Test
    void siEsMayorNoSeTocanLosEncargados() {
        var datos = datosEstudiante(LocalDate.now().minusYears(25), 1, "FEMENINO", 10101);

        guardar(new EstudianteEdicionCompletaRequest(datos, LocalDate.now(), null,
                List.of(new EncargadoEdicion(20L, datosEncargado("200000002", "VECINO", true)))));

        verify(encargadoRepository, never()).save(any());
        verify(relacionRepository, never()).save(any());
    }

    // ---------- Instrumentos ----------

    @Test
    void actualizarCompleto_soloCambiaElPrincipal() {
        var extra = relacionInstrumento(1L, violin, false);
        var principal = relacionInstrumento(2L, piano, true);
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(ID_ESTUDIANTE))
                .thenReturn(List.of(extra, principal));
        Instrumento flauta = instrumento(3, "Flauta");
        when(instrumentoRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(flauta));
        var datos = datosEstudiante(LocalDate.now().minusYears(25), 3, "FEMENINO", 10101);

        guardar(new EstudianteEdicionCompletaRequest(datos, LocalDate.now(), null, null));

        assertSame(flauta, principal.getInstrumento());
        assertSame(violin, extra.getInstrumento());
        verify(estudianteInstrumentoRepository, never()).save(extra);
    }

    @Test
    void actualizar_soloCambiaElPrincipal() {
        var extra = relacionInstrumento(1L, violin, false);
        var principal = relacionInstrumento(2L, piano, true);
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(ID_ESTUDIANTE))
                .thenReturn(List.of(extra, principal));
        Instrumento flauta = instrumento(3, "Flauta");
        when(instrumentoRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(flauta));

        service.actualizar(ID_ESTUDIANTE, datosEstudiante(LocalDate.now().minusYears(25), 3, "FEMENINO", 10101));

        assertSame(flauta, principal.getInstrumento());
        assertSame(violin, extra.getInstrumento());
        verify(estudianteInstrumentoRepository, never()).save(extra);
    }

    @Test
    void sinRelacionPrincipalSeCreaComoPrincipal() {
        var extra = relacionInstrumento(1L, violin, false);
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(ID_ESTUDIANTE))
                .thenReturn(List.of(extra));
        var datos = datosEstudiante(LocalDate.now().minusYears(25), 1, "FEMENINO", 10101);

        guardar(new EstudianteEdicionCompletaRequest(datos, LocalDate.now(), null, null));

        verify(estudianteInstrumentoRepository).save(argThat(r ->
                r != extra && r.getInstrumento() == piano && Boolean.TRUE.equals(r.getEsPrincipal())));
        assertSame(violin, extra.getInstrumento());
    }

    @Test
    void profesorQueNoDaEseInstrumentoDaError() {
        Profesor profesor = new Profesor();
        profesor.setId(5L);
        profesor.setActivo(true);
        profesor.setEspecialidad("Violín");
        when(profesorRepository.findById(5L)).thenReturn(Optional.of(profesor));
        var datos = datosEstudiante(LocalDate.now().minusYears(25), 1, "FEMENINO", 10101);

        var error = assertThrows(EdicionEstudianteException.class, () -> guardar(
                new EstudianteEdicionCompletaRequest(datos, LocalDate.now(), 5L, null)));

        assertEquals(EdicionEstudianteException.INSTRUMENTOS, error.getErrores().get(0).seccion());
        verify(estudianteRepository, never()).save(any());
    }

    @Test
    void profesorNullDejaElInstrumentoSinProfesor() {
        var principal = relacionInstrumento(2L, piano, true);
        principal.setProfesor(new Profesor());
        when(estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(ID_ESTUDIANTE))
                .thenReturn(List.of(principal));
        var datos = datosEstudiante(LocalDate.now().minusYears(25), 1, "FEMENINO", 10101);

        guardar(new EstudianteEdicionCompletaRequest(datos, LocalDate.now(), null, null));

        assertNull(principal.getProfesor());
    }

    // ---------- Póliza ----------

    @Test
    void fechasDePolizaSinArchivoDanError() {
        var datos = datosEstudiante(LocalDate.now().minusYears(25), 1, "FEMENINO", 10101);

        var error = assertThrows(EdicionEstudianteException.class, () -> service.actualizarCompleto(ID_ESTUDIANTE,
                new EstudianteEdicionCompletaRequest(datos, LocalDate.now(), null, null),
                null, LocalDate.now(), null));

        assertEquals(EdicionEstudianteException.POLIZA, error.getErrores().get(0).seccion());
    }

    @Test
    void siLaTransaccionFalla_seBorraElPdfCopiado(@TempDir Path carpeta) throws IOException {
        EstudianteRepository repoEstudiantes = mock(EstudianteRepository.class);
        TipoDocumentoRepository repoTipos = mock(TipoDocumentoRepository.class);
        DocumentoEstudianteRepository repoDocumentos = mock(DocumentoEstudianteRepository.class);
        UsuarioRepository repoUsuarios = mock(UsuarioRepository.class);
        DocumentoEstudianteService real = new DocumentoEstudianteService(
                repoDocumentos, repoEstudiantes, repoTipos, repoUsuarios);
        ReflectionTestUtils.setField(real, "storageDirectory", carpeta.toString());

        TiposDocumento poliza = new TiposDocumento();
        poliza.setId(4);
        poliza.setCodigo("POLIZA");
        poliza.setNombre("Póliza");
        poliza.setActivo(true);
        when(repoEstudiantes.findByIdAndActivoTrue(ID_ESTUDIANTE)).thenReturn(Optional.of(estudiante));
        when(repoTipos.findById(4)).thenReturn(Optional.of(poliza));
        when(repoUsuarios.findByCedula("admin")).thenReturn(Optional.of(new Usuario()));
        when(repoDocumentos.save(any())).thenAnswer(i -> i.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", null, "ROLE_ADMINISTRATIVO"));

        TransactionSynchronizationManager.initSynchronization();
        real.adjuntarOReemplazar(ID_ESTUDIANTE, 4, pdf(), null, null);
        assertEquals(1, archivosEn(carpeta), "El PDF se copia antes del commit");

        // Simula que la transacción se revierte después de copiar el archivo.
        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertEquals(0, archivosEn(carpeta));
    }

    @AfterEach
    void limpiarContextos() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---------- Datos de apoyo (ficticios) ----------

    private void guardar(EstudianteEdicionCompletaRequest request) {
        service.actualizarCompleto(ID_ESTUDIANTE, request, null, null, null);
    }

    private EstudianteEdicionCompletaRequest menor(List<EncargadoEdicion> encargados) {
        return new EstudianteEdicionCompletaRequest(
                datosEstudiante(LocalDate.now().minusYears(12), 1, "FEMENINO", 10101),
                LocalDate.now(), null, encargados);
    }

    private EstudianteUpdateRequest datosEstudiante(LocalDate nacimiento, int idInstrumento, String genero, int distrito) {
        boolean conCentro = nacimiento.isAfter(LocalDate.now().minusYears(18));
        return new EstudianteUpdateRequest(1, "100000001", "Ana", "Prueba", null, nacimiento, genero, distrito,
                "Dirección de prueba", "88880000", "ana.prueba@example.com", "Primaria",
                conCentro ? "Escuela de prueba" : null, null, idInstrumento, List.of(1L));
    }

    private EncargadoUpdateRequest datosEncargado(String numero, String parentesco, boolean principal) {
        EncargadoUpdateRequest datos = new EncargadoUpdateRequest();
        datos.setIdTipoIdentificacion(1);
        datos.setNumeroIdentificacion(numero);
        datos.setNombres("Encargado");
        datos.setPrimerApellido("Prueba");
        datos.setTelefonoPrincipal("88881111");
        datos.setParentesco(parentesco);
        datos.setEsResponsablePrincipal(principal);
        datos.setAutorizadoRetiro(false);
        return datos;
    }

    private ContactoEmergenciaUpdateRequest contacto(Long id) {
        return new ContactoEmergenciaUpdateRequest(id, "Contacto Prueba", "88882222", "Vecino");
    }

    private Encargado encargadoExistente(Long id, String numero) {
        Encargado encargado = new Encargado();
        encargado.setId(id);
        encargado.setIdTipoIdentificacion(cedula);
        encargado.setNumeroIdentificacion(numero);
        encargado.setNombres("Encargado");
        encargado.setPrimerApellido("Prueba");
        encargado.setActivo(true);
        when(encargadoRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(1, numero))
                .thenReturn(Optional.of(encargado));
        return encargado;
    }

    private final List<EstudiantesEncargado> relaciones = new ArrayList<>();

    private EstudiantesEncargado relacion(Encargado encargado, String parentesco, boolean principal, String observaciones) {
        EstudiantesEncargadoId id = new EstudiantesEncargadoId();
        id.setIdEstudiante(ID_ESTUDIANTE);
        id.setIdEncargado(encargado.getId());
        EstudiantesEncargado relacion = new EstudiantesEncargado();
        relacion.setId(id);
        relacion.setEstudiante(estudiante);
        relacion.setEncargado(encargado);
        relacion.setParentesco(parentesco);
        relacion.setEsResponsablePrincipal(principal);
        relacion.setObservaciones(observaciones);
        relaciones.add(relacion);
        when(relacionRepository.findByIdEstudiante(ID_ESTUDIANTE)).thenReturn(relaciones);
        when(relacionRepository.findByIdEstudianteAndIdEncargado(ID_ESTUDIANTE, encargado.getId()))
                .thenReturn(Optional.of(relacion));
        return relacion;
    }

    private Instrumento instrumento(int id, String nombre) {
        Instrumento instrumento = new Instrumento();
        instrumento.setId(id);
        instrumento.setNombre(nombre);
        return instrumento;
    }

    private EstudiantesInstrumento relacionInstrumento(Long id, Instrumento instrumento, boolean principal) {
        EstudiantesInstrumento relacion = new EstudiantesInstrumento();
        relacion.setId(id);
        relacion.setEstudiante(estudiante);
        relacion.setInstrumento(instrumento);
        relacion.setEsPrincipal(principal);
        relacion.setFechaInicio(LocalDate.now().minusYears(1));
        return relacion;
    }

    private MockMultipartFile pdf() throws IOException {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            documento.addPage(new PDPage());
            documento.save(salida);
            return new MockMultipartFile("poliza", "poliza.pdf", "application/pdf", salida.toByteArray());
        }
    }

    private long archivosEn(Path carpeta) throws IOException {
        try (Stream<Path> rutas = Files.walk(carpeta)) {
            return rutas.filter(Files::isRegularFile).count();
        }
    }

    private void assertTieneMensaje(EdicionEstudianteException error, String mensaje) {
        assertTrue(error.getErrores().stream().anyMatch(e -> mensaje.equals(e.mensaje())),
                () -> "Errores: " + error.getErrores());
    }
}
