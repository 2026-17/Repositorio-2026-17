// Catálogos y utilidades de Grupos (pantalla y formulario).

export const MODALIDADES = [
    { value: "PRESENCIAL", label: "Presencial" },
    { value: "VIRTUAL", label: "Virtual" },
    { value: "HIBRIDA", label: "Híbrida" },
];

// Igual que ck_grupos_estado en la BD.
export const ESTADOS_GRUPO = [
    { value: "PLANIFICADO", label: "Planificado", tono: "neutral" },
    { value: "PUBLICADO", label: "Publicado", tono: "info" },
    { value: "EN_CURSO", label: "En curso", tono: "success" },
    { value: "CERRADO", label: "Cerrado", tono: "neutral" },
    { value: "CANCELADO", label: "Cancelado", tono: "danger" },
];

export const ETIQUETA_ESTADO_GRUPO = Object.fromEntries(ESTADOS_GRUPO.map((e) => [e.value, e.label]));
export const TONO_ESTADO_GRUPO = Object.fromEntries(ESTADOS_GRUPO.map((e) => [e.value, e.tono]));

export const ETIQUETA_MODALIDAD = Object.fromEntries(MODALIDADES.map((m) => [m.value, m.label]));

// 1 = lunes ... 7 = domingo (igual que la tabla horarios_grupos).
export const DIAS = [
    { value: "1", label: "Lunes", corto: "Lun" },
    { value: "2", label: "Martes", corto: "Mar" },
    { value: "3", label: "Miércoles", corto: "Mié" },
    { value: "4", label: "Jueves", corto: "Jue" },
    { value: "5", label: "Viernes", corto: "Vie" },
    { value: "6", label: "Sábado", corto: "Sáb" },
    { value: "7", label: "Domingo", corto: "Dom" },
];

const DIA_CORTO = Object.fromEntries(DIAS.map((d) => [d.value, d.corto]));

/*
 * Cursos a coordinar en Dirección (Música de Cámara): sus grupos pueden no
 * tener horario ni profesor; el profesor se asigna por estudiante.
 */
export const TEXTO_HORARIO_A_COORDINAR = "A coordinar en Dirección";
export const TEXTO_PROFESOR_POR_ESTUDIANTE = "Se asigna por estudiante";

/*
 * Esos grupos tampoco tienen cupo (sinCupo = true, cupoMaximo en null):
 * nunca se llenan y no se muestra el valor guardado en la base de datos.
 */
export const TEXTO_SIN_CUPO = "No requiere cupo";
export const TEXTO_CUPO_FORMULARIO = "No requiere cupo: no tiene límite de estudiantes, se matricula según convenga.";

// "7 matriculados" (o "1 matriculado").
export function textoMatriculados(cantidad) {
    const n = Number(cantidad) || 0;
    return `${n} ${n === 1 ? "matriculado" : "matriculados"}`;
}

// "09:00:00" → "09:00"
export function aHoraCorta(hora) {
    return String(hora || "").slice(0, 5);
}

// [{diaSemana:1, horaInicio:"09:00:00", horaFin:"10:30:00"}] → ["Lun 09:00–10:30"]
export function resumenHorario(horarios = []) {
    return horarios.map((h) => `${DIA_CORTO[String(h.diaSemana)] || "?"} ${aHoraCorta(h.horaInicio)}–${aHoraCorta(h.horaFin)}`);
}
