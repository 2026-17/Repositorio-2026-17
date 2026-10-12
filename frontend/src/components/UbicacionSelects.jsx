import { useEffect, useState } from "react";
import { API_URL } from "../config/api";
import { lanzarError, mensajeDeExcepcion } from "../utils/api";
import Field from "./ui/Field";
import "./ubicacion.css";

// Selectores en cascada provincia -> cantón -> distrito (catálogos de la BD).
// value: { idProvincia, codigoCanton, codigoDistrito } (strings, "" = sin elegir)
// onChange recibe el objeto completo; al cambiar un nivel se limpian los de abajo.
// Lo que se guarda en el backend es solo codigoDistrito.
// idPrefix: prefijo de los ids de los <select> (para enlazar el resumen de errores).

function cargar(ruta, mensaje) {
  const token = localStorage.getItem("token");
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  return fetch(`${API_URL}/registro/catalogos/${ruta}`, { headers })
    .then((r) => (r.ok ? r.json() : lanzarError(r, mensaje)));
}

// Carga la lista hija del nivel elegido. Guarda junto con la lista a qué
// "padre" pertenece, así nunca se muestran opciones de una selección anterior.
// Si la carga falla, devuelve el mensaje en "error" para mostrarlo bajo el select.
function useListaHija(padre, ruta, mensaje) {
  const [estado, setEstado] = useState({ padre: "", lista: [], error: "" });

  useEffect(() => {
    if (!padre) return undefined;
    let cancelado = false;
    cargar(ruta(padre), mensaje)
      .then((lista) => { if (!cancelado) setEstado({ padre, lista, error: "" }); })
      .catch((e) => { if (!cancelado) setEstado({ padre, lista: [], error: mensajeDeExcepcion(e, mensaje) }); });
    return () => { cancelado = true; };
  }, [padre, ruta, mensaje]);

  return padre && estado.padre === padre ? estado : { lista: [], error: "" };
}

const rutaCantones = (idProvincia) => `provincias/${idProvincia}/cantones`;
const rutaDistritos = (codigoCanton) => `cantones/${codigoCanton}/distritos`;

const MENSAJE_PROVINCIAS = "No se pudieron cargar las provincias.";
const MENSAJE_CANTONES = "No se pudieron cargar los cantones de esa provincia.";
const MENSAJE_DISTRITOS = "No se pudieron cargar los distritos de ese cantón.";

export default function UbicacionSelects({ value, onChange, errors = {}, required = false, idPrefix = "" }) {
  const [provincias, setProvincias] = useState([]);
  const [errorProvincias, setErrorProvincias] = useState("");
  const cantones = useListaHija(value.idProvincia, rutaCantones, MENSAJE_CANTONES);
  const distritos = useListaHija(value.codigoCanton, rutaDistritos, MENSAJE_DISTRITOS);

  useEffect(() => {
    cargar("provincias", MENSAJE_PROVINCIAS)
      .then(setProvincias)
      .catch((e) => {
        setProvincias([]);
        setErrorProvincias(mensajeDeExcepcion(e, MENSAJE_PROVINCIAS));
      });
  }, []);

  // El error de validación del formulario tiene prioridad sobre el de carga.
  const errorCarga = {
    idProvincia: errorProvincias,
    codigoCanton: cantones.error,
    codigoDistrito: distritos.error,
  };

  const select = (campo, etiqueta, opciones, deshabilitado, alCambiar) => {
    const id = `${idPrefix}${campo}`;
    const error = errors[campo] || errorCarga[campo];
    return (
      <Field id={id} errorKey={campo} label={etiqueta} optional={!required} error={error} select>
        <select
          id={id}
          className="sg-input sg-select"
          value={value[campo]}
          disabled={deshabilitado}
          onChange={(e) => alCambiar(e.target.value)}
          aria-invalid={error ? "true" : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
        >
          <option value="">Seleccione</option>
          {opciones.map((o) => <option key={o.id} value={o.id}>{o.nombre}</option>)}
        </select>
      </Field>
    );
  };

  return (
    <div className="ubicacion-grid">
      {select("idProvincia", "Provincia", provincias, false,
        (v) => onChange({ idProvincia: v, codigoCanton: "", codigoDistrito: "" }))}
      {select("codigoCanton", "Cantón", cantones.lista, !value.idProvincia,
        (v) => onChange({ ...value, codigoCanton: v, codigoDistrito: "" }))}
      {select("codigoDistrito", "Distrito", distritos.lista, !value.codigoCanton,
        (v) => onChange({ ...value, codigoDistrito: v }))}
    </div>
  );
}
