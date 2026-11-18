import Field from "../../components/ui/Field";

/*
 * Campos del plan de estudio de un programa (versión, inicio de vigencia
 * y nota mínima). Los usa el asistente "Nuevo programa" y la edición de
 * un programa que no tiene plan vigente.
 *
 * prefijo: evita ids repetidos si hay dos formularios en la página.
 */
export default function CamposPlanEstudio({ plan, errores, onChange, prefijo = "" }) {
    const id = (campo) => `${prefijo}${campo}`;
    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${id(campo)}-error` : `${id(campo)}-hint`,
    });

    function cambiar(e) {
        onChange(e.target.name, e.target.value);
    }

    return (
        <div className="form-grid form-grid--3">
            <Field id={id("version")} errorKey={id("version")} label="Versión" hint="La primera suele ser 1.0." error={errores.version}>
                <input
                    id={id("version")}
                    name="version"
                    type="text"
                    className="sg-input tnum"
                    maxLength={20}
                    value={plan.version}
                    onChange={cambiar}
                    autoComplete="off"
                    {...aria("version")}
                />
            </Field>
            <Field id={id("fechaVigenciaDesde")} errorKey={id("fechaVigenciaDesde")} label="Vigente desde" hint="Desde cuándo rige el plan." error={errores.fechaVigenciaDesde}>
                <input
                    id={id("fechaVigenciaDesde")}
                    name="fechaVigenciaDesde"
                    type="date"
                    className="sg-input"
                    value={plan.fechaVigenciaDesde}
                    onChange={cambiar}
                    {...aria("fechaVigenciaDesde")}
                />
            </Field>
            <Field id={id("notaMinimaAprobacion")} errorKey={id("notaMinimaAprobacion")} label="Nota mínima de aprobación" hint="De 0 a 100." error={errores.notaMinimaAprobacion}>
                <input
                    id={id("notaMinimaAprobacion")}
                    name="notaMinimaAprobacion"
                    type="number"
                    inputMode="decimal"
                    min="0"
                    max="100"
                    step="0.01"
                    className="sg-input tnum"
                    value={plan.notaMinimaAprobacion}
                    onChange={cambiar}
                    {...aria("notaMinimaAprobacion")}
                />
            </Field>
        </div>
    );
}
