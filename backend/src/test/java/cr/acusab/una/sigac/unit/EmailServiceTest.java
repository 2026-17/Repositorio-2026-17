package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.logic.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/*
 * Cuerpo que se manda a Brevo: además del HTML lleva replyTo (el remitente
 * no recibe correo) y una versión en texto plano, que Microsoft
 * (Hotmail/Outlook) valora para no mandar el correo a Correo no deseado.
 */
class EmailServiceTest {

    private EmailService email;
    private MockRestServiceServer servidor;

    @BeforeEach
    void setUp() {
        email = new EmailService("clave-de-prueba", "noreply@example.com", "SIGAC Test",
                "https://example.com/logo.png", "https://sigac.example.com");
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(email, "restClient", builder.build());
    }

    @Test
    void codigoDeRecuperacion_llevaReplyToYTextoPlano() {
        esperarCorreoComun()
                .andExpect(jsonPath("$.subject").value("Código para recuperar su contraseña · Luis"))
                .andExpect(jsonPath("$.textContent", allOf(
                        containsString("Hola, Luis Prueba."),
                        containsString("Su código de verificación: 123456"),
                        containsString("Vence en 15 minutos"),
                        containsString("«Revise su correo»"),
                        containsString("https://sigac.example.com/recuperar-contrasena"),
                        containsString("(cédula *****0878)"))))
                .andRespond(withSuccess());

        email.enviarCodigoRecuperacion("luis@test.cr", "Luis Prueba", "*****0878", "123456", 15);

        servidor.verify();
    }

    @Test
    void codigoDeCambio_llevaReplyToYTextoPlano() {
        esperarCorreoComun()
                .andExpect(jsonPath("$.textContent", allOf(
                        containsString("Cambio de contraseña"),
                        containsString("Su código de verificación: 654321"),
                        containsString("Vence en 10 minutos"),
                        containsString("https://sigac.example.com/login"))))
                .andRespond(withSuccess());

        email.enviarCodigoCambio("luis@test.cr", "Luis Prueba", "*****0878", "654321", 10);

        servidor.verify();
    }

    @Test
    void contrasenaTemporal_llevaReplyToYTextoPlano() {
        esperarCorreoComun()
                .andExpect(jsonPath("$.subject").value("Su cuenta en SIGAC"))
                .andExpect(jsonPath("$.textContent", allOf(
                        containsString("Hola, Luis Prueba."),
                        containsString("Cédula: 100000878"),
                        containsString("Contraseña temporal: Abc-2345defg"),
                        containsString("https://sigac.example.com/login"))))
                .andRespond(withSuccess());

        email.enviarContrasenaTemporal("luis@test.cr", "Luis Prueba", "100000878", "Abc-2345defg");

        servidor.verify();
    }

    // Lo que todos los correos deben traer: remitente igual, replyTo, HTML y texto sin etiquetas con el pie
    private ResponseActions esperarCorreoComun() {
        return servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.sender.email").value("noreply@example.com"))
                .andExpect(jsonPath("$.sender.name").value("SIGAC Test"))
                .andExpect(jsonPath("$.replyTo.email").value("acusab@gmail.com"))
                .andExpect(jsonPath("$.replyTo.name").value("ACUSAB"))
                .andExpect(jsonPath("$.to[0].email").value("luis@test.cr"))
                .andExpect(jsonPath("$.htmlContent", containsString("<!DOCTYPE html>")))
                .andExpect(jsonPath("$.textContent", allOf(
                        not(containsString("<")),
                        containsString("Correo: acusab@gmail.com"),
                        containsString("WhatsApp: +506 8708-5959"))));
    }
}
