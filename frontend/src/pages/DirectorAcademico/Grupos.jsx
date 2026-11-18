import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Ban, CircleUserRound, Pencil, Plus, Search, Trash2, UserX, Users, UsersRound, X } from "lucide-react";
import { API_URL } from "../../config/api";
import "./cursos.css";
import "./programas.css";
import "./grupos.css";
import FormularioGrupo from "./FormularioGrupo";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";
import StatusBadge from "../../components/ui/StatusBadge";
import { paginar } from "../../components/ui/utilidades";
import { ATAJO_BUSCADOR, useAtajoBuscador } from "../../hooks/useAtajoBuscador";
import { useDialog } from "../../components/dialog/useDialog";
import { leerError, mensajeDeExcepcion } from "../../utils/api";
import { ETIQUETA_ESTADO_GRUPO, ETIQUETA_MODALIDAD, TONO_ESTADO_GRUPO, aHoraCorta, resumenHorario } from "./gruposComun";

const TAMANO_PAGINA = 10;

const VIGENTES = ["PLANIFICADO", "PUBLICADO", "EN_CURSO"];
const esVigente = (g) => VIGENTES.includes(g.estado);
const estaLleno = (g) => g.cupoMaximo > 0 && g.inscritos >= g.cupoMaximo;

const FILTROS = [
    { valor: "vigentes", etiqueta: "Vigentes", aplica: esVigente },
    { valor: "sinProfesor", etiqueta: "Sin profesor", aplica: (g) => esVigente(g) && !g.idProfesor },
    { valor: "llenos", etiqueta: "Llenos", aplica: (g) => esVigente(g) && estaLleno(g) },
    { valor: "finalizados", etiqueta: "Cerrados y cancelados", aplica: (g) => !esVigente(g) },
    { valor: "todos", etiqueta: "Todos", aplica: () => true },
];

async function solicitar(ruta, opciones = {}, porDefecto = "No fue posible completar la operación.") {
    const token = localStorage.getItem("token");
    const response = await fetch(`${API_URL}${ruta}`, {
        ...opciones,
        headers: {
            Authorization: `Bearer ${token}`,
            ...(opciones.body ? { "Content-Type": "application/json" } : {}),
        },
    });
    if (!response.ok) {
        const error = await leerError(response, porDefecto);
        throw new Error(error.message);
    }
    return response.status === 204 ? null : response.json();
}

function mostrarFormulario() {
    requestAnimationFrame(() =>
        document.getElementById("form-grupo")?.scrollIntoView({ behavior: "smooth", block: "start" })
    );
}

// Payload de PUT a partir de un grupo del listado (para cancelarlo).
function payloadDesdeGrupo(g, cambios) {
    return {
        idCurso: g.idCurso,
        idPeriodo: g.idPeriodo,
        codigo: g.codigo,
        cupoMaximo: g.cupoMaximo,
        idProfesor: g.idProfesor,
        modalidad: g.modalidad,
        aula: g.aula,
        fechaInicio: g.fechaInicio,
        fechaFin: g.fechaFin,
        estado: g.estado,
        horarios: (g.horarios || []).map((h) => ({
            diaSemana: h.diaSemana,
            horaInicio: aHoraCorta(h.horaInicio),
            horaFin: aHoraCorta(h.horaFin),
            aula: h.aula,
        })),
        ...cambios,
    };
}

/*
 * Grupos: resumen, lista, creación, modificación y eliminación en una
 * sola pantalla. Un grupo con matrículas o clases registradas no se
 * elimina (se perdería historial): se ofrece cancelarlo.
 */
function Grupos() {
    const dialog = useDialog();
    const location = useLocation();
    const navigate = useNavigate();
    useAtajoBuscador("txtBuscarGrupo");

    const [grupos, setGrupos] = useState([]);
    const [cargando, setCargando] = useState(true);
    const [busqueda, setBusqueda] = useState("");
    const [filtro, setFiltro] = useState("vigentes");
    const [idPeriodo, setIdPeriodo] = useState("");
    const [pagina, setPagina] = useState(1);

    // La ruta vieja /grupos/crear llega con { nuevoGrupo: true }.
    const [formAbierto, setFormAbierto] = useState(() => Boolean(location.state?.nuevoGrupo));
    const [grupoEditando, setGrupoEditando] = useState(null);
    const [procesando, setProcesando] = useState(null);

    useEffect(() => {
        if (location.state?.nuevoGrupo) navigate(location.pathname, { replace: true, state: null });
    }, [location, navigate]);

    useEffect(() => {
        let activo = true;
        solicitar("/grupos", {}, "No fue posible cargar los grupos.")
            .then((datos) => { if (activo) setGrupos(Array.isArray(datos) ? datos : []); })
            .catch((e) => { if (activo) dialog.error(mensajeDeExcepcion(e, "No fue posible cargar los grupos.")); })
            .finally(() => { if (activo) setCargando(false); });
        return () => { activo = false; };
    }, [dialog]);

    async function recargar() {
        setCargando(true);
        try {
            const datos = await solicitar("/grupos", {}, "No fue posible cargar los grupos.");
            setGrupos(Array.isArray(datos) ? datos : []);
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No fue posible cargar los grupos."));
        } finally {
            setCargando(false);
        }
    }

    // Períodos que tienen grupos, para el filtro (del más reciente al más antiguo).
    const periodos = useMemo(() => {
        const mapa = new Map();
        grupos.forEach((g) => {
            if (!mapa.has(g.idPeriodo)) mapa.set(g.idPeriodo, { id: g.idPeriodo, nombre: g.nombrePeriodo, inicio: g.fechaInicioPeriodo || "" });
        });
        return [...mapa.values()].sort((a, b) => b.inicio.localeCompare(a.inicio));
    }, [grupos]);

    const delPeriodo = useMemo(
        () => grupos.filter((g) => !idPeriodo || String(g.idPeriodo) === idPeriodo),
        [grupos, idPeriodo]
    );

    const resumen = useMemo(() => {
        const vigentes = delPeriodo.filter(esVigente);
        return {
            vigentes: vigentes.length,
            inscritos: vigentes.reduce((t, g) => t + g.inscritos, 0),
            cupos: vigentes.reduce((t, g) => t + g.cupoMaximo, 0),
            sinProfesor: vigentes.filter((g) => !g.idProfesor).length,
            llenos: vigentes.filter(estaLleno).length,
        };
    }, [delPeriodo]);

    const filtrados = useMemo(() => {
        const regla = FILTROS.find((f) => f.valor === filtro)?.aplica || (() => true);
        const texto = busqueda.trim().toLowerCase();
        return delPeriodo.filter((g) =>
            regla(g) &&
            (!texto ||
                (g.codigo || "").toLowerCase().includes(texto) ||
                (g.nombreCurso || "").toLowerCase().includes(texto) ||
                (g.codigoCurso || "").toLowerCase().includes(texto) ||
                (g.nombreProfesor || "").toLowerCase().includes(texto) ||
                (g.aula || "").toLowerCase().includes(texto))
        );
    }, [delPeriodo, filtro, busqueda]);

    const filasPagina = paginar(filtrados, pagina, TAMANO_PAGINA);

    function cambiarBusqueda(v) { setBusqueda(v); setPagina(1); }
    function cambiarFiltro(v) { setFiltro(v); setPagina(1); }
    function cambiarPeriodo(v) { setIdPeriodo(v); setPagina(1); }

    function abrirNuevo() {
        setGrupoEditando(null);
        setFormAbierto(true);
        mostrarFormulario();
    }

    function abrirEditar(grupo) {
        setGrupoEditando(grupo);
        setFormAbierto(true);
        mostrarFormulario();
    }

    function cerrarFormulario() {
        setFormAbierto(false);
        setGrupoEditando(null);
    }

    function formularioGuardado() {
        cerrarFormulario();
        void recargar();
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    async function eliminar(grupo) {
        const nombre = `${grupo.codigo} (${grupo.nombreCurso})`;

        // Con historial no se elimina: se ofrece cancelarlo.
        if (!grupo.eliminable) {
            if (!grupo.editable) {
                await dialog.aviso(`El grupo ${nombre} tiene matrículas o clases registradas y ya está ${ETIQUETA_ESTADO_GRUPO[grupo.estado]?.toLowerCase()}: se conserva como historial y no se puede eliminar.`);
                return;
            }

            const cancelar = await dialog.confirmar(
                `${nombre} tiene matrículas o clases registradas, así que no se puede eliminar sin perder historial académico. ` +
                (grupo.inscritos > 0
                    ? `Puede cancelarlo: dejará de impartirse y sus ${grupo.inscritos} estudiantes inscritos quedarán en un grupo cancelado.`
                    : "Puede cancelarlo: dejará de impartirse y se conservará su información."),
                { titulo: "¿Cancelar el grupo en lugar de eliminarlo?", textoAceptar: "Sí, cancelar grupo", textoCancelar: "No, volver", peligro: true }
            );
            if (!cancelar) return;

            setProcesando(grupo.id);
            try {
                await solicitar(`/grupos/${grupo.id}`, { method: "PUT", body: JSON.stringify(payloadDesdeGrupo(grupo, { estado: "CANCELADO" })) }, "No fue posible cancelar el grupo.");
                dialog.exito(`El grupo "${grupo.codigo}" fue cancelado.`);
                if (grupoEditando?.id === grupo.id) cerrarFormulario();
                await recargar();
            } catch (e) {
                dialog.error(mensajeDeExcepcion(e, "No fue posible cancelar el grupo."));
            } finally {
                setProcesando(null);
            }
            return;
        }

        const confirmado = await dialog.confirmar(
            `Se eliminará ${nombre} junto con su horario. Esta acción no se puede deshacer.`,
            { titulo: "¿Eliminar este grupo?", textoAceptar: "Sí, eliminar", peligro: true }
        );
        if (!confirmado) return;

        setProcesando(grupo.id);
        try {
            await solicitar(`/grupos/${grupo.id}`, { method: "DELETE" }, "No fue posible eliminar el grupo.");
            dialog.exito(`El grupo "${grupo.codigo}" fue eliminado.`);
            if (grupoEditando?.id === grupo.id) cerrarFormulario();
            await recargar();
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No fue posible eliminar el grupo."));
        } finally {
            setProcesando(null);
        }
    }

    const etiquetaFiltro = FILTROS.find((f) => f.valor === filtro)?.etiqueta;
    const porcentajeOcupacion = resumen.cupos > 0 ? Math.round((resumen.inscritos / resumen.cupos) * 100) : 0;

    return (
        <div className="cursos grupos">
            <PageHead
                title="Grupos"
                sub="Abra, consulte y modifique los grupos de cada curso por período: cupo, profesor, modalidad y horario. Un grupo con matrículas no se elimina: se cancela."
                actions={(
                    <button type="button" className="sg-btn sg-btn--primary" onClick={abrirNuevo}>
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Nuevo grupo
                    </button>
                )}
            />

            <ul className="programa-resumen" aria-label="Resumen de grupos">
                <Tarjeta icon={UsersRound} valor={resumen.vigentes} etiqueta="Grupos vigentes" activo={filtro === "vigentes"} onClick={() => cambiarFiltro("vigentes")} cargando={cargando} />
                <Tarjeta icon={Users} valor={`${resumen.inscritos} / ${resumen.cupos}`} etiqueta={`Cupos ocupados · ${porcentajeOcupacion}%`} cargando={cargando} />
                <Tarjeta icon={UserX} valor={resumen.sinProfesor} etiqueta="Sin profesor" tono={resumen.sinProfesor > 0 ? "warning" : undefined} activo={filtro === "sinProfesor"} onClick={() => cambiarFiltro("sinProfesor")} cargando={cargando} />
                <Tarjeta icon={Ban} valor={resumen.llenos} etiqueta="Grupos llenos" activo={filtro === "llenos"} onClick={() => cambiarFiltro("llenos")} cargando={cargando} />
            </ul>

            {formAbierto && (
                <section id="form-grupo" className="form-incrustado">
                    <FormularioGrupo
                        key={grupoEditando ? grupoEditando.id : "nuevo"}
                        grupo={grupoEditando}
                        onCancel={cerrarFormulario}
                        onSuccess={formularioGuardado}
                    />
                </section>
            )}

            <div className="panel">
                <div className="filters programa-filtros" role="search">
                    <Field id="txtBuscarGrupo" label="Buscar" icon={Search} className="f-search">
                        <input
                            id="txtBuscarGrupo"
                            type="search"
                            className="sg-input"
                            placeholder="Código, curso, profesor o aula (Ctrl+K)"
                            value={busqueda}
                            onChange={(e) => cambiarBusqueda(e.target.value)}
                            aria-keyshortcuts={ATAJO_BUSCADOR}
                        />
                    </Field>
                    <Field id="selPeriodoGrupo" label="Período" select>
                        <select id="selPeriodoGrupo" className="sg-input sg-select" value={idPeriodo} onChange={(e) => cambiarPeriodo(e.target.value)}>
                            <option value="">Todos los períodos</option>
                            {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                        </select>
                    </Field>
                </div>

                <div className="grupos-tabs">
                    <div className="tabs" role="tablist" aria-label="Filtrar por estado">
                        {FILTROS.map((f) => (
                            <button key={f.valor} type="button" role="tab" aria-selected={filtro === f.valor} onClick={() => cambiarFiltro(f.valor)}>
                                {f.etiqueta}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="chips">
                    <span className="result-count" aria-live="polite">
                        <b>{cargando ? "…" : filtrados.length}</b>
                        {filtrados.length === 1 ? " grupo" : " grupos"}
                        {filtro !== "todos" && <> · {etiquetaFiltro?.toLowerCase()}</>}
                    </span>
                    {busqueda.trim() && (
                        <span className="chip">
                            Búsqueda: {busqueda.trim()}
                            <button type="button" aria-label="Quitar filtro de búsqueda" onClick={() => cambiarBusqueda("")}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                    {idPeriodo && (
                        <span className="chip">
                            Período: {periodos.find((p) => String(p.id) === idPeriodo)?.nombre}
                            <button type="button" aria-label="Quitar filtro de período" onClick={() => cambiarPeriodo("")}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                </div>

                {cargando && grupos.length === 0 ? (
                    <p className="muted lista-cargando" role="status">Cargando grupos...</p>
                ) : filtrados.length === 0 ? (
                    <EmptyState
                        icon={grupos.length === 0 ? UsersRound : Search}
                        title={grupos.length === 0 ? "Todavía no hay grupos" : busqueda.trim() ? `Ningún grupo coincide con «${busqueda.trim()}»` : `No hay grupos en «${etiquetaFiltro}»`}
                        description={grupos.length === 0 ? "Abra el primero con el botón «Nuevo grupo»." : "Cambie el filtro, el período o la búsqueda."}
                        action={grupos.length === 0 ? (
                            <button type="button" className="sg-btn sg-btn--primary" onClick={abrirNuevo}>
                                <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                                Nuevo grupo
                            </button>
                        ) : undefined}
                    />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards">
                            <caption className="sr-only">Grupos, página {pagina}</caption>
                            <thead>
                                <tr>
                                    <th scope="col">Grupo</th>
                                    <th scope="col">Período</th>
                                    <th scope="col">Profesor</th>
                                    <th scope="col">Horario</th>
                                    <th scope="col">Cupo</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="actions"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {filasPagina.map((g) => {
                                    const ocupacion = g.cupoMaximo > 0 ? Math.min(100, Math.round((g.inscritos / g.cupoMaximo) * 100)) : 0;
                                    const horario = resumenHorario(g.horarios);
                                    return (
                                        <tr key={g.id} className={esVigente(g) ? undefined : "programa-inactivo"}>
                                            <td className="c-main">
                                                <div className="who">
                                                    <span className="avatar avatar--sm avatar--icono" aria-hidden="true">
                                                        <UsersRound size={16} strokeWidth={1.75} />
                                                    </span>
                                                    <div>
                                                        <b className="tnum">{g.codigo}</b>
                                                        <span>{g.nombreCurso}</span>
                                                        <span className="grupos-meta">{ETIQUETA_MODALIDAD[g.modalidad] || g.modalidad}{g.aula ? ` · ${g.aula}` : ""}</span>
                                                    </div>
                                                </div>
                                            </td>
                                            <td data-label="Período">{g.nombrePeriodo}</td>
                                            <td data-label="Profesor">
                                                {g.nombreProfesor ? (
                                                    <span className="grupos-profesor"><CircleUserRound size={16} strokeWidth={1.75} aria-hidden="true" /> {g.nombreProfesor}</span>
                                                ) : (
                                                    <StatusBadge tone="warning">Sin asignar</StatusBadge>
                                                )}
                                            </td>
                                            <td data-label="Horario">
                                                <ul className="grupos-horario">
                                                    {horario.length > 0 ? horario.map((h) => <li key={h} className="tnum">{h}</li>) : <li className="muted">Sin horario</li>}
                                                </ul>
                                            </td>
                                            <td data-label="Cupo">
                                                <div className="grupos-cupo">
                                                    <span className="tnum"><b>{g.inscritos}</b> / {g.cupoMaximo}</span>
                                                    <span className={`grupos-cupo__barra${estaLleno(g) ? " is-lleno" : ""}`} role="img" aria-label={`${ocupacion}% ocupado`}>
                                                        <i style={{ width: `${ocupacion}%` }} />
                                                    </span>
                                                </div>
                                            </td>
                                            <td>
                                                <StatusBadge tone={TONO_ESTADO_GRUPO[g.estado] || "neutral"}>
                                                    {ETIQUETA_ESTADO_GRUPO[g.estado] || g.estado}
                                                </StatusBadge>
                                            </td>
                                            <td className="actions">
                                                <div className="row-actions">
                                                    {g.editable && (
                                                        <button type="button" className="sg-btn sg-btn--ghost sg-btn--sm" onClick={() => abrirEditar(g)} aria-label={`Modificar grupo ${g.codigo}`}>
                                                            <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                                            Modificar
                                                        </button>
                                                    )}
                                                    {(g.eliminable || g.editable) && (
                                                        <button
                                                            type="button"
                                                            className="sg-btn sg-btn--ghost sg-btn--sm grupos-eliminar"
                                                            onClick={() => eliminar(g)}
                                                            disabled={procesando === g.id}
                                                            aria-label={`${g.eliminable ? "Eliminar" : "Cancelar"} grupo ${g.codigo}`}
                                                        >
                                                            {g.eliminable ? <Trash2 size={16} strokeWidth={1.75} aria-hidden="true" /> : <Ban size={16} strokeWidth={1.75} aria-hidden="true" />}
                                                            {g.eliminable ? "Eliminar" : "Cancelar"}
                                                        </button>
                                                    )}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                {!cargando && filtrados.length > 0 && (
                    <div className="table-foot">
                        <Pagination page={pagina} pageSize={TAMANO_PAGINA} total={filtrados.length} onChange={setPagina} />
                    </div>
                )}
            </div>
        </div>
    );
}

function Tarjeta({ icon: Icono, valor, etiqueta, tono, activo, onClick, cargando }) {
    const contenido = (
        <>
            <span className={`programa-resumen__icono${tono ? ` is-${tono}` : ""}`} aria-hidden="true">
                <Icono size={20} strokeWidth={1.75} />
            </span>
            <span className="programa-resumen__texto">
                <b className="tnum">{cargando ? "…" : valor}</b>
                <span>{etiqueta}</span>
            </span>
        </>
    );

    return (
        <li>
            {onClick ? (
                <button type="button" className={`programa-resumen__item${activo ? " is-activo" : ""}`} onClick={onClick} aria-pressed={activo}>
                    {contenido}
                </button>
            ) : (
                <div className="programa-resumen__item is-estatico">{contenido}</div>
            )}
        </li>
    );
}

export default Grupos;
