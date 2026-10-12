import { API_URL } from "../config/api";

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
        throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
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

export async function confirmarMatricula(token, idsGrupo) {
    const response = await fetch(`${API_URL}/matriculas-estudiante/confirmar`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ idsGrupo }),
    });

    return leerRespuesta(response, "No fue posible registrar la matrícula.");
}
