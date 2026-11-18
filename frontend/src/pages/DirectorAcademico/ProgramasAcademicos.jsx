import { useEffect, useMemo, useState } from "react";
import { BookOpen, CircleOff, GraduationCap, Library, Pencil, Plus, Power, Search, TriangleAlert, X } from "lucide-react";
import "./cursos.css";
import "./programas.css";
import CrearProgramaAcademico from "./CrearProgramaAcademico";
import EditarProgramaAcademico from "./EditarProgramaAcademico";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";
import StatusBadge from "../../components/ui/StatusBadge";
import { paginar } from "../../components/ui/utilidades";
import { ATAJO_BUSCADOR, useAtajoBuscador } from "../../hooks/useAtajoBuscador";
import { useDialog } from "../../components/dialog/useDialog";
import { cambiarEstadoPrograma, fechaLegible, obtenerProgramasGestion } from "./programasApi";

const TAMANO_PAGINA = 10;

/*
 * Filtros rápidos. Las tarjetas de resumen de arriba también los aplican
 * (al hacer clic en «Sin plan vigente» se ven solo esos programas).
 */
const FILTROS = [
    { valor: "activos", etiqueta: "Activos", aplica: (p) => p.activo },
    { valor: "sinPlan", etiqueta: "Sin plan vigente", aplica: (p) => p.activo && !p.planVigente },
    { valor: "inactivos", etiqueta: "Inactivos", aplica: (p) => !p.activo },
    { valor: "todos", etiqueta: "Todos", aplica: () => true },
];

function mostrarFormulario() {
    requestAnimationFrame(() =>
        document.getElementById("form-programa")?.scrollIntoView({ behavior: "smooth", block: "start" })
    );
}

/*
 * Programas académicos: resumen, lista, creación (asistente), modificación
 * y activación/desactivación en una sola pantalla. Un programa no se liga
 * a ningún período académico.
 */
function ProgramasAcademicos() {
    const dialog = useDialog();
    useAtajoBuscador("txtBuscarPrograma");

    const [programas, setProgramas] = useState([]);
    const [cargando, setCargando] = useState(true);
    const [busqueda, setBusqueda] = useState("");
    const [filtro, setFiltro] = useState("activos");
    const [pagina, setPagina] = useState(1);
    const [formAbierto, setFormAbierto] = useState(false);
    const [programaEditando, setProgramaEditando] = useState(null);
    const [cambiandoEstado, setCambiandoEstado] = useState(null);

    useEffect(() => {
        let activo = true;
        obtenerProgramasGestion()
            .then((datos) => { if (activo) setProgramas(datos); })
            .catch((e) => { if (activo) dialog.error(e.message); })
            .finally(() => { if (activo) setCargando(false); });
        return () => { activo = false; };
    }, [dialog]);

    async function recargar() {
        setCargando(true);
        try {
            const datos = await obtenerProgramasGestion();
            setProgramas(datos);
            return datos;
        } catch (e) {
            dialog.error(e.message);
            return null;
        } finally {
            setCargando(false);
        }
    }

    const resumen = useMemo(() => ({
        activos: programas.filter((p) => p.activo).length,
        sinPlan: programas.filter((p) => p.activo && !p.planVigente).length,
        inactivos: programas.filter((p) => !p.activo).length,
        cursos: programas.filter((p) => p.activo).reduce((total, p) => total + (p.cantidadCursos || 0), 0),
    }), [programas]);

    const filtrados = useMemo(() => {
        const regla = FILTROS.find((f) => f.valor === filtro)?.aplica || (() => true);
        const texto = busqueda.trim().toLowerCase();
        return programas.filter((p) =>
            regla(p) &&
            (!texto ||
                (p.nombre || "").toLowerCase().includes(texto) ||
                (p.codigo || "").toLowerCase().includes(texto) ||
                (p.descripcion || "").toLowerCase().includes(texto))
        );
    }, [programas, filtro, busqueda]);

    const filasPagina = paginar(filtrados, pagina, TAMANO_PAGINA);

    function cambiarBusqueda(valor) { setBusqueda(valor); setPagina(1); }
    function cambiarFiltro(valor) { setFiltro(valor); setPagina(1); }

    function abrirNuevo() {
        setProgramaEditando(null);
        setFormAbierto(true);
        mostrarFormulario();
    }

    function abrirEditar(programa) {
        setProgramaEditando(programa);
        setFormAbierto(true);
        mostrarFormulario();
    }

    function cerrarFormulario() {
        setFormAbierto(false);
        setProgramaEditando(null);
    }

    function formularioGuardado() {
        cerrarFormulario();
        void recargar();
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    // Al crear un plan desde «Modificar» el formulario sigue abierto con los datos nuevos.
    async function planCreado(actualizado) {
        setProgramaEditando(actualizado);
        await recargar();
    }

    async function alternarEstado(programa) {
        const activar = !programa.activo;

        const confirmado = await dialog.confirmar(
            activar
                ? `${programa.nombre} (${programa.codigo}) volverá a aparecer como opción al crear cursos.`
                : `${programa.nombre} (${programa.codigo}) dejará de aparecer al crear cursos. Sus planes, cursos e inscripciones se conservan y puede reactivarlo cuando quiera.`,
            {
                titulo: activar ? "¿Activar este programa?" : "¿Desactivar este programa?",
                textoAceptar: activar ? "Sí, activar" : "Sí, desactivar",
                peligro: !activar,
            }
        );
        if (!confirmado) return;

        setCambiandoEstado(programa.id);
        try {
            await cambiarEstadoPrograma(programa.id, activar);
            dialog.exito(`El programa "${programa.nombre}" fue ${activar ? "activado" : "desactivado"} correctamente.`);
            if (programaEditando?.id === programa.id) cerrarFormulario();
            await recargar();
        } catch (e) {
            dialog.error(e.message);
        } finally {
            setCambiandoEstado(null);
        }
    }

    const etiquetaFiltro = FILTROS.find((f) => f.valor === filtro)?.etiqueta;

    return (
        <div className="cursos programas">
            <PageHead
                title="Programas académicos"
                sub="Cree y administre los programas de formación de ACUSAB y su plan de estudio vigente. Los programas no dependen de un período; al desactivarlos su información se conserva."
                actions={(
                    <button type="button" className="sg-btn sg-btn--primary" onClick={abrirNuevo}>
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Nuevo programa
                    </button>
                )}
            />

            <ul className="programa-resumen" aria-label="Resumen de programas">
                <TarjetaResumen icon={GraduationCap} valor={resumen.activos} etiqueta="Programas activos" activo={filtro === "activos"} onClick={() => cambiarFiltro("activos")} cargando={cargando} />
                <TarjetaResumen icon={TriangleAlert} valor={resumen.sinPlan} etiqueta="Sin plan vigente" tono={resumen.sinPlan > 0 ? "warning" : undefined} activo={filtro === "sinPlan"} onClick={() => cambiarFiltro("sinPlan")} cargando={cargando} />
                <TarjetaResumen icon={CircleOff} valor={resumen.inactivos} etiqueta="Inactivos" activo={filtro === "inactivos"} onClick={() => cambiarFiltro("inactivos")} cargando={cargando} />
                <TarjetaResumen icon={BookOpen} valor={resumen.cursos} etiqueta="Cursos en mallas activas" cargando={cargando} />
            </ul>

            {formAbierto && (
                <section id="form-programa" className="form-incrustado">
                    {programaEditando ? (
                        <EditarProgramaAcademico
                            key={`${programaEditando.id}-${programaEditando.planVigente?.id ?? "sin-plan"}`}
                            programa={programaEditando}
                            onCancel={cerrarFormulario}
                            onSuccess={formularioGuardado}
                            onPlanCreado={planCreado}
                        />
                    ) : (
                        <CrearProgramaAcademico onCancel={cerrarFormulario} onSuccess={formularioGuardado} />
                    )}
                </section>
            )}

            <div className="panel">
                <div className="filters programa-filtros" role="search">
                    <Field id="txtBuscarPrograma" label="Buscar" icon={Search} className="f-search">
                        <input
                            id="txtBuscarPrograma"
                            type="search"
                            className="sg-input"
                            placeholder="Nombre, código o descripción (Ctrl+K)"
                            value={busqueda}
                            onChange={(e) => cambiarBusqueda(e.target.value)}
                            aria-keyshortcuts={ATAJO_BUSCADOR}
                        />
                    </Field>
                    <div className="tabs" role="tablist" aria-label="Filtrar por estado">
                        {FILTROS.map((f) => (
                            <button
                                key={f.valor}
                                type="button"
                                role="tab"
                                aria-selected={filtro === f.valor}
                                onClick={() => cambiarFiltro(f.valor)}
                            >
                                {f.etiqueta}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="chips">
                    <span className="result-count" aria-live="polite">
                        <b>{cargando ? "…" : filtrados.length}</b>
                        {filtrados.length === 1 ? " programa" : " programas"}
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
                </div>

                {cargando && programas.length === 0 ? (
                    <p className="muted lista-cargando" role="status">Cargando programas académicos...</p>
                ) : filtrados.length === 0 ? (
                    <VacioSegunFiltro busqueda={busqueda.trim()} filtro={filtro} hayProgramas={programas.length > 0} onNuevo={abrirNuevo} />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards">
                            <caption className="sr-only">Programas académicos, página {pagina}</caption>
                            <thead>
                                <tr>
                                    <th scope="col">Programa</th>
                                    <th scope="col">Plan vigente</th>
                                    <th scope="col" className="num">Cursos</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="actions"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {filasPagina.map((p) => (
                                    <tr key={p.id} className={p.activo ? undefined : "programa-inactivo"}>
                                        <td className="c-main">
                                            <div className="who">
                                                <span className="avatar avatar--sm avatar--icono" aria-hidden="true">
                                                    <Library size={16} strokeWidth={1.75} />
                                                </span>
                                                <div>
                                                    <b>{p.nombre}</b>
                                                    <span className="tnum">{p.codigo}</span>
                                                    {p.descripcion && <span className="programa-desc-corta" title={p.descripcion}>{p.descripcion}</span>}
                                                </div>
                                            </div>
                                        </td>
                                        <td data-label="Plan vigente">
                                            {p.planVigente ? (
                                                <div className="programa-plan-celda">
                                                    <b className="tnum">Versión {p.planVigente.version}</b>
                                                    <span className="muted">Desde {fechaLegible(p.planVigente.fechaVigenciaDesde)}</span>
                                                </div>
                                            ) : (
                                                <StatusBadge tone="warning">Sin plan</StatusBadge>
                                            )}
                                        </td>
                                        <td className="num tnum" data-label="Cursos">{p.planVigente ? p.cantidadCursos : "—"}</td>
                                        <td>
                                            <StatusBadge tone={p.activo ? "success" : "neutral"}>
                                                {p.activo ? "Activo" : "Inactivo"}
                                            </StatusBadge>
                                        </td>
                                        <td className="actions">
                                            <div className="row-actions">
                                                <button
                                                    type="button"
                                                    className="sg-btn sg-btn--ghost sg-btn--sm"
                                                    onClick={() => abrirEditar(p)}
                                                    aria-label={`Modificar ${p.nombre}`}
                                                >
                                                    <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                                    {p.activo && !p.planVigente ? "Agregar plan" : "Modificar"}
                                                </button>
                                                <button
                                                    type="button"
                                                    className="sg-btn sg-btn--ghost sg-btn--sm"
                                                    onClick={() => alternarEstado(p)}
                                                    disabled={cambiandoEstado === p.id}
                                                    aria-label={`${p.activo ? "Desactivar" : "Activar"} ${p.nombre}`}
                                                >
                                                    <Power size={16} strokeWidth={1.75} aria-hidden="true" />
                                                    {p.activo ? "Desactivar" : "Activar"}
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
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

function TarjetaResumen({ icon: Icono, valor, etiqueta, tono, activo, onClick, cargando }) {
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

function VacioSegunFiltro({ busqueda, filtro, hayProgramas, onNuevo }) {
    if (!hayProgramas) {
        return (
            <EmptyState
                icon={GraduationCap}
                title="Todavía no hay programas académicos"
                description="Cree el primero: el asistente le guía en tres pasos y deja listo su plan de estudio."
                action={(
                    <button type="button" className="sg-btn sg-btn--primary" onClick={onNuevo}>
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Nuevo programa
                    </button>
                )}
            />
        );
    }
    if (busqueda) {
        return (
            <EmptyState
                icon={Search}
                title={`Ningún programa coincide con «${busqueda}»`}
                description="Revise la ortografía, busque por el código o cambie el filtro de estado."
            />
        );
    }
    const mensajes = {
        activos: ["No hay programas activos", "Active alguno desde la pestaña «Inactivos» o cree uno nuevo."],
        sinPlan: ["Todos los programas activos tienen plan vigente", "Ya se les pueden crear cursos."],
        inactivos: ["No hay programas inactivos", "Los programas que desactive aparecerán aquí."],
        todos: ["No hay programas", ""],
    };
    const [titulo, descripcion] = mensajes[filtro];
    return <EmptyState icon={filtro === "sinPlan" ? GraduationCap : CircleOff} title={titulo} description={descripcion} />;
}

export default ProgramasAcademicos;
