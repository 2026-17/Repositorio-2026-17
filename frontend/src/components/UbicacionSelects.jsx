import { useEffect, useState } from "react";
import { API_URL } from "../config/api";
import Field from "./ui/Field";
import "./ubicacion.css";

// Selectores en cascada provincia -> cantón -> distrito (catálogos de la BD).
// value: { idProvincia, codigoCanton, codigoDistrito } (strings, "" = sin elegir)
// onChange recibe el objeto completo; al cambiar un nivel se limpian los de abajo.
// Lo que se guarda en el backend es solo codigoDistrito.
// idPrefix: prefijo de los ids de los <select> (para enlazar el resumen de errores).

function cargar(ruta) {
  const token = localStorage.getItem("token");
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  return fetch(`${API_URL}/registro/catalogos/${ruta}`, { headers })
    .then((r) => (r.ok ? r.json() : Promise.reject(new Error("No se pudo cargar el catálogo de ubicaciones."))));
}

// Carga la lista hija del nivel elegido. Guarda junto con la lista a qué
// "padre" pertenece, así nunca se muestran opciones de una selección anterior.
function useListaHija(padre, ruta) {
  const [estado, setEstado] = useState({ padre: "", lista: [] });

  useEffect(() => {
    if (!padre) return undefined;
    let cancelado = false;
    cargar(ruta(padre))
      .then((lista) => { if (!cancelado) setEstado({ padre, lista }); })
      .catch(() => { if (!cancelado) setEstado({ padre, lista: [] }); });
    return () => { cancelado = true; };
  }, [padre, ruta]);

  return padre && estado.padre === padre ? estado.lista : [];
}

const rutaCantones = (idProvincia) => `provincias/${idProvincia}/cantones`;
const rutaDistritos = (codigoCanton) => `cantones/${codigoCanton}/distritos`;

export default function UbicacionSelects({ value, onChange, errors = {}, required = false, idPrefix = "" }) {
  const [provincias, setProvincias] = useState([]);
  const cantones = useListaHija(value.idProvincia, rutaCantones);
  const distritos = useListaHija(value.codigoCanton, rutaDistritos);

  useEffect(() => {
    cargar("provincias").then(setProvincias).catch(() => setProvincias([]));
  }, []);

  const select = (campo, etiqueta, opciones, deshabilitado, alCambiar) => {
    const id = `${idPrefix}${campo}`;
    return (
      <Field id={id} errorKey={campo} label={etiqueta} optional={!required} error={errors[campo]} select>
        <select
          id={id}
          className="sg-input sg-select"
          value={value[campo]}
          disabled={deshabilitado}
          onChange={(e) => alCambiar(e.target.value)}
          aria-invalid={errors[campo] ? "true" : undefined}
          aria-describedby={errors[campo] ? `${id}-error` : undefined}
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
      {select("codigoCanton", "Cantón", cantones, !value.idProvincia,
        (v) => onChange({ ...value, codigoCanton: v, codigoDistrito: "" }))}
      {select("codigoDistrito", "Distrito", distritos, !value.codigoCanton,
        (v) => onChange({ ...value, codigoDistrito: v }))}
    </div>
  );
}
