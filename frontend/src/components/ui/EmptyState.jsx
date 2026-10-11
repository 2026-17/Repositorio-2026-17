import { Search } from "lucide-react";

// Reemplaza a la tabla o lista vacía: explica por qué y ofrece una salida.
export default function EmptyState({ icon: Icono = Search, title, description, action, compact = false }) {
    return (
        <div className={`sg-empty${compact ? " sg-empty--compact" : ""}`}>
            <span className="sg-empty__mark"><Icono size={24} strokeWidth={1.75} aria-hidden="true" /></span>
            <p className="sg-empty__title">{title}</p>
            {description && <p className="sg-empty__desc">{description}</p>}
            {action && <div className="sg-empty__action">{action}</div>}
        </div>
    );
}
