package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contraseña temporal de las cuentas nuevas (ver 2.3.1 Controles sobre la
 * composición y asignación del password). Nadie la escribe ni la ve en el
 * sistema: se genera al azar, se guarda solo su hash y se envía al correo de
 * la cuenta. En el primer inicio de sesión el usuario debe cambiarla.
 */
@Service
public class ContrasenaTemporalService {

    private static final int LONGITUD = 12;

    // Sin caracteres que se confunden al leerlos en el correo (0/O, 1/l/I)
    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnpqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final String ESPECIALES = "!#$%*+-=?@";
    private static final String TODOS = MAYUSCULAS + MINUSCULAS + NUMEROS + ESPECIALES;

    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public ContrasenaTemporalService(PasswordEncoder passwordEncoder, EmailService emailService) {
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Genera una contraseña temporal para la cuenta, la deja pendiente de
     * cambio y programa el correo con la contraseña. El usuario debe tener ya
     * cédula, nombre y correo.
     * <p>
     * Dentro de una transacción, el correo se envía justo antes del commit:
     * así solo sale si el registro completo fue válido, y si el envío falla
     * se revierte el registro (una cuenta cuya contraseña nadie conoce no
     * sirve de nada).
     */
    public void asignar(Usuario usuario) {
        String contrasena = generar();
        usuario.setHashContrasena(passwordEncoder.encode(contrasena));
        usuario.setRequiereCambioContrasena(true);

        String correo = usuario.getCorreo();
        String nombre = usuario.getNombre();
        String cedula = usuario.getCedula();
        Runnable enviar = () -> emailService.enviarContrasenaTemporal(correo, nombre, cedula, contrasena);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    enviar.run();
                }
            });
        } else {
            enviar.run();
        }
    }

    // Cumple PoliticaContrasena: al menos una mayúscula, un número y un carácter especial
    String generar() {
        List<Character> caracteres = new ArrayList<>(LONGITUD);
        caracteres.add(aleatorio(MAYUSCULAS));
        caracteres.add(aleatorio(MINUSCULAS));
        caracteres.add(aleatorio(NUMEROS));
        caracteres.add(aleatorio(ESPECIALES));
        while (caracteres.size() < LONGITUD) {
            caracteres.add(aleatorio(TODOS));
        }
        Collections.shuffle(caracteres, random);

        StringBuilder contrasena = new StringBuilder(LONGITUD);
        caracteres.forEach(contrasena::append);
        return contrasena.toString();
    }

    private char aleatorio(String alfabeto) {
        return alfabeto.charAt(random.nextInt(alfabeto.length()));
    }
}
