import { solicitar } from "./espaciosApi";

/*
 * Cursos a coordinar en Dirección (Música de Cámara) - acceso al backend.
 * Reutiliza solicitar() de los espacios de instrumento.
 */

// Grupos del período con sus estudiantes matriculados.
export function listarCursosACoordinar(idPeriodo) {
    const params = idPeriodo ? `?idPeriodo=${encodeURIComponent(idPeriodo)}` : "";
    return solicitar(`/cursos-a-coordinar${params}`, {}, "No fue posible cargar los cursos a coordinar.");
}

// Profesor asignado (o null = sin asignar) y cómo lo cumplirá, de un estudiante.
export function guardarCoordinacion(idDetalle, { idProfesorAsignado, notasCoordinacion }) {
    return solicitar(`/cursos-a-coordinar/detalles/${idDetalle}`, {
        method: "PATCH",
        body: JSON.stringify({ idProfesorAsignado, notasCoordinacion }),
    }, "No fue posible guardar la coordinación.");
}

// Mismo endpoint que el formulario de grupos.
export function listarProfesoresActivosGrupo() {
    return solicitar("/profesores?activo=true", {}, "No fue posible cargar los profesores.");
}
