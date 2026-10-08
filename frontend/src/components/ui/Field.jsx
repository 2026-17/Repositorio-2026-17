import { ChevronDown, CircleX } from "lucide-react";

/*
 * Campo del sistema de diseño (sg-field): etiqueta visible, ayuda arriba del
 * control y error con ícono debajo. El control va como hijo y debe tener el
 * mismo id que se pasa aquí (para el <label htmlFor> y el resumen de errores).
 *
 * - optional: añade «(opcional)» a la etiqueta.
 * - icon: ícono de lucide a la izquierda del control (p. ej. la lupa).
 * - select: dibuja la flecha del <select> (el control lleva "sg-input sg-select").
 */
export default function Field({ id, label, optional = false, hint, error, errorKey, icon: Icono, select = false, className = "", children }) {
    const envoltura = select
        ? "sg-select-wrap"
        : `sg-input-wrap${Icono ? " has-icon" : ""}`;

    return (
        <div className={`sg-field${error ? " has-error" : ""} ${className}`.trim()} data-error-field={errorKey || id}>
            {label && (
                <label className="sg-field__label" htmlFor={id}>
                    {label}
                    {optional && <span className="sg-field__optional"> (opcional)</span>}
                </label>
            )}
            {hint && <p className="sg-field__hint" id={id ? `${id}-hint` : undefined}>{hint}</p>}
            <div className={envoltura}>
                {Icono && <Icono size={18} strokeWidth={1.75} className="sg-input-wrap__icon" aria-hidden="true" />}
                {children}
                {select && <ChevronDown size={18} strokeWidth={1.75} className="sg-select-wrap__chev" aria-hidden="true" />}
            </div>
            {error && (
                <p className="sg-field__error" id={id ? `${id}-error` : undefined}>
                    <CircleX size={16} strokeWidth={1.75} aria-hidden="true" />
                    {error}
                </p>
            )}
        </div>
    );
}
