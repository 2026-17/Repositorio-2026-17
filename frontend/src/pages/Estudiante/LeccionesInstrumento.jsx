import { CircleCheck, Music, TriangleAlert } from "lucide-react";

import Alert from "../../components/ui/Alert";
import StatusBadge from "../../components/ui/StatusBadge";
import { nombreDia, rangoHoras12 } from "../../utils/horas";
import { formatearHorarios } from "../../utils/matricula";

/*
 * Matrícula - Lecciones de instrumento (una sección por instrumento).
 *
 * El nivel no se elige: lo calcula el sistema (el siguiente Instrumento de
 * la malla en ese instrumento). El estudiante elige uno de los espacios
 * libres de su profesor asignado. Un espacio que choca con un curso
 * elegido o con la lección del otro instrumento se muestra deshabilitado
 * con el motivo.
 *
 * Props:
 * - lecciones: datos.leccionesInstrumento del backend.
 * - elegidos: { [idInstrumento]: idGrupo } (lo que el estudiante marcó).
 * - efectivos: { [idInstrumento]: idGrupo } (lo que sigue siendo válido).
 * - motivos: { [idGrupo]: texto } por qué un espacio no se puede elegir.
 * - onElegir(idInstrumento, idGrupo).
 */
function textoEspacio(espacio) {
    const h = espacio.horarios?.[0];
    if (!h) return "Horario por definir";
    return `${nombreDia(h.diaSemana)} · ${rangoHoras12(h.horaInicio, h.horaFin)}`;
}

function aulaEspacio(espacio) {
    return espacio.horarios?.[0]?.aula || espacio.aula || "Aula por definir";
}

export default function LeccionesInstrumento({ lecciones, efectivos, motivos, onElegir, numero = 1 }) {
    if (!lecciones || lecciones.length === 0) return null;

    const varios = lecciones.length > 1;

    return (
        <section className="panel bloque lecciones" aria-labelledby="t-lecciones">
            <div className="panel-head">
                <div>
                    <h2 id="t-lecciones">{numero}. {varios ? "Sus lecciones de instrumento" : "Su lección de instrumento"}</h2>
                    <p className="panel-head-sub">
                        {varios
                            ? "Elija un espacio semanal por cada instrumento. Cada instrumento avanza por separado, con su profesor."
                            : "Elija el espacio semanal en que recibirá su lección individual."}
                    </p>
                </div>
            </div>

            <div className="panel-body lecciones-lista">
                {lecciones.map((leccion) => {
                    const elegido = efectivos[leccion.idInstrumento];
                    const nombreGrupo = `espacio-${leccion.idInstrumento}`;

                    return (
                        <article className="leccion" key={leccion.idInstrumento} aria-labelledby={`leccion-${leccion.idInstrumento}`}>
                            <h3 className="leccion-titulo" id={`leccion-${leccion.idInstrumento}`}>
                                <Music size={18} strokeWidth={1.75} aria-hidden="true" />
                                {leccion.nombreInstrumento}
                                <StatusBadge tone={leccion.delPlan ? "primary" : "neutral"} icon={null}>
                                    {leccion.delPlan ? "Instrumento del plan" : "Instrumento extra"}
                                </StatusBadge>
                            </h3>

                            <dl className="leccion-datos">
                                <div>
                                    <dt>Curso que le corresponde</dt>
                                    <dd>
                                        <b>{leccion.nombreNivel ?? "—"}</b>
                                        <span>{leccion.motivoNivel}</span>
                                    </dd>
                                </div>
                                <div>
                                    <dt>{leccion.delPlan ? "Cuenta para su graduación" : "Avance"}</dt>
                                    <dd>
                                        <span>{leccion.delPlan
                                            ? "Es el instrumento de su plan de estudio."
                                            : "Solo avanza en sus niveles; no reemplaza al instrumento del plan."}</span>
                                    </dd>
                                </div>
                                <div>
                                    <dt>Su profesor asignado</dt>
                                    <dd><b>{leccion.profesor ?? "Sin asignar"}</b></dd>
                                </div>
                            </dl>

                            {leccion.matriculado ? (
                                <Alert tone="success" title="Ya tiene su espacio de este período">
                                    {leccion.matriculado.nombreCurso} · {formatearHorarios(leccion.matriculado.horarios)}
                                </Alert>
                            ) : !leccion.disponible ? (
                                <Alert tone={leccion.completado ? "info" : "warning"} title={leccion.completado ? "Niveles completos" : "No puede elegir espacio"}>
                                    {leccion.motivoNoDisponible}
                                </Alert>
                            ) : (
                                <fieldset className="fieldset">
                                    <legend className="leccion-legend">Espacios libres de {leccion.profesor}</legend>
                                    <div className="choice-list espacios-grid">
                                        {leccion.espaciosLibres.map((espacio) => {
                                            const motivo = motivos[espacio.idGrupo];
                                            const marcado = elegido === espacio.idGrupo;
                                            return (
                                                <label
                                                    key={espacio.idGrupo}
                                                    className={`choice opcion-espacio${marcado ? " is-checked" : ""}${motivo ? " is-disabled" : ""}`}
                                                >
                                                    <input
                                                        type="radio"
                                                        name={nombreGrupo}
                                                        value={espacio.idGrupo}
                                                        checked={marcado}
                                                        disabled={Boolean(motivo)}
                                                        onChange={() => onElegir(leccion.idInstrumento, espacio.idGrupo)}
                                                        aria-describedby={motivo ? `motivo-${espacio.idGrupo}` : undefined}
                                                    />
                                                    <span className="choice-main">
                                                        <b className="tnum">{textoEspacio(espacio)}</b>
                                                        <span>{aulaEspacio(espacio)}</span>
                                                        {motivo && (
                                                            <span className="espacio-motivo" id={`motivo-${espacio.idGrupo}`}>
                                                                <TriangleAlert size={16} strokeWidth={1.75} aria-hidden="true" />
                                                                {motivo}
                                                            </span>
                                                        )}
                                                        {marcado && (
                                                            <span className="espacio-elegido">
                                                                <CircleCheck size={16} strokeWidth={1.75} aria-hidden="true" />
                                                                Espacio elegido
                                                            </span>
                                                        )}
                                                    </span>
                                                </label>
                                            );
                                        })}
                                    </div>
                                    <p className="muted leccion-nota">
                                        Si ninguno de estos horarios le sirve, comuníquese con la administración de ACUSAB.
                                    </p>
                                </fieldset>
                            )}
                        </article>
                    );
                })}
            </div>
        </section>
    );
}
