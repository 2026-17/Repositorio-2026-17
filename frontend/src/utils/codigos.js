/*
 * Códigos sugeridos a partir de un nombre. Se usan en los formularios
 * donde el usuario debe escribir un código (programas, cursos…): el
 * código se propone solo mientras el usuario no escriba uno propio.
 *
 * Reglas:
 *   - Sin tildes ni signos, en mayúsculas.
 *   - Se ignoran palabras vacías («de», «la», «y»…).
 *   - Varias palabras → sus iniciales:   "Formación Musical Infantil" → "FMI"
 *   - Una sola palabra → sus primeras 4 letras:   "Violín" → "VIOL"
 *   - Números y números romanos se agregan con guion:
 *         "Piano Básico II" → "PB-II"      "Teoría Musical 1" → "TM-1"
 */

const PALABRAS_VACIAS = new Set([
    "de", "del", "la", "las", "el", "los", "y", "e", "o", "u",
    "en", "para", "con", "a", "al", "por", "sin",
]);

const ROMANO = /^(?=[IVXLC])M{0,3}(C[MD]|D?C{0,3})(X[CL]|L?X{0,3})(I[XV]|V?I{0,3})$/;

function esNumero(palabra) {
    return /^\d+$/.test(palabra) || ROMANO.test(palabra);
}

export function sugerirCodigo(nombre, { maximo = 30 } = {}) {
    const palabras = String(nombre || "")
        .normalize("NFD")
        .replace(/\p{M}/gu, "")
        .replace(/[^A-Za-z0-9 ]+/g, " ")
        .split(/\s+/)
        .filter(Boolean);

    if (palabras.length === 0) return "";

    // Los romanos se reconocen solo en mayúsculas (así «I» no es la letra «i»).
    const numeros = palabras.filter(esNumero).map((p) => p.toUpperCase());
    const texto = palabras.filter((p) => !esNumero(p));
    const importantes = texto.filter((p) => !PALABRAS_VACIAS.has(p.toLowerCase()));
    const fuente = importantes.length > 0 ? importantes : texto;

    let base;
    if (fuente.length === 0) base = "";
    else if (fuente.length === 1) base = fuente[0].slice(0, 4);
    else base = fuente.slice(0, 6).map((p) => p[0]).join("");

    return [base.toUpperCase(), ...numeros]
        .filter(Boolean)
        .join("-")
        .slice(0, maximo)
        .replace(/-+$/, "");
}

// Letras, números y guiones; sin guion al inicio, al final ni dos seguidos.
export const PATRON_CODIGO = /^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$/;

// Normaliza lo que el usuario escribe en un campo de código.
export function normalizarCodigoEscrito(valor) {
    return valor.toUpperCase().replace(/\s+/g, "-");
}
