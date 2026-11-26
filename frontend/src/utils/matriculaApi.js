import { API_URL } from "../config/api";
import { SesionExpiradaError } from "./api";

// Historia 2 - Acceso al backend de matrícula del estudiante.
// El estudiante y el período los determina el servidor a partir de la sesión.

async function leerRespuesta(response, mensajePorDefecto) {
    let data;

    try {
        data = await response.json();
    } catch {
        data = null;
    }

    if (response.status === 401) {
        throw new SesionExpiradaError();
    }

    if (response.status === 403) {
        throw new Error("No tiene permisos para acceder a la matrícula de cursos.");
    }

    if (!response.ok) {
        const error = new Error(data?.message || mensajePorDefecto);
        // Cuando se incumplen varias reglas a la vez, el servidor las lista todas.
        error.errores = Array.isArray(data?.errores) ? data.errores : [];
        throw error;
    }

    return data;
}

export async function consultarMiOferta(token) {
    const response = await fetch(`${API_URL}/matriculas-estudiante/mi-oferta`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    return leerRespuesta(response, "No fue posible consultar la información de matrícula.");
}

// Horario semanal: grupos matriculados en un período. Sin idPeriodo, el
// servidor elige el período en curso.
export async function consultarMiHorario(token, idPeriodo) {
    const params = idPeriodo ? `?idPeriodo=${encodeURIComponent(idPeriodo)}` : "";
    const response = await fetch(`${API_URL}/matriculas-estudiante/mi-horario${params}`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    return leerRespuesta(response, "No fue posible consultar su horario.");
}

// Agrupaciones publicadas del período vigente (orquestas, bandas, coros...),
// con el motivo si alguna está bloqueada. Nunca trae el nivel.
export async function consultarMisAgrupaciones(token) {
    const response = await fetch(`${API_URL}/matriculas-estudiante/agrupaciones`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    return leerRespuesta(response, "No fue posible consultar las agrupaciones.");
}

// espacios: un espacio de instrumento por instrumento vigente, como
// [{ idEspacio, idInstrumento }] (idInstrumento = la lección para la que se
// eligió; hace falta en los espacios de "cualquier instrumento del
// profesor"). El nivel lo calcula el servidor.
// idsAgrupacion: grupos de agrupación elegidos, EN EL ORDEN en que se
// marcaron. Qué agrupación se califica como coro lo decide el servidor.
export async function confirmarMatricula(token, idsGrupo, espacios = [], idsAgrupacion = []) {
    const response = await fetch(`${API_URL}/matriculas-estudiante/confirmar`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ idsGrupo, espacios, idsAgrupacion }),
    });

    return leerRespuesta(response, "No fue posible registrar la matrícula.");
}
