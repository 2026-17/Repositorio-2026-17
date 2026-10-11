import { Check } from "lucide-react";

// Indicador de pasos de un asistente (como el prototipo). actual empieza en 0.
export default function Stepper({ pasos, actual, etiqueta = "Pasos" }) {
    return (
        <>
            <ol className="stepper" aria-label={etiqueta}>
                {pasos.map((paso, i) => (
                    <li
                        key={paso}
                        className={i < actual ? "is-done" : i === actual ? "is-current" : undefined}
                        aria-current={i === actual ? "step" : undefined}
                    >
                        <span className="step-dot">
                            {i < actual ? <Check size={16} strokeWidth={2} aria-hidden="true" /> : i + 1}
                        </span>
                        <span className="step-label">
                            {paso}
                            {i < actual && <span className="sr-only"> (completo)</span>}
                        </span>
                    </li>
                ))}
            </ol>
            <p className="step-mobile" aria-hidden="true">
                Paso {actual + 1} de {pasos.length} · <span>{pasos[actual]}</span>
            </p>
        </>
    );
}
