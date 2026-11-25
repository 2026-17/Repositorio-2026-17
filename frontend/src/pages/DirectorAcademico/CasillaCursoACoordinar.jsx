/*
 * Casilla "Horario y profesor a coordinar en Dirección" (Crear y Editar
 * curso). Por ejemplo, Música de Cámara: sus grupos se publican sin
 * horario ni profesor y la Dirección le asigna el profesor a cada
 * estudiante en «Cursos a coordinar».
 */
export default function CasillaCursoACoordinar({ checked, onChange, disabled = false }) {
    return (
        <label className={`choice span-all${checked ? " is-checked" : ""}${disabled ? " is-disabled" : ""}`}>
            <input
                type="checkbox"
                name="aCoordinar"
                checked={Boolean(checked)}
                disabled={disabled}
                onChange={(e) => onChange(e.target.checked)}
                aria-describedby="aCoordinar-ayuda"
            />
            <span className="choice-main">
                <b>Horario y profesor a coordinar en Dirección</b>
                <span id="aCoordinar-ayuda">
                    Los grupos de este curso pueden publicarse sin horario ni profesor. La Dirección asigna el
                    profesor a cada estudiante.
                </span>
            </span>
        </label>
    );
}
