import { CircleCheck, Info, TriangleAlert, X } from "lucide-react";

const ICONOS = { success: CircleCheck, info: Info, warning: TriangleAlert };

// Aviso breve (role="status"), no bloquea. tone: success | info | warning.
export default function Toast({ tone = "success", onDismiss, children }) {
    const Icono = ICONOS[tone] || Info;
    return (
        <div className={`sg-toast sg-toast--${tone}`} role="status" aria-live="polite">
            <Icono size={20} strokeWidth={1.75} className="sg-toast__icon" aria-hidden="true" />
            <p className="sg-toast__text">{children}</p>
            {onDismiss && (
                <button type="button" className="sg-icon-btn" aria-label="Cerrar notificación" onClick={onDismiss}>
                    <X size={16} strokeWidth={1.75} aria-hidden="true" />
                </button>
            )}
        </div>
    );
}
