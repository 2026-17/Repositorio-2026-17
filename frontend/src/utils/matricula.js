// Historia 2 - Matricular cursos previamente autorizados.
//
// Reglas de validación de la selección de cursos, separadas de la UI.
// Replican las que aplica el servidor al confirmar para dar respuesta
// inmediata al estudiante; el servidor es quien decide.

const NOMBRES_DIAS = { 1: "Lun", 2: "Mar", 3: "Mié", 4: "Jue", 5: "Vie", 6: "Sáb", 7: "Dom" };

export function formatearHorarios(horarios) {
    if (!horarios || horarios.length === 0) return "Por definir";
    return horarios
        .map((h) => `${NOMBRES_DIAS[h.diaSemana] ?? "?"} ${h.horaInicio}-${h.horaFin}`)
        .join(", ");
}

// La API entrega los cursos aprobados como [{ idCurso, nota }]. Se indexan
// por curso; la nota puede ser null (valoración cualitativa).
export function mapaAprobados(cursosAprobados) {
    return new Map((cursosAprobados ?? []).map((c) => [c.idCurso, c.nota]));
}

function estaAprobado(aprobados, idCurso, notaMinima) {
    if (!aprobados.has(idCurso)) return false;
    const nota = aprobados.get(idCurso);
    // Igual que el servidor: con nota mínima, la nota debe existir y alcanzarla.
    return notaMinima == null || (nota != null && nota >= notaMinima);
}

function horasSolapan(horarioA, horarioB) {
    return horarioA.diaSemana === horarioB.diaSemana
        && horarioA.horaInicio < horarioB.horaFin
        && horarioB.horaInicio < horarioA.horaFin;
}

export function gruposConflictoHorario(grupoA, grupoB) {
    return grupoA.horarios.some((ha) => grupoB.horarios.some((hb) => horasSolapan(ha, hb)));
}

// Prerrequisitos: deben aparecer entre los cursos que el estudiante ya aprobó.
// Correquisitos: deben estar ya aprobados o matriculados, o incluidos en la
// misma selección. También se valida el choque de horario contra lo
// seleccionado y contra lo que el estudiante ya tenga matriculado en el período.
export function validarSeleccion({
    requisitosPorCurso,
    aprobados,
    seleccionados,
    gruposYaMatriculados = [],
}) {
    const idsCursosSeleccionados = seleccionados.map((g) => g.idCurso);
    const idsCursosMatriculados = gruposYaMatriculados.map((g) => g.idCurso);
    const erroresPorGrupo = {};

    seleccionados.forEach((grupo) => {
        const errores = [];
        const requisitos = requisitosPorCurso[grupo.idCurso] ?? [];

        requisitos.forEach((req) => {
            if (req.tipo === "REQUISITO" && !estaAprobado(aprobados, req.idCursoRequerido, req.notaMinima)) {
                errores.push(`Prerrequisito incumplido: debe haber aprobado "${req.nombreCursoRequerido}".`);
            }

            if (
                req.tipo === "CORREQUISITO"
                && !estaAprobado(aprobados, req.idCursoRequerido, req.notaMinima)
                && !idsCursosMatriculados.includes(req.idCursoRequerido)
                && !idsCursosSeleccionados.includes(req.idCursoRequerido)
            ) {
                errores.push(`Correquisito incumplido: debe matricular junto con "${req.nombreCursoRequerido}".`);
            }
        });

        seleccionados.forEach((otroGrupo) => {
            if (otroGrupo.idGrupo === grupo.idGrupo) return;
            if (gruposConflictoHorario(grupo, otroGrupo)) {
                errores.push(`Choque de horario con "${otroGrupo.nombreCurso}" (${otroGrupo.codigo}).`);
            }
        });

        gruposYaMatriculados.forEach((grupoMatriculado) => {
            if (gruposConflictoHorario(grupo, grupoMatriculado)) {
                errores.push(
                    `Choque de horario con "${grupoMatriculado.nombreCurso}" (${grupoMatriculado.codigo}), ya matriculado este período.`
                );
            }
        });

        if (errores.length > 0) {
            erroresPorGrupo[grupo.idGrupo] = errores;
        }
    });

    return erroresPorGrupo;
}

// Un curso no se puede seleccionar si ya está matriculado en el período, si no
// tiene cupos, o si tiene un prerrequisito (no correquisito) incumplido: esos
// casos "impiden continuar" sin depender de qué más se seleccione.
export function tieneBloqueoEstatico({
    grupo,
    requisitosPorCurso,
    aprobados,
    idsCursosMatriculados = [],
}) {
    if (idsCursosMatriculados.includes(grupo.idCurso)) {
        return "Ya matriculado en este curso durante el período vigente.";
    }

    if (grupo.cupoDisponible <= 0) {
        return "Sin cupos disponibles.";
    }

    const requisitos = requisitosPorCurso[grupo.idCurso] ?? [];
    const prerrequisitoFaltante = requisitos.find(
        (req) => req.tipo === "REQUISITO" && !estaAprobado(aprobados, req.idCursoRequerido, req.notaMinima)
    );

    if (prerrequisitoFaltante) {
        return `Prerrequisito incumplido: debe haber aprobado "${prerrequisitoFaltante.nombreCursoRequerido}".`;
    }

    return null;
}
