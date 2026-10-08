import { useState } from "react";
import { CircleX, Eye, EyeOff } from "lucide-react";

/*
 * Campo de contraseña con botón para mostrarla u ocultarla.
 * describedBy agrega otros ids a aria-describedby (p. ej. la lista de requisitos).
 */
export default function PasswordField({ id, label, hint, error, describedBy, value, onChange, autoComplete, placeholder }) {
    const [visible, setVisible] = useState(false);
    const hintId = hint ? `${id}-hint` : null;
    const errorId = error ? `${id}-error` : null;
    const descripcion = [hintId, errorId, describedBy].filter(Boolean).join(" ") || undefined;

    return (
        <div className={`sg-field${error ? " has-error" : ""}`} data-error-field={id}>
            <label className="sg-field__label" htmlFor={id}>{label}</label>
            {hint && <p className="sg-field__hint" id={hintId}>{hint}</p>}
            <div className="sg-input-wrap pass-wrap">
                <input
                    id={id}
                    className="sg-input"
                    type={visible ? "text" : "password"}
                    value={value}
                    onChange={onChange}
                    autoComplete={autoComplete}
                    placeholder={placeholder}
                    aria-invalid={error ? "true" : undefined}
                    aria-describedby={descripcion}
                />
                <button
                    type="button"
                    className="pass-toggle"
                    onClick={() => setVisible((v) => !v)}
                    aria-label={visible ? "Ocultar contraseña" : "Mostrar contraseña"}
                    aria-pressed={visible}
                >
                    {visible
                        ? <EyeOff size={18} strokeWidth={1.75} aria-hidden="true" />
                        : <Eye size={18} strokeWidth={1.75} aria-hidden="true" />}
                </button>
            </div>
            {error && (
                <p className="sg-field__error" id={errorId}>
                    <CircleX size={16} strokeWidth={1.75} aria-hidden="true" />
                    {error}
                </p>
            )}
        </div>
    );
}
