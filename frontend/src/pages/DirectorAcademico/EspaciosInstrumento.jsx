import { useEffect, useMemo, useState } from "react";
import { Clock, Music, Pencil, Plus, Save, Trash2, X } from "lucide-react";

import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import { useDialog } from "../../components/dialog/useDialog";
import { mensajeDeExcepcion } from "../../utils/api";
import { DIAS_SEMANA, rangoHoras12 } from "../../utils/horas";
import CrearEspacios from "./CrearEspacios";
import { eliminarEspacio, listarEspacios, modificarEspacio, nombreProfesor, solicitar } from "./espaciosApi";
import "./espacios.css";

/*
 * Espacios de instrumento - Agenda del profesor.
 *
 * Muestra qué espacios de un profesor en un instrumento ya tomó un
 * estudiante (y con qué nivel) y cuáles siguen libres para la matrícula.
 * Desde aquí se abre «Crear espacios». Un espacio libre se puede
 * modificar o eliminar; uno ocupado, no.
 */
export default function EspaciosInstrumento() {
    const dialog = useDialog();

    const [periodos, setPeriodos] = useState([]);
    const [instrumentos, setInstrumentos] = useState([]);
    const [profesores, setProfesores] = useState([]);

    const [idPeriodo, setIdPeriodo] = useState("");
    const [idInstrumento, setIdInstrumento] = useState("");
    const [idProfesor, setIdProfesor] = useState("");

    // Espacios cargados y para qué filtros (así "cargando" se deriva sin
    // cambiar el estado de forma síncrona dentro del efecto).
    const [cargados, setCargados] = useState({ clave: null, lista: [] });
    const [creando, setCreando] = useState(false);
    const [editando, setEditando] = useState(null);
    const [procesando, setProcesando] = useState(null);
    const [recarga, setRecarga] = useState(0);

    // Catálogos: períodos abiertos e instrumentos activos.
    useEffect(() => {
        let activo = true;
        Promise.all([
            solicitar("/periodos-academicos", {}, "No fue posible cargar los períodos."),
            solicitar("/instrumentos", {}, "No fue posible cargar los instrumentos."),
        ])
            .then(([listaPeriodos, listaInstrumentos]) => {
                if (!activo) return;
                setPeriodos(listaPeriodos ?? []);
                setInstrumentos(listaInstrumentos ?? []);
                if (listaPeriodos?.length) setIdPeriodo((actual) => actual || String(listaPeriodos[0].id));
            })
            .catch((e) => dialog.error(mensajeDeExcepcion(e, "No fue posible cargar la información necesaria.")));
        return () => { activo = false; };
    }, [dialog]);

    useEffect(() => {
        if (!idInstrumento) return undefined;
        let activo = true;
        solicitar(`/profesores/por-instrumento/${idInstrumento}`, {}, "No fue posible cargar los profesores.")
            .then((lista) => { if (activo) setProfesores(lista ?? []); })
            .catch((e) => dialog.error(mensajeDeExcepcion(e, "No fue posible cargar los profesores.")));
        return () => { activo = false; };
    }, [idInstrumento, dialog]);

    const listo = Boolean(idPeriodo && idInstrumento && idProfesor);
    const clave = `${idPeriodo}|${idInstrumento}|${idProfesor}|${recarga}`;

    useEffect(() => {
        if (!listo) return undefined;
        let activo = true;
        listarEspacios({ idPeriodo, idInstrumento, idProfesor })
            .then((lista) => { if (activo) setCargados({ clave, lista: lista ?? [] }); })
            .catch((e) => {
                if (!activo) return;
                setCargados({ clave, lista: [] });
                dialog.error(mensajeDeExcepcion(e, "No fue posible cargar los espacios."));
            });
        return () => { activo = false; };
    }, [listo, clave, idPeriodo, idInstrumento, idProfesor, dialog]);

    const cargando = listo && cargados.clave !== clave;
    const visibles = useMemo(() => (listo && !cargando ? cargados.lista : []), [listo, cargando, cargados]);
    const ocupados = visibles.filter((e) => e.ocupado).length;
    const libres = visibles.length - ocupados;

    const dias = useMemo(() => DIAS_SEMANA
        .filter((d) => d.valor <= 5 || visibles.some((e) => e.diaSemana === d.valor))
        .map((d) => ({ ...d, espacios: visibles.filter((e) => e.diaSemana === d.valor) })), [visibles]);

    const profesor = profesores.find((p) => String(p.id) === String(idProfesor));
    const instrumento = instrumentos.find((i) => String(i.id) === String(idInstrumento));

    async function eliminar(espacio) {
        const acepta = await dialog.confirmar(
            `¿Eliminar el espacio del ${DIAS_SEMANA[espacio.diaSemana - 1]?.nombre.toLowerCase()} de ${rangoHoras12(espacio.horaInicio, espacio.horaFin)}?`,
            { titulo: "Eliminar espacio", peligro: true, textoAceptar: "Eliminar" }
        );
        if (!acepta) return;

        setProcesando(espacio.idGrupo);
        try {
            await eliminarEspacio(espacio.idGrupo);
            setRecarga((n) => n + 1);
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No fue posible eliminar el espacio."));
        } finally {
            setProcesando(null);
        }
    }

    async function guardarEdicion() {
        setProcesando(editando.idGrupo);
        try {
            await modificarEspacio(editando.idGrupo, {
                diaSemana: Number(editando.diaSemana),
                horaInicio: editando.horaInicio,
                horaFin: editando.horaFin,
                aula: editando.aula?.trim() || null,
            });
            setEditando(null);
            setRecarga((n) => n + 1);
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No fue posible modificar el espacio."));
        } finally {
            setProcesando(null);
        }
    }

    if (creando) {
        return (
            <div className="espacios">
                <CrearEspacios
                    periodos={periodos}
                    instrumentos={instrumentos}
                    valoresIniciales={{ idPeriodo, idInstrumento, idProfesor }}
                    onCancel={() => setCreando(false)}
                    onSuccess={(valores) => {
                        setIdPeriodo(String(valores.idPeriodo));
                        setIdInstrumento(String(valores.idInstrumento));
                        setIdProfesor(String(valores.idProfesor));
                        setCreando(false);
                        setRecarga((n) => n + 1);
                    }}
                />
            </div>
        );
    }

    return (
        <div className="espacios">
            <PageHead
                title="Espacios de instrumento"
                sub="Vea qué espacios de cada profesor ya tomó un estudiante y con qué nivel, y cuáles siguen libres para la matrícula."
                actions={(
                    <button type="button" className="sg-btn sg-btn--primary" onClick={() => setCreando(true)}>
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Crear espacios
                    </button>
                )}
            />

            <div className="panel espacios-filtros" role="search" aria-label="Filtros">
                <Field id="f-periodo" label="Período" select>
                    <select id="f-periodo" className="sg-input sg-select" value={idPeriodo} onChange={(e) => setIdPeriodo(e.target.value)}>
                        <option value="">Seleccione un período</option>
                        {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                    </select>
                </Field>
                <Field id="f-instrumento" label="Instrumento" select>
                    <select
                        id="f-instrumento"
                        className="sg-input sg-select"
                        value={idInstrumento}
                        onChange={(e) => { setIdInstrumento(e.target.value); setIdProfesor(""); setProfesores([]); }}
                    >
                        <option value="">Seleccione un instrumento</option>
                        {instrumentos.map((i) => <option key={i.id} value={i.id}>{i.nombre}</option>)}
                    </select>
                </Field>
                <Field id="f-profesor" label="Profesor" select className="espacios-filtros__profesor">
                    <select id="f-profesor" className="sg-input sg-select" value={idProfesor} onChange={(e) => setIdProfesor(e.target.value)} disabled={!idInstrumento}>
                        <option value="">{idInstrumento ? "Seleccione un profesor" : "Elija primero el instrumento"}</option>
                        {profesores.map((p) => <option key={p.id} value={p.id}>{nombreProfesor(p)}</option>)}
                    </select>
                </Field>
            </div>

            {!listo ? (
                <div className="panel">
                    <EmptyState
                        icon={Music}
                        title="Elija un profesor"
                        description="Seleccione el período, el instrumento y el profesor para ver su semana de lecciones."
                    />
                </div>
            ) : (
                <>
                    <div className="espacios-kpis">
                        <div className="panel espacios-kpi"><span>Espacios del profesor</span><b className="tnum">{visibles.length}</b></div>
                        <div className="panel espacios-kpi"><span>Ocupados</span><b className="tnum">{ocupados}</b></div>
                        <div className="panel espacios-kpi"><span>Libres para matrícula</span><b className="tnum is-libre">{libres}</b></div>
                    </div>

                    <section className="panel" aria-labelledby="t-semana" aria-busy={cargando || undefined}>
                        <div className="panel-head">
                            <h2 id="t-semana">Semana de {profesor ? nombreProfesor(profesor) : "…"} · {instrumento?.nombre}</h2>
                            <div className="espacios-leyenda" aria-hidden="true">
                                <span><i className="is-ocupado" />Ocupado</span>
                                <span><i className="is-libre" />Libre</span>
                            </div>
                        </div>

                        {visibles.length === 0 && !cargando ? (
                            <EmptyState
                                icon={Clock}
                                title="Sin espacios en este período"
                                description="Este profesor todavía no tiene espacios de este instrumento. Use «Crear espacios»."
                            />
                        ) : (
                            <div className="espacios-semana-scroll">
                                <div className="espacios-semana" style={{ gridTemplateColumns: `repeat(${dias.length}, minmax(170px, 1fr))` }}>
                                    {dias.map((d) => (
                                        <div key={d.valor} className="espacios-dia">
                                            <h3 className="espacios-dia__titulo">{d.nombre}</h3>
                                            {d.espacios.length === 0 && <p className="muted espacios-dia__vacio">Sin espacios</p>}
                                            {d.espacios.map((e) => (
                                                editando?.idGrupo === e.idGrupo ? (
                                                    <form
                                                        key={e.idGrupo}
                                                        className="espacio-tarjeta is-editando"
                                                        onSubmit={(ev) => { ev.preventDefault(); guardarEdicion(); }}
                                                    >
                                                        <label className="sr-only" htmlFor={`ed-dia-${e.idGrupo}`}>Día</label>
                                                        <select id={`ed-dia-${e.idGrupo}`} className="sg-input sg-select" value={editando.diaSemana} onChange={(ev) => setEditando((x) => ({ ...x, diaSemana: ev.target.value }))}>
                                                            {DIAS_SEMANA.map((dd) => <option key={dd.valor} value={dd.valor}>{dd.nombre}</option>)}
                                                        </select>
                                                        <div className="espacio-tarjeta__horas">
                                                            <label className="sr-only" htmlFor={`ed-ini-${e.idGrupo}`}>Hora de inicio</label>
                                                            <input id={`ed-ini-${e.idGrupo}`} type="time" className="sg-input" value={editando.horaInicio} onChange={(ev) => setEditando((x) => ({ ...x, horaInicio: ev.target.value }))} />
                                                            <label className="sr-only" htmlFor={`ed-fin-${e.idGrupo}`}>Hora de fin</label>
                                                            <input id={`ed-fin-${e.idGrupo}`} type="time" className="sg-input" value={editando.horaFin} onChange={(ev) => setEditando((x) => ({ ...x, horaFin: ev.target.value }))} />
                                                        </div>
                                                        <label className="sr-only" htmlFor={`ed-aula-${e.idGrupo}`}>Aula</label>
                                                        <input id={`ed-aula-${e.idGrupo}`} className="sg-input" placeholder="Aula" maxLength={80} value={editando.aula ?? ""} onChange={(ev) => setEditando((x) => ({ ...x, aula: ev.target.value }))} />
                                                        <div className="espacio-tarjeta__acciones">
                                                            <button type="submit" className="sg-btn sg-btn--primary sg-btn--sm" disabled={procesando === e.idGrupo}>
                                                                <Save size={16} strokeWidth={1.75} aria-hidden="true" />Guardar
                                                            </button>
                                                            <button type="button" className="sg-icon-btn" aria-label="Cancelar la modificación" onClick={() => setEditando(null)}>
                                                                <X size={18} strokeWidth={1.75} aria-hidden="true" />
                                                            </button>
                                                        </div>
                                                    </form>
                                                ) : e.ocupado ? (
                                                    <div key={e.idGrupo} className="espacio-tarjeta is-ocupado">
                                                        <span className="espacio-tarjeta__hora tnum">{rangoHoras12(e.horaInicio, e.horaFin)}</span>
                                                        <b>{e.nombreEstudiante}</b>
                                                        <span className="espacio-tarjeta__nivel">{e.nombreNivel}</span>
                                                    </div>
                                                ) : (
                                                    <div key={e.idGrupo} className="espacio-tarjeta is-libre">
                                                        <span className="espacio-tarjeta__hora tnum">{rangoHoras12(e.horaInicio, e.horaFin)}</span>
                                                        <b className="espacio-tarjeta__libre"><Clock size={16} strokeWidth={1.75} aria-hidden="true" />Libre</b>
                                                        <span className="muted">Cualquier nivel{e.aula ? ` · ${e.aula}` : ""}</span>
                                                        <div className="espacio-tarjeta__acciones">
                                                            <button
                                                                type="button"
                                                                className="sg-icon-btn"
                                                                aria-label={`Modificar el espacio de ${rangoHoras12(e.horaInicio, e.horaFin)}`}
                                                                onClick={() => setEditando({
                                                                    idGrupo: e.idGrupo,
                                                                    diaSemana: String(e.diaSemana),
                                                                    horaInicio: String(e.horaInicio).slice(0, 5),
                                                                    horaFin: String(e.horaFin).slice(0, 5),
                                                                    aula: e.aula ?? "",
                                                                })}
                                                            >
                                                                <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className="sg-icon-btn grupos-eliminar"
                                                                aria-label={`Eliminar el espacio de ${rangoHoras12(e.horaInicio, e.horaFin)}`}
                                                                disabled={procesando === e.idGrupo}
                                                                onClick={() => eliminar(e)}
                                                            >
                                                                <Trash2 size={16} strokeWidth={1.75} aria-hidden="true" />
                                                            </button>
                                                        </div>
                                                    </div>
                                                )
                                            ))}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        <p className="panel-body muted espacios-pie">
                            El nivel aparece cuando un estudiante toma el espacio. Los niveles I a XIV conviven en la misma
                            semana del profesor sin crear un grupo por nivel. Los espacios ocupados no se pueden modificar ni eliminar.
                        </p>
                    </section>
                </>
            )}
        </div>
    );
}
