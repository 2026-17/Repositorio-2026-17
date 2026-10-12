package cr.acusab.una.sigac.logic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envía correos transaccionales con la API HTTP de Brevo (no SMTP: Railway
 * bloquea SMTP saliente en los planes Free/Trial/Hobby).
 * Si brevo.api-key está vacía, no envía nada y muestra el código (o la
 * contraseña temporal) en consola, para poder trabajar localmente sin cuenta
 * de Brevo.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String apiKey;
    private final String remitenteCorreo;
    private final String remitenteNombre;
    // Mismo logo que el favicon del frontend (frontend/public/favicon.png).
    // Los clientes de correo solo muestran imágenes con URL pública.
    private final String logoUrl;
    // Dirección pública del frontend, para los enlaces "Ingresar a SIGAC" de los correos.
    private final String sigacUrl;

    // Medios de contacto de ACUSAB del pie de los correos.
    // Si cambian, actualizar también frontend/src/constants/contacto.js.
    private static final String CONTACTO_CORREO = "acusab@gmail.com";
    private static final String CONTACTO_TELEFONO = "+506 2269-3322";
    private static final String CONTACTO_WHATSAPP = "+506 8708-5959";

    public EmailService(
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${brevo.sender-email}") String remitenteCorreo,
            @Value("${brevo.sender-name}") String remitenteNombre,
            @Value("${sigac.logo-url:https://sigac.acusab.ed.cr/favicon.png}") String logoUrl,
            @Value("${sigac.frontend-url:https://sigac.acusab.ed.cr}") String sigacUrl
    ) {
        this.logoUrl = logoUrl;
        // Sin "/" final, para poder concatenar rutas como "/login"
        this.sigacUrl = sigacUrl.replaceAll("/+$", "");
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder().requestFactory(fabrica).build();
        this.apiKey = apiKey;
        this.remitenteCorreo = remitenteCorreo;
        this.remitenteNombre = remitenteNombre;
    }

    public void enviarCodigoRecuperacion(String destinatario, String codigo, int minutosValidez) {
        enviarCodigo(destinatario, codigo, minutosValidez,
                "Código para recuperar su contraseña",
                "Recuperación de contraseña",
                "Recibimos una solicitud para recuperar la contraseña de su cuenta en SIGAC.",
                "Escriba este código en SIGAC, en la pantalla <strong>«Revise su correo»</strong> de "
                        + "<strong>Recuperar contraseña</strong> donde hizo la solicitud. Si cerró esa pantalla, "
                        + "ingrese a SIGAC, elija <strong>«¿Olvidó su contraseña?»</strong> y pida un código nuevo.",
                "/recuperar-contrasena",
                "Si usted no hizo esta solicitud, puede ignorar este correo: su contraseña no cambiará.");
    }

    public void enviarCodigoCambio(String destinatario, String codigo, int minutosValidez) {
        enviarCodigo(destinatario, codigo, minutosValidez,
                "Código para cambiar su contraseña",
                "Cambio de contraseña",
                "Recibimos una solicitud para cambiar la contraseña de su cuenta en SIGAC.",
                "Escriba este código en SIGAC, en la pantalla de <strong>cambio de contraseña</strong> "
                        + "donde hizo la solicitud, para confirmar el cambio. Si cerró esa pantalla, "
                        + "inicie sesión en SIGAC y vuelva a solicitar el cambio.",
                "/login",
                "Si usted no hizo esta solicitud, alguien podría conocer su contraseña actual. "
                        + "No comparta este código y comuníquese con la administración de ACUSAB.");
    }

    /**
     * Correo de bienvenida de una cuenta nueva con su contraseña temporal
     * (ver ContrasenaTemporalService).
     */
    public void enviarContrasenaTemporal(String destinatario, String nombre, String cedula, String contrasena) {
        if (apiKey.isBlank()) {
            log.warn("brevo.api-key vacía: no se envía el correo. Contraseña temporal para {} (cédula {}): {}",
                    destinatario, cedula, contrasena);
            return;
        }

        String contenido = """
                <h1 style="margin:0 0 16px; font-size:22px; color:#662483;">Bienvenido(a) a SIGAC</h1>
                <p style="margin:0 0 24px; font-size:15px; line-height:1.6;">Hola, %s. Se creó su cuenta en el Sistema de Gestión Académica de ACUSAB. Para ingresar use estos datos:</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 24px; background-color:#faf7fc; border:1px solid #e9dcf2; border-radius:10px;">
                  <tr>
                    <td style="padding:16px 20px 8px; font-size:13px; color:#5a5a63;">Cédula</td>
                    <td style="padding:16px 20px 8px; font-size:16px; font-weight:bold; text-align:right;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:8px 20px 16px; font-size:13px; color:#5a5a63;">Contraseña temporal</td>
                    <td style="padding:8px 20px 16px; font-family:'Courier New', Courier, monospace; font-size:18px; font-weight:bold; letter-spacing:2px; color:#662483; text-align:right;">%s</td>
                  </tr>
                </table>
                <p style="margin:0 0 24px; font-size:15px; line-height:1.6;">La primera vez que ingrese, el sistema le pedirá <strong>crear una contraseña nueva</strong>. La contraseña temporal deja de servir en cuanto la cambie.</p>
                %s
                %s
                """.formatted(
                HtmlUtils.htmlEscape(nombre), HtmlUtils.htmlEscape(cedula), HtmlUtils.htmlEscape(contrasena),
                enlaceSigac("/login", "Ingrese a SIGAC con el botón de abajo. En la pantalla <strong>Iniciar sesión</strong> "
                        + "escriba su cédula y la contraseña temporal."),
                avisoSeguridad("No comparta esta contraseña con nadie. Si no esperaba este correo, "
                        + "comuníquese con la administración de ACUSAB."));

        enviar(destinatario, "Su cuenta en SIGAC", contenido);
    }

    private void enviarCodigo(String destinatario, String codigo, int minutosValidez,
                              String asunto, String titulo, String introduccion,
                              String instruccion, String ruta, String avisoSeguridad) {
        if (apiKey.isBlank()) {
            log.warn("brevo.api-key vacía: no se envía el correo. Código para {}: {} (vence en {} min)",
                    destinatario, codigo, minutosValidez);
            return;
        }

        String contenido = """
                <h1 style="margin:0 0 16px; font-size:22px; color:#662483;">%s</h1>
                <p style="margin:0 0 24px; font-size:15px; line-height:1.6;">%s</p>
                <p style="margin:0 0 8px; font-size:13px; color:#5a5a63; text-align:center; text-transform:uppercase; letter-spacing:1px;">Su código de verificación</p>
                <table role="presentation" align="center" cellpadding="0" cellspacing="0" style="margin:0 auto 12px;">
                  <tr>
                    <td style="background-color:#faf7fc; border:2px dashed #662483; border-radius:10px; padding:16px 28px; font-family:'Courier New', Courier, monospace; font-size:32px; font-weight:bold; letter-spacing:8px; color:#662483; text-align:center;">%s</td>
                  </tr>
                </table>
                <p style="margin:0 0 24px; font-size:13px; color:#5a5a63; text-align:center;">Vence en <strong>%d minutos</strong> y solo puede usarse una vez.</p>
                %s
                %s
                """.formatted(titulo, introduccion, codigo, minutosValidez,
                enlaceSigac(ruta, instruccion),
                avisoSeguridad("<strong>¿No fue usted?</strong> " + avisoSeguridad));

        enviar(destinatario, asunto, contenido);
    }

    // Indica dónde realizar la acción en SIGAC y da el botón (y el enlace en texto) para entrar
    private String enlaceSigac(String ruta, String instruccion) {
        String url = HtmlUtils.htmlEscape(sigacUrl + ruta);
        return """
                <p style="margin:0 0 16px; font-size:15px; line-height:1.6;">%s</p>
                <table role="presentation" align="center" cellpadding="0" cellspacing="0" style="margin:0 auto 12px;">
                  <tr>
                    <td style="background-color:#662483; border-radius:8px;">
                      <a href="%s" target="_blank" style="display:inline-block; padding:12px 28px; font-size:15px; font-weight:bold; color:#ffffff; text-decoration:none;">Ingresar a SIGAC</a>
                    </td>
                  </tr>
                </table>
                <p style="margin:0 0 28px; font-size:12px; line-height:1.5; color:#5a5a63; text-align:center;">Si el botón no funciona, copie esta dirección en su navegador:<br><a href="%s" target="_blank" style="color:#662483; word-break:break-all;">%s</a></p>
                """.formatted(instruccion, url, url, url);
    }

    // Recuadro rojo de advertencia al final del correo
    private static String avisoSeguridad(String texto) {
        return """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                  <tr>
                    <td style="background-color:#fbeaec; border-left:4px solid #be1622; border-radius:6px; padding:14px 16px; font-size:13px; line-height:1.5; color:#5a1a20;">
                      %s
                    </td>
                  </tr>
                </table>
                """.formatted(texto);
    }

    private void enviar(String destinatario, String asunto, String contenido) {
        // Correo con tablas y estilos en línea: es lo que la mayoría de clientes
        // de correo (Gmail, Outlook) muestran de forma confiable.
        String html = """
                <!DOCTYPE html>
                <html lang="es">
                <body style="margin:0; padding:0; background-color:#f4f0f8;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f0f8; padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px; background-color:#ffffff; border-radius:12px; overflow:hidden; font-family:Arial, Helvetica, sans-serif; color:#2b2b33;">
                          <tr>
                            <td style="background-color:#662483; padding:24px 32px; text-align:center;">
                              <div style="display:inline-block; background-color:#ffffff; border-radius:12px; padding:6px;">
                                <img src="%s" width="56" height="50" alt="SIGAC" style="display:block; border:0; width:56px; height:50px;">
                              </div>
                              <div style="margin-top:8px; font-size:20px; font-weight:bold; color:#ffffff; letter-spacing:1px;">SIGAC</div>
                              <div style="margin-top:4px; font-size:13px; color:#e9dcf2;">ACUSAB · Santa Bárbara de Heredia</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="background-color:#fbfaf7; border-top:1px solid #ece9e2; padding:18px 32px; text-align:center; font-size:12px; line-height:1.5; color:#777777;">
                              Este correo se envía automáticamente, por favor no lo responda.<br>
                              ¿Dudas? Comuníquese con ACUSAB:<br>
                              Correo: <a href="mailto:%s" style="color:#662483;">%s</a><br>
                              Teléfono: %s · WhatsApp: %s<br>
                              <a href="%s" target="_blank" style="color:#662483;">%s</a> · Sistema de Gestión Académica (SIGAC)
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(logoUrl), contenido,
                CONTACTO_CORREO, CONTACTO_CORREO, CONTACTO_TELEFONO, CONTACTO_WHATSAPP,
                HtmlUtils.htmlEscape(sigacUrl), HtmlUtils.htmlEscape(sigacUrl.replaceFirst("^https?://", "")));

        Map<String, Object> cuerpo = Map.of(
                "sender", Map.of("name", remitenteNombre, "email", remitenteCorreo),
                "to", List.of(Map.of("email", destinatario)),
                "subject", asunto,
                "htmlContent", html
        );

        try {
            restClient.post()
                    .uri(BREVO_URL)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("No se pudo enviar el correo a {} con Brevo: {}", destinatario, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo enviar el correo. Intente de nuevo más tarde.");
        }
    }
}
