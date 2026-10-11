package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.CodigoVerificacion;
import cr.acusab.una.sigac.data.entity.CodigoVerificacion.Proposito;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CodigoVerificacionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Genera y valida los códigos de 6 dígitos que se envían por correo para
 * recuperar o cambiar la contraseña. Solo guarda el hash del código.
 */
@Service
public class CodigoVerificacionService {

    public static final int MINUTOS_VIGENCIA = 15;
    private static final int INTENTOS_MAXIMOS = 5;
    private static final int MAXIMO_POR_HORA = 5;
    private static final Duration ESPERA_ENTRE_ENVIOS = Duration.ofMinutes(1);

    public static final String CODIGO_INVALIDO = "El código no es válido o ya venció. Solicite uno nuevo.";

    private final CodigoVerificacionRepository codigoVerificacionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public CodigoVerificacionService(
            CodigoVerificacionRepository codigoVerificacionRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.codigoVerificacionRepository = codigoVerificacionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Crea un código nuevo para el usuario y devuelve el código en texto plano
     * (solo para enviarlo por correo). Al crearlo, los códigos anteriores del
     * mismo propósito dejan de servir, porque solo se valida el más reciente.
     */
    @Transactional
    public String generar(Usuario usuario, Proposito proposito) {
        Instant ahora = Instant.now();

        long ultimoMinuto = codigoVerificacionRepository.countByIdUsuarioAndPropositoAndCreadoEnAfter(
                usuario, proposito, ahora.minus(ESPERA_ENTRE_ENVIOS));
        long ultimaHora = codigoVerificacionRepository.countByIdUsuarioAndPropositoAndCreadoEnAfter(
                usuario, proposito, ahora.minus(Duration.ofHours(1)));
        if (ultimoMinuto > 0 || ultimaHora >= MAXIMO_POR_HORA) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Se han solicitado demasiados códigos. Espere un momento e intente de nuevo.");
        }

        String codigo = String.format("%06d", random.nextInt(1_000_000));

        CodigoVerificacion nuevo = new CodigoVerificacion();
        nuevo.setIdUsuario(usuario);
        nuevo.setProposito(proposito);
        nuevo.setHashCodigo(passwordEncoder.encode(codigo));
        nuevo.setIntentos((short) 0);
        nuevo.setExpiraEn(ahora.plus(Duration.ofMinutes(MINUTOS_VIGENCIA)));
        nuevo.setCreadoEn(ahora);
        codigoVerificacionRepository.save(nuevo);

        return codigo;
    }

    /**
     * Comprueba el código y, si es correcto, lo marca como usado.
     * Corre en su propia transacción para que el intento fallido quede guardado
     * aunque luego se lance la excepción (y aunque quien llama haga rollback).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ResponseStatusException.class)
    public void validar(Usuario usuario, Proposito proposito, String codigo) {
        CodigoVerificacion actual = codigoVerificacionRepository
                .findFirstByIdUsuarioAndPropositoOrderByIdDesc(usuario, proposito)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, CODIGO_INVALIDO));

        Instant ahora = Instant.now();
        boolean vencido = actual.getUsadoEn() != null
                || actual.getExpiraEn().isBefore(ahora)
                || actual.getIntentos() >= INTENTOS_MAXIMOS;
        if (vencido) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CODIGO_INVALIDO);
        }

        if (codigo == null || !passwordEncoder.matches(codigo, actual.getHashCodigo())) {
            actual.setIntentos((short) (actual.getIntentos() + 1));
            codigoVerificacionRepository.save(actual);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CODIGO_INVALIDO);
        }

        actual.setUsadoEn(ahora);
        codigoVerificacionRepository.save(actual);
    }
}
