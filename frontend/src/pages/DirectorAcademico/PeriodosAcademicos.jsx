import { useEffect, useMemo, useState } from "react";
import { CalendarRange, Pencil, Plus, Search, X } from "lucide-react";
import { API_URL } from "../../config/api";
import "./cursos.css";
import CrearPeriodoAcademico from "./CrearPeriodoAcademico";
import EditarPeriodoAcademico from "./EditarPeriodoAcademico";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";
import StatusBadge from "../../components/ui/StatusBadge";
import { paginar } from "../../components/ui/utilidades";
import { ATAJO_BUSCADOR, useAtajoBuscador } from "../../hooks/useAtajoBuscador";
import { ETIQUETA_ESTADO_PERIODO, TONO_ESTADO_PERIODO } from "./estadosPeriodo";

import { useDialog } from "../../components/dialog/useDialog";

const TAMANO_PAGINA = 10;

async function obtenerPeriodos() {
    const token = localStorage.getItem("token");
    const response = await fetch(`${API_URL}/periodos-academicos/todos`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    if (response.status === 401) {
        throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
    }
    if (response.status === 403) {
        throw new Error("No tiene permisos para consultar los períodos académicos.");
    }
    if (!response.ok) {
        throw new Error("No fue posible cargar los períodos académicos.");
    }

    const datos = await response.json();
    return Array.isArray(datos) ? datos : [];
}

// Lleva a la vista el formulario que se acaba de abrir sobre la lista.
function mostrarFormulario() {
    requestAnimationFrame(() =>
        document.getElementById("form-periodo")?.scrollIntoView({
            behavior: "smooth",
            block: "start",
        })
    );
}

/*
 * Períodos académicos: lista, creación y modificación en una sola pantalla.
 * "Nuevo período" y "Modificar" abren el formulario sobre la lista.
 */
function PeriodosAcademicos() {
    const dialog = useDialog();

    // Ctrl+K o "/" enfocan el buscador
    useAtajoBuscador("txtBuscarPeriodo");
    const [periodos, setPeriodos] = useState([]);
    const [cargando, setCargando] = useState(true);
    const [busqueda, setBusqueda] = useState("");
    const [pagina, setPagina] = useState(1);
    const [formAbierto, setFormAbierto] = useState(false);
    const [periodoEditando, setPeriodoEditando] = useState(null);

    useEffect(() => {
        let activo = true;

        obtenerPeriodos()
            .then((datos) => { if (activo) setPeriodos(datos); })
            .catch((e) => { if (activo) dialog.error(e.message || "Ocurrió un error al cargar los períodos académicos."); })
            .finally(() => { if (activo) setCargando(false); });

        return () => { activo = false; };
    }, [dialog]);

    // Recarga después de crear o modificar un período.
    async function recargarPeriodos() {
        setCargando(true);
        try {
            setPeriodos(await obtenerPeriodos());
        } catch (e) {
            dialog.error(e.message || "Ocurrió un error al cargar los períodos académicos.");
        } finally {
            setCargando(false);
        }
    }

    const periodosFiltrados = useMemo(() => {
        const texto = busqueda.trim().toLowerCase();
        if (!texto) return periodos;
        return periodos.filter((p) =>
            (p.nombre || "").toLowerCase().includes(texto) ||
            (p.codigo || "").toLowerCase().includes(texto)
        );
    }, [periodos, busqueda]);

    const filasPagina = paginar(periodosFiltrados, pagina, TAMANO_PAGINA);

    function cambiarBusqueda(valor) {
        setBusqueda(valor);
        setPagina(1);
    }

    function abrirNuevo() {
        setPeriodoEditando(null);
        setFormAbierto(true);
        mostrarFormulario();
    }

    function abrirEditar(periodo) {
        setPeriodoEditando(periodo);
        setFormAbierto(true);
        mostrarFormulario();
    }

    function cerrarFormulario() {
        setFormAbierto(false);
        setPeriodoEditando(null);
    }

    function formularioGuardado() {
        cerrarFormulario();
        void recargarPeriodos();
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    return (
        <div className="cursos">
            <PageHead
                title="Períodos académicos"
                sub="Cree, consulte y modifique los períodos que organizan cursos, grupos, matrícula y pagos. Su información histórica no se elimina al modificarlos."
                actions={(
                    <button type="button" className="sg-btn sg-btn--primary" onClick={abrirNuevo}>
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Nuevo período
                    </button>
                )}
            />

            {formAbierto && (
                <section id="form-periodo" className="form-incrustado">
                    {periodoEditando ? (
                        <EditarPeriodoAcademico
                            key={periodoEditando.id}
                            periodo={periodoEditando}
                            onCancel={cerrarFormulario}
                            onSuccess={formularioGuardado}
                        />
                    ) : (
                        <CrearPeriodoAcademico
                            onCancel={cerrarFormulario}
                            onSuccess={formularioGuardado}
                        />
                    )}
                </section>
            )}

            <div className="panel">
                <div className="filters" role="search">
                    <Field id="txtBuscarPeriodo" label="Buscar" icon={Search} className="f-search">
                        <input
                            id="txtBuscarPeriodo"
                            type="search"
                            className="sg-input"
                            placeholder="Nombre o código del período (Ctrl+K)"
                            value={busqueda}
                            onChange={(e) => cambiarBusqueda(e.target.value)}
                            aria-keyshortcuts={ATAJO_BUSCADOR}
                        />
                    </Field>
                </div>

                <div className="chips">
                    <span className="result-count" aria-live="polite">
                        <b>{cargando ? "…" : periodosFiltrados.length}</b>
                        {periodosFiltrados.length === 1 ? " período" : " períodos"}
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

                {cargando ? (
                    <p className="muted lista-cargando" role="status">Cargando períodos académicos...</p>
                ) : periodosFiltrados.length === 0 ? (
                    <EmptyState
                        icon={CalendarRange}
                        title={busqueda.trim() ? `Ningún período coincide con «${busqueda.trim()}»` : "No hay períodos académicos registrados"}
                        description={busqueda.trim()
                            ? "Revise la ortografía o busque por el código del período."
                            : "Cree el primero con el botón \"Nuevo período\"."}
                    />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards">
                            <caption className="sr-only">Períodos académicos, página {pagina}</caption>
                            <thead>
                                <tr>
                                    <th scope="col">Período</th>
                                    <th scope="col">Fechas</th>
                                    <th scope="col">Matrícula</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="actions"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {filasPagina.map((periodo) => (
                                    <tr key={periodo.id}>
                                        <td className="c-main">
                                            <div className="who">
                                                <span className="avatar avatar--sm avatar--icono" aria-hidden="true">
                                                    <CalendarRange size={16} strokeWidth={1.75} />
                                                </span>
                                                <div>
                                                    <b>{periodo.nombre}</b>
                                                    <span>{periodo.codigo || "—"}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="tnum" data-label="Fechas">{periodo.fechaInicio} — {periodo.fechaFin}</td>
                                        <td className="tnum" data-label="Matrícula">
                                            {periodo.fechaInicioMatricula
                                                ? `${periodo.fechaInicioMatricula} — ${periodo.fechaFinMatricula}`
                                                : "Sin definir"}
                                        </td>
                                        <td>
                                            <StatusBadge tone={TONO_ESTADO_PERIODO[periodo.estado] || "neutral"}>
                                                {ETIQUETA_ESTADO_PERIODO[periodo.estado] || periodo.estado}
                                            </StatusBadge>
                                        </td>
                                        <td className="actions">
                                            <button
                                                type="button"
                                                className="sg-btn sg-btn--ghost sg-btn--sm"
                                                onClick={() => abrirEditar(periodo)}
                                                aria-label={`Modificar ${periodo.nombre}`}
                                            >
                                                <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                                Modificar
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {!cargando && periodosFiltrados.length > 0 && (
                    <div className="table-foot">
                        <Pagination page={pagina} pageSize={TAMANO_PAGINA} total={periodosFiltrados.length} onChange={setPagina} />
                    </div>
                )}
            </div>
        </div>
    );
}

export default PeriodosAcademicos;
