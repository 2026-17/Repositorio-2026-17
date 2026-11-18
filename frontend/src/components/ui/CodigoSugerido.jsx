import { CircleCheck, Loader2, Sparkles } from "lucide-react";
import "./codigoSugerido.css";

/*
 * Piezas que acompañan a un campo de código sugerido a partir del nombre.
 *
 * <AyudaCodigoSugerido>: insignia «Sugerido» mientras el código es el
 * propuesto, o el enlace «Usar la sugerencia» si el usuario escribió otro.
 *
 * <EstadoDisponibilidad>: «Verificando…», «Disponible» o «Ya existe».
 * Su id se usa en aria-describedby del campo.
 */
export function AyudaCodigoSugerido({ editado, codigo, sugerencia, onUsarSugerencia, children }) {
    return (
        <div className="codigo-ayuda">
            {!editado && codigo && (
                <span className="sg-badge sg-badge--primary">
                    <Sparkles size={14} strokeWidth={2} aria-hidden="true" /> Sugerido
                </span>
            )}
            {editado && sugerencia && sugerencia !== codigo && (
                <button type="button" className="link-btn" onClick={onUsarSugerencia}>
                    Usar la sugerencia «{sugerencia}»
                </button>
            )}
            {children}
        </div>
    );
}

export function EstadoDisponibilidad({ id, estado, que = "código", mensajeOcupado, className = "" }) {
    if (!estado) return <span id={id} hidden />;

    const etiqueta = que.charAt(0).toUpperCase() + que.slice(1);

    const contenido = {
        consultando: <><Loader2 size={14} strokeWidth={2} className="codigo-girando" aria-hidden="true" /> Verificando {que}…</>,
        libre: <><CircleCheck size={14} strokeWidth={2} aria-hidden="true" /> {etiqueta} disponible</>,
        ocupado: <>{mensajeOcupado || `Ya existe un registro con este ${que}.`}</>,
    }[estado];

    return (
        <p id={id} className={`codigo-disponibilidad is-${estado} ${className}`.trim()} aria-live="polite">
            {contenido}
        </p>
    );
}
