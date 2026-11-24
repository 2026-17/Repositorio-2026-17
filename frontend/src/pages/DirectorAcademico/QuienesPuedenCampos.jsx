import Field from "../../components/ui/Field";

/*
 * Agrupaciones - Bloque "¿Quiénes pueden matricularla?" (opcional).
 * Lo usan el catálogo y «Crear agrupación». Las edades son en años
 * cumplidos al inicio del período; vacío = sin límite.
 *
 * Props:
 * - idBase: prefijo de los id de los campos (deben ser únicos en la página).
 * - valor: { edadMinima: "", edadMaxima: "", soloInclusion: false }.
 * - onChange(nuevoValor).
 * - error: texto si las edades no son válidas.
 */
export default function QuienesPuedenCampos({ idBase, valor, onChange, error, disabled = false }) {
    const ayudaId = `${idBase}-ayuda`;

    return (
        <fieldset className="fieldset quienes-pueden" aria-describedby={ayudaId}>
            <legend className="sg-field__label">
                ¿Quiénes pueden matricularla? <span className="sg-field__optional">(opcional)</span>
            </legend>
            <p className="sg-field__hint" id={ayudaId}>
                Ej.: Coro pre solfeo, edad máxima 5 (menores de 6 años). Inclusión: solo estudiantes de inclusión.
                Las edades se cuentan al inicio del período.
            </p>
            <div className="quienes-pueden__edades">
                <Field id={`${idBase}-min`} label="Edad mínima" error={error}>
                    <input
                        id={`${idBase}-min`}
                        type="number"
                        min="0"
                        max="120"
                        step="1"
                        inputMode="numeric"
                        className="sg-input"
                        placeholder="Sin mínimo"
                        value={valor.edadMinima}
                        onChange={(e) => onChange({ ...valor, edadMinima: e.target.value })}
                        disabled={disabled}
                        aria-invalid={error ? "true" : undefined}
                        aria-describedby={error ? `${idBase}-min-error` : undefined}
                    />
                </Field>
                <Field id={`${idBase}-max`} label="Edad máxima">
                    <input
                        id={`${idBase}-max`}
                        type="number"
                        min="0"
                        max="120"
                        step="1"
                        inputMode="numeric"
                        className="sg-input"
                        placeholder="Sin máximo"
                        value={valor.edadMaxima}
                        onChange={(e) => onChange({ ...valor, edadMaxima: e.target.value })}
                        disabled={disabled}
                        aria-invalid={error ? "true" : undefined}
                    />
                </Field>
            </div>
            <label className="choice quienes-pueden__inclusion">
                <input
                    type="checkbox"
                    checked={valor.soloInclusion}
                    onChange={(e) => onChange({ ...valor, soloInclusion: e.target.checked })}
                    disabled={disabled}
                />
                <span>Solo estudiantes de inclusión</span>
            </label>
        </fieldset>
    );
}
