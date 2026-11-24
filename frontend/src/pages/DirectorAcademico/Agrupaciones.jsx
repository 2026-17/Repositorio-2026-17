import { useEffect, useMemo, useState } from "react";
import { ChevronDown, ChevronUp, Pencil, Plus, Save, Users, X } from "lucide-react";

import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";
import { useDialog } from "../../components/dialog/useDialog";
import { mensajeDeExcepcion } from "../../utils/api";
import { formatearHorarios } from "../../utils/matricula";
import CrearAgrupacion from "./CrearAgrupacion";
import QuienesPuedenCampos from "./QuienesPuedenCampos";
import { errorDeEdades, quienesPuedenDesde, quienesPuedenParaEnviar } from "./quienesPueden";
import { solicitar } from "./espaciosApi";
import { ETIQUETA_ESTADO_GRUPO, TONO_ESTADO_GRUPO } from "./gruposComun";
import {
    ETIQUETA_TIPO,
    TIPOS_AGRUPACION,
    crearAgrupacion,
    listarAgrupaciones,
    listarEstudiantesGrupo,
    listarGruposAgrupacion,
    listarProfesoresActivos,
    modificarAgrupacion,
} from "./agrupacionesApi";
import "./espacios.css";
import "./agrupaciones.css";

const FORMULARIO_VACIO = {
    id: null, codigo: "", nombre: "", tipo: "INSTRUMENTAL", descripcion: "", activa: true, idsInstrumento: [],
    quienes: quienesPuedenDesde(null),
};

// "Menores de 6 años · Solo inclusión" (para la tabla del catálogo).
function textoQuienes(a) {
    const partes = [];
    if (a.edadMinima != null && a.edadMaxima != null) partes.push(`De ${a.edadMinima} a ${a.edadMaxima} años`);
    else if (a.edadMaxima != null) partes.push(`Menores de ${a.edadMaxima + 1} años`);
    else if (a.edadMinima != null) partes.push(`De ${a.edadMinima} años o más`);
    if (a.soloInclusion) partes.push("Solo inclusión");
    return partes.join(" · ");
}

// "Coro de niños" -> "CORO-DE-NINOS" (sugerencia; se puede cambiar).
function sugerirCodigo(nombre) {
    return nombre
        .normalize("NFD")
        .replace(/[̀-ͯ]/g, "")
        .toUpperCase()
        .replace(/[^A-Z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "")
        .slice(0, 20);
}

/*
 * Agrupaciones (Orquesta, Banda, Coro de niños...).
 *
 * - Agrupaciones del período: los grupos abiertos con su horario, profesores
 *   y matriculados. «Crear agrupación» abre el formulario del mockup 9.
 * - Catálogo: nombre, tipo e instrumentos permitidos de cada agrupación.
 */
export default function Agrupaciones() {
    const dialog = useDialog();

    const [periodos, setPeriodos] = useState([]);
    const [instrumentos, setInstrumentos] = useState([]);
    const [profesores, setProfesores] = useState([]);
    const [agrupaciones, setAgrupaciones] = useState([]);

    const [idPeriodo, setIdPeriodo] = useState("");
    const [grupos, setGrupos] = useState({ clave: null, lista: [] });
    const [recarga, setRecarga] = useState(0);

    const [formulario, setFormulario] = useState(null);
    const [erroresFormulario, setErroresFormulario] = useState({});
    const [guardando, setGuardando] = useState(false);

    // null = lista; { grupo } = crear o editar el grupo.
    const [editor, setEditor] = useState(null);
    const [abierto, setAbierto] = useState(null);
    const [estudiantes, setEstudiantes] = useState({});

    // Catálogos.
    useEffect(() => {
        let activo = true;
        Promise.all([
            solicitar("/periodos-academicos", {}, "No fue posible cargar los períodos."),
            solicitar("/instrumentos", {}, "No fue posible cargar los instrumentos."),
            listarProfesoresActivos(),
        ])
            .then(([listaPeriodos, listaInstrumentos, listaProfesores]) => {
                if (!activo) return;
                setPeriodos(listaPeriodos ?? []);
                setInstrumentos(listaInstrumentos ?? []);
                setProfesores((listaProfesores ?? []).filter((p) => p.activo !== false));
                if (listaPeriodos?.length) setIdPeriodo((actual) => actual || String(listaPeriodos[0].id));
            })
            .catch((e) => dialog.error(mensajeDeExcepcion(e, "No fue posible cargar la información necesaria.")));
        return () => { activo = false; };
    }, [dialog]);

    useEffect(() => {
        let activo = true;
        listarAgrupaciones()
            .then((lista) => { if (activo) setAgrupaciones(lista ?? []); })
            .catch((e) => dialog.error(mensajeDeExcepcion(e, "No fue posible cargar las agrupaciones.")));
        return () => { activo = false; };
    }, [dialog, recarga]);

    const clave = `${idPeriodo}|${recarga}`;

    useEffect(() => {
        if (!idPeriodo) return undefined;
        let activo = true;
        listarGruposAgrupacion(idPeriodo)
            .then((lista) => { if (activo) setGrupos({ clave, lista: lista ?? [] }); })
            .catch((e) => {
                if (!activo) return;
                setGrupos({ clave, lista: [] });
                dialog.error(mensajeDeExcepcion(e, "No fue posible cargar las agrupaciones del período."));
            });
        return () => { activo = false; };
    }, [clave, idPeriodo, dialog]);

    const cargando = Boolean(idPeriodo) && grupos.clave !== clave;
    const listaGrupos = useMemo(() => (cargando ? [] : grupos.lista), [cargando, grupos]);

    async function verEstudiantes(grupo) {
        if (abierto === grupo.idGrupo) {
            setAbierto(null);
            return;
        }
        setAbierto(grupo.idGrupo);
        try {
            const lista = await listarEstudiantesGrupo(grupo.idGrupo);
            setEstudiantes((prev) => ({ ...prev, [grupo.idGrupo]: lista ?? [] }));
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No fue posible cargar los estudiantes."));
        }
    }

    /* ---------- Catálogo ---------- */

    function abrirFormulario(agrupacion) {
        setErroresFormulario({});
        setFormulario(agrupacion
            ? {
                id: agrupacion.id,
                codigo: agrupacion.codigo,
                nombre: agrupacion.nombre,
                tipo: agrupacion.tipo,
                descripcion: agrupacion.descripcion ?? "",
                activa: agrupacion.activa,
                idsInstrumento: agrupacion.instrumentos.map((i) => i.id),
                quienes: quienesPuedenDesde(agrupacion),
                cantidadGrupos: agrupacion.cantidadGrupos,
                codigoTocado: true,
            }
            : { ...FORMULARIO_VACIO });
    }

    function cambiar(campo, valor) {
        setFormulario((f) => {
            const siguiente = { ...f, [campo]: valor };
            if (campo === "nombre" && !f.codigoTocado) siguiente.codigo = sugerirCodigo(valor);
            if (campo === "codigo") siguiente.codigoTocado = true;
            return siguiente;
        });
        setErroresFormulario((e) => ({ ...e, [campo]: undefined }));
    }

    async function guardarAgrupacion(evento) {
        evento.preventDefault();
        const errores = {};
        if (!formulario.nombre.trim()) errores.nombre = "Escriba el nombre.";
        if (!formulario.codigo.trim()) errores.codigo = "Escriba el código.";
        else if (!/^[A-Za-z0-9-]+$/.test(formulario.codigo)) errores.codigo = "Use solo letras sin tilde, números y guiones.";
        const errorQuienes = errorDeEdades(formulario.quienes);
        if (errorQuienes) errores.quienes = errorQuienes;
        setErroresFormulario(errores);
        if (Object.keys(errores).length > 0) return;

        setGuardando(true);
        try {
            const payload = {
                codigo: formulario.codigo.trim(),
                nombre: formulario.nombre.trim(),
                tipo: formulario.tipo,
                descripcion: formulario.descripcion.trim() || null,
                activa: formulario.activa,
                idsInstrumento: formulario.idsInstrumento,
                ...quienesPuedenParaEnviar(formulario.quienes),
            };
            if (formulario.id) await modificarAgrupacion(formulario.id, payload);
            else await crearAgrupacion(payload);
            await dialog.exito(formulario.id ? "La agrupación se modificó." : "La agrupación se creó.");
            setFormulario(null);
            setRecarga((n) => n + 1);
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No fue posible guardar la agrupación."));
        } finally {
            setGuardando(false);
        }
    }

    if (editor) {
        return (
            <div className="espacios">
                <CrearAgrupacion
                    periodos={periodos}
                    agrupaciones={agrupaciones}
                    instrumentos={instrumentos}
                    profesores={profesores}
                    grupo={editor.grupo}
                    onCancel={() => setEditor(null)}
                    onSuccess={(guardado) => {
                        setIdPeriodo(String(guardado.idPeriodo));
                        setEditor(null);
                        setRecarga((n) => n + 1);
                    }}
                />
            </div>
        );
    }

    return (
        <div className="espacios">
            <PageHead
                title="Agrupaciones"
                sub="Orquestas, bandas y coros donde tocan juntos estudiantes de distintos niveles. El sistema calcula el nivel de cada estudiante en cada agrupación."
                actions={(
                    <button type="button" className="sg-btn sg-btn--primary" onClick={() => setEditor({ grupo: null })} disabled={agrupaciones.length === 0}>
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Crear agrupación
                    </button>
                )}
            />

            <section className="panel" aria-labelledby="t-periodo" aria-busy={cargando || undefined}>
                <div className="panel-head agrupaciones-cabecera">
                    <h2 id="t-periodo">Agrupaciones del período</h2>
                    <Field id="f-periodo-agr" label="Período" select className="agrupaciones-filtro">
                        <select id="f-periodo-agr" className="sg-input sg-select" value={idPeriodo} onChange={(e) => setIdPeriodo(e.target.value)}>
                            <option value="">Seleccione un período</option>
                            {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                        </select>
                    </Field>
                </div>

                {listaGrupos.length === 0 && !cargando ? (
                    <EmptyState
                        icon={Users}
                        title="Sin agrupaciones en este período"
                        description={agrupaciones.length === 0
                            ? "Primero cree las agrupaciones en el catálogo de abajo y luego ábralas en el período con «Crear agrupación»."
                            : "Use «Crear agrupación» para abrir una agrupación en este período."}
                    />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards">
                            <caption className="sr-only">Agrupaciones del período</caption>
                            <thead>
                                <tr>
                                    <th scope="col">Agrupación</th>
                                    <th scope="col">Horario</th>
                                    <th scope="col">Profesores</th>
                                    <th scope="col" className="num">Matriculados</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {listaGrupos.map((g) => [
                                    <tr key={g.idGrupo}>
                                        <td className="c-main">
                                            <b>{g.nombreAgrupacion}</b>
                                            <span className="sub">{g.codigo} · {ETIQUETA_TIPO[g.tipoAgrupacion] ?? g.tipoAgrupacion}</span>
                                        </td>
                                        <td data-label="Horario">{formatearHorarios(g.horarios.map((h) => ({ ...h, horaInicio: String(h.horaInicio).slice(0, 5), horaFin: String(h.horaFin).slice(0, 5) })))}</td>
                                        <td data-label="Profesores">
                                            {g.profesores.length === 0 ? <span className="muted">Sin profesores</span> : g.profesores.map((p) => (
                                                <span key={p.idProfesor} className="agrupaciones-prof">
                                                    {p.nombre}{p.responsable && <StatusBadge tone="primary" icon={null}>Responsable</StatusBadge>}
                                                </span>
                                            ))}
                                        </td>
                                        <td className="num tnum" data-label="Matriculados">{g.matriculados}/{g.cupoMaximo}</td>
                                        <td data-label="Estado"><StatusBadge tone={TONO_ESTADO_GRUPO[g.estado] ?? "neutral"}>{ETIQUETA_ESTADO_GRUPO[g.estado] ?? g.estado}</StatusBadge></td>
                                        <td className="num agrupaciones-acciones-fila">
                                            <button type="button" className="sg-btn sg-btn--ghost sg-btn--sm" onClick={() => verEstudiantes(g)} aria-expanded={abierto === g.idGrupo} aria-controls={`est-${g.idGrupo}`}>
                                                {abierto === g.idGrupo ? <ChevronUp size={16} aria-hidden="true" /> : <ChevronDown size={16} aria-hidden="true" />}
                                                Estudiantes
                                            </button>
                                            {["PLANIFICADO", "PUBLICADO"].includes(g.estado) && (
                                                <button type="button" className="sg-icon-btn" aria-label={`Editar ${g.nombreAgrupacion} (${g.codigo})`} onClick={() => setEditor({ grupo: g })}>
                                                    <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                                </button>
                                            )}
                                        </td>
                                    </tr>,
                                    abierto === g.idGrupo && (
                                        <tr key={`${g.idGrupo}-est`} id={`est-${g.idGrupo}`} className="agrupaciones-detalle">
                                            <td colSpan={6}>
                                                {!estudiantes[g.idGrupo] ? <span className="muted">Cargando…</span>
                                                    : estudiantes[g.idGrupo].length === 0 ? <span className="muted">Todavía no hay estudiantes matriculados.</span>
                                                        : (
                                                            <ul className="agrupaciones-estudiantes">
                                                                {estudiantes[g.idGrupo].map((e) => (
                                                                    <li key={e.idEstudiante}>
                                                                        <b>{e.nombreCompleto}</b>
                                                                        <span>{e.etiqueta}</span>
                                                                        {e.calificaComoCoro && <StatusBadge tone="info" icon={null}>Se califica como coro</StatusBadge>}
                                                                    </li>
                                                                ))}
                                                            </ul>
                                                        )}
                                            </td>
                                        </tr>
                                    ),
                                ])}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>

            <section className="panel" aria-labelledby="t-catalogo">
                <div className="panel-head">
                    <div>
                        <h2 id="t-catalogo">Catálogo de agrupaciones</h2>
                        <p className="panel-head-sub">Instrumental cuenta para Conjunto Instrumental I a XII; Coral, para Coro I a XIV.</p>
                    </div>
                    {!formulario && (
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={() => abrirFormulario(null)}>
                            <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                            Nueva agrupación
                        </button>
                    )}
                </div>

                {formulario && (
                    <form className="panel-body agrupaciones-formulario" onSubmit={guardarAgrupacion} noValidate>
                        <h3>{formulario.id ? `Editar «${formulario.nombre}»` : "Nueva agrupación"}</h3>
                        <div className="form-grid">
                            <Field id="cat-nombre" label="Nombre" error={erroresFormulario.nombre}>
                                <input id="cat-nombre" className="sg-input" maxLength={100} value={formulario.nombre} onChange={(e) => cambiar("nombre", e.target.value)} aria-invalid={erroresFormulario.nombre ? "true" : undefined} aria-describedby={erroresFormulario.nombre ? "cat-nombre-error" : undefined} placeholder="Ej. Orquesta" />
                            </Field>
                            <Field id="cat-codigo" label="Código" error={erroresFormulario.codigo} hint={formulario.cantidadGrupos > 0 ? "Ya tiene grupos: el código no se puede cambiar." : "Se usa en el código de cada grupo (AGR-código-1)."}>
                                <input id="cat-codigo" className="sg-input" maxLength={20} value={formulario.codigo} onChange={(e) => cambiar("codigo", e.target.value.toUpperCase())} disabled={formulario.cantidadGrupos > 0} aria-invalid={erroresFormulario.codigo ? "true" : undefined} aria-describedby={erroresFormulario.codigo ? "cat-codigo-error" : "cat-codigo-hint"} />
                            </Field>
                            <Field id="cat-tipo" label="Tipo" select hint={formulario.cantidadGrupos > 0 ? "Ya tiene grupos: el tipo no se puede cambiar." : TIPOS_AGRUPACION.find((t) => t.value === formulario.tipo)?.ayuda}>
                                <select id="cat-tipo" className="sg-input sg-select" value={formulario.tipo} onChange={(e) => cambiar("tipo", e.target.value)} disabled={formulario.cantidadGrupos > 0} aria-describedby="cat-tipo-hint">
                                    {TIPOS_AGRUPACION.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                                </select>
                            </Field>
                            <Field id="cat-descripcion" label="Descripción" optional>
                                <input id="cat-descripcion" className="sg-input" maxLength={500} value={formulario.descripcion} onChange={(e) => cambiar("descripcion", e.target.value)} />
                            </Field>
                        </div>

                        <fieldset className="fieldset agrupaciones-instrumentos" aria-describedby="cat-instr-hint">
                            <legend className="sg-field__label">Instrumentos permitidos</legend>
                            <p className="sg-field__hint" id="cat-instr-hint">Si no marca ninguno, la aceptan estudiantes de cualquier instrumento.</p>
                            <div className="agrupaciones-instrumentos__lista">
                                {instrumentos.map((i) => (
                                    <label key={i.id} className="choice agrupaciones-instrumento">
                                        <input
                                            type="checkbox"
                                            checked={formulario.idsInstrumento.includes(i.id)}
                                            onChange={() => cambiar("idsInstrumento", formulario.idsInstrumento.includes(i.id)
                                                ? formulario.idsInstrumento.filter((x) => x !== i.id)
                                                : [...formulario.idsInstrumento, i.id])}
                                        />
                                        <span>{i.nombre}</span>
                                    </label>
                                ))}
                            </div>
                        </fieldset>

                        <QuienesPuedenCampos
                            idBase="cat-quienes"
                            valor={formulario.quienes}
                            onChange={(valor) => cambiar("quienes", valor)}
                            error={erroresFormulario.quienes}
                            disabled={guardando}
                        />

                        <label className="choice agrupaciones-activa">
                            <input type="checkbox" checked={formulario.activa} onChange={(e) => cambiar("activa", e.target.checked)} />
                            <span>Activa (se puede abrir en un período)</span>
                        </label>

                        <div className="pasos-acciones">
                            <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando}>
                                {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <Save size={18} strokeWidth={1.75} aria-hidden="true" />}
                                Guardar
                            </button>
                            <button type="button" className="sg-btn sg-btn--ghost" onClick={() => setFormulario(null)} disabled={guardando}>
                                <X size={18} strokeWidth={1.75} aria-hidden="true" />
                                Cancelar
                            </button>
                        </div>
                    </form>
                )}

                {agrupaciones.length === 0 ? (
                    <EmptyState icon={Users} title="Sin agrupaciones" description="Cree la primera con «Nueva agrupación»." compact />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards">
                            <caption className="sr-only">Catálogo de agrupaciones</caption>
                            <thead>
                                <tr>
                                    <th scope="col">Agrupación</th>
                                    <th scope="col">Tipo</th>
                                    <th scope="col">Instrumentos permitidos</th>
                                    <th scope="col" className="num">Grupos</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {agrupaciones.map((a) => (
                                    <tr key={a.id}>
                                        <td className="c-main"><b>{a.nombre}</b><span className="sub">{a.codigo}</span></td>
                                        <td data-label="Tipo">{ETIQUETA_TIPO[a.tipo] ?? a.tipo}</td>
                                        <td data-label="Instrumentos">
                                            {a.instrumentos.length === 0 ? "Cualquier instrumento" : a.instrumentos.map((i) => i.nombre).join(", ")}
                                            {textoQuienes(a) && <span className="sub">{textoQuienes(a)}</span>}
                                        </td>
                                        <td className="num tnum" data-label="Grupos">{a.cantidadGrupos}</td>
                                        <td data-label="Estado">
                                            <StatusBadge tone={a.activa ? "success" : "neutral"}>{a.activa ? "Activa" : "Inactiva"}</StatusBadge>
                                        </td>
                                        <td className="num">
                                            <button type="button" className="sg-icon-btn" aria-label={`Editar ${a.nombre}`} onClick={() => abrirFormulario(a)}>
                                                <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </div>
    );
}
