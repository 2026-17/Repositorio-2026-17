import { useEffect, useState } from "react";

/*
 * Validación en vivo de que un valor (por ejemplo, un código) no esté
 * en uso. Espera una pausa al escribir antes de consultar.
 *
 *   const estado = useDisponibilidad(codigo, (valor, signal) => fetch(...).then(r => r.json()).then(d => d.codigoDisponible));
 *
 * consultar(valor, signal) debe resolver true (libre) o false (ocupado).
 * Devuelve:
 *   undefined     → valor vacío, o la consulta falló (lo valida el backend al guardar)
 *   "consultando" → aún no hay respuesta para el valor escrito
 *   "libre" | "ocupado"
 *
 * El estado se calcula durante el render comparando la última respuesta
 * con el valor actual, así el efecto solo guarda respuestas.
 */
export function useDisponibilidad(valor, consultar, { espera = 400 } = {}) {
    const texto = (valor || "").trim();
    const [respuesta, setRespuesta] = useState(null); // { valor, libre, fallo }

    useEffect(() => {
        if (!texto) return undefined;

        const control = new AbortController();
        const temporizador = setTimeout(() => {
            Promise.resolve(consultar(texto, control.signal))
                .then((libre) => setRespuesta({ valor: texto, libre: Boolean(libre), fallo: typeof libre !== "boolean" }))
                .catch((error) => {
                    if (error?.name !== "AbortError") setRespuesta({ valor: texto, fallo: true });
                });
        }, espera);

        return () => {
            clearTimeout(temporizador);
            control.abort();
        };
        // consultar se define en cada render del formulario; solo importa el valor.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [texto, espera]);

    if (!texto) return undefined;
    if (!respuesta || respuesta.valor !== texto) return "consultando";
    if (respuesta.fallo) return undefined;
    return respuesta.libre ? "libre" : "ocupado";
}
