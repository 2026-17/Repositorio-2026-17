import Field from "../../components/ui/Field";
import "./requisitosCurso.css";

export default function CamposRequisitosCurso({ configuracion, setConfiguracion, guardando }) {
    function alternar(id, tipo) {
        const campo = tipo === "pre" ? "prerrequisitos" : "correquisitos";
        const actual = configuracion[campo] || [];
        setConfiguracion({ ...configuracion, [campo]: actual.includes(id)
            ? actual.filter(x => x !== id) : [...actual, id] });
    }

    return (
        <>
            <div className="form-section">
                <h3 className="form-section-title">Restricción de edad</h3>
                <p className="form-section-sub">Opcional. Use solo números enteros. Deje un campo vacío si no existe ese límite.</p>
                <div className="form-grid">
                    <Field id="req-edadMinima" label="Edad mínima" optional>
                        <input
                            id="req-edadMinima"
                            className="sg-input"
                            type="number"
                            min="0"
                            max="150"
                            step="1"
                            inputMode="numeric"
                            value={configuracion.edadMinima ?? ""}
                            onChange={(e) => setConfiguracion({
                                ...configuracion,
                                edadMinima: e.target.value === "" ? null : Number(e.target.value)
                            })}
                            disabled={guardando}
                            placeholder="Ej. 10"
                        />
                    </Field>
                    <Field id="req-edadMaxima" label="Edad máxima" optional>
                        <input
                            id="req-edadMaxima"
                            className="sg-input"
                            type="number"
                            min="0"
                            max="150"
                            step="1"
                            inputMode="numeric"
                            value={configuracion.edadMaxima ?? ""}
                            onChange={(e) => setConfiguracion({
                                ...configuracion,
                                edadMaxima: e.target.value === "" ? null : Number(e.target.value)
                            })}
                            disabled={guardando}
                            placeholder="Ej. 17"
                        />
                    </Field>
                </div>
            </div>

            <div className="form-section">
                <div className="requisitos-curso__grid">
                    {[["pre", "Prerrequisitos", "Cursos que el estudiante debe haber aprobado antes"], ["co", "Correquisitos", "Cursos que deben cursarse en simultáneo"]].map(([tipo, titulo, descripcion]) => {
                        const campo = tipo === "pre" ? "prerrequisitos" : "correquisitos";
                        const seleccionados = configuracion[campo] || [];
                        return (
                            <fieldset className="fieldset" key={tipo}>
                                <legend className="form-section-title">{titulo}</legend>
                                <p className="form-section-sub">{descripcion} · {seleccionados.length} seleccionado{seleccionados.length === 1 ? "" : "s"}</p>
                                <div className="choice-list requisitos-curso__lista">
                                    {configuracion.cursos.map((curso) => {
                                        const marcado = seleccionados.includes(curso.idCurso);
                                        return (
                                            <label className={`choice${marcado ? " is-checked" : ""}${guardando ? " is-disabled" : ""}`} key={`${tipo}-${curso.idCurso}`}>
                                                <input
                                                    type="checkbox"
                                                    checked={marcado}
                                                    onChange={() => alternar(curso.idCurso, tipo)}
                                                    disabled={guardando}
                                                />
                                                <span className="choice-main">
                                                    <b>{curso.nombre}</b>
                                                    <span>{[curso.codigo, curso.nivel != null ? `Nivel ${curso.nivel}` : null].filter(Boolean).join(" · ")}</span>
                                                </span>
                                            </label>
                                        );
                                    })}
                                    {configuracion.cursos.length === 0 && (
                                        <p className="muted requisitos-curso__vacio">No hay otros cursos disponibles en este plan.</p>
                                    )}
                                </div>
                            </fieldset>
                        );
                    })}
                </div>
            </div>
        </>
    );
}
