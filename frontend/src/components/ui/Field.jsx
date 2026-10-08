import { CircleX } from "lucide-react";

/*
 * Campo del sistema de diseño (sg-field): etiqueta visible, ayuda arriba del
 * control y error con ícono debajo. El control va como hijo y debe tener el
 * mismo id que se pasa aquí (para el <label htmlFor> y el resumen de errores).
 * optional añade «(opcional)» a la etiqueta.
 */
export default function Field({ id, label, optional = false, hint, error, errorKey, className = "", children }) {
    return (
        <div className={`sg-field${error ? " has-error" : ""} ${className}`.trim()} data-error-field={errorKey || id}>
            {label && (
                <label className="sg-field__label" htmlFor={id}>
                    {label}
                    {optional && <span className="sg-field__optional"> (opcional)</span>}
                </label>
            )}
            {hint && <p className="sg-field__hint" id={id ? `${id}-hint` : undefined}>{hint}</p>}
            <div className="sg-input-wrap">{children}</div>
            {error && (
                <p className="sg-field__error" id={id ? `${id}-error` : undefined}>
                    <CircleX size={16} strokeWidth={1.75} aria-hidden="true" />
                    {error}
                </p>
            )}
        </div>
    );
}
