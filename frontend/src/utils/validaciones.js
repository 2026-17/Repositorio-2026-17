/*
 * Reglas de formato compartidas por los formularios. El backend aplica las
 * mismas reglas (PoliticaDatosPersona y ReglasIdentificacion), así que lo que
 * se avisa aquí antes de enviar coincide con lo que el servidor rechazaría.
 */

// Sin puntos al inicio, al final ni seguidos; dominio terminado en 2+ letras.
const CORREO = /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}$/;

export function esCorreoValido(texto) {
    return CORREO.test((texto || "").trim());
}

// --- Teléfonos: exactamente 8 dígitos ---
export const MENSAJE_TELEFONO = "El teléfono debe tener 8 dígitos (ej. 8888-4321).";

// Quita espacios y guiones: "8888-4321" -> "88884321"
export function limpiarTelefono(texto) {
    return (texto || "").replace(/[\s-]/g, "");
}

export function esTelefonoValido(texto) {
    return /^\d{8}$/.test(limpiarTelefono(texto));
}

// --- Nombres y apellidos: letras (con tildes y ñ), espacios, apóstrofo y guion ---
export const MENSAJE_NOMBRE = "Solo se permiten letras.";

export function esNombreValido(texto) {
    return /^\p{L}[\p{L}\p{M}' -]*$/u.test((texto || "").trim());
}

// --- Identificación según el tipo ---

// Quita espacios y guiones: "1-1111-1111" -> "111111111"
export function limpiarIdentificacion(texto) {
    return (texto || "").replace(/[\s-]/g, "");
}

function sinTildes(texto) {
    return (texto || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toUpperCase();
}

// Reconoce el tipo por su código o su nombre. Devuelve null si no es uno de
// los tres con regla de formato.
export function formatoIdentificacion(tipo) {
    if (!tipo) return null;
    const texto = sinTildes(`${tipo.codigo || ""} ${tipo.nombre || ""}`);
    if (texto.includes("DIMEX")) return "DIMEX";
    if (texto.includes("PASAPORTE")) return "PASAPORTE";
    if (texto.includes("CEDULA") && !texto.includes("JURIDICA") && !texto.includes("RESIDENCIA")) return "CEDULA";
    return null;
}

/*
 * Mensaje de error de la identificación, o "" si es válida.
 * tipo: objeto del catálogo ({ codigo, nombre }); sujeto: p. ej. "del encargado".
 */
export function errorIdentificacion(tipo, numero, sujeto = "") {
    const valor = limpiarIdentificacion(numero);
    const de = sujeto ? ` ${sujeto}` : "";
    switch (formatoIdentificacion(tipo)) {
        case "CEDULA":
            return /^\d{9}$/.test(valor) ? "" : `La cédula nacional${de} debe tener 9 dígitos.`;
        case "DIMEX":
            return /^[A-Za-z0-9]{11,12}$/.test(valor) ? "" : `El DIMEX${de} debe tener entre 11 y 12 letras o números.`;
        case "PASAPORTE":
            return /^[A-Za-z0-9]{6,20}$/.test(valor) ? "" : `El pasaporte${de} debe tener entre 6 y 20 letras o números.`;
        default:
            return "";
    }
}

// --- Edad del estudiante: de 2 a 100 años ---
export const EDAD_MINIMA = 2;
export const EDAD_MAXIMA = 100;

function fechaISO(fecha) {
    return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, "0")}-${String(fecha.getDate()).padStart(2, "0")}`;
}

// Límites para el input de fecha de nacimiento (min = hace 100 años, max = hace 2 años)
export function limitesFechaNacimiento() {
    const hoy = new Date();
    const max = new Date(hoy.getFullYear() - EDAD_MINIMA, hoy.getMonth(), hoy.getDate());
    const min = new Date(hoy.getFullYear() - EDAD_MAXIMA - 1, hoy.getMonth(), hoy.getDate() + 1);
    return { min: fechaISO(min), max: fechaISO(max) };
}

// Mensaje de error para una edad calculada, o "" si está en el rango
export function errorEdad(edad) {
    if (edad === null || edad === undefined) return "";
    if (edad < EDAD_MINIMA) return "El estudiante debe tener al menos 2 años.";
    if (edad > EDAD_MAXIMA) return "Revise la fecha de nacimiento.";
    return "";
}

// Fecha de hoy en formato yyyy-mm-dd (para max de inputs de fecha)
export function hoyISO() {
    return fechaISO(new Date());
}
