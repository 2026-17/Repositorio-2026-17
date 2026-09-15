package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.ContactoEmergencia;
import cr.acusab.una.sigac.data.entity.Encargado;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantesEncargado;
import cr.acusab.una.sigac.data.entity.EstudiantesEncargadoId;
import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.data.repository.ContactoEmergenciaRepository;
import cr.acusab.una.sigac.data.repository.EncargadoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteEncargadoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import cr.acusab.una.sigac.presentation.dto.request.ContactoEmergenciaRequest;
import cr.acusab.una.sigac.presentation.dto.request.ContactoEmergenciaUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EncargadoCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EncargadoUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.ContactoEmergenciaResponse;
import cr.acusab.una.sigac.presentation.dto.response.EncargadoResponse;
import cr.acusab.una.sigac.presentation.dto.response.TipoIdentificacionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EncargadoService {

    private static final int EDAD_MAYORIA = 18;
    private static final int MAX_CONTACTOS_EMERGENCIA = 2;

    private static final Set<String> PARENTESCOS_VALIDOS =
            Set.of("MADRE", "PADRE", "TUTOR", "FAMILIAR", "OTRO");

    private final EstudianteRepository estudianteRepository;
    private final EncargadoRepository encargadoRepository;
    private final EstudianteEncargadoRepository estudianteEncargadoRepository;
    private final ContactoEmergenciaRepository contactoEmergenciaRepository;
    private final TipoIdentificacionRepository tipoIdentificacionRepository;

    public EncargadoService(
            EstudianteRepository estudianteRepository,
            EncargadoRepository encargadoRepository,
            EstudianteEncargadoRepository estudianteEncargadoRepository,
            ContactoEmergenciaRepository contactoEmergenciaRepository,
            TipoIdentificacionRepository tipoIdentificacionRepository
    ) {
        this.estudianteRepository = estudianteRepository;
        this.encargadoRepository = encargadoRepository;
        this.estudianteEncargadoRepository = estudianteEncargadoRepository;
        this.contactoEmergenciaRepository = contactoEmergenciaRepository;
        this.tipoIdentificacionRepository = tipoIdentificacionRepository;
    }

    @Transactional
    public EncargadoResponse registrar(EncargadoCreateRequest request) {

        Estudiante estudiante = buscarEstudianteActivo(request.getIdEstudiante());
        validarEstudianteRequiereEncargado(estudiante);

        TiposIdentificacion tipo = buscarTipoIdentificacion(
                request.getIdTipoIdentificacion()
        );

        String numeroIdentificacion = limpiarTexto(
                request.getNumeroIdentificacion()
        );

        validarIdentificacion(tipo, numeroIdentificacion);

        String parentesco = validarYNormalizarParentesco(
                request.getParentesco()
        );

        List<ContactoEmergenciaRequest> contactos =
                request.getContactosEmergencia() != null
                        ? request.getContactosEmergencia()
                        : List.of();

        validarCantidadContactosNuevaAsociacion(
                estudiante.getId(),
                contactos.size()
        );

        Encargado encargado = encargadoRepository
                .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        tipo.getId(),
                        numeroIdentificacion
                )
                .orElse(null);

        Instant now = Instant.now();

        if (encargado == null) {
            encargado = new Encargado();
            encargado.setCreadoEn(now);
        } else {
            if (estudianteEncargadoRepository
                    .existsByIdEstudianteAndIdEncargado(
                            estudiante.getId(),
                            encargado.getId()
                    )) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El encargado ya se encuentra asociado a este estudiante."
                );
            }
        }

        completarDatosEncargado(
                encargado,
                tipo,
                numeroIdentificacion,
                request.getNombres(),
                request.getPrimerApellido(),
                request.getSegundoApellido(),
                request.getTelefonoPrincipal(),
                request.getTelefonoSecundario(),
                request.getCorreo(),
                request.getOcupacion(),
                request.getEstadoCivil(),
                request.getDireccion()
        );

        encargado.setActivo(true);
        encargado.setActualizadoEn(now);

        encargado = encargadoRepository.save(encargado);

        EstudiantesEncargadoId relacionId = new EstudiantesEncargadoId();
        relacionId.setIdEstudiante(estudiante.getId());
        relacionId.setIdEncargado(encargado.getId());

        EstudiantesEncargado relacion = new EstudiantesEncargado();
        relacion.setId(relacionId);
        relacion.setEstudiante(estudiante);
        relacion.setEncargado(encargado);
        relacion.setParentesco(parentesco);
        relacion.setEsResponsablePrincipal(
                request.getEsResponsablePrincipal()
        );
        relacion.setEsContactoEmergencia(false);
        relacion.setAutorizadoRetiro(
                request.getAutorizadoRetiro()
        );
        relacion.setObservaciones(
                limpiarTextoOpcional(request.getObservaciones())
        );
        relacion.setCreadoEn(now);

        estudianteEncargadoRepository.save(relacion);

        for (ContactoEmergenciaRequest contactoRequest : contactos) {
            crearContacto(
                    estudiante,
                    encargado,
                    contactoRequest,
                    now
            );
        }

        return toResponse(
                estudiante.getId(),
                encargado,
                relacion
        );
    }

    @Transactional(readOnly = true)
    public List<EncargadoResponse> listarPorEstudiante(Long idEstudiante) {

        Estudiante estudiante = buscarEstudianteActivo(idEstudiante);

        return estudianteEncargadoRepository
                .findByIdEstudiante(estudiante.getId())
                .stream()
                .map(relacion -> {
                    Encargado encargado = relacion.getEncargado();

                    if (encargado == null) {
                        throw new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Encargado no encontrado."
                        );
                    }

                    return toResponse(
                            estudiante.getId(),
                            encargado,
                            relacion
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public EncargadoResponse obtener(
            Long idEstudiante,
            Long idEncargado
    ) {

        buscarEstudianteActivo(idEstudiante);

        EstudiantesEncargado relacion =
                buscarRelacion(idEstudiante, idEncargado);

        Encargado encargado = encargadoRepository
                .findByIdAndActivoTrue(idEncargado)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Encargado no encontrado o inactivo."
                ));

        return toResponse(
                idEstudiante,
                encargado,
                relacion
        );
    }

    @Transactional
    public EncargadoResponse modificar(
            Long idEstudiante,
            Long idEncargado,
            EncargadoUpdateRequest request
    ) {

        Estudiante estudiante =
                buscarEstudianteActivo(idEstudiante);

        validarEstudianteRequiereEncargado(estudiante);

        EstudiantesEncargado relacion =
                buscarRelacion(idEstudiante, idEncargado);

        Encargado encargado = encargadoRepository
                .findByIdAndActivoTrue(idEncargado)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Encargado no encontrado o inactivo."
                ));

        TiposIdentificacion tipo =
                buscarTipoIdentificacion(
                        request.getIdTipoIdentificacion()
                );

        String numeroIdentificacion =
                limpiarTexto(request.getNumeroIdentificacion());

        validarIdentificacion(tipo, numeroIdentificacion);

        boolean cambioIdentificacion =
                !tipo.getId().equals(
                        encargado.getIdTipoIdentificacion().getId()
                )
                        || !numeroIdentificacion.equals(
                        encargado.getNumeroIdentificacion()
                );

        if (cambioIdentificacion) {
            encargadoRepository
                    .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                            tipo.getId(),
                            numeroIdentificacion
                    )
                    .filter(otro ->
                            !otro.getId().equals(encargado.getId())
                    )
                    .ifPresent(otro -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Ya existe otro encargado con esa identificación."
                        );
                    });
        }

        String parentesco =
                validarYNormalizarParentesco(
                        request.getParentesco()
                );

        List<ContactoEmergenciaUpdateRequest> contactosSolicitados =
                request.getContactosEmergencia() != null
                        ? request.getContactosEmergencia()
                        : List.of();

        validarCantidadContactosActualizacion(
                idEstudiante,
                idEncargado,
                contactosSolicitados.size()
        );

        completarDatosEncargado(
                encargado,
                tipo,
                numeroIdentificacion,
                request.getNombres(),
                request.getPrimerApellido(),
                request.getSegundoApellido(),
                request.getTelefonoPrincipal(),
                request.getTelefonoSecundario(),
                request.getCorreo(),
                request.getOcupacion(),
                request.getEstadoCivil(),
                request.getDireccion()
        );

        encargado.setActualizadoEn(Instant.now());

        relacion.setParentesco(parentesco);
        relacion.setEsResponsablePrincipal(
                request.getEsResponsablePrincipal()
        );
        relacion.setAutorizadoRetiro(
                request.getAutorizadoRetiro()
        );
        relacion.setObservaciones(
                limpiarTextoOpcional(request.getObservaciones())
        );

        encargadoRepository.save(encargado);
        estudianteEncargadoRepository.save(relacion);

        actualizarContactos(
                estudiante,
                encargado,
                contactosSolicitados
        );

        return toResponse(
                idEstudiante,
                encargado,
                relacion
        );
    }

    private Estudiante buscarEstudianteActivo(Long idEstudiante) {

        if (idEstudiante == null || idEstudiante <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El identificador del estudiante no es válido."
            );
        }

        return estudianteRepository
                .findByIdAndActivoTrue(idEstudiante)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Estudiante no encontrado o inactivo."
                ));
    }

    private void validarEstudianteRequiereEncargado(
            Estudiante estudiante
    ) {

        LocalDate fechaNacimiento =
                estudiante.getFechaNacimiento();

        if (fechaNacimiento == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El estudiante no tiene una fecha de nacimiento registrada."
            );
        }

        if (fechaNacimiento.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha de nacimiento del estudiante no es válida."
            );
        }

        int edad = Period.between(
                fechaNacimiento,
                LocalDate.now()
        ).getYears();

        if (edad >= EDAD_MAYORIA) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo se puede registrar un encargado para un estudiante menor de edad."
            );
        }
    }

    private TiposIdentificacion buscarTipoIdentificacion(
            Integer idTipoIdentificacion
    ) {

        TiposIdentificacion tipo =
                tipoIdentificacionRepository
                        .findById(idTipoIdentificacion)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Tipo de identificación no existe."
                                )
                        );

        if (!Boolean.TRUE.equals(tipo.getActivo())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El tipo de identificación seleccionado se encuentra inactivo."
            );
        }

        return tipo;
    }

    private void validarIdentificacion(
            TiposIdentificacion tipo,
            String numeroIdentificacion
    ) {

        if (numeroIdentificacion == null
                || numeroIdentificacion.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El número de identificación es obligatorio."
            );
        }

        if ("CEDULA".equalsIgnoreCase(tipo.getCodigo())) {
            String cedulaNormalizada =
                    numeroIdentificacion
                            .replace("-", "")
                            .replace(" ", "");

            if (!cedulaNormalizada.matches("\\d{9}")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La cédula debe contener 9 dígitos."
                );
            }
        }
    }

    private String validarYNormalizarParentesco(
            String parentesco
    ) {

        String normalizado =
                limpiarTexto(parentesco).toUpperCase();

        if (!PARENTESCOS_VALIDOS.contains(normalizado)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Parentesco inválido. Valores permitidos: MADRE, PADRE, TUTOR, FAMILIAR u OTRO."
            );
        }

        return normalizado;
    }

    private void validarCantidadContactosNuevaAsociacion(
            Long idEstudiante,
            int cantidadSolicitada
    ) {

        long existentes =
                contactoEmergenciaRepository
                        .countByEstudiante_IdAndActivoTrue(
                                idEstudiante
                        );

        if (existentes + cantidadSolicitada
                > MAX_CONTACTOS_EMERGENCIA) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El estudiante puede tener como máximo dos contactos de emergencia."
            );
        }
    }

    private void validarCantidadContactosActualizacion(
            Long idEstudiante,
            Long idEncargado,
            int cantidadSolicitada
    ) {

        long totalActual =
                contactoEmergenciaRepository
                        .countByEstudiante_IdAndActivoTrue(
                                idEstudiante
                        );

        long actualesDelEncargado =
                contactoEmergenciaRepository
                        .findByEstudiante_IdAndEncargado_IdAndActivoTrue(
                                idEstudiante,
                                idEncargado
                        )
                        .size();

        long contactosDeOtros =
                totalActual - actualesDelEncargado;

        if (contactosDeOtros + cantidadSolicitada
                > MAX_CONTACTOS_EMERGENCIA) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El estudiante puede tener como máximo dos contactos de emergencia."
            );
        }
    }

    private EstudiantesEncargado buscarRelacion(
            Long idEstudiante,
            Long idEncargado
    ) {

        return estudianteEncargadoRepository
                .findByIdEstudianteAndIdEncargado(
                        idEstudiante,
                        idEncargado
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "El encargado no está asociado a este estudiante."
                        )
                );
    }

    private void completarDatosEncargado(
            Encargado encargado,
            TiposIdentificacion tipo,
            String numeroIdentificacion,
            String nombres,
            String primerApellido,
            String segundoApellido,
            String telefonoPrincipal,
            String telefonoSecundario,
            String correo,
            String ocupacion,
            String estadoCivil,
            String direccion
    ) {

        encargado.setIdTipoIdentificacion(tipo);
        encargado.setNumeroIdentificacion(
                limpiarTexto(numeroIdentificacion)
        );
        encargado.setNombres(limpiarTexto(nombres));
        encargado.setPrimerApellido(
                limpiarTexto(primerApellido)
        );
        encargado.setSegundoApellido(
                limpiarTextoOpcional(segundoApellido)
        );
        encargado.setTelefonoPrincipal(
                limpiarTexto(telefonoPrincipal)
        );
        encargado.setTelefonoSecundario(
                limpiarTextoOpcional(telefonoSecundario)
        );
        encargado.setCorreo(
                limpiarTextoOpcional(correo)
        );
        encargado.setOcupacion(
                limpiarTextoOpcional(ocupacion)
        );
        encargado.setEstadoCivil(
                limpiarTextoOpcional(estadoCivil)
        );
        encargado.setDireccion(
                limpiarTextoOpcional(direccion)
        );
    }

    private void crearContacto(
            Estudiante estudiante,
            Encargado encargado,
            ContactoEmergenciaRequest request,
            Instant now
    ) {

        ContactoEmergencia contacto =
                new ContactoEmergencia();

        contacto.setEstudiante(estudiante);
        contacto.setEncargado(encargado);
        contacto.setNombre(
                limpiarTexto(request.getNombre())
        );
        contacto.setTelefono(
                limpiarTexto(request.getTelefono())
        );
        contacto.setRelacion(
                limpiarTexto(request.getRelacion())
        );
        contacto.setActivo(true);
        contacto.setCreadoEn(now);
        contacto.setActualizadoEn(now);

        contactoEmergenciaRepository.save(contacto);
    }

    private void actualizarContactos(
            Estudiante estudiante,
            Encargado encargado,
            List<ContactoEmergenciaUpdateRequest> solicitados
    ) {

        List<ContactoEmergencia> actuales =
                contactoEmergenciaRepository
                        .findByEstudiante_IdAndEncargado_IdAndActivoTrue(
                                estudiante.getId(),
                                encargado.getId()
                        );

        Map<Long, ContactoEmergencia> actualesPorId =
                new HashMap<>();

        for (ContactoEmergencia contacto : actuales) {
            actualesPorId.put(contacto.getId(), contacto);
        }

        Instant now = Instant.now();

        for (ContactoEmergenciaUpdateRequest request : solicitados) {

            ContactoEmergencia contacto;

            if (request.getIdContactoEmergencia() == null) {
                contacto = new ContactoEmergencia();
                contacto.setEstudiante(estudiante);
                contacto.setEncargado(encargado);
                contacto.setActivo(true);
                contacto.setCreadoEn(now);
            } else {
                contacto = actualesPorId.remove(
                        request.getIdContactoEmergencia()
                );

                if (contacto == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "El contacto de emergencia indicado no pertenece a este estudiante y encargado."
                    );
                }
            }

            contacto.setNombre(
                    limpiarTexto(request.getNombre())
            );
            contacto.setTelefono(
                    limpiarTexto(request.getTelefono())
            );
            contacto.setRelacion(
                    limpiarTexto(request.getRelacion())
            );
            contacto.setActivo(true);
            contacto.setActualizadoEn(now);

            contactoEmergenciaRepository.save(contacto);
        }

        for (ContactoEmergencia contactoOmitido :
                actualesPorId.values()) {

            contactoOmitido.setActivo(false);
            contactoOmitido.setActualizadoEn(now);

            contactoEmergenciaRepository.save(
                    contactoOmitido
            );
        }
    }

    private EncargadoResponse toResponse(
            Long idEstudiante,
            Encargado encargado,
            EstudiantesEncargado relacion
    ) {

        TiposIdentificacion tipo =
                encargado.getIdTipoIdentificacion();

        List<ContactoEmergenciaResponse> contactos =
                contactoEmergenciaRepository
                        .findByEstudiante_IdAndEncargado_IdAndActivoTrue(
                                idEstudiante,
                                encargado.getId()
                        )
                        .stream()
                        .map(this::toContactoResponse)
                        .toList();

        return new EncargadoResponse(
                encargado.getId(),
                idEstudiante,
                new TipoIdentificacionResponse(
                        tipo.getId(),
                        tipo.getCodigo(),
                        tipo.getNombre()
                ),
                encargado.getNumeroIdentificacion(),
                encargado.getNombres(),
                encargado.getPrimerApellido(),
                encargado.getSegundoApellido(),
                encargado.getTelefonoPrincipal(),
                encargado.getTelefonoSecundario(),
                encargado.getCorreo(),
                encargado.getOcupacion(),
                encargado.getEstadoCivil(),
                encargado.getDireccion(),
                relacion.getParentesco(),
                relacion.getEsResponsablePrincipal(),
                relacion.getAutorizadoRetiro(),
                relacion.getObservaciones(),
                encargado.getActivo(),
                encargado.getCreadoEn(),
                encargado.getActualizadoEn(),
                contactos
        );
    }

    private ContactoEmergenciaResponse toContactoResponse(
            ContactoEmergencia contacto
    ) {

        return new ContactoEmergenciaResponse(
                contacto.getId(),
                contacto.getNombre(),
                contacto.getTelefono(),
                contacto.getRelacion(),
                contacto.getActivo(),
                contacto.getCreadoEn(),
                contacto.getActualizadoEn()
        );
    }

    private String limpiarTexto(String valor) {

        if (valor == null) {
            return "";
        }

        return valor.trim();
    }

    private String limpiarTextoOpcional(String valor) {

        if (valor == null) {
            return null;
        }

        String limpio = valor.trim();

        return limpio.isEmpty() ? null : limpio;
    }
}