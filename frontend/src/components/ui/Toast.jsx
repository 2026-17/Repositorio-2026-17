import { CircleCheck, Info, X } from "lucide-react";

// Confirmación breve (role="status"). Solo para éxito o información.
export default function Toast({ tone = "success", onDismiss, children }) {
    const Icono = tone === "success" ? CircleCheck : Info;
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
