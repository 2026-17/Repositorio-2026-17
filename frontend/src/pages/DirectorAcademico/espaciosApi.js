import { API_URL } from "../../config/api";
import { lanzarError } from "../../utils/api";

/*
 * Espacios de instrumento - acceso al backend.
 *
 * Los errores de creación por bloque vienen en error.errores como
 * [{ indice, mensaje }] (indice = posición en la lista enviada, desde 0).
 */
export async function solicitar(ruta, opciones = {}, porDefecto = "No fue posible completar la operación.") {
    const token = localStorage.getItem("token");
    const response = await fetch(`${API_URL}${ruta}`, {
        ...opciones,
        headers: {
            Authorization: `Bearer ${token}`,
            ...(opciones.body ? { "Content-Type": "application/json" } : {}),
        },
    });
    if (!response.ok) await lanzarError(response, porDefecto);
    return response.status === 204 ? null : response.json();
}

export function listarEspacios({ idPeriodo, idInstrumento, idProfesor }) {
    const params = new URLSearchParams();
    if (idPeriodo) params.set("idPeriodo", idPeriodo);
    if (idInstrumento) params.set("idInstrumento", idInstrumento);
    if (idProfesor) params.set("idProfesor", idProfesor);
    return solicitar(`/espacios-instrumento?${params}`, {}, "No fue posible cargar los espacios.");
}

export function crearEspacios(payload) {
    return solicitar("/espacios-instrumento", { method: "POST", body: JSON.stringify(payload) },
        "No fue posible crear los espacios.");
}

export function modificarEspacio(idGrupo, bloque) {
    return solicitar(`/espacios-instrumento/${idGrupo}`, { method: "PUT", body: JSON.stringify(bloque) },
        "No fue posible modificar el espacio.");
}

export function eliminarEspacio(idGrupo) {
    return solicitar(`/espacios-instrumento/${idGrupo}`, { method: "DELETE" },
        "No fue posible eliminar el espacio.");
}

export function nombreProfesor(p) {
    return [p.nombres, p.primerApellido, p.segundoApellido].filter(Boolean).join(" ");
}
