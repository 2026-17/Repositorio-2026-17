/*
 * Casilla "Se cursa en espacios de instrumento" (Crear y Editar curso).
 * Instrumento I a XIV no se abren como grupos: cada estudiante lleva su
 * nivel en un espacio individual de su profesor.
 */
export default function CasillaCursoInstrumento({ checked, onChange, disabled = false }) {
    return (
        <label className={`choice span-all${checked ? " is-checked" : ""}${disabled ? " is-disabled" : ""}`}>
            <input
                type="checkbox"
                name="esCursoInstrumento"
                checked={Boolean(checked)}
                disabled={disabled}
                onChange={(e) => onChange(e.target.checked)}
                aria-describedby="esCursoInstrumento-ayuda"
            />
            <span className="choice-main">
                <b>Se cursa en espacios de instrumento</b>
                <span id="esCursoInstrumento-ayuda">
                    Márquelo en Instrumento I a XIV. Estos cursos no se abren en Grupos: cada estudiante elige
                    un espacio individual de su profesor y el sistema le asigna el nivel que le corresponde.
                </span>
            </span>
        </label>
    );
}
