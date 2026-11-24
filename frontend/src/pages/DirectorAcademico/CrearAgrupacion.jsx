import { useEffect, useMemo, useState } from "react";
import { Info, Plus, Star, Trash2, TriangleAlert } from "lucide-react";

import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import StatusBadge from "../../components/ui/StatusBadge";
import { useDialog } from "../../components/dialog/useDialog";
import { mensajeDeExcepcion } from "../../utils/api";
import { DIAS_SEMANA } from "../../utils/horas";
import {
    ETIQUETA_TIPO,
    crearGrupoAgrupacion,
    listarEstudiantesGrupo,
    modificarGrupoAgrupacion,
    revisarChoques,
} from "./agrupacionesApi";
import QuienesPuedenCampos from "./QuienesPuedenCampos";
import { errorDeEdades, quienesPuedenDesde, quienesPuedenParaEnviar } from "./quienesPueden";

// Misma franja que los grupos normales y los espacios.
const HORA_MINIMA = "07:00";
const HORA_MAXIMA = "22:00";

let siguienteClave = 1;
const nuevoBloque = (datos = {}) => ({ clave: siguienteClave++, dia: "1", inicio: "", fin: "", ...datos });

function errorDeBloque(bloques, i) {
    const b = bloques[i];
    if (!b.inicio || !b.fin) return "Indique la hora de inicio y la de fin.";
    if (b.fin <= b.inicio) return "La hora de fin debe ser posterior a la de inicio.";
    if (b.inicio < HORA_MINIMA || b.fin > HORA_MAXIMA) return "El horario debe estar entre las 7:00 a. m. y las 10:00 p. m.";
    const otro = bloques.findIndex((o, j) => j < i && o.dia === b.dia && o.inicio && o.fin && b.inicio < o.fin && o.inicio < b.fin);
    if (otro >= 0) return `Choca con el bloque ${otro + 1} de este mismo grupo.`;
    return null;
}

/*
 * Agrupaciones - Crear o editar el grupo de una agrupación en un período
 * (mockup 9 · Director: crear agrupación).
 *
 * Props:
 * - periodos, agrupaciones (catálogo), instrumentos, profesores (activos).
 * - grupo: el grupo que se edita (null = crear).
 * - onCancel(), onSuccess(grupoGuardado).
 */
export default function CrearAgrupacion({ periodos, agrupaciones, instrumentos, profesores, grupo, onCancel, onSuccess }) {
    const dialog = useDialog();
    const editando = Boolean(grupo);

    const [idAgrupacion, setIdAgrupacion] = useState(grupo ? String(grupo.idAgrupacion) : "");
    const [idPeriodo, setIdPeriodo] = useState(grupo ? String(grupo.idPeriodo) : (periodos[0] ? String(periodos[0].id) : ""));
    const [cupo, setCupo] = useState(grupo ? String(grupo.cupoMaximo) : "30");
    const [aula, setAula] = useState(grupo?.aula ?? "");
    const [idsInstrumento, setIdsInstrumento] = useState(
        () => (grupo?.instrumentosPermitidos ?? []).map((i) => i.id)
    );
    // ¿Quiénes pueden matricularla? (de la agrupación: edades y solo inclusión)
    const [quienes, setQuienes] = useState(() => quienesPuedenDesde(grupo));
    const errorQuienes = errorDeEdades(quienes);
    const [profesoresGrupo, setProfesoresGrupo] = useState(
        () => (grupo?.profesores ?? []).map((p) => ({ idProfesor: p.idProfesor, responsable: p.responsable, nombre: p.nombre }))
    );
    const [profesorPorAgregar, setProfesorPorAgregar] = useState("");
    const [bloques, setBloques] = useState(() => (grupo?.horarios?.length
        ? grupo.horarios.map((h) => nuevoBloque({ dia: String(h.diaSemana), inicio: String(h.horaInicio).slice(0, 5), fin: String(h.horaFin).slice(0, 5) }))
        : [nuevoBloque()]));

    // Choques revisados y para qué datos (así se descartan sin setState en el efecto).
    const [revisados, setRevisados] = useState({ clave: null, lista: [] });
    const [estudiantes, setEstudiantes] = useState([]);
    const [errorGeneral, setErrorGeneral] = useState("");
    const [guardando, setGuardando] = useState(null);

    const agrupacion = agrupaciones.find((a) => String(a.id) === idAgrupacion);

    // Al elegir otra agrupación, "Solo para estudiantes de" toma sus instrumentos.
    function cambiarAgrupacion(valor) {
        setIdAgrupacion(valor);
        const elegida = agrupaciones.find((a) => String(a.id) === valor);
        setIdsInstrumento((elegida?.instrumentos ?? []).map((i) => i.id));
        setQuienes(quienesPuedenDesde(elegida));
    }

    // Vista interna: estudiantes ya matriculados con su nivel.
    useEffect(() => {
        if (!grupo?.idGrupo) return undefined;
        let activo = true;
        listarEstudiantesGrupo(grupo.idGrupo)
            .then((lista) => { if (activo) setEstudiantes(lista ?? []); })
            .catch(() => { if (activo) setEstudiantes([]); });
        return () => { activo = false; };
    }, [grupo]);

    const erroresBloque = useMemo(() => bloques.map((_, i) => errorDeBloque(bloques, i)), [bloques]);
    const bloquesValidos = bloques.filter((_, i) => !erroresBloque[i]);

    function armarPayload(estado) {
        return {
            idAgrupacion: Number(idAgrupacion),
            idPeriodo: Number(idPeriodo),
            cupoMaximo: Number(cupo),
            aula: aula.trim() || null,
            estado,
            idsInstrumento,
            quienesPueden: quienesPuedenParaEnviar(quienes),
            profesores: profesoresGrupo.map((p) => ({ idProfesor: p.idProfesor, responsable: p.responsable })),
            bloques: bloques.map((b) => ({ diaSemana: Number(b.dia), horaInicio: b.inicio, horaFin: b.fin })),
        };
    }

    // Aviso amarillo: choques con otros grupos y espacios de sus profesores.
    const claveChoques = JSON.stringify({
        idPeriodo,
        profesores: profesoresGrupo.map((p) => p.idProfesor),
        bloques: bloquesValidos.map((b) => [b.dia, b.inicio, b.fin]),
    });

    const puedeRevisar = Boolean(idPeriodo) && profesoresGrupo.length > 0 && bloquesValidos.length > 0;
    const choques = puedeRevisar && revisados.clave === claveChoques ? revisados.lista : [];

    useEffect(() => {
        if (!puedeRevisar) return undefined;
        let activo = true;
        const temporizador = setTimeout(() => {
            revisarChoques({
                idPeriodo: Number(idPeriodo),
                profesores: profesoresGrupo.map((p) => ({ idProfesor: p.idProfesor, responsable: p.responsable })),
                bloques: bloquesValidos.map((b) => ({ diaSemana: Number(b.dia), horaInicio: b.inicio, horaFin: b.fin })),
            }, grupo?.idGrupo)
                .then((lista) => { if (activo) setRevisados({ clave: claveChoques, lista: lista ?? [] }); })
                .catch(() => { if (activo) setRevisados({ clave: claveChoques, lista: [] }); });
        }, 400);
        return () => { activo = false; clearTimeout(temporizador); };
        // claveChoques resume lo que importa para no repetir la consulta en cada render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [claveChoques]);

    const responsable = profesoresGrupo.find((p) => p.responsable);
    const disponiblesParaAgregar = profesores.filter((p) => !profesoresGrupo.some((g) => g.idProfesor === p.idProfesor));

    function agregarProfesor() {
        const p = profesores.find((x) => String(x.idProfesor) === profesorPorAgregar);
        if (!p) return;
        setProfesoresGrupo((prev) => [...prev, { idProfesor: p.idProfesor, nombre: p.nombreCompleto, responsable: prev.length === 0 }]);
        setProfesorPorAgregar("");
    }

    function quitarProfesor(idProfesor) {
        setProfesoresGrupo((prev) => {
            const resto = prev.filter((p) => p.idProfesor !== idProfesor);
            // Si se quita al responsable, el primero que queda pasa a serlo.
            if (resto.length > 0 && !resto.some((p) => p.responsable)) resto[0] = { ...resto[0], responsable: true };
            return resto;
        });
    }

    function marcarResponsable(idProfesor) {
        setProfesoresGrupo((prev) => prev.map((p) => ({ ...p, responsable: p.idProfesor === idProfesor })));
    }

    function cambiarBloque(clave, campo, valor) {
        setBloques((prev) => prev.map((b) => (b.clave === clave ? { ...b, [campo]: valor } : b)));
    }

    function alternarInstrumento(id) {
        setIdsInstrumento((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
    }

    // Qué impide guardar (null = se puede).
    function motivoBloqueo(estado) {
        if (!idAgrupacion) return "Elija la agrupación.";
        if (!idPeriodo) return "Elija el período.";
        if (!cupo || Number(cupo) < 1) return "Indique un cupo de al menos 1 estudiante.";
        if (errorQuienes) return errorQuienes;
        const i = erroresBloque.findIndex(Boolean);
        if (i >= 0) return `Corrija el bloque ${i + 1} del horario.`;
        if (estado === "PUBLICADO" && !responsable) return "Para publicar, agregue un profesor y márquelo como responsable.";
        return null;
    }

    const motivoPlanificada = motivoBloqueo("PLANIFICADO");
    const motivoPublicar = motivoBloqueo("PUBLICADO");

    async function guardar(estado) {
        if (motivoBloqueo(estado) || guardando) return;
        setGuardando(estado);
        setErrorGeneral("");
        try {
            const payload = armarPayload(estado);
            const guardado = editando
                ? await modificarGrupoAgrupacion(grupo.idGrupo, payload)
                : await crearGrupoAgrupacion(payload);

            const texto = estado === "PUBLICADO"
                ? `La agrupación ${guardado.nombreAgrupacion} (${guardado.codigo}) quedó publicada para la matrícula.`
                : `La agrupación ${guardado.nombreAgrupacion} (${guardado.codigo}) quedó guardada como planificada.`;

            if (guardado.choques?.length > 0) {
                await dialog.aviso(`${texto} Revise estos choques de horario de sus profesores:`, {
                    titulo: "Guardada con avisos",
                    lista: guardado.choques.map((c) => c.mensaje),
                });
            } else {
                await dialog.exito(texto);
            }
            onSuccess(guardado);
        } catch (e) {
            setErrorGeneral(mensajeDeExcepcion(e, "No fue posible guardar la agrupación."));
        } finally {
            setGuardando(null);
        }
    }

    return (
        <div className="agrupaciones-crear">
            <div className="page-head">
                <div>
                    <p className="espacios-migas">
                        <button type="button" className="espacios-migas__enlace" onClick={onCancel}>Agrupaciones</button>
                        {" / "}{editando ? `Editar ${grupo.codigo}` : "Crear agrupación"}
                    </p>
                    <h2 className="page-title" id="page-title" tabIndex={-1}>{editando ? "Editar agrupación" : "Crear agrupación"}</h2>
                    <p className="page-sub">
                        Abra una agrupación en un período: su horario fijo, el cupo y los profesores. Estudiantes de distintos
                        niveles tocan juntos; el sistema calcula el nivel de cada uno.
                    </p>
                </div>
            </div>

            {errorGeneral && <Alert tone="danger" title={errorGeneral} className="bloque" autoFocus />}

            <div className="espacios-layout">
                <div className="espacios-principal">
                    <section className="panel" aria-labelledby="t-datos">
                        <div className="panel-head"><h3 id="t-datos">Datos</h3></div>
                        <div className="panel-body form-grid">
                            <Field id="agr-agrupacion" label="Agrupación" select hint={agrupaciones.length === 0 ? "Primero cree la agrupación en el catálogo." : undefined}>
                                <select id="agr-agrupacion" className="sg-input sg-select" value={idAgrupacion} onChange={(e) => cambiarAgrupacion(e.target.value)} disabled={Boolean(guardando) || (editando && grupo.matriculados > 0)}>
                                    <option value="">Seleccione una agrupación</option>
                                    {agrupaciones.filter((a) => a.activa || String(a.id) === idAgrupacion).map((a) => (
                                        <option key={a.id} value={a.id}>{a.nombre}</option>
                                    ))}
                                </select>
                            </Field>
                            <Field id="agr-periodo" label="Período académico" select>
                                <select id="agr-periodo" className="sg-input sg-select" value={idPeriodo} onChange={(e) => setIdPeriodo(e.target.value)} disabled={Boolean(guardando) || (editando && grupo.matriculados > 0)}>
                                    <option value="">Seleccione un período</option>
                                    {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                                </select>
                            </Field>
                            <Field id="agr-tipo" label="Tipo" hint={agrupacion ? (agrupacion.tipo === "CORAL" ? "Cuenta para Coro I a XIV." : "Cuenta para Conjunto Instrumental I a XII.") : "Lo define la agrupación."}>
                                <input id="agr-tipo" className="sg-input" readOnly value={agrupacion ? ETIQUETA_TIPO[agrupacion.tipo] : ""} aria-describedby="agr-tipo-hint" />
                            </Field>
                            <Field id="agr-cupo" label="Cupo" hint={editando && grupo.matriculados > 0 ? `Ya hay ${grupo.matriculados} matriculados.` : "Estudiantes que caben en la agrupación."}>
                                <input id="agr-cupo" type="number" min={Math.max(1, grupo?.matriculados ?? 0)} max="500" inputMode="numeric" className="sg-input" value={cupo} onChange={(e) => setCupo(e.target.value)} disabled={Boolean(guardando)} aria-describedby="agr-cupo-hint" />
                            </Field>
                            <Field id="agr-aula" label="Aula" optional>
                                <input id="agr-aula" className="sg-input" maxLength={80} value={aula} onChange={(e) => setAula(e.target.value)} disabled={Boolean(guardando)} placeholder="Ej. Salón principal" />
                            </Field>
                        </div>

                        <fieldset className="fieldset panel-body agrupaciones-instrumentos" aria-describedby="agr-instr-hint">
                            <legend className="sg-field__label">Solo para estudiantes de</legend>
                            <p className="sg-field__hint" id="agr-instr-hint">
                                Si no marca ninguno, la aceptan estudiantes de cualquier instrumento. Se guarda en la agrupación y vale para todos sus períodos.
                            </p>
                            <div className="agrupaciones-instrumentos__lista">
                                {instrumentos.map((i) => (
                                    <label key={i.id} className="choice agrupaciones-instrumento">
                                        <input type="checkbox" checked={idsInstrumento.includes(i.id)} onChange={() => alternarInstrumento(i.id)} disabled={Boolean(guardando) || !idAgrupacion} />
                                        <span>{i.nombre}</span>
                                    </label>
                                ))}
                            </div>
                        </fieldset>

                        <div className="panel-body">
                            <QuienesPuedenCampos
                                idBase="agr-quienes"
                                valor={quienes}
                                onChange={setQuienes}
                                error={errorQuienes}
                                disabled={Boolean(guardando) || !idAgrupacion}
                            />
                            <p className="sg-field__hint">Se guarda en la agrupación y vale para todos sus períodos.</p>
                        </div>
                    </section>

                    <section className="panel" aria-labelledby="t-profesores">
                        <div className="panel-head">
                            <div>
                                <h3 id="t-profesores">Profesores</h3>
                                <p className="panel-head-sub">Uno es el responsable: es quien aparece como profesor del grupo en las demás pantallas.</p>
                            </div>
                        </div>
                        {profesoresGrupo.length === 0 ? (
                            <p className="panel-body muted">Todavía no hay profesores. Para publicar necesita al menos el responsable.</p>
                        ) : (
                            <fieldset className="fieldset">
                                <legend className="sr-only">Profesor responsable</legend>
                                <ul className="agrupaciones-profesores">
                                    {profesoresGrupo.map((p) => (
                                        <li key={p.idProfesor}>
                                            <b>{p.nombre}</b>
                                            <label className="agrupaciones-responsable">
                                                <input type="radio" name="responsable" checked={p.responsable} onChange={() => marcarResponsable(p.idProfesor)} disabled={Boolean(guardando)} />
                                                <span>Responsable</span>
                                                {p.responsable && <Star size={16} strokeWidth={1.75} aria-hidden="true" />}
                                            </label>
                                            <button type="button" className="sg-icon-btn grupos-eliminar" aria-label={`Quitar a ${p.nombre}`} onClick={() => quitarProfesor(p.idProfesor)} disabled={Boolean(guardando)}>
                                                <Trash2 size={18} strokeWidth={1.75} aria-hidden="true" />
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            </fieldset>
                        )}
                        <div className="panel-foot agrupaciones-agregar">
                            <Field id="agr-profesor" label="Agregar profesor" select>
                                <select id="agr-profesor" className="sg-input sg-select" value={profesorPorAgregar} onChange={(e) => setProfesorPorAgregar(e.target.value)} disabled={Boolean(guardando)}>
                                    <option value="">Seleccione un profesor</option>
                                    {disponiblesParaAgregar.map((p) => <option key={p.idProfesor} value={p.idProfesor}>{p.nombreCompleto}</option>)}
                                </select>
                            </Field>
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={agregarProfesor} disabled={!profesorPorAgregar || Boolean(guardando)}>
                                <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                                Agregar
                            </button>
                        </div>
                    </section>

                    <section className="panel" aria-labelledby="t-horario">
                        <div className="panel-head"><h3 id="t-horario">Horario</h3></div>
                        <div className="panel-body agrupaciones-bloques">
                            {bloques.map((b, i) => {
                                const error = erroresBloque[i];
                                const idError = `bloque-error-${b.clave}`;
                                return (
                                    <div key={b.clave} className={`agrupaciones-bloque${error && (b.inicio || b.fin) ? " has-error" : ""}`}>
                                        <div className="agrupaciones-bloque__campos">
                                            <Field id={`bl-dia-${b.clave}`} label={`Día ${bloques.length > 1 ? i + 1 : ""}`.trim()} select>
                                                <select id={`bl-dia-${b.clave}`} className="sg-input sg-select" value={b.dia} onChange={(e) => cambiarBloque(b.clave, "dia", e.target.value)} disabled={Boolean(guardando)}>
                                                    {DIAS_SEMANA.map((d) => <option key={d.valor} value={d.valor}>{d.nombre}</option>)}
                                                </select>
                                            </Field>
                                            <Field id={`bl-ini-${b.clave}`} label="Inicio">
                                                <input id={`bl-ini-${b.clave}`} type="time" min={HORA_MINIMA} max={HORA_MAXIMA} className="sg-input" value={b.inicio} onChange={(e) => cambiarBloque(b.clave, "inicio", e.target.value)} disabled={Boolean(guardando)} aria-describedby={error ? idError : undefined} />
                                            </Field>
                                            <Field id={`bl-fin-${b.clave}`} label="Fin">
                                                <input id={`bl-fin-${b.clave}`} type="time" min={HORA_MINIMA} max={HORA_MAXIMA} className="sg-input" value={b.fin} onChange={(e) => cambiarBloque(b.clave, "fin", e.target.value)} disabled={Boolean(guardando)} aria-describedby={error ? idError : undefined} />
                                            </Field>
                                            {bloques.length > 1 && (
                                                <button type="button" className="sg-icon-btn grupos-eliminar" aria-label={`Quitar el bloque ${i + 1}`} onClick={() => setBloques((prev) => prev.filter((x) => x.clave !== b.clave))} disabled={Boolean(guardando)}>
                                                    <Trash2 size={18} strokeWidth={1.75} aria-hidden="true" />
                                                </button>
                                            )}
                                        </div>
                                        {error && (b.inicio || b.fin) && (
                                            <p className="sg-field__error" id={idError}>{error}</p>
                                        )}
                                    </div>
                                );
                            })}

                            {choques.length > 0 && (
                                <Alert tone="warning" title="Choques de horario de los profesores">
                                    <ul className="agrupaciones-choques">
                                        {choques.map((c, i) => <li key={`${c.idProfesor}-${c.indiceBloque}-${i}`}>{c.mensaje}</li>)}
                                    </ul>
                                    <p>Puede guardar igual; revise con cada profesor.</p>
                                </Alert>
                            )}
                        </div>
                        <div className="panel-foot">
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={() => setBloques((prev) => [...prev, nuevoBloque()])} disabled={Boolean(guardando)}>
                                <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                                Agregar otro día
                            </button>
                        </div>
                    </section>

                    <div className="panel sticky-save agrupaciones-acciones">
                        <span id="agr-motivo" className={motivoPlanificada ? "dirty" : "muted"} aria-live="polite">
                            {motivoPlanificada && <i aria-hidden="true" />}
                            {motivoPlanificada || motivoPublicar || "Lista para guardar."}
                        </span>
                        <div className="pasos-acciones">
                            <button type="button" className="sg-btn sg-btn--ghost" onClick={onCancel} disabled={Boolean(guardando)}>Cancelar</button>
                            <button type="button" className={`sg-btn sg-btn--secondary${guardando === "PLANIFICADO" ? " is-loading" : ""}`} onClick={() => guardar("PLANIFICADO")} disabled={Boolean(motivoPlanificada) || Boolean(guardando) || (editando && grupo.matriculados > 0)} aria-describedby="agr-motivo">
                                {guardando === "PLANIFICADO" && <span className="sg-spinner" aria-hidden="true" />}
                                Guardar como planificada
                            </button>
                            <button type="button" className={`sg-btn sg-btn--primary${guardando === "PUBLICADO" ? " is-loading" : ""}`} onClick={() => guardar("PUBLICADO")} disabled={Boolean(motivoPublicar) || Boolean(guardando)} aria-describedby="agr-motivo">
                                {guardando === "PUBLICADO" && <span className="sg-spinner" aria-hidden="true" />}
                                Guardar y publicar
                            </button>
                        </div>
                    </div>
                </div>

                <aside className="panel espacios-resumen" aria-labelledby="t-interno">
                    <div className="panel-head">
                        <h3 id="t-interno">Así queda cada estudiante <span className="muted">(uso interno)</span></h3>
                    </div>
                    <div className="panel-body">
                        <p className="espacios-nota">
                            <Info size={16} strokeWidth={1.75} aria-hidden="true" />
                            <span>
                                El nivel sale de lo que cada estudiante aprobó en ESTA agrupación: si aprobó
                                {agrupacion?.tipo === "CORAL" ? " Coro IV" : " Conjunto Instrumental IV"}, lleva el V; si no lo aprobó, lo repite.
                                Si nunca ha estado aquí, empieza en I o en el siguiente a su historial de la certificación.
                            </span>
                        </p>
                        <p className="espacios-nota">
                            <TriangleAlert size={16} strokeWidth={1.75} aria-hidden="true" />
                            <span>El estudiante no ve su nivel: en su matrícula y su horario solo aparece «{agrupacion?.nombre ?? "el nombre de la agrupación"}».</span>
                        </p>

                        {editando && (
                            estudiantes.length === 0 ? (
                                <p className="muted">Todavía no hay estudiantes matriculados.</p>
                            ) : (
                                <ul className="agrupaciones-estudiantes">
                                    {estudiantes.map((e) => (
                                        <li key={e.idEstudiante}>
                                            <b>{e.nombreCompleto}</b>
                                            <span>{e.etiqueta}</span>
                                            {e.calificaComoCoro && <StatusBadge tone="info" icon={null}>Se califica como coro</StatusBadge>}
                                        </li>
                                    ))}
                                </ul>
                            )
                        )}
                    </div>
                </aside>
            </div>
        </div>
    );
}
