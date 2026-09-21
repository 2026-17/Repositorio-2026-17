// HU-002 / Issue #61 - Selección de cursos para matrícula.
// Reglas de validación de la selección de cursos, separadas de la UI.

const NOMBRES_DIAS = { 1: "Lun", 2: "Mar", 3: "Mié", 4: "Jue", 5: "Vie", 6: "Sáb", 7: "Dom" };

export function formatearHorarios(horarios) {
    if (!horarios || horarios.length === 0) return "Sin horario definido";
    return horarios
        .map((h) => `${NOMBRES_DIAS[h.diaSemana] ?? "?"} ${h.horaInicio}-${h.horaFin}`)
        .join(", ");
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
// Correquisitos: deben estar ya aprobados o matriculados junto con este curso
// dentro de la misma solicitud. También se valida choque de horario contra lo
// seleccionado y contra lo que el estudiante ya tenga matriculado en el período.
export function validarSeleccion({
    oferta,
    requisitosPorCurso,
    cursosAprobados,
    idsGruposSeleccionados,
    gruposYaMatriculados = [],
}) {
    const seleccionados = oferta.filter((g) => idsGruposSeleccionados.includes(g.idGrupo));
    const idsCursosSeleccionados = seleccionados.map((g) => g.idCurso);
    const erroresPorGrupo = {};

    seleccionados.forEach((grupo) => {
        const errores = [];
        const requisitos = requisitosPorCurso[grupo.idCurso] ?? [];

        requisitos.forEach((req) => {
            const cursoRequerido = oferta.find((g) => g.idCurso === req.idCursoRequerido);
            const nombreRequerido = cursoRequerido?.nombreCurso ?? `curso #${req.idCursoRequerido}`;
            const yaAprobado = cursosAprobados.includes(req.idCursoRequerido);

            if (req.tipo === "REQUISITO" && !yaAprobado) {
                errores.push(`Prerrequisito incumplido: debe haber aprobado "${nombreRequerido}".`);
            }

            if (req.tipo === "CORREQUISITO" && !yaAprobado && !idsCursosSeleccionados.includes(req.idCursoRequerido)) {
                errores.push(`Correquisito incumplido: debe matricular junto con "${nombreRequerido}".`);
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
    cursosAprobados,
    oferta,
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
        (req) => req.tipo === "REQUISITO" && !cursosAprobados.includes(req.idCursoRequerido)
    );

    if (prerrequisitoFaltante) {
        const cursoRequerido = oferta.find((g) => g.idCurso === prerrequisitoFaltante.idCursoRequerido);
        const nombreRequerido = cursoRequerido?.nombreCurso ?? `curso #${prerrequisitoFaltante.idCursoRequerido}`;
        return `Prerrequisito incumplido: debe haber aprobado "${nombreRequerido}".`;
    }

    return null;
}
