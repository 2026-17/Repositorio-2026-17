// Ayudantes del formulario de ubicación (ver UbicacionSelects.jsx).

export const UBICACION_VACIA = { idProvincia: "", codigoCanton: "", codigoDistrito: "" };

// Convierte la "ubicacion" que devuelve el backend al formato del formulario.
export function ubicacionDesdeRespuesta(ubicacion) {
  if (!ubicacion) return UBICACION_VACIA;
  return {
    idProvincia: String(ubicacion.idProvincia ?? ""),
    codigoCanton: String(ubicacion.codigoCanton ?? ""),
    codigoDistrito: String(ubicacion.codigoDistrito ?? ""),
  };
}
