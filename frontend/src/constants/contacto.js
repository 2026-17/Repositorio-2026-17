// Medios de contacto de ACUSAB (se muestran en la pantalla de inicio).
// Si cambian, actualizar también el pie de los correos en EmailService.java.
export const CONTACTO_ACUSAB = {
    correo: "acusab@gmail.com",
    telefono: "+506 2269-3322",
    whatsapp: "+506 8708-5959",
};

// Solo dígitos, con código de país: formato que piden tel: y wa.me
export function soloDigitos(numero) {
    return numero.replace(/\D/g, "");
}
