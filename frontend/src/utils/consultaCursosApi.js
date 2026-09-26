import { API_URL } from "../config/api";

// HU-10 - Consultar cursos disponibles de un período académico,
// previo a solicitar matrícula.

async function leerRespuesta(response, mensajePorDefecto) {
    let data;

    try {
        data = await response.json();
    } catch {
        data = null;
    }

    if (response.status === 401) {
        throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
    }

    if (response.status === 403) {
        throw new Error("No tiene permisos para consultar los cursos disponibles.");
    }

    if (!response.ok) {
        throw new Error(data?.message || mensajePorDefecto);
    }

    return data;
}

export async function consultarProgramas(token) {
    const response = await fetch(`${API_URL}/programas`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    return leerRespuesta(response, "No fue posible consultar los programas académicos.");
}

export async function consultarPeriodos(token) {
    const response = await fetch(`${API_URL}/periodos-academicos`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    return leerRespuesta(response, "No fue posible consultar los períodos académicos.");
}

export async function consultarCursosDisponibles(token, { idPeriodo, idPrograma, texto }) {
    const params = new URLSearchParams({ idPeriodo, idPrograma });

    if (texto && texto.trim()) {
        params.set("texto", texto.trim());
    }

    const response = await fetch(`${API_URL}/consulta-cursos?${params.toString()}`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    return leerRespuesta(response, "No fue posible consultar los cursos disponibles.");
}
