import { useEffect, useMemo, useState } from "react";
import { CircleAlert, Info, Lock, Plus, Trash2 } from "lucide-react";

import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import StatusBadge from "../../components/ui/StatusBadge";
import { useDialog } from "../../components/dialog/useDialog";
import { mensajeDeExcepcion } from "../../utils/api";
import { DIAS_SEMANA, aMinutos, desdeMinutos, hora12, nombreDia, rangoHoras12 } from "../../utils/horas";
import { crearEspacios, listarEspacios, nombreProfesor, solicitar } from "./espaciosApi";

// Misma franja que los grupos normales.
const HORA_MINIMA = "07:00";
const HORA_MAXIMA = "22:00";
const DURACION_POR_DEFECTO = 40;

const FILA_VACIA = { dia: "1", inicio: "", fin: "", aula: "" };

let siguienteClave = 1;
const nuevaFila = (datos = {}) => ({ clave: siguienteClave++, ...FILA_VACIA, ...datos });

function textoDuracion(minutos) {
    const h = Math.floor(minutos / 60);
    const m = minutos % 60;
    if (h === 0) return `${m} min`;
    return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

// Errores de cada fila que se pueden saber sin el servidor.
function erroresLocales(filas, existentes) {
    const errores = {};

    filas.forEach((f, i) => {
        if (!f.inicio || !f.fin) {
            errores[f.clave] = "Indique la hora de inicio y la de fin.";
        } else if (f.fin <= f.inicio) {
            errores[f.clave] = "La hora de fin debe ser posterior a la de inicio.";
        } else if (f.inicio < HORA_MINIMA || f.fin > HORA_MAXIMA) {
            errores[f.clave] = "Los espacios deben estar entre las 7:00 a. m. y las 10:00 p. m.";
        } else {
            const otra = filas.findIndex((g, j) => j < i && g.inicio && g.fin && g.dia === f.dia
                && f.inicio < g.fin && g.inicio < f.fin);
            if (otra >= 0) {
                errores[f.clave] = `Choca con el espacio ${otra + 1} de esta lista. Cambie la hora o quite este espacio.`;
                return;
            }
            const choque = existentes.find((e) => String(e.diaSemana) === f.dia
                && f.inicio < e.horaFin.slice(0, 5) && e.horaInicio.slice(0, 5) < f.fin);
            if (choque) {
                errores[f.clave] = `Choca con el espacio ${choque.codigo} del ${nombreDia(choque.diaSemana).toLowerCase()} `
                    + `de ${rangoHoras12(choque.horaInicio, choque.horaFin)} de este mismo profesor. Cambie la hora o quite este espacio.`;
            }
        }
    });

    return errores;
}

/*
 * Espacios de instrumento - Crear espacios (lote).
 * Cada fila es un espacio: lección individual, cupo 1, sin nivel.
 */
export default function CrearEspacios({ periodos, instrumentos, valoresIniciales, onCancel, onSuccess }) {
    const dialog = useDialog();

    const [idPeriodo, setIdPeriodo] = useState(valoresIniciales?.idPeriodo ?? "");
    const [idInstrumento, setIdInstrumento] = useState(valoresIniciales?.idInstrumento ?? "");
    const [idProfesor, setIdProfesor] = useState(valoresIniciales?.idProfesor ?? "");
    const [aula, setAula] = useState("");

    const [profesores, setProfesores] = useState([]);
    const [existentes, setExistentes] = useState([]);

    // Instrumentos que da el profesor elegido, y si los espacios son de cualquiera de ellos.
    const [suyos, setSuyos] = useState({ idProfesor: "", lista: [] });
    const [deCualquiera, setDeCualquiera] = useState(false);

    const [generador, setGenerador] = useState({ dia: "1", desde: "", hasta: "", duracion: String(DURACION_POR_DEFECTO) });
    const [filas, setFilas] = useState([]);
    const [erroresServidor, setErroresServidor] = useState({});
    const [errorGeneral, setErrorGeneral] = useState("");
    const [guardando, setGuardando] = useState(false);

    // Profesores activos con el instrumento como especialidad.
    useEffect(() => {
        if (!idInstrumento) return undefined;
        let activo = true;
        solicitar(`/profesores/por-instrumento/${idInstrumento}`, {}, "No fue posible cargar los profesores.")
            .then((lista) => { if (activo) setProfesores(Array.isArray(lista) ? lista : []); })
            .catch((e) => dialog.error(mensajeDeExcepcion(e, "No fue posible cargar los profesores.")));
        return () => { activo = false; };
    }, [idInstrumento, dialog]);

    // Espacios que el profesor ya tiene en el período (de cualquier instrumento),
    // para avisar los choques antes de enviar.
    useEffect(() => {
        if (!idPeriodo || !idProfesor) return undefined;
        let activo = true;
        listarEspacios({ idPeriodo, idProfesor })
            .then((lista) => { if (activo) setExistentes(Array.isArray(lista) ? lista : []); })
            .catch(() => { if (activo) setExistentes([]); });
        return () => { activo = false; };
    }, [idPeriodo, idProfesor]);

    useEffect(() => {
        if (!idProfesor) return undefined;
        let activo = true;
        solicitar(`/espacios-instrumento/profesores/${idProfesor}/instrumentos`, {}, "No fue posible cargar los instrumentos del profesor.")
            .then((lista) => { if (activo) setSuyos({ idProfesor: String(idProfesor), lista: Array.isArray(lista) ? lista : [] }); })
            .catch(() => { if (activo) setSuyos({ idProfesor: String(idProfesor), lista: [] }); });
        return () => { activo = false; };
    }, [idProfesor]);

    const instrumento = instrumentos.find((i) => String(i.id) === String(idInstrumento));
    const profesor = profesores.find((p) => String(p.id) === String(idProfesor));
    const periodo = periodos.find((p) => String(p.id) === String(idPeriodo));
    const conProfesor = Boolean(idPeriodo && idProfesor);

    // "Cualquiera de sus instrumentos" solo si el profesor da dos o más.
    const instrumentosProfesor = suyos.idProfesor === String(idProfesor) ? suyos.lista : [];
    const puedeVarios = instrumentosProfesor.length >= 2;
    const variosNombres = instrumentosProfesor.map((i) => i.nombre).join(" o ");
    const esDeCualquiera = deCualquiera && puedeVarios;
    const nombreInstrumentoEspacios = esDeCualquiera ? variosNombres : instrumento?.nombre;

    function cambiarProfesor(valor) {
        setIdProfesor(valor);
        setDeCualquiera(false);
    }

    const errores = useMemo(() => {
        const locales = erroresLocales(filas, conProfesor ? existentes : []);
        return { ...erroresServidor, ...locales };
    }, [filas, conProfesor, existentes, erroresServidor]);

    const conError = filas.filter((f) => errores[f.clave]).length;
    const minutosSemana = filas.reduce((total, f) =>
        total + (f.inicio && f.fin && f.fin > f.inicio ? aMinutos(f.fin) - aMinutos(f.inicio) : 0), 0);

    // Generador de espacios seguidos.
    const generados = useMemo(() => {
        const duracion = Number(generador.duracion);
        if (!generador.desde || !generador.hasta || !duracion || duracion < 10) return [];
        const lista = [];
        for (let m = aMinutos(generador.desde); m + duracion <= aMinutos(generador.hasta); m += duracion) {
            lista.push({ dia: generador.dia, inicio: desdeMinutos(m), fin: desdeMinutos(m + duracion) });
        }
        return lista;
    }, [generador]);

    function agregarGenerados() {
        setFilas((prev) => [...prev, ...generados.map((g) => nuevaFila(g))]);
        setErroresServidor({});
    }

    function cambiarFila(clave, campo, valor) {
        setFilas((prev) => prev.map((f) => (f.clave === clave ? { ...f, [campo]: valor } : f)));
        setErroresServidor((prev) => {
            const siguiente = { ...prev };
            delete siguiente[clave];
            return siguiente;
        });
    }

    function quitarFila(clave) {
        setFilas((prev) => prev.filter((f) => f.clave !== clave));
        setErroresServidor({});
    }

    function limpiar() {
        setFilas([]);
        setAula("");
        setErroresServidor({});
        setErrorGeneral("");
    }

    const faltaCabecera = !idPeriodo || !idInstrumento || !idProfesor;
    const primeraConError = filas.find((f) => errores[f.clave]);

    let motivoBloqueo = null;
    if (faltaCabecera) motivoBloqueo = "Elija el período, el instrumento y el profesor.";
    else if (filas.length === 0) motivoBloqueo = "Agregue al menos un espacio a la lista.";
    else if (primeraConError) {
        motivoBloqueo = `Corrija el espacio del ${nombreDia(primeraConError.dia).toLowerCase()}`
            + `${primeraConError.inicio ? ` a las ${hora12(primeraConError.inicio)}` : ""} para continuar.`;
    }

    async function crear() {
        if (motivoBloqueo || guardando) return;

        setGuardando(true);
        setErrorGeneral("");

        try {
            const creados = await crearEspacios({
                idPeriodo: Number(idPeriodo),
                // Sin instrumento = cualquiera de los del profesor.
                idInstrumento: esDeCualquiera ? null : Number(idInstrumento),
                idProfesor: Number(idProfesor),
                aula: aula.trim() || null,
                bloques: filas.map((f) => ({
                    diaSemana: Number(f.dia),
                    horaInicio: f.inicio,
                    horaFin: f.fin,
                    aula: f.aula.trim() || null,
                })),
            });

            await dialog.exito(`Se crearon ${creados.length} ${creados.length === 1 ? "espacio" : "espacios"} de ${nombreInstrumentoEspacios} para ${profesor ? nombreProfesor(profesor) : "el profesor"}.`);
            onSuccess({ idPeriodo, idInstrumento, idProfesor });
        } catch (e) {
            // Errores por fila: [{ indice, mensaje }]. No se creó ninguno.
            if (Array.isArray(e.errores) && e.errores.length > 0 && typeof e.errores[0] === "object") {
                const porClave = {};
                e.errores.forEach(({ indice, mensaje }) => {
                    if (filas[indice]) porClave[filas[indice].clave] = mensaje;
                });
                setErroresServidor(porClave);
                setErrorGeneral(`${e.errores.length === 1 ? "Un espacio tiene" : `${e.errores.length} espacios tienen`} errores. No se creó ninguno.`);
            } else {
                setErrorGeneral(mensajeDeExcepcion(e, "No fue posible crear los espacios."));
            }
        } finally {
            setGuardando(false);
        }
    }

    return (
        <div className="espacios-crear" id="form-espacios">
            <div className="page-head">
                <div>
                    <p className="espacios-migas">
                        <button type="button" className="espacios-migas__enlace" onClick={onCancel}>Espacios de instrumento</button> / Crear espacios
                    </p>
                    <h2 className="page-title">Crear espacios de instrumento</h2>
                    <p className="page-sub">
                        Cada espacio es una lección individual semanal de un profesor. No tiene nivel: el nivel
                        (Instrumento I a XIV) lo define el estudiante que lo tome al matricular.
                    </p>
                </div>
            </div>

            <Alert tone="info" title="El mismo espacio sirve para cualquier nivel." className="bloque">
                El lunes a las 7:10 a. m. puede ser Instrumento I para un estudiante y, el año siguiente, Instrumento V
                para otro. Solo pueden tomarlo los estudiantes que tengan a este profesor asignado en su instrumento.
            </Alert>

            {errorGeneral && <Alert tone="danger" title={errorGeneral} className="bloque" autoFocus />}

            <div className="espacios-layout">
                <div className="espacios-principal">
                    <section className="panel" aria-labelledby="t-prof">
                        <div className="panel-head"><h3 id="t-prof">Profesor e instrumento</h3></div>
                        <div className="panel-body form-grid">
                            <Field id="esp-periodo" label="Período académico" select>
                                <select id="esp-periodo" className="sg-input sg-select" value={idPeriodo} onChange={(e) => setIdPeriodo(e.target.value)} disabled={guardando}>
                                    <option value="">Seleccione un período</option>
                                    {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                                </select>
                            </Field>
                            <Field id="esp-instrumento" label="Instrumento" select>
                                <select
                                    id="esp-instrumento"
                                    className="sg-input sg-select"
                                    value={idInstrumento}
                                    onChange={(e) => { setIdInstrumento(e.target.value); cambiarProfesor(""); setProfesores([]); }}
                                    disabled={guardando}
                                >
                                    <option value="">Seleccione un instrumento</option>
                                    {instrumentos.map((i) => <option key={i.id} value={i.id}>{i.nombre}</option>)}
                                </select>
                            </Field>
                            <Field
                                id="esp-profesor"
                                label="Profesor"
                                select
                                hint={instrumento ? `Solo aparecen profesores activos con ${instrumento.nombre} como especialidad.` : "Elija primero el instrumento."}
                            >
                                <select id="esp-profesor" className="sg-input sg-select" value={idProfesor} onChange={(e) => cambiarProfesor(e.target.value)} disabled={guardando || !idInstrumento} aria-describedby="esp-profesor-hint">
                                    <option value="">{idInstrumento && profesores.length === 0 ? "Ningún profesor con esa especialidad" : "Seleccione un profesor"}</option>
                                    {profesores.map((p) => <option key={p.id} value={p.id}>{nombreProfesor(p)}</option>)}
                                </select>
                            </Field>
                            <Field id="esp-aula" label="Aula" optional hint="Se usa en los espacios que no indiquen otra.">
                                <input id="esp-aula" className="sg-input" maxLength={80} value={aula} onChange={(e) => setAula(e.target.value)} disabled={guardando} aria-describedby="esp-aula-hint" placeholder="Ej. Aula 3" />
                            </Field>
                            {puedeVarios && instrumento && (
                                <fieldset className="fieldset span-2" id="esp-alcance">
                                    <legend>¿Para qué instrumento son los espacios?</legend>
                                    <div className="choice-list">
                                        <label className={`choice${!esDeCualquiera ? " is-checked" : ""}`}>
                                            <input type="radio" name="esp-alcance" checked={!esDeCualquiera} onChange={() => setDeCualquiera(false)} disabled={guardando} />
                                            <span className="choice-main">
                                                <b>Solo {instrumento.nombre}</b>
                                                <span>Solo los pueden tomar estudiantes de {instrumento.nombre} con este profesor.</span>
                                            </span>
                                        </label>
                                        <label className={`choice${esDeCualquiera ? " is-checked" : ""}`}>
                                            <input type="radio" name="esp-alcance" checked={esDeCualquiera} onChange={() => setDeCualquiera(true)} disabled={guardando} />
                                            <span className="choice-main">
                                                <b>Cualquiera de sus instrumentos ({variosNombres})</b>
                                                <span>Cada hora la puede tomar un estudiante de cualquiera de ellos que tenga a este profesor.</span>
                                            </span>
                                        </label>
                                    </div>
                                </fieldset>
                            )}
                        </div>
                        <p className="espacios-cupo">
                            <Lock size={18} strokeWidth={1.75} aria-hidden="true" />
                            <span><b>Cupo: 1 estudiante por espacio.</b> Las lecciones de instrumento son individuales; el cupo no se puede cambiar.</span>
                        </p>
                    </section>

                    <section className="panel" aria-labelledby="t-gen">
                        <div className="panel-head">
                            <div>
                                <h3 id="t-gen">Generar espacios seguidos</h3>
                                <p className="panel-head-sub">Para no agregarlos uno por uno: indique el día, el rango de horas y la duración de cada lección.</p>
                            </div>
                        </div>
                        <div className="panel-body espacios-generador">
                            <Field id="gen-dia" label="Día" select>
                                <select id="gen-dia" className="sg-input sg-select" value={generador.dia} onChange={(e) => setGenerador((g) => ({ ...g, dia: e.target.value }))}>
                                    {DIAS_SEMANA.map((d) => <option key={d.valor} value={d.valor}>{d.nombre}</option>)}
                                </select>
                            </Field>
                            <Field id="gen-desde" label="Desde">
                                <input id="gen-desde" type="time" min={HORA_MINIMA} max={HORA_MAXIMA} className="sg-input" value={generador.desde} onChange={(e) => setGenerador((g) => ({ ...g, desde: e.target.value }))} />
                            </Field>
                            <Field id="gen-hasta" label="Hasta">
                                <input id="gen-hasta" type="time" min={HORA_MINIMA} max={HORA_MAXIMA} className="sg-input" value={generador.hasta} onChange={(e) => setGenerador((g) => ({ ...g, hasta: e.target.value }))} />
                            </Field>
                            <Field id="gen-duracion" label="Duración (minutos)">
                                <input id="gen-duracion" type="number" min="10" max="240" step="5" inputMode="numeric" className="sg-input" value={generador.duracion} onChange={(e) => setGenerador((g) => ({ ...g, duracion: e.target.value }))} />
                            </Field>
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={agregarGenerados} disabled={generados.length === 0}>
                                {generados.length === 0
                                    ? "Agregar a la lista"
                                    : `Agregar ${generados.length} ${generados.length === 1 ? "espacio" : "espacios"} a la lista`}
                            </button>
                        </div>
                    </section>

                    <section className="panel" aria-labelledby="t-lista">
                        <div className="panel-head">
                            <h3 id="t-lista">Espacios de la semana</h3>
                            <span className="muted">
                                {filas.length} {filas.length === 1 ? "espacio" : "espacios"}
                                {minutosSemana > 0 && ` · ${textoDuracion(minutosSemana)} por semana`}
                            </span>
                        </div>

                        {filas.length === 0 ? (
                            <p className="panel-body muted">
                                Use el generador o el botón «Agregar un espacio». Puede editar cada fila antes de crear.
                            </p>
                        ) : (
                            <div className="table-wrap">
                                <table className="dtable espacios-tabla">
                                    <caption className="sr-only">Espacios que se van a crear</caption>
                                    <thead>
                                        <tr>
                                            <th scope="col">#</th>
                                            <th scope="col">Día</th>
                                            <th scope="col">Inicio</th>
                                            <th scope="col">Fin</th>
                                            <th scope="col">Aula</th>
                                            <th scope="col"><span className="sr-only">Acciones</span></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {filas.map((f, i) => {
                                            const error = errores[f.clave];
                                            const idError = `fila-error-${f.clave}`;
                                            const descr = error ? idError : undefined;
                                            return [
                                                <tr key={f.clave} className={error ? "has-error" : undefined}>
                                                    <td className="tnum">{i + 1}</td>
                                                    <td>
                                                        <select aria-label={`Día del espacio ${i + 1}`} className="sg-input sg-select" value={f.dia} onChange={(e) => cambiarFila(f.clave, "dia", e.target.value)} aria-invalid={error ? "true" : undefined} aria-describedby={descr}>
                                                            {DIAS_SEMANA.map((d) => <option key={d.valor} value={d.valor}>{d.nombre}</option>)}
                                                        </select>
                                                    </td>
                                                    <td>
                                                        <input aria-label={`Hora de inicio del espacio ${i + 1}`} type="time" min={HORA_MINIMA} max={HORA_MAXIMA} className="sg-input" value={f.inicio} onChange={(e) => cambiarFila(f.clave, "inicio", e.target.value)} aria-invalid={error ? "true" : undefined} aria-describedby={descr} />
                                                    </td>
                                                    <td>
                                                        <input aria-label={`Hora de fin del espacio ${i + 1}`} type="time" min={HORA_MINIMA} max={HORA_MAXIMA} className="sg-input" value={f.fin} onChange={(e) => cambiarFila(f.clave, "fin", e.target.value)} aria-invalid={error ? "true" : undefined} aria-describedby={descr} />
                                                    </td>
                                                    <td>
                                                        <input aria-label={`Aula del espacio ${i + 1}`} className="sg-input" maxLength={80} value={f.aula} placeholder={aula || "Aula por defecto"} onChange={(e) => cambiarFila(f.clave, "aula", e.target.value)} />
                                                    </td>
                                                    <td className="num">
                                                        <button type="button" className="sg-icon-btn grupos-eliminar" aria-label={`Quitar el espacio ${i + 1}`} onClick={() => quitarFila(f.clave)}>
                                                            <Trash2 size={18} strokeWidth={1.75} aria-hidden="true" />
                                                        </button>
                                                    </td>
                                                </tr>,
                                                error && (
                                                    <tr key={`${f.clave}-error`} className="has-error espacios-fila-error">
                                                        <td colSpan={6}>
                                                            <span className="sg-field__error" id={idError}>
                                                                <CircleAlert size={16} strokeWidth={1.75} aria-hidden="true" />
                                                                {error}
                                                            </span>
                                                        </td>
                                                    </tr>
                                                ),
                                            ];
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}

                        <div className="panel-foot">
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={() => setFilas((prev) => [...prev, nuevaFila({ dia: generador.dia })])}>
                                <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                                Agregar un espacio
                            </button>
                        </div>
                    </section>
                </div>

                <aside className="panel espacios-resumen" aria-labelledby="t-res">
                    <div className="panel-head"><h3 id="t-res">Resumen</h3></div>
                    <div className="panel-body">
                        <dl className="espacios-dl">
                            <dt>Profesor</dt><dd><b>{profesor ? nombreProfesor(profesor) : "—"}</b></dd>
                            <dt>Instrumento</dt><dd>{nombreInstrumentoEspacios ? <StatusBadge tone="primary" icon={null}>{nombreInstrumentoEspacios}</StatusBadge> : "—"}</dd>
                            <dt>Período</dt><dd>{periodo?.nombre ?? "—"}</dd>
                            <dt>Espacios</dt>
                            <dd className="tnum">
                                {filas.length}
                                {conError > 0 && ` (${filas.length - conError} listos, ${conError} con error)`}
                            </dd>
                            <dt>Cupo total</dt><dd className="tnum">{filas.length} {filas.length === 1 ? "estudiante" : "estudiantes"}</dd>
                        </dl>
                        <p className="muted espacios-nota">
                            <Info size={16} strokeWidth={1.75} aria-hidden="true" />
                            Los espacios se publican para la matrícula. Cada estudiante con este profesor asignado en
                            {nombreInstrumentoEspacios ? ` ${nombreInstrumentoEspacios}` : " ese instrumento"} verá solo los espacios libres.
                        </p>
                        <div className="espacios-acciones">
                            <button
                                type="button"
                                className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`}
                                disabled={Boolean(motivoBloqueo) || guardando}
                                aria-describedby="crear-motivo"
                                onClick={crear}
                            >
                                {guardando && <span className="sg-spinner" aria-hidden="true" />}
                                {guardando
                                    ? "Creando…"
                                    : filas.length === 0 ? "Crear espacios" : `Crear ${filas.length} ${filas.length === 1 ? "espacio" : "espacios"}`}
                            </button>
                            {motivoBloqueo && <span id="crear-motivo" className="espacios-motivo">{motivoBloqueo}</span>}
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={limpiar} disabled={guardando}>Limpiar formulario</button>
                            <button type="button" className="sg-btn sg-btn--ghost" onClick={onCancel} disabled={guardando}>Volver a la agenda</button>
                        </div>
                    </div>
                </aside>
            </div>
        </div>
    );
}
