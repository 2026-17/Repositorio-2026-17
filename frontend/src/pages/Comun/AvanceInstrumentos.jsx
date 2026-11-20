import { useEffect, useMemo, useState } from "react";
import { GraduationCap } from "lucide-react";

import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import StatusBadge from "../../components/ui/StatusBadge";
import { useDialog } from "../../components/dialog/useDialog";
import { API_URL } from "../../config/api.js";
import { lanzarError, mensajeDeExcepcion } from "../../utils/api";

const ROMANO = /Instrumento\s+([IVXLC]+)\s*$/i;

const ORIGEN = {
    SIGAC: "calificación registrada en SIGAC",
    CERTIFICACION: "certificación anterior a SIGAC",
    NIVEL_INGRESO: "nivel de ingreso (prueba de ubicación)",
};

function etiquetaCorta(nivel) {
    return nivel.nombre?.match(ROMANO)?.[1] ?? String(nivel.numero);
}

function formatDate(iso) {
    if (!iso) return "";
    const [y, m, d] = iso.split("-");
    return `${d}/${m}/${y}`;
}

// Debajo de cada nivel: período, "Conv." o "Siguiente".
function marcaNivel(nivel) {
    if (nivel.estado === "CONVALIDADO") return "Conv.";
    if (nivel.estado === "APROBADO") return nivel.nombrePeriodo ?? (nivel.fecha ? formatDate(nivel.fecha).slice(-4) : "Aprob.");
    if (nivel.estado === "SIGUIENTE") return "Siguiente";
    return "";
}

function textoEstado(nivel) {
    const base = {
        APROBADO: "aprobado",
        CONVALIDADO: "convalidado",
        SIGUIENTE: "le corresponde matricular",
        PENDIENTE: "pendiente",
    }[nivel.estado];
    const origen = nivel.origen ? `, ${ORIGEN[nivel.origen] ?? nivel.origen}` : "";
    return `${nivel.nombre}: ${base}${origen}`;
}

// "Certificación anterior a SIGAC (I a IV) y calificación registrada en SIGAC (V)."
function resumenOrigen(niveles) {
    const grupos = [];
    niveles.filter((n) => n.origen).forEach((n) => {
        const ultimo = grupos[grupos.length - 1];
        if (ultimo && ultimo.origen === n.origen) ultimo.hasta = etiquetaCorta(n);
        else grupos.push({ origen: n.origen, desde: etiquetaCorta(n), hasta: etiquetaCorta(n) });
    });
    if (grupos.length === 0) return null;
    const partes = grupos.map((g) => {
        const texto = ORIGEN[g.origen] ?? g.origen;
        const rango = g.desde === g.hasta ? g.desde : `${g.desde} a ${g.hasta}`;
        return `${texto} (${rango})`;
    });
    const frase = partes.join(" y ");
    return `${frase.charAt(0).toUpperCase()}${frase.slice(1)}.`;
}

/*
 * Avance por instrumento: por cada instrumento vigente, si es del plan o
 * extra, su profesor y la tira de niveles I..XIV con su estado y origen.
 * Con puedeRegistrarNivel (DIRECTOR_ACADEMICO), el panel "Registrar
 * nivel de ingreso".
 *
 * version: cambia cuando los instrumentos del estudiante cambian (recarga).
 */
export default function AvanceInstrumentos({ idEstudiante, version, puedeRegistrarNivel = false }) {
    const dialog = useDialog();

    const [cargado, setCargado] = useState({ clave: null, datos: null, error: "" });
    const [recarga, setRecarga] = useState(0);
    const [form, setForm] = useState({ idInstrumento: "", idCurso: "", motivo: "" });
    const [errores, setErrores] = useState({});
    const [guardando, setGuardando] = useState(false);

    const clave = `${idEstudiante}|${version}|${recarga}`;

    useEffect(() => {
        let activo = true;
        const token = localStorage.getItem("token");
        fetch(`${API_URL}/estudiantes/${idEstudiante}/avance-instrumentos`, {
            headers: { Authorization: `Bearer ${token}` },
        })
            .then((r) => (r.ok ? r.json() : lanzarError(r, "No fue posible consultar el avance por instrumento.")))
            .then((datos) => { if (activo) setCargado({ clave, datos, error: "" }); })
            .catch((e) => {
                if (activo) setCargado({ clave, datos: null, error: mensajeDeExcepcion(e, "No fue posible consultar el avance por instrumento.") });
            });
        return () => { activo = false; };
    }, [idEstudiante, clave]);

    const cargando = cargado.clave !== clave;
    const datos = cargando ? null : cargado.datos;
    const instrumentos = useMemo(() => datos?.instrumentos ?? [], [datos]);

    // Instrumentos que admiten nivel de ingreso: sin niveles aprobados en SIGAC.
    const candidatos = instrumentos.filter((i) => !i.tieneAprobadosEnSigac);
    const elegido = instrumentos.find((i) => String(i.idInstrumento) === String(form.idInstrumento));

    async function registrarNivel(event) {
        event.preventDefault();
        const e = {};
        if (!form.idInstrumento) e.idInstrumento = "Seleccione el instrumento.";
        if (!form.idCurso) e.idCurso = "Seleccione el nivel en que empieza.";
        if (!form.motivo.trim()) e.motivo = "Indique el motivo (por ejemplo, el resultado de la prueba de ubicación).";
        setErrores(e);
        if (Object.keys(e).length > 0) return;

        const nivel = elegido.niveles.find((n) => String(n.idCurso) === String(form.idCurso));
        const anteriores = elegido.niveles.filter((n) => n.numero < nivel.numero);
        const acepta = await dialog.confirmar(
            anteriores.length === 0
                ? `${elegido.nombreInstrumento} empezará en ${nivel.nombre}. No hay niveles anteriores que convalidar.`
                : `${elegido.nombreInstrumento} empezará en ${nivel.nombre}. Quedarán convalidados: ${anteriores.map((n) => n.nombre).join(", ")}. El cambio se guarda en la bitácora.`,
            { titulo: "Registrar nivel de ingreso", textoAceptar: "Registrar nivel de ingreso" }
        );
        if (!acepta) return;

        setGuardando(true);
        try {
            const token = localStorage.getItem("token");
            const r = await fetch(`${API_URL}/estudiantes/${idEstudiante}/instrumentos/${form.idInstrumento}/nivel-ingreso`, {
                method: "POST",
                headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
                body: JSON.stringify({ idCurso: Number(form.idCurso), motivo: form.motivo.trim() }),
            });
            if (!r.ok) await lanzarError(r, "No fue posible registrar el nivel de ingreso.");
            setForm({ idInstrumento: "", idCurso: "", motivo: "" });
            setRecarga((n) => n + 1);
            dialog.exito(`Nivel de ingreso registrado: ${elegido.nombreInstrumento} empieza en ${nivel.nombre}.`);
        } catch (err) {
            dialog.error(mensajeDeExcepcion(err, "No fue posible registrar el nivel de ingreso."));
        } finally {
            setGuardando(false);
        }
    }

    return (
        <section className="panel" aria-labelledby="t-avance" aria-busy={cargando || undefined}>
            <div className="panel-head">
                <div>
                    <h2 id="t-avance">Avance por instrumento</h2>
                    <p className="panel-head-sub">
                        Cada instrumento avanza por separado; solo el instrumento del plan cuenta para graduarse.
                        {datos?.nombrePlan && ` Plan: ${datos.nombrePlan}.`}
                    </p>
                </div>
            </div>

            {cargado.error && !cargando && <Alert tone="danger" title={cargado.error} className="avance-alerta" />}

            {!cargando && !cargado.error && instrumentos.length === 0 && (
                <EmptyState compact icon={GraduationCap} title="Sin avance que mostrar" description="El estudiante no tiene instrumentos vigentes." />
            )}

            <div className="avance-lista">
                {instrumentos.map((inst) => {
                    const ultimos = inst.niveles.filter((n) => n.estado === "APROBADO" || n.estado === "CONVALIDADO");
                    const ultimo = ultimos[ultimos.length - 1];
                    const origen = resumenOrigen(inst.niveles);

                    return (
                        <article key={inst.idEstudianteInstrumento} className="avance-inst" aria-labelledby={`avance-${inst.idInstrumento}`}>
                            <div className="avance-inst__head">
                                <h3 id={`avance-${inst.idInstrumento}`}>{inst.nombreInstrumento}</h3>
                                <StatusBadge tone={inst.delPlan ? "primary" : "neutral"} icon={null}>
                                    {inst.delPlan ? "Instrumento del plan" : "Instrumento extra · no cuenta para graduarse"}
                                </StatusBadge>
                            </div>
                            <dl className="avance-inst__datos">
                                <div><dt>Profesor asignado</dt><dd>{inst.nombreProfesor ?? "Sin asignar"}</dd></div>
                                <div>
                                    <dt>Último nivel aprobado</dt>
                                    <dd>
                                        {ultimo ? ultimo.nombre : "Ninguno"}
                                        {ultimo && (
                                            <span className="muted">
                                                {" · "}
                                                {ultimo.estado === "CONVALIDADO" ? "Convalidado" : "Aprobado"}
                                                {ultimo.nombrePeriodo ? ` · ${ultimo.nombrePeriodo}` : ""}
                                            </span>
                                        )}
                                    </dd>
                                </div>
                                <div>
                                    <dt>Le corresponde matricular</dt>
                                    <dd><b>{inst.completado ? "Completó todos los niveles" : (inst.nombreCursoSiguiente ?? "—")}</b></dd>
                                </div>
                            </dl>

                            {inst.niveles.length === 0 ? (
                                <p className="muted">El plan del estudiante no tiene cursos de instrumento marcados.</p>
                            ) : (
                                <ol className="avance-tira" aria-label={`Niveles de ${inst.nombreInstrumento}`}>
                                    {inst.niveles.map((n) => (
                                        <li key={n.idCurso} className={`avance-nivel is-${n.estado.toLowerCase()}`} title={textoEstado(n)}>
                                            <span className="sr-only">{textoEstado(n)}</span>
                                            <b aria-hidden="true">{etiquetaCorta(n)}</b>
                                            <span aria-hidden="true">{marcaNivel(n)}</span>
                                        </li>
                                    ))}
                                </ol>
                            )}
                            {origen && <p className="muted avance-origen">Origen del avance: {origen}</p>}
                        </article>
                    );
                })}
            </div>

            {puedeRegistrarNivel && candidatos.length > 0 && (
                <form className="avance-ingreso" onSubmit={registrarNivel} noValidate>
                    <h3>Registrar nivel de ingreso</h3>
                    <p className="muted">
                        Solo para estudiantes que entran con estudios previos en un instrumento. Los niveles anteriores
                        quedan como convalidados y el cambio se guarda en la bitácora con su nombre.
                    </p>
                    <div className="form-grid">
                        <Field id="ni-instrumento" label="Instrumento" select error={errores.idInstrumento}>
                            <select
                                id="ni-instrumento"
                                className="sg-input sg-select"
                                value={form.idInstrumento}
                                onChange={(e) => { setForm((f) => ({ ...f, idInstrumento: e.target.value, idCurso: "" })); setErrores({}); }}
                                disabled={guardando}
                                aria-invalid={errores.idInstrumento ? "true" : undefined}
                            >
                                <option value="">Seleccione un instrumento</option>
                                {candidatos.map((i) => <option key={i.idInstrumento} value={i.idInstrumento}>{i.nombreInstrumento}</option>)}
                            </select>
                        </Field>
                        <Field id="ni-nivel" label="Empieza en" select error={errores.idCurso}>
                            <select
                                id="ni-nivel"
                                className="sg-input sg-select"
                                value={form.idCurso}
                                onChange={(e) => setForm((f) => ({ ...f, idCurso: e.target.value }))}
                                disabled={guardando || !elegido}
                                aria-invalid={errores.idCurso ? "true" : undefined}
                            >
                                <option value="">{elegido ? "Seleccione el nivel" : "Elija primero el instrumento"}</option>
                                {(elegido?.niveles ?? []).map((n) => <option key={n.idCurso} value={n.idCurso}>{n.nombre}</option>)}
                            </select>
                        </Field>
                        <Field id="ni-motivo" label="Motivo" className="span-all" hint="Obligatorio. Por ejemplo: resultado de la prueba de ubicación del 10/01/2027." error={errores.motivo}>
                            <textarea
                                id="ni-motivo"
                                className="sg-input"
                                maxLength={500}
                                value={form.motivo}
                                onChange={(e) => setForm((f) => ({ ...f, motivo: e.target.value }))}
                                disabled={guardando}
                                aria-invalid={errores.motivo ? "true" : undefined}
                                aria-describedby="ni-motivo-hint"
                            />
                        </Field>
                    </div>
                    <div className="avance-ingreso__acciones">
                        <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando} aria-busy={guardando || undefined}>
                            {guardando && <span className="sg-spinner" aria-hidden="true" />}
                            {guardando ? "Registrando…" : "Registrar nivel de ingreso"}
                        </button>
                    </div>
                </form>
            )}
        </section>
    );
}
