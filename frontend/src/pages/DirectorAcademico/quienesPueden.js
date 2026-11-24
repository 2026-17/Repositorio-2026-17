// Agrupaciones - Ayudantes del bloque "¿Quiénes pueden matricularla?" (QuienesPuedenCampos).

export function errorDeEdades({ edadMinima, edadMaxima }) {
    const min = edadMinima === "" ? null : Number(edadMinima);
    const max = edadMaxima === "" ? null : Number(edadMaxima);
    if ((min != null && (!Number.isInteger(min) || min < 0 || min > 120))
        || (max != null && (!Number.isInteger(max) || max < 0 || max > 120))) {
        return "Las edades deben ser números enteros entre 0 y 120.";
    }
    if (min != null && max != null && max < min) return "La edad máxima no puede ser menor que la mínima.";
    return null;
}

// Cursos: errores de edadMinima / edadMaxima del formulario ({} si están bien).
export function erroresEdadCurso(form) {
    const error = errorDeEdades({ edadMinima: form.edadMinima ?? "", edadMaxima: form.edadMaxima ?? "" });
    if (!error) return {};
    return error.includes("máxima no puede") ? { edadMaxima: error } : { edadMinima: error };
}

// "" -> null; texto -> número.
export function edadParaEnviar(valor) {
    return valor === "" || valor == null ? null : Number(valor);
}

// Para enviar al backend: "" -> null.
export function quienesPuedenParaEnviar({ edadMinima, edadMaxima, soloInclusion }) {
    return {
        edadMinima: edadMinima === "" ? null : Number(edadMinima),
        edadMaxima: edadMaxima === "" ? null : Number(edadMaxima),
        soloInclusion: Boolean(soloInclusion),
    };
}

export function quienesPuedenDesde(origen) {
    return {
        edadMinima: origen?.edadMinima != null ? String(origen.edadMinima) : "",
        edadMaxima: origen?.edadMaxima != null ? String(origen.edadMaxima) : "",
        soloInclusion: Boolean(origen?.soloInclusion),
    };
}

