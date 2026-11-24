import Field from "../../components/ui/Field";

/*
 * Cursos - Edad mínima y máxima (opcionales, regla D7). Años cumplidos al
 * inicio del período; vacío = sin límite. Fuera del rango, el estudiante ve
 * el grupo bloqueado en su matrícula.
 *
 * Props: form (con edadMinima y edadMaxima como texto), errores,
 * onChange (el handleChange del formulario, usa name), aria(campo), disabled.
 */
export default function CamposEdadCurso({ form, errores, onChange, aria, disabled = false }) {
    return (
        <>
            <Field id="edadMinima" label="Edad mínima" optional hint="Años cumplidos al inicio del período." error={errores.edadMinima}>
                <input id="edadMinima" name="edadMinima" type="number" className="sg-input" min="0" max="120" step="1" inputMode="numeric" value={form.edadMinima} onChange={onChange} disabled={disabled} placeholder="Sin mínimo" {...aria("edadMinima")} />
            </Field>
            <Field id="edadMaxima" label="Edad máxima" optional hint="Ej.: 5 = solo menores de 6 años." error={errores.edadMaxima}>
                <input id="edadMaxima" name="edadMaxima" type="number" className="sg-input" min="0" max="120" step="1" inputMode="numeric" value={form.edadMaxima} onChange={onChange} disabled={disabled} placeholder="Sin máximo" {...aria("edadMaxima")} />
            </Field>
        </>
    );
}
