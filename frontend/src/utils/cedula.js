import { formatoIdentificacion, limpiarIdentificacion } from "./validaciones";

/*
 * Máscara de la cédula nacional: 1-1111-1111 (9 dígitos).
 * Al backend siempre se envía sin guiones con limpiarIdentificacion().
 */

export const DIGITOS_CEDULA = 9;
export const PLACEHOLDER_CEDULA = "1-1111-1111";

// true si el tipo del catálogo es la cédula nacional
export function esCedulaNacional(tipo) {
    return formatoIdentificacion(tipo) === "CEDULA";
}

// "111111111" o "1 1111 1111" -> "1-1111-1111". Mientras se escribe: "12" -> "1-2".
export function formatearCedula(texto) {
    const digitos = String(texto ?? "").replace(/\D/g, "").slice(0, DIGITOS_CEDULA);
    if (digitos.length <= 1) return digitos;
    if (digitos.length <= 5) return `${digitos[0]}-${digitos.slice(1)}`;
    return `${digitos[0]}-${digitos.slice(1, 5)}-${digitos.slice(5)}`;
}

/*
 * Para tablas y listados. Si se conoce el tipo, solo la cédula nacional lleva
 * guiones; si no se conoce, se da formato a los números de 9 dígitos
 * exactos (forma de la cédula nacional) y lo demás se deja como está.
 */
export function mostrarIdentificacion(numero, tipo) {
    if (numero == null || numero === "") return numero;
    const limpio = limpiarIdentificacion(String(numero));
    const conMascara = tipo ? esCedulaNacional(tipo) : /^\d{9}$/.test(limpio);
    return conMascara && /^\d{9}$/.test(limpio) ? formatearCedula(limpio) : numero;
}

/*
 * Al cambiar el tipo de identificación: si pasa a cédula nacional, el valor
 * se reformatea (o se limpia si tiene letras o más de 9 dígitos); si pasa a
 * otro tipo, se quitan los guiones de la máscara y el campo queda libre.
 */
export function ajustarIdentificacionAlTipo(valor, tipo) {
    const limpio = limpiarIdentificacion(valor);
    if (!esCedulaNacional(tipo)) return limpio;
    return /^\d{0,9}$/.test(limpio) ? formatearCedula(limpio) : "";
}

// Cuántos dígitos hay antes de la posición indicada
function digitosAntes(texto, posicion) {
    return texto.slice(0, posicion).replace(/\D/g, "").length;
}

// Posición en el texto con máscara justo después del dígito número n
export function posicionTrasDigitos(conMascara, n) {
    if (n <= 0) return 0;
    let vistos = 0;
    for (let i = 0; i < conMascara.length; i++) {
        if (/\d/.test(conMascara[i])) vistos += 1;
        if (vistos === n) return i + 1;
    }
    return conMascara.length;
}

/*
 * Aplica la máscara a lo que quedó en el input y calcula dónde dejar el
 * cursor (conserva cuántos dígitos había antes de él).
 */
export function aplicarMascaraConCursor(texto, cursor) {
    const valor = formatearCedula(texto);
    const n = Math.min(digitosAntes(texto, cursor ?? texto.length), DIGITOS_CEDULA);
    return { valor, cursor: posicionTrasDigitos(valor, n) };
}

/*
 * Modo automático (buscadores y campos sin tipo de identificación): si lo
 * escrito son solo dígitos (hasta 9) se muestra como cédula nacional; si
 * tiene letras o más dígitos (DIMEX, pasaporte) queda tal cual, sin guiones.
 */
export function pareceCedula(texto) {
    const valor = String(texto ?? "");
    return /^[\d\s-]*$/.test(valor) && valor.replace(/\D/g, "").length <= DIGITOS_CEDULA;
}

/*
 * Texto de un buscador listo para comparar: en minúsculas y, si es una
 * cédula escrita con la máscara, sin guiones (los datos vienen sin guiones).
 */
export function textoDeBusqueda(texto) {
    const valor = String(texto ?? "").trim();
    return (pareceCedula(valor) ? limpiarIdentificacion(valor) : valor).toLowerCase();
}

// La identificación coincide con lo buscado (con o sin guiones)
export function identificacionCoincide(numero, buscado) {
    return limpiarIdentificacion(String(numero ?? "")).toLowerCase().includes(buscado);
}
