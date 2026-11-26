/*
 * Lectura común de las respuestas de error del backend.
 *
 * El backend responde los errores con { status, message, fields?, errores? }.
 * Si el cuerpo no es JSON (por ejemplo, el 413 que devuelve Nginx en HTML),
 * se usa un mensaje pensado para el usuario final.
 *
 * delBackend indica si el mensaje vino del servidor o es uno de respaldo.
 */
export async function leerError(response, porDefecto) {
    const data = await response.json().catch(() => null);

    const status = response.status;
    const mensajeBackend = typeof data?.message === "string" && data.message.trim()
        ? data.message
        : null;

    return {
        status,
        message: mensajeBackend || mensajeSinCuerpo(status, porDefecto),
        fields: data?.fields && typeof data.fields === "object" ? data.fields : {},
        errores: Array.isArray(data?.errores) ? data.errores : [],
        delBackend: Boolean(mensajeBackend),
    };
}

/*
 * Mensaje único de sesión vencida (401) para todas las pantallas. Es el
 * mismo que manda el backend en SESION_EXPIRADA.
 */
export const MENSAJE_SESION_EXPIRADA = "Su sesión expiró. Inicie sesión de nuevo.";

/*
 * Excepción que lanzan las pantallas cuando el backend responde 401. El
 * vigilante de sesión ya muestra la ventana para volver a ingresar; esto
 * solo corta la operación con el mismo mensaje en todas partes.
 */
export class SesionExpiradaError extends Error {
    constructor(mensaje = MENSAJE_SESION_EXPIRADA) {
        super(mensaje);
        this.name = "SesionExpiradaError";
        this.status = 401;
    }
}

function mensajeSinCuerpo(status, porDefecto) {
    if (status === 413) return "El archivo es demasiado grande.";
    if (status === 401) return MENSAJE_SESION_EXPIRADA;
    if (status === 403) return "No tiene permisos para realizar esta acción.";
    return porDefecto;
}

/*
 * Mensaje para mostrar en un catch. Si fetch no pudo conectarse lanza un
 * TypeError ("Failed to fetch"), que no sirve al usuario: se usa porDefecto.
 */
export function mensajeDeExcepcion(e, porDefecto) {
    if (e instanceof TypeError || !e?.message) return porDefecto;
    return e.message;
}

/*
 * Convierte el mapa "fields" del backend en errores por campo del formulario.
 * Quita el prefijo de los objetos anidados ("profesor.correo" -> "correo") y
 * permite renombrar campos que se llaman distinto en el formulario, tanto por
 * la ruta completa ("usuario.cedula") como por el nombre corto.
 */
export function erroresDeCampos(fields, equivalencias = {}) {
    const errores = {};
    Object.entries(fields || {}).forEach(([ruta, mensaje]) => {
        const corto = ruta.split(".").pop();
        const campo = equivalencias[ruta] || equivalencias[corto] || corto;
        if (!errores[campo]) errores[campo] = mensaje;
    });
    return errores;
}

/*
 * Decide a qué campo del formulario pertenece un error de duplicado (409)
 * según el texto del backend: "identificacion" o null si no coincide (en ese
 * caso se muestra como error general). El correo se puede compartir.
 */
export function campoDeConflicto(mensaje) {
    const texto = (mensaje || "")
        .toLowerCase()
        .normalize("NFD")
        .replace(/[̀-ͯ]/g, "");
    if (texto.includes("identificacion") || texto.includes("cedula")) return "identificacion";
    return null;
}

/*
 * Igual que leerError, pero lanza un Error con el mensaje (y status, fields
 * y errores como propiedades) para usarlo dentro de un try/catch.
 */
export async function lanzarError(response, porDefecto) {
    const info = await leerError(response, porDefecto);
    const error = new Error(info.message);
    Object.assign(error, info);
    throw error;
}
