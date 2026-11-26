import { API_URL } from "../config/api";
import {
    EVENTO_SESION_CAMBIO,
    expiracionDelToken,
    haySesion,
    limpiarSesion,
} from "./sesion";

/*
 * Vigilante de la sesión (se instala una sola vez en main.jsx).
 *
 * Como un portero que mira el reloj: el token del backend dura una hora y,
 * cuando vence, cualquier petición responde 401. En vez de que cada pantalla
 * lo descubra por su cuenta, aquí se centraliza:
 *
 * 1. Envuelve window.fetch: si una respuesta del backend es 401 (fuera de
 *    /auth/) y había sesión, borra la sesión y emite "sigac:sesion-expirada".
 *    La respuesta original se devuelve igual, para no romper las pantallas.
 * 2. Temporizador: a 2 minutos de vencer avisa ("sigac:sesion-por-vencer") y
 *    al vencer emite "sigac:sesion-expirada".
 * 3. Escucha "storage" para enterarse si en otra pestaña se cerró o se inició
 *    sesión.
 *
 * El componente AvisoSesionExpirada lee el estado con useEstadoSesion.
 */

export const EVENTO_SESION_EXPIRADA = "sigac:sesion-expirada";
export const EVENTO_SESION_POR_VENCER = "sigac:sesion-por-vencer";
export const EVENTO_SESION_RENOVADA = "sigac:sesion-renovada";

const AVISO_ANTES_MS = 2 * 60 * 1000;

const MENSAJE_OTRA_PESTANA =
    "La sesión se cerró en otra pestaña. Inicie sesión de nuevo para continuar.";

/*
 * Estado que muestra el aviso: null, { tipo: "por-vencer", vence } o
 * { tipo: "expirada", mensaje, codigo }. Es un objeto nuevo en cada cambio
 * (useSyncExternalStore compara por referencia).
 */
let estado = null;
const oyentes = new Set();

let instalado = false;
let tokenProgramado = null;
let temporizadores = [];
let leyendoRespuesta = false;

function cambiarEstado(nuevo) {
    estado = nuevo;
    oyentes.forEach((oyente) => oyente());
}

export function suscribirEstadoSesion(oyente) {
    oyentes.add(oyente);
    return () => oyentes.delete(oyente);
}

export function obtenerEstadoSesion() {
    return estado;
}

// El aviso ya se atendió (o no aplica en la pantalla actual).
export function descartarAvisoSesion() {
    cambiarEstado(null);
}

function cancelarTemporizadores() {
    temporizadores.forEach(clearTimeout);
    temporizadores = [];
}

/*
 * Emite la sesión expirada una sola vez, aunque fallen varias peticiones a
 * la vez o coincidan el temporizador y un 401.
 */
function emitirExpirada(mensaje, codigo) {
    if (estado?.tipo === "expirada") return;

    cancelarTemporizadores();
    tokenProgramado = null;
    limpiarSesion();

    // codigo: el del backend (SESION_EXPIRADA, CUENTA_INACTIVA...) o null.
    const detalle = { mensaje: mensaje || null, codigo: codigo || null };
    cambiarEstado({ tipo: "expirada", ...detalle });
    window.dispatchEvent(new CustomEvent(EVENTO_SESION_EXPIRADA, { detail: detalle }));
}

/*
 * Programa el aviso y el vencimiento según el token guardado. Si el token
 * cambió (inicio de sesión nuevo, aquí o en otra pestaña), se reprograma; si
 * se borró, se cancela.
 */
function programar({ forzar = false } = {}) {
    const token = localStorage.getItem("token") || null;
    if (!forzar && token === tokenProgramado) return;

    const esNuevo = token !== null && token !== tokenProgramado;
    tokenProgramado = token;
    cancelarTemporizadores();

    if (!token) return;

    if (esNuevo && estado) {
        // Sesión nueva: se quita cualquier aviso de la anterior.
        cambiarEstado(null);
        window.dispatchEvent(new Event(EVENTO_SESION_RENOVADA));
    }

    const vence = expiracionDelToken(token);
    if (vence === null) return;

    const faltan = vence - Date.now();
    if (faltan <= 0) {
        emitirExpirada();
        return;
    }

    temporizadores.push(setTimeout(() => {
        if (estado?.tipo === "expirada") return;
        cambiarEstado({ tipo: "por-vencer", vence });
        window.dispatchEvent(new CustomEvent(EVENTO_SESION_POR_VENCER, { detail: { vence } }));
    }, Math.max(0, faltan - AVISO_ANTES_MS)));

    temporizadores.push(setTimeout(() => emitirExpirada(), faltan));
}

// Otra pestaña cambió el localStorage.
function alCambiarOtraPestana(evento) {
    if (evento.key !== null && evento.key !== "token") return;

    const anterior = tokenProgramado;
    const actual = localStorage.getItem("token") || null;

    if (anterior && !actual) {
        // Si el token ya estaba por vencer, la otra pestaña solo se adelantó
        // unos segundos: se usa el mensaje normal de sesión expirada.
        const vence = expiracionDelToken(anterior);
        const yaVencia = vence !== null && vence - Date.now() < 5000;
        emitirExpirada(yaVencia ? null : MENSAJE_OTRA_PESTANA);
        return;
    }

    programar();
}

/*
 * Al volver a la pestaña (o al despertar el equipo) los temporizadores pueden
 * haberse atrasado: se revisa de nuevo con la hora real.
 */
function alVolverALaPestana() {
    if (document.visibilityState === "visible" && tokenProgramado) {
        programar({ forzar: true });
    }
}

function urlDe(entrada) {
    if (typeof entrada === "string") return entrada;
    if (entrada instanceof URL) return entrada.href;
    return entrada?.url || "";
}

function conBarra(url) {
    return url.endsWith("/") ? url : `${url}/`;
}

/*
 * Solo cuentan las peticiones al backend que no son de /auth/ (login,
 * recuperar contraseña, cambio de contraseña): ahí un 401 es una respuesta
 * normal del formulario, no una sesión vencida.
 */
function esPeticionProtegida(entrada) {
    try {
        const base = conBarra(new URL(API_URL, window.location.href).href);
        const url = new URL(urlDe(entrada), window.location.href).href;
        return url.startsWith(base) && !url.startsWith(`${base}auth/`);
    } catch {
        return false;
    }
}

function revisarRespuesta(entrada, respuesta) {
    if (respuesta.status !== 401 || leyendoRespuesta) return;
    if (estado?.tipo === "expirada" || !haySesion() || !esPeticionProtegida(entrada)) return;

    // clone(): la pantalla que hizo la petición todavía puede leer el cuerpo.
    leyendoRespuesta = true;
    respuesta.clone().json()
        .catch(() => null)
        .then((datos) => {
            const mensaje = typeof datos?.message === "string" && datos.message.trim()
                ? datos.message
                : null;
            const codigo = typeof datos?.codigo === "string" ? datos.codigo : null;
            emitirExpirada(mensaje, codigo);
        })
        .finally(() => {
            leyendoRespuesta = false;
        });
}

export function instalarVigilanteSesion() {
    if (instalado) return;
    instalado = true;

    const fetchOriginal = window.fetch.bind(window);
    window.fetch = async (entrada, opciones) => {
        const respuesta = await fetchOriginal(entrada, opciones);
        try {
            revisarRespuesta(entrada, respuesta);
        } catch {
            /* el vigilante nunca debe romper una petición */
        }
        return respuesta;
    };

    window.addEventListener(EVENTO_SESION_CAMBIO, () => programar());
    window.addEventListener("storage", alCambiarOtraPestana);
    document.addEventListener("visibilitychange", alVolverALaPestana);

    programar();
}
