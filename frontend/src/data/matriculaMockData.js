// HU-002 / Issue #61 - Selección de cursos para matrícula.
//
// El backend real de matrícula (grupos con horario/cupo/profesor, requisitos_cursos,
// autorizaciones_matricula y matriculas/matriculas_detalles) todavía no existe en
// Java, solo está modelado en sigac_db.sql. Este módulo simula esas tablas y las
// funciones de acceso simulan las llamadas de red que existirán a futuro, para que
// integrar el backend real después sea solo reemplazar el cuerpo de cada función.

const RETRASO_RED_MS = 350;

function esperar(ms = RETRASO_RED_MS) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

// TODO(backend): reemplazar por el estudiante resuelto a partir del usuario
// autenticado (hoy no existe GET /estudiantes/me ni el login devuelve idEstudiante).
const ESTUDIANTE_ACTUAL = {
    id: 1,
    numeroIdentificacion: "118760345",
    nombreCompleto: "María José Rodríguez Solano",
    correo: "maria.rodriguez@est.una.ac.cr",
    activo: true,
    idPlanEstudio: 10,
    nombrePlanEstudio: "Bachillerato en Ingeniería en Sistemas",
    // Cursos que el estudiante ya aprobó, usados para validar prerrequisitos.
    cursosAprobados: [101, 102, 103],
};

// TODO(backend): reemplazar por GET /periodos-academicos?estado=MATRICULA
// (hoy /periodos-academicos existe pero devuelve todos los periodos sin filtrar).
const PERIODO_EN_MATRICULA = {
    id: 5,
    codigo: "2026-2",
    nombre: "II Ciclo 2026",
    fechaInicio: "2026-07-06",
    fechaFin: "2026-11-27",
    fechaInicioMatricula: "2026-09-08",
    fechaFinMatricula: "2026-09-19",
    estado: "MATRICULA",
};

// Catálogo de cursos usado para resolver nombre/código/nivel dentro de la oferta.
const CURSOS = {
    101: { codigo: "PG-101", nombre: "Programación I", nivel: 1 },
    102: { codigo: "MA-102", nombre: "Matemática I", nivel: 1 },
    103: { codigo: "IN-103", nombre: "Inglés I", nivel: 1 },
    104: { codigo: "PG-104", nombre: "Programación II", nivel: 2 },
    105: { codigo: "ED-105", nombre: "Estructuras de Datos", nivel: 3 },
    106: { codigo: "BD-106", nombre: "Base de Datos I", nivel: 3 },
    107: { codigo: "MA-107", nombre: "Matemática II", nivel: 2 },
    108: { codigo: "FI-108", nombre: "Física I", nivel: 2 },
    109: { codigo: "RC-109", nombre: "Redes de Computadoras", nivel: 4 },
    110: { codigo: "PC-110", nombre: "Programación Concurrente", nivel: 4 },
    111: { codigo: "IN-111", nombre: "Inglés II", nivel: 2 },
};

// TODO(backend): reemplazar por GET /preaprobaciones-matricula?idEstudiante={id}&idPeriodo={idPeriodo}
// Registrada por el administrador académico: habilita al estudiante a matricular
// en este período y define qué cursos puede seleccionar.
const PREAPROBACION_MATRICULA = {
    idEstudiante: 1,
    idPeriodo: 5,
    habilitado: true,
    idsCursosAutorizados: [104, 105, 106, 107, 108, 109, 110, 111],
};

// TODO(backend): reemplazar por GET /matriculas?idEstudiante={id}&idPeriodo={idPeriodo}
// Cursos que el estudiante ya tiene matriculados en el período vigente (de una
// solicitud anterior ya confirmada), usados para bloquear duplicados y choques
// de horario contra la nueva selección.
const MATRICULAS_VIGENTES = [{ idEstudiante: 1, idPeriodo: 5, idGrupo: 1008 }];

// TODO(backend): reemplazar por GET /requisitos-cursos?idPlanEstudio={idPlanEstudio}
// tipo: "REQUISITO" (prerrequisito, debe estar ya aprobado) o "CORREQUISITO"
// (debe matricularse en el mismo periodo, o ya estar aprobado).
const REQUISITOS_POR_CURSO = {
    104: [{ tipo: "REQUISITO", idCursoRequerido: 101 }],
    105: [{ tipo: "REQUISITO", idCursoRequerido: 104 }],
    106: [{ tipo: "REQUISITO", idCursoRequerido: 104 }],
    108: [{ tipo: "CORREQUISITO", idCursoRequerido: 107 }],
    109: [{ tipo: "REQUISITO", idCursoRequerido: 106 }],
    110: [{ tipo: "REQUISITO", idCursoRequerido: 105 }],
};

// TODO(backend): reemplazar por GET /grupos?idPeriodo={idPeriodo} (join con
// horarios_grupos y con el nombre del profesor, ya resuelto aquí para el mock).
const GRUPOS = [
    {
        idGrupo: 1001,
        idPeriodo: 5,
        idCurso: 104,
        codigo: "PG-104-01",
        profesor: "Carlos Jiménez Vargas",
        modalidad: "PRESENCIAL",
        cupoMaximo: 25,
        cupoDisponible: 4,
        horarios: [
            { diaSemana: 1, horaInicio: "07:00", horaFin: "09:00" },
            { diaSemana: 3, horaInicio: "07:00", horaFin: "09:00" },
        ],
    },
    {
        idGrupo: 1002,
        idPeriodo: 5,
        idCurso: 105,
        codigo: "ED-105-01",
        profesor: "Ana Lucía Mora",
        modalidad: "PRESENCIAL",
        cupoMaximo: 20,
        cupoDisponible: 0,
        horarios: [
            { diaSemana: 2, horaInicio: "09:00", horaFin: "11:00" },
            { diaSemana: 4, horaInicio: "09:00", horaFin: "11:00" },
        ],
    },
    {
        idGrupo: 1003,
        idPeriodo: 5,
        idCurso: 106,
        codigo: "BD-106-01",
        profesor: "Luis Fernando Castro",
        modalidad: "PRESENCIAL",
        cupoMaximo: 22,
        cupoDisponible: 6,
        horarios: [
            { diaSemana: 1, horaInicio: "09:00", horaFin: "11:00" },
            { diaSemana: 3, horaInicio: "09:00", horaFin: "11:00" },
        ],
    },
    {
        idGrupo: 1004,
        idPeriodo: 5,
        idCurso: 107,
        codigo: "MA-107-01",
        profesor: "Gabriela Solís",
        modalidad: "PRESENCIAL",
        cupoMaximo: 30,
        cupoDisponible: 12,
        horarios: [
            { diaSemana: 2, horaInicio: "07:00", horaFin: "09:00" },
            { diaSemana: 4, horaInicio: "07:00", horaFin: "09:00" },
        ],
    },
    {
        idGrupo: 1005,
        idPeriodo: 5,
        idCurso: 108,
        codigo: "FI-108-01",
        profesor: "Manuel Herrera",
        modalidad: "PRESENCIAL",
        cupoMaximo: 25,
        cupoDisponible: 10,
        horarios: [{ diaSemana: 2, horaInicio: "13:00", horaFin: "15:00" }],
    },
    {
        idGrupo: 1006,
        idPeriodo: 5,
        idCurso: 109,
        codigo: "RC-109-01",
        profesor: "Diego Ramírez",
        modalidad: "PRESENCIAL",
        cupoMaximo: 20,
        cupoDisponible: 8,
        horarios: [{ diaSemana: 5, horaInicio: "13:00", horaFin: "16:00" }],
    },
    {
        idGrupo: 1007,
        idPeriodo: 5,
        idCurso: 110,
        codigo: "PC-110-01",
        profesor: "Karina Zúñiga",
        modalidad: "PRESENCIAL",
        cupoMaximo: 15,
        cupoDisponible: 0,
        horarios: [{ diaSemana: 4, horaInicio: "15:00", horaFin: "18:00" }],
    },
    {
        idGrupo: 1008,
        idPeriodo: 5,
        idCurso: 111,
        codigo: "IN-111-01",
        profesor: "Silvia Vargas",
        modalidad: "VIRTUAL",
        cupoMaximo: 20,
        cupoDisponible: 15,
        // Mismo horario que el grupo 1001, a propósito para poder demostrar
        // la validación de choque de horario entre cursos seleccionados.
        horarios: [{ diaSemana: 1, horaInicio: "07:00", horaFin: "09:00" }],
    },
];

export async function obtenerEstudianteActual() {
    await esperar();
    return structuredClone(ESTUDIANTE_ACTUAL);
}

export async function obtenerPeriodoEnMatricula() {
    await esperar();
    return structuredClone(PERIODO_EN_MATRICULA);
}

export async function obtenerOfertaCursos(idPeriodo) {
    await esperar();
    return GRUPOS.filter((grupo) => grupo.idPeriodo === idPeriodo).map((grupo) => {
        const curso = CURSOS[grupo.idCurso] ?? { codigo: "—", nombre: "Curso desconocido", nivel: null };
        return {
            ...structuredClone(grupo),
            codigoCurso: curso.codigo,
            nombreCurso: curso.nombre,
            nivel: curso.nivel,
        };
    });
}

export async function obtenerRequisitosPorPlan() {
    await esperar();
    return structuredClone(REQUISITOS_POR_CURSO);
}

export async function obtenerPreaprobacion(idEstudiante, idPeriodo) {
    await esperar();
    if (PREAPROBACION_MATRICULA.idEstudiante !== idEstudiante || PREAPROBACION_MATRICULA.idPeriodo !== idPeriodo) {
        return { idEstudiante, idPeriodo, habilitado: false, idsCursosAutorizados: [] };
    }
    return structuredClone(PREAPROBACION_MATRICULA);
}

export async function obtenerMatriculasVigentes(idEstudiante, idPeriodo) {
    await esperar();
    return MATRICULAS_VIGENTES.filter(
        (m) => m.idEstudiante === idEstudiante && m.idPeriodo === idPeriodo
    ).map((m) => {
        const grupo = GRUPOS.find((g) => g.idGrupo === m.idGrupo);
        const curso = CURSOS[grupo.idCurso] ?? { codigo: "—", nombre: "Curso desconocido" };
        return {
            idGrupo: grupo.idGrupo,
            idCurso: grupo.idCurso,
            codigo: grupo.codigo,
            nombreCurso: curso.nombre,
            horarios: structuredClone(grupo.horarios),
        };
    });
}

// TODO(backend): reemplazar por POST /matriculas con
// { idEstudiante, idPeriodo, idsGrupos }. El backend real deberá validar y
// reservar el cupo de forma atómica; aquí se simula sobre el arreglo en memoria.
export async function enviarSolicitudMatricula({ idEstudiante, idPeriodo, idsGrupos }) {
    await esperar(600);

    const habilitado =
        PREAPROBACION_MATRICULA.idEstudiante === idEstudiante &&
        PREAPROBACION_MATRICULA.idPeriodo === idPeriodo &&
        PREAPROBACION_MATRICULA.habilitado;
    if (!habilitado) {
        throw new Error(
            "La habilitación para matricular ya no está vigente. Se requiere la revisión previa del administrador académico."
        );
    }

    const gruposSeleccionados = idsGrupos.map((idGrupo) => GRUPOS.find((g) => g.idGrupo === idGrupo));
    const sinCupo = gruposSeleccionados.find((grupo) => !grupo || grupo.cupoDisponible <= 0);
    if (sinCupo) {
        throw new Error(`El curso ${sinCupo.codigo ?? "seleccionado"} ya no tiene cupos disponibles. Actualice la selección.`);
    }

    gruposSeleccionados.forEach((grupo) => {
        grupo.cupoDisponible -= 1;
    });

    return {
        idSolicitud: Math.floor(Math.random() * 90000) + 10000,
        idPeriodo,
        estado: "PENDIENTE",
        fechaSolicitud: new Date().toISOString(),
        cursos: gruposSeleccionados.map((grupo) => ({
            idGrupo: grupo.idGrupo,
            codigo: grupo.codigo,
            cupoDisponible: grupo.cupoDisponible,
        })),
    };
}
