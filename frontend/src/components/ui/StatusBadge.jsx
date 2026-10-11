import { CircleAlert, CircleCheck, Clock, Info } from "lucide-react";

const ICONOS = { success: CircleCheck, warning: Clock, danger: CircleAlert, info: Info };

// Estado con ícono + texto (nunca solo color).
// tone: success | warning | danger | info | neutral | primary. icon={null} lo quita.
export default function StatusBadge({ tone = "neutral", icon, children }) {
    const Icono = icon === null ? null : icon || ICONOS[tone];
    return (
        <span className={`sg-badge sg-badge--${tone}`}>
            {Icono && <Icono size={14} strokeWidth={2} aria-hidden="true" />}
            {children}
        </span>
    );
}
