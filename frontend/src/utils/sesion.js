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
}

export function limpiarSesion() {
    CLAVES.forEach((clave) => localStorage.removeItem(clave));
}

// Hay sesión si quedó un token guardado al iniciar sesión
export function haySesion() {
    return Boolean(localStorage.getItem("token"));
}

// El backend solo acepta el cambio de contraseña mientras esto sea cierto
export function cambioContrasenaPendiente() {
    return localStorage.getItem("requiereCambioContrasena") === "true";
}
