// Códigos de rol tal como los envía el backend (ver entidad Role -> columna "codigo").
export const ROLES = {
    ADMINISTRADOR: "ADMINISTRADOR",
    DIRECTOR_ACADEMICO: "DIRECTOR_ACADEMICO",
    ADMINISTRATIVO: "ADMINISTRATIVO",
    PROFESOR: "PROFESOR",
    JUNTA_DIRECTIVA: "JUNTA_DIRECTIVA",
    ESTUDIANTE: "ESTUDIANTE",
};

// Nombre legible para mostrar en pantalla (por ejemplo, en el menú de usuario).
export const ROLE_LABELS = {
    [ROLES.ADMINISTRADOR]: "Administrador",
    [ROLES.DIRECTOR_ACADEMICO]: "Director Académico",
    [ROLES.ADMINISTRATIVO]: "Administrativo",
    [ROLES.PROFESOR]: "Profesor",
    [ROLES.JUNTA_DIRECTIVA]: "Junta Directiva",
    [ROLES.ESTUDIANTE]: "Estudiante",
};

export function etiquetaRol(codigoRol) {
    return ROLE_LABELS[codigoRol] || codigoRol || "Usuario";
}
