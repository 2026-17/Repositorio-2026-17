import { useSyncExternalStore } from "react";

import { API_URL } from "../config/api";

/*
 * Notificaciones del estudiante compartidas entre la campana del Header y la
 * pantalla "Mis notificaciones": se guardan en un único almacén para que, al
 * marcar una como leída, el contador de la campana se actualice solo.
 */

let estado = { notificaciones: [], cargando: false, cargado: false };
let consultaEnCurso = null;
const suscriptores = new Set();

function actualizar(cambios) {
    estado = { ...estado, ...cambios };
    suscriptores.forEach((avisar) => avisar());
}

function suscribir(avisar) {
    suscriptores.add(avisar);
    return () => suscriptores.delete(avisar);
}

function leerEstado() {
    return estado;
}

function encabezados() {
    return { Authorization: `Bearer ${localStorage.getItem("token")}` };
}

async function leerCuerpo(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}

// Cantidad de notificaciones pendientes de leer
export function contarNoLeidas(notificaciones) {
    return notificaciones.filter((n) => !n.leida).length;
}

/*
 * Consulta las notificaciones del estudiante. Si ya hay una consulta en
 * curso, se reutiliza. Lanza un Error con el mensaje para el usuario.
 */
export function cargarNotificaciones() {
    if (consultaEnCurso) return consultaEnCurso;

    actualizar({ cargando: true });

    consultaEnCurso = (async () => {
        try {
            const response = await fetch(`${API_URL}/notificaciones-estudiante/mias`, {
                headers: encabezados(),
            });
            const data = await leerCuerpo(response);

            if (response.status === 401) {
                throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            }
            if (response.status === 403) {
                throw new Error("No tiene permisos para consultar estas notificaciones.");
            }
            if (!response.ok) {
                throw new Error(data.message || "No fue posible consultar las notificaciones.");
            }

            actualizar({ notificaciones: Array.isArray(data) ? data : [], cargado: true });
        } catch (error) {
            actualizar({ notificaciones: [], cargado: true });
            throw error;
        } finally {
            actualizar({ cargando: false });
            consultaEnCurso = null;
        }
    })();

    return consultaEnCurso;
}

// Marca una notificación como leída y actualiza a todos los suscriptores.
export async function marcarNotificacionLeida(idNotificacion) {
    const response = await fetch(`${API_URL}/notificaciones-estudiante/${idNotificacion}/leer`, {
        method: "PATCH",
        headers: encabezados(),
    });
    const data = await leerCuerpo(response);

    if (response.status === 401) {
        throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
    }
    if (response.status === 403) {
        throw new Error("No tiene permisos para modificar esta notificación.");
    }
    if (!response.ok) {
        throw new Error(data.message || "No fue posible marcar la notificación como leída.");
    }

    actualizar({
        notificaciones: estado.notificaciones.map((item) =>
            item.idNotificacion === idNotificacion ? data : item
        ),
    });
}

/*
 * { notificaciones, cargando, cargado, noLeidas } del estudiante con sesión.
 * La carga se pide con cargarNotificaciones().
 */
export function useNotificacionesEstudiante() {
    const actual = useSyncExternalStore(suscribir, leerEstado);
    return { ...actual, noLeidas: contarNoLeidas(actual.notificaciones) };
}
