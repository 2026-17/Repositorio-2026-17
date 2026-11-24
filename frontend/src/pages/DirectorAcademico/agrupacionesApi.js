import { solicitar } from "./espaciosApi";

/*
 * Agrupaciones (Orquesta, Banda, Coro...) - acceso al backend.
 * Reutiliza solicitar() de los espacios de instrumento.
 */

export const TIPOS_AGRUPACION = [
    { value: "INSTRUMENTAL", label: "Instrumental", ayuda: "Cuenta para Conjunto Instrumental I a XII." },
    { value: "CORAL", label: "Coral", ayuda: "Cuenta para Coro I a XIV." },
];

export const ETIQUETA_TIPO = Object.fromEntries(TIPOS_AGRUPACION.map((t) => [t.value, t.label]));

// Catálogo
export function listarAgrupaciones() {
    return solicitar("/agrupaciones", {}, "No fue posible cargar las agrupaciones.");
}

export function crearAgrupacion(payload) {
    return solicitar("/agrupaciones", { method: "POST", body: JSON.stringify(payload) },
        "No fue posible crear la agrupación.");
}

export function modificarAgrupacion(id, payload) {
    return solicitar(`/agrupaciones/${id}`, { method: "PUT", body: JSON.stringify(payload) },
        "No fue posible modificar la agrupación.");
}

// Grupos de una agrupación en un período
export function listarGruposAgrupacion(idPeriodo) {
    const params = idPeriodo ? `?idPeriodo=${encodeURIComponent(idPeriodo)}` : "";
    return solicitar(`/agrupaciones/grupos${params}`, {}, "No fue posible cargar las agrupaciones del período.");
}

export function crearGrupoAgrupacion(payload) {
    return solicitar("/agrupaciones/grupos", { method: "POST", body: JSON.stringify(payload) },
        "No fue posible guardar la agrupación.");
}

export function modificarGrupoAgrupacion(idGrupo, payload) {
    return solicitar(`/agrupaciones/grupos/${idGrupo}`, { method: "PUT", body: JSON.stringify(payload) },
        "No fue posible guardar la agrupación.");
}

// Choques del horario con los grupos y espacios de los profesores (sin guardar).
export function revisarChoques(payload, idGrupo) {
    const params = idGrupo ? `?idGrupo=${encodeURIComponent(idGrupo)}` : "";
    return solicitar(`/agrupaciones/grupos/choques${params}`, { method: "POST", body: JSON.stringify(payload) },
        "No fue posible revisar los choques de horario.");
}

// Vista interna: estudiantes del grupo con su nivel.
export function listarEstudiantesGrupo(idGrupo) {
    return solicitar(`/agrupaciones/grupos/${idGrupo}/estudiantes`, {},
        "No fue posible cargar los estudiantes de la agrupación.");
}

export function listarProfesoresActivos() {
    return solicitar("/asignaciones-profesores/profesores-activos", {}, "No fue posible cargar los profesores.");
}
