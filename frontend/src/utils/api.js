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

function mensajeSinCuerpo(status, porDefecto) {
    if (status === 413) return "El archivo es demasiado grande.";
    if (status === 401) return "La sesión ha expirado. Inicie sesión nuevamente.";
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
 * según el texto del backend: "correo", "identificacion" o null si no
 * coincide con ninguno (en ese caso se muestra como error general).
 */
export function campoDeConflicto(mensaje) {
    const texto = (mensaje || "")
        .toLowerCase()
        .normalize("NFD")
        .replace(/[̀-ͯ]/g, "");
    if (texto.includes("correo")) return "correo";
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
