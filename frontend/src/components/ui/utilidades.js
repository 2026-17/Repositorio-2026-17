// Ayudantes de los componentes del sistema de diseño (src/components/ui).

// Iniciales para los avatares: "María Fernanda Solís" → "MF".
export function iniciales(nombre) {
    const partes = String(nombre || "").trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) return "?";
    return ((partes[0][0] || "") + (partes[1]?.[0] || "")).toUpperCase();
}

/*
 * Convierte un objeto { campo: mensaje } en la lista para el resumen de
 * errores. Cada enlace apunta al id del control (prefijo + campo).
 */
export function listaErrores(errores, prefijo = "") {
    return Object.entries(errores || {})
        .filter(([, texto]) => texto)
        .map(([campo, texto]) => ({ href: `#${prefijo}${campo}`, text: texto }));
}

// Devuelve la porción de la lista para la página indicada.
export function paginar(lista, page, pageSize) {
    return lista.slice((page - 1) * pageSize, page * pageSize);
}
