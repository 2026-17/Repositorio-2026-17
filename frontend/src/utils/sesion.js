import { RUTA_INICIO } from "../constants/menu";
import { ROLES } from "../constants/roles";

/*
 * Datos de la sesión guardados en este navegador (los usan el Header
 * y las páginas internas).
 */
const CLAVES = ["token", "rol", "cedula", "nombre", "correo", "requiereCambioContrasena"];

/*
 * Página de inicio de cada rol después de iniciar sesión: la pantalla
 * que explica las opciones de su menú.
 */
export const RUTAS_POR_ROL = {
    [ROLES.ADMINISTRADOR]: RUTA_INICIO,
    [ROLES.DIRECTOR_ACADEMICO]: RUTA_INICIO,
    [ROLES.ADMINISTRATIVO]: RUTA_INICIO,
    [ROLES.PROFESOR]: RUTA_INICIO,
    [ROLES.JUNTA_DIRECTIVA]: RUTA_INICIO,
    [ROLES.ESTUDIANTE]: RUTA_INICIO,
};

// Pantalla para cambiar la contraseña temporal en el primer inicio de sesión
export const RUTA_CAMBIO_CONTRASENA_TEMPORAL = "/cambiar-contrasena-temporal";

/*
 * Evento de esta pestaña cuando se guarda o se borra la sesión, para que el
 * vigilante de sesión reprograme su temporizador. (Para las otras pestañas
 * el navegador ya emite el evento "storage".)
 */
export const EVENTO_SESION_CAMBIO = "sigac:sesion-cambio";

function avisarCambio() {
    window.dispatchEvent(new Event(EVENTO_SESION_CAMBIO));
}

// data: LoginResponse del backend
export function guardarSesion(data, cedula) {
    localStorage.setItem("token", data.token || "");
    localStorage.setItem("rol", data.rol || "");
    localStorage.setItem("cedula", cedula);
    localStorage.setItem("nombre", data.nombre || "");
    localStorage.setItem("correo", data.correo || "");
    if (data.requiereCambioContrasena) {
        localStorage.setItem("requiereCambioContrasena", "true");
    } else {
        localStorage.removeItem("requiereCambioContrasena");
    }
    avisarCambio();
}

export function limpiarSesion() {
    CLAVES.forEach((clave) => localStorage.removeItem(clave));
    avisarCambio();
}

/*
 * Momento en que vence el token guardado, en milisegundos (Date.now()), o
 * null si no hay token o no se puede leer.
 *
 * El JWT son tres partes separadas por puntos; la del medio (payload) es
 * JSON en base64url. Se lee sin verificar la firma: solo sirve para avisar
 * a tiempo, el backend es quien decide si el token vale.
 */
export function expiracionDelToken(token = localStorage.getItem("token")) {
    try {
        const payload = token?.split(".")[1];
        if (!payload) return null;
        const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
        const relleno = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
        const exp = JSON.parse(atob(relleno)).exp;
        return Number.isFinite(exp) ? exp * 1000 : null;
    } catch {
        return null;
    }
}

// Hay sesión si quedó un token guardado al iniciar sesión
export function haySesion() {
    return Boolean(localStorage.getItem("token"));
}

// El backend solo acepta el cambio de contraseña mientras esto sea cierto
export function cambioContrasenaPendiente() {
    return localStorage.getItem("requiereCambioContrasena") === "true";
}
