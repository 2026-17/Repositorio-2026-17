// Estados de un período académico (lista, creación y modificación).
export const ESTADOS_PERIODO = [
    { value: "PLANIFICADO", label: "Planificado" },
    { value: "MATRICULA", label: "Matrícula" },
    { value: "EN_CURSO", label: "Activo (en curso)" },
    { value: "CERRADO", label: "Cerrado" },
    { value: "CANCELADO", label: "Cancelado" },
];

// Color de la insignia según el estado del período
export const TONO_ESTADO_PERIODO = {
    PLANIFICADO: "info",
    MATRICULA: "warning",
    EN_CURSO: "success",
    CERRADO: "neutral",
    CANCELADO: "danger",
};

export const ETIQUETA_ESTADO_PERIODO = Object.fromEntries(
    ESTADOS_PERIODO.map((e) => [e.value, e.label])
);

// El estado solo avanza: PLANIFICADO → MATRICULA → EN_CURSO → CERRADO.
// CANCELADO solo desde PLANIFICADO o MATRICULA. Misma regla que el backend.
const ORDEN_ESTADOS = ["PLANIFICADO", "MATRICULA", "EN_CURSO", "CERRADO"];

export function cambioEstadoPermitido(actual, nuevo) {
    if (!actual || actual === nuevo) return true;
    if (actual === "CANCELADO") return false;
    if (nuevo === "CANCELADO") return actual === "PLANIFICADO" || actual === "MATRICULA";
    return ORDEN_ESTADOS.indexOf(nuevo) >= ORDEN_ESTADOS.indexOf(actual);
}
