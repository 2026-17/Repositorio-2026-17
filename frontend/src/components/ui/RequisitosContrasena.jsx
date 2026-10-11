import { CircleCheck, CircleX } from "lucide-react";

/*
 * Lista de requisitos de la contraseña que se marca mientras se escribe.
 * requisitos: resultado de revisarContrasena (politicaContrasena.js).
 */
export default function RequisitosContrasena({ id, requisitos }) {
    return (
        <ul className="rules" id={id} aria-label="Requisitos de la contraseña">
            {requisitos.map((r) => (
                <li key={r.id} className={r.ok ? "ok" : ""}>
                    {r.ok
                        ? <CircleCheck size={16} strokeWidth={1.75} aria-hidden="true" />
                        : <CircleX size={16} strokeWidth={1.75} aria-hidden="true" />}
                    {r.texto}
                    <span className="sr-only">{r.ok ? " (cumplido)" : " (pendiente)"}</span>
                </li>
            ))}
        </ul>
    );
}
