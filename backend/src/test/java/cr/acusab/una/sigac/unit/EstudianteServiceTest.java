package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Canton;
import cr.acusab.una.sigac.data.entity.Distrito;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.Provincia;
import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.logic.service.BecaService;
import cr.acusab.una.sigac.logic.service.ContrasenaTemporalService;
import cr.acusab.una.sigac.logic.service.CuentaUsuarioService;
import cr.acusab.una.sigac.logic.service.EstudianteService;
import cr.acusab.una.sigac.logic.service.InscripcionPlanEstudioService;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Reglas de registro y edición de estudiantes: encargados, edad permitida
 * y sincronización de la cuenta de usuario.
 */
class EstudianteServiceTest {

    private static final String CEDULA_ESTUDIANTE = "118760542";

    private EstudianteRepository estudianteRepository;
    private TipoIdentificacionRepository tipoIdentificacionRepository;
    private UsuarioRepository usuarioRepository;
    private EstudianteEncargadoRepository estudianteEncargadoRepository;
    private InstrumentoRepository instrumentoRepository;
    private EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private DistritoRepository distritoRepository;
    private InscripcionPlanEstudioService inscripcionPlanService;

    private EstudianteService service;

    @BeforeEach
    void setUp() {
        estudianteRepository = mock(EstudianteRepository.class);
        tipoIdentificacionRepository = mock(TipoIdentificacionRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        estudianteEncargadoRepository = mock(EstudianteEncargadoRepository.class);
        instrumentoRepository = mock(InstrumentoRepository.class);
        estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);
        distritoRepository = mock(DistritoRepository.class);
        inscripcionPlanService = mock(InscripcionPlanEstudioService.class);

        // Servicio de cuenta real: así se prueba la sincronización completa
        CuentaUsuarioService cuentaUsuarioService =
                new CuentaUsuarioService(usuarioRepository, mock(EstadoUsuarioRepository.class));

        service = new EstudianteService(
                estudianteRepository,
                mock(EncargadoRepository.class),
                estudianteEncargadoRepository,
                instrumentoRepository,
                estudianteInstrumentoRepository,
                mock(ProfesorRepository.class),
                mock(DocumentoEstudianteRepository.class),
                tipoIdentificacionRepository,
                mock(TipoDocumentoRepository.class),
                mock(CentroEducativoRepository.class),
                usuarioRepository,
                mock(RoleRepository.class),
                mock(EstadoUsuarioRepository.class),
                mock(ContrasenaTemporalService.class),
                mock(BecaService.class),
                inscripcionPlanService,
                distritoRepository,
                cuentaUsuarioService
        );

        TiposIdentificacion cedula = new TiposIdentificacion();
        cedula.setId(1);
        cedula.setCodigo("CEDULA");
        cedula.setNombre("Cédula nacional");
        cedula.setActivo(true);
        when(tipoIdentificacionRepository.findByIdAndActivoTrue(1)).thenReturn(Optional.of(cedula));
    }

    // ---------- Registro: encargados ----------

    @Test
    void crear_rechazaEncargadoConLaMismaIdentificacionQueElEstudiante() {
        var request = registroMenor(List.of(encargado(CEDULA_ESTUDIANTE, true)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.crear(request, poliza()));

        assertEquals("El encargado no puede tener la misma identificación que el estudiante.", ex.getMessage());
    }

    @Test
    void crear_rechazaDosEncargadosConLaMismaIdentificacion() {
        var request = registroMenor(List.of(
                encargado("204440555", true),
                encargado("204440555", false)
        ));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.crear(request, poliza()));

        assertEquals("Los dos encargados no pueden tener la misma identificación.", ex.getMessage());
    }

    // ---------- Registro: edad ----------

    @Test
    void crear_rechazaEstudianteDeUnAnio() {
        var request = registro(LocalDate.now().minusYears(1), List.of(encargado("204440555", true)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.crear(request, poliza()));

        assertEquals("El estudiante debe tener al menos 2 años.", ex.getMessage());
    }

    // ---------- Edición: cuenta de usuario ----------

    @Test
    void actualizar_sincronizaCedulaYCorreoDeLaCuentaDeUsuario() {
        Usuario cuenta = new Usuario();
        cuenta.setId(30L);
        cuenta.setCedula(CEDULA_ESTUDIANTE);
        cuenta.setCorreo("viejo@correo.com");
        prepararEdicion(cuenta);

        service.actualizar(5L, edicion("118760543", "nuevo@correo.com"));

        assertEquals("118760543", cuenta.getCedula());
        assertEquals("nuevo@correo.com", cuenta.getCorreo());
        verify(usuarioRepository).save(cuenta);
    }

    @Test
    void actualizar_rechazaCorreoQueYaUsaOtraCuenta() {
        Usuario cuenta = new Usuario();
        cuenta.setId(30L);
        cuenta.setCedula(CEDULA_ESTUDIANTE);
        cuenta.setCorreo("viejo@correo.com");
        prepararEdicion(cuenta);

        Usuario otra = new Usuario();
        otra.setId(99L);
        when(usuarioRepository.findByCorreo("ocupado@correo.com")).thenReturn(Optional.of(otra));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> service.actualizar(5L, edicion(CEDULA_ESTUDIANTE, "ocupado@correo.com")));

        assertEquals("El correo ya está registrado en otra cuenta.", ex.getMessage());
        verify(estudianteRepository, never()).save(any());
    }

    @Test
    void actualizar_rechazaMenorSinEncargadosRegistrados() {
        Usuario cuenta = new Usuario();
        cuenta.setId(30L);
        prepararEdicion(cuenta);
        when(estudianteEncargadoRepository.countByIdEstudiante(5L)).thenReturn(0L);

        var request = new EstudianteUpdateRequest(1, CEDULA_ESTUDIANTE, "Ana", "Mora", null,
                LocalDate.now().minusYears(10), "FEMENINO", 10101, "Barrio Centro", null,
                "ana@correo.com", "Primaria", "Escuela Central", null, 3, List.of(1L));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.actualizar(5L, request));

        assertEquals("Un menor de edad debe tener al menos un encargado registrado.", ex.getMessage());
    }

    // ---------- Datos de prueba ----------

    private EstudianteCreateRequest registroMenor(List<EstudianteCreateRequest.EncargadoRequest> encargados) {
        return registro(LocalDate.now().minusYears(10), encargados);
    }

    private EstudianteCreateRequest registro(LocalDate nacimiento, List<EstudianteCreateRequest.EncargadoRequest> encargados) {
        return new EstudianteCreateRequest(1, CEDULA_ESTUDIANTE, "Ana", "Mora", null, nacimiento,
                "FEMENINO", 10101, "Barrio Centro", null, "ana@correo.com", "Primaria", "Escuela Central",
                null, 3, null, List.of(1L), encargados, null);
    }

    private EstudianteCreateRequest.EncargadoRequest encargado(String cedula, boolean principal) {
        return new EstudianteCreateRequest.EncargadoRequest(1, cedula, "Laura", "Mora", null,
                "88884321", null, null, null, "MADRE", principal, false);
    }

    private MockMultipartFile poliza() {
        return new MockMultipartFile("politica", "poliza.pdf", "application/pdf", "%PDF-1.4 poliza".getBytes());
    }

    private EstudianteUpdateRequest edicion(String cedula, String correo) {
        // Mayor de edad: no exige encargados ni centro educativo
        return new EstudianteUpdateRequest(1, cedula, "Ana", "Mora", null, LocalDate.now().minusYears(25),
                "FEMENINO", 10101, "Barrio Centro", null, correo, "Universitaria", null, null, 3, List.of(1L));
    }

    private void prepararEdicion(Usuario cuenta) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5L);
        estudiante.setUsuario(cuenta);
        when(estudianteRepository.findById(5L)).thenReturn(Optional.of(estudiante));
        when(estudianteRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(any(), any()))
                .thenReturn(Optional.empty());

        Instrumento instrumento = new Instrumento();
        instrumento.setId(3);
        when(instrumentoRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(instrumento));

        Provincia provincia = new Provincia();
        provincia.setId(1);
        Canton canton = new Canton();
        canton.setCodigo(101);
        canton.setProvincia(provincia);
        Distrito distrito = new Distrito();
        distrito.setCodigo(10101);
        distrito.setCanton(canton);
        when(distritoRepository.findById(10101)).thenReturn(Optional.of(distrito));

        when(usuarioRepository.findByCedula(any())).thenReturn(Optional.empty());
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());
        when(estudianteInstrumentoRepository.findAll()).thenReturn(List.of());
        when(inscripcionPlanService.sincronizar(anyLong(), any())).thenReturn(List.of(1L));
    }
}
