import { API_URL } from "../../config/api";

/*
 * Llamadas al backend de Programas académicos (/programas).
 * Todas lanzan Error con un mensaje listo para mostrar al usuario.
 */

const MENSAJE_401 = "La sesión ha expirado. Inicie sesión nuevamente.";

async function solicitar(ruta, { method = "GET", body, mensaje403, mensajeError } = {}) {
    const token = localStorage.getItem("token");

    const response = await fetch(`${API_URL}${ruta}`, {
        method,
        headers: {
            Authorization: `Bearer ${token}`,
            ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    let data = null;
    try {
        data = await response.json();
    } catch {
        // La respuesta puede venir sin cuerpo.
    }

    if (response.status === 401) throw new Error(MENSAJE_401);
    if (response.status === 403) throw new Error(mensaje403 || "No tiene permisos para gestionar programas académicos.");
    if (!response.ok) {
        const error = new Error(data?.message || mensajeError || "No fue posible completar la operación.");
        error.status = response.status;
        error.fields = data?.fields;
        throw error;
    }

    return data;
}

export async function obtenerProgramasGestion() {
    const datos = await solicitar("/programas/gestion", {
        mensaje403: "No tiene permisos para consultar los programas académicos.",
        mensajeError: "No fue posible cargar los programas académicos.",
    });
    return Array.isArray(datos) ? datos : [];
}

export function consultarDisponibilidad({ codigo = "", nombre = "" }, signal) {
    const params = new URLSearchParams();
    if (codigo.trim()) params.set("codigo", codigo.trim());
    if (nombre.trim()) params.set("nombre", nombre.trim());

    const token = localStorage.getItem("token");
    return fetch(`${API_URL}/programas/disponibilidad?${params}`, {
        headers: { Authorization: `Bearer ${token}` },
        signal,
    }).then((r) => (r.ok ? r.json() : null));
}

export function crearPrograma(payload) {
    return solicitar("/programas", {
        method: "POST",
        body: payload,
        mensaje403: "No tiene permisos para crear programas académicos.",
        mensajeError: "No fue posible crear el programa académico.",
    });
}

export function actualizarPrograma(id, payload) {
    return solicitar(`/programas/${id}`, {
        method: "PUT",
        body: payload,
        mensaje403: "No tiene permisos para modificar programas académicos.",
        mensajeError: "No fue posible modificar el programa académico.",
    });
}

export function agregarPlanPrograma(id, payload) {
    return solicitar(`/programas/${id}/planes`, {
        method: "POST",
        body: payload,
        mensajeError: "No fue posible crear el plan de estudio.",
    });
}

export function cambiarEstadoPrograma(id, activo) {
    return solicitar(`/programas/${id}/estado`, {
        method: "PATCH",
        body: { activo },
        mensajeError: activo
            ? "No fue posible activar el programa académico."
            : "No fue posible desactivar el programa académico.",
    });
}

// ---------- Utilidades de formulario ----------

export const PLAN_VACIO = {
    version: "1.0",
    fechaVigenciaDesde: "",
    notaMinimaAprobacion: "70",
};

export function hoyISO() {
    const d = new Date();
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

// La sugerencia de código a partir del nombre está en src/utils/codigos.js.

export const PATRON_VERSION = /^[0-9A-Za-z][0-9A-Za-z.-]*$/;

// Valida los datos del plan; devuelve { campo: mensaje }.
export function validarPlan(plan) {
    const errores = {};
    const version = plan.version.trim();

    if (!version) errores.version = "Ingrese la versión del plan.";
    else if (version.length > 20) errores.version = "La versión no puede superar los 20 caracteres.";
    else if (!PATRON_VERSION.test(version)) errores.version = "Use solo letras, números, puntos y guiones (ej. 1.0).";

    if (!plan.fechaVigenciaDesde) errores.fechaVigenciaDesde = "Indique desde cuándo rige el plan.";

    const nota = String(plan.notaMinimaAprobacion).trim();
    const numero = Number(nota);
    if (nota === "") errores.notaMinimaAprobacion = "Ingrese la nota mínima de aprobación.";
    else if (Number.isNaN(numero) || numero < 0 || numero > 100) errores.notaMinimaAprobacion = "La nota debe estar entre 0 y 100.";
    else if (!/^\d{1,3}([.,]\d{1,2})?$/.test(nota)) errores.notaMinimaAprobacion = "Use como máximo dos decimales.";

    return errores;
}

export function planAPayload(plan) {
    return {
        version: plan.version.trim(),
        fechaVigenciaDesde: plan.fechaVigenciaDesde,
        notaMinimaAprobacion: Number(String(plan.notaMinimaAprobacion).replace(",", ".")),
    };
}

export function fechaLegible(iso) {
    if (!iso) return "—";
    const [a, m, d] = String(iso).slice(0, 10).split("-").map(Number);
    return new Date(a, m - 1, d).toLocaleDateString("es-CR", { day: "numeric", month: "long", year: "numeric" });
}
