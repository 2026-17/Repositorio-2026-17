import "./requisitosCurso.css";

export default function CamposRequisitosCurso({ configuracion, setConfiguracion, guardando }) {
    function alternar(id, tipo) {
        const campo = tipo === "pre" ? "prerrequisitos" : "correquisitos";
        const actual = configuracion[campo] || [];
        setConfiguracion({ ...configuracion, [campo]: actual.includes(id)
            ? actual.filter(x => x !== id) : [...actual, id] });
    }
    return <>
                        <section className="requisitos-curso__edad">
                            <h2>Restricción de edad</h2>
                            <p>Opcional. Use solo números enteros. Deje un campo vacío si no existe ese límite.</p>
                            <div className="requisitos-curso__selectores">
                                <label>Edad mínima
                                    <input
                                        type="number"
                                        min="0"
                                        max="150"
                                        step="1"
                                        value={configuracion.edadMinima ?? ""}
                                        onChange={(e) => setConfiguracion({
                                            ...configuracion,
                                            edadMinima: e.target.value === "" ? null : Number(e.target.value)
                                        })}
                                        disabled={guardando}
                                        placeholder="Ej. 10"
                                    />
                                </label>
                                <label>Edad máxima
                                    <input
                                        type="number"
                                        min="0"
                                        max="150"
                                        step="1"
                                        value={configuracion.edadMaxima ?? ""}
                                        onChange={(e) => setConfiguracion({
                                            ...configuracion,
                                            edadMaxima: e.target.value === "" ? null : Number(e.target.value)
                                        })}
                                        disabled={guardando}
                                        placeholder="Ej. 17"
                                    />
                                </label>
                            </div>
                        </section>

                        <div className="requisitos-curso__grid">
                            {[["pre", "Prerrequisitos", "Cursos que el estudiante debe haber aprobado antes"], ["co", "Correquisitos", "Cursos que deben cursarse en simultáneo"]].map(([tipo, titulo, descripcion]) => (
                                <section key={tipo}>
                                    <h2>{titulo}</h2><p>{descripcion}</p>
                                    <div className="requisitos-curso__lista">
                                        {configuracion.cursos.map((curso) => {
                                            const campo = tipo === "pre" ? "prerrequisitos" : "correquisitos";
                                            return <label className="requisito-card" key={`${tipo}-${curso.idCurso}`}>
                                                <input type="checkbox"
                                                    checked={(configuracion[campo] || []).includes(curso.idCurso)}
                                                    onChange={() => alternar(curso.idCurso, tipo)}
                                                    disabled={guardando} />
                                                <span><strong>{curso.nombre}</strong><small>{curso.codigo} · Nivel {curso.nivel}</small></span>
                                            </label>;
                                        })}
                                        {configuracion.cursos.length === 0 && <div className="requisitos-curso__vacio">No hay otros cursos disponibles en este plan.</div>}
                                    </div>
                                </section>
                            ))}
                        </div>
    </>;
}
