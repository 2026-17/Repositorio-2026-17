import { useEffect, useRef } from "react";
import { CircleAlert, CircleCheck, Info, TriangleAlert, X } from "lucide-react";

const ICONOS = { info: Info, success: CircleCheck, warning: TriangleAlert, danger: CircleAlert };

/*
 * Aviso en página. tone: info | success | warning | danger.
 * errors: [{ href, text }] lo convierte en resumen de errores con enlaces a
 * cada campo. autoFocus lo enfoca al aparecer (útil al enviar un formulario).
 */
export default function Alert({ tone = "info", title, errors, autoFocus = false, onDismiss, className = "", children }) {
    const ref = useRef(null);
    const Icono = ICONOS[tone];
    const esResumen = Array.isArray(errors) && errors.length > 0;

    useEffect(() => {
        if (autoFocus && ref.current) ref.current.focus();
    }, [autoFocus, errors]);

    return (
        <div
            ref={ref}
            className={`sg-alert sg-alert--${tone} ${className}`.trim()}
            role={tone === "danger" ? "alert" : "status"}
            tabIndex={autoFocus ? -1 : undefined}
        >
            <Icono size={20} strokeWidth={1.75} className="sg-alert__icon" aria-hidden="true" />
            <div className="sg-alert__body">
                {title && <p className="sg-alert__title">{title}</p>}
                {children && <div className="sg-alert__text">{children}</div>}
                {esResumen && (
                    <ul className="sg-alert__list">
                        {errors.map((e, i) => (
                            <li key={i}><a href={e.href}>{e.text}</a></li>
                        ))}
                    </ul>
                )}
            </div>
            {onDismiss && (
                <button type="button" className="sg-icon-btn" aria-label="Cerrar aviso" onClick={onDismiss}>
                    <X size={18} strokeWidth={1.75} aria-hidden="true" />
                </button>
            )}
        </div>
    );
}

