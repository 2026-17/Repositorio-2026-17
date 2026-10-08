import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronsUpDown, Pencil, Plus, Search, Users, X } from "lucide-react";
import { API_URL } from "../../config/api.js";
import CreateStudent from "./createStudent";
import EditarEstudiante from "./EditarEstudiante";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";
import StatusBadge from "../../components/ui/StatusBadge";
import { iniciales, paginar } from "../../components/ui/utilidades";
import "./styles/admin_Sis.css";
import "./styles/Estudiantes.css";

import { useDialog } from "../../components/dialog/useDialog";

const TAMANO_PAGINA = 10;
const ETIQUETA_ESTADO = { true: "Activos", false: "Inactivos", "": "Todos" };

function Estudiantes() {
    const dialog = useDialog();
    const [estudiantes, setEstudiantes] = useState([]);
    const [busqueda, setBusqueda] = useState("");
    const [filtroActivo, setFiltroActivo] = useState("true");
    const [orden, setOrden] = useState("asc");
    const [pagina, setPagina] = useState(1);
    const [cargando, setCargando] = useState(true);
    const [formAbierto, setFormAbierto] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [recargar, setRecargar] = useState(0);

    const getHeaders = useCallback(() => {
        const token = localStorage.getItem("token");
        return token ? { Authorization: `Bearer ${token}` } : {};
    }, []);

    const cargarEstudiantes = useCallback(async () => {
        setCargando(true);
        try {
            const params = new URLSearchParams();
            if (filtroActivo) params.set("activo", filtroActivo);

            const response = await fetch(
                `${API_URL}/estudiantes/listado${params.toString() ? `?${params.toString()}` : ""}`,
                { headers: getHeaders() }
            );

            if (!response.ok) throw new Error("No se pudo cargar la lista de estudiantes.");
            setEstudiantes(await response.json());
        } catch (e) {
            dialog.error(e.message);
        } finally {
            setCargando(false);
        }
    }, [filtroActivo, getHeaders, recargar, dialog]);

    useEffect(() => {
        cargarEstudiantes();
    }, [cargarEstudiantes]);

    const estudiantesFiltrados = useMemo(() => {
        const texto = busqueda.trim().toLowerCase();
        const lista = !texto ? [...estudiantes] : estudiantes.filter((e) =>
            (e.nombreCompleto || "").toLowerCase().includes(texto) ||
            (e.numeroIdentificacion || "").toLowerCase().includes(texto)
        );

        lista.sort((a, b) => {
            const comparacion = (a.nombreCompleto || "").localeCompare(b.nombreCompleto || "", "es");
            return orden === "asc" ? comparacion : -comparacion;
        });

        return lista;
    }, [estudiantes, busqueda, orden]);

    const filasPagina = paginar(estudiantesFiltrados, pagina, TAMANO_PAGINA);

    function cambiarBusqueda(valor) {
        setBusqueda(valor);
        setPagina(1);
    }

    function cambiarEstado(valor) {
        setFiltroActivo(valor);
        setPagina(1);
    }

    function quitarFiltros() {
        cambiarBusqueda("");
        cambiarEstado("true");
    }

    function abrirNuevo() {
        setEditingId(null);
        setFormAbierto(true);
        requestAnimationFrame(() =>
            document.getElementById("form-estudiante")?.scrollIntoView({
                behavior: "smooth",
                block: "start",
            })
        );
    }

    function abrirEditar(estudiante) {
        setEditingId(estudiante.id);
        setFormAbierto(true);
        requestAnimationFrame(() =>
            document.getElementById("form-estudiante")?.scrollIntoView({
                behavior: "smooth",
                block: "start",
            })
        );
    }

    function cerrarFormulario() {
        setFormAbierto(false);
        setEditingId(null);
    }

    function formularioGuardado() {
        setFormAbierto(false);
        setEditingId(null);
        dialog.exito(editingId
            ? "Información del estudiante actualizada correctamente."
            : "Estudiante registrado correctamente.");
        setRecargar((n) => n + 1);
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    const hayFiltros = Boolean(busqueda.trim()) || filtroActivo !== "true";

    return (
        <>
            <PageHead
                title="Estudiantes"
                sub="Registre, consulte y administre la información académica y de contacto de los estudiantes."
                actions={(
                    <button type="button" className="sg-btn sg-btn--primary" onClick={abrirNuevo}>
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Nuevo estudiante
                    </button>
                )}
            />

            {formAbierto && (
                <section id="form-estudiante" className="form-incrustado">
                    {editingId ? (
                        <EditarEstudiante
                            studentId={editingId}
                            onCancel={cerrarFormulario}
                            onSuccess={formularioGuardado}
                        />
                    ) : (
                        <CreateStudent
                            onCancel={cerrarFormulario}
                            onSuccess={formularioGuardado}
                        />
                    )}
                </section>
            )}

            <div className="panel">
                <div className="filters" role="search">
                    <Field id="txtBuscarEstudiante" label="Buscar" icon={Search} className="f-search">
                        <input
                            id="txtBuscarEstudiante"
                            className="sg-input"
                            type="search"
                            value={busqueda}
                            onChange={(e) => cambiarBusqueda(e.target.value)}
                            placeholder="Nombre o número de identificación"
                        />
                    </Field>
                    <Field id="cmbEstadoEstudiante" label="Estado" select>
                        <select
                            id="cmbEstadoEstudiante"
                            className="sg-input sg-select"
                            value={filtroActivo}
                            onChange={(e) => cambiarEstado(e.target.value)}
                        >
                            <option value="true">Activos</option>
                            <option value="false">Inactivos</option>
                            <option value="">Todos</option>
                        </select>
                    </Field>
                </div>

                <div className="chips">
                    <span className="result-count" aria-live="polite">
                        <b>{estudiantesFiltrados.length}</b>
                        {estudiantesFiltrados.length === 1 ? " resultado" : " resultados"}
                    </span>
                    {busqueda.trim() && (
                        <span className="chip">
                            Búsqueda: {busqueda.trim()}
                            <button type="button" aria-label="Quitar filtro de búsqueda" onClick={() => cambiarBusqueda("")}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                    {filtroActivo !== "true" && (
                        <span className="chip">
                            Estado: {ETIQUETA_ESTADO[filtroActivo]}
                            <button type="button" aria-label="Quitar filtro de estado" onClick={() => cambiarEstado("true")}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                    {hayFiltros && (
                        <button type="button" className="link-btn chips-clear" onClick={quitarFiltros}>
                            Quitar filtros
                        </button>
                    )}
                </div>

                {cargando ? (
                    <div className="table-wrap" aria-busy="true">
                        <table className="dtable">
                            <caption className="sr-only">Cargando estudiantes...</caption>
                            <tbody>
                                {[0, 1, 2, 3, 4].map((i) => (
                                    <tr key={i}>
                                        {[0, 1, 2, 3, 4, 5].map((j) => (
                                            <td key={j}><span className="skeleton" /></td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : estudiantesFiltrados.length === 0 ? (
                    <EmptyState
                        icon={Users}
                        title={busqueda.trim()
                            ? `Ningún estudiante coincide con «${busqueda.trim()}»`
                            : "No hay estudiantes con estos filtros"}
                        description="Revise la ortografía, busque por número de identificación o quite algún filtro."
                        action={hayFiltros && (
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={quitarFiltros}>
                                Quitar filtros
                            </button>
                        )}
                    />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards">
                            <caption className="sr-only">Lista de estudiantes, página {pagina}</caption>
                            <thead>
                                <tr>
                                    <th scope="col" aria-sort={orden === "asc" ? "ascending" : "descending"}>
                                        <button type="button" onClick={() => setOrden(orden === "asc" ? "desc" : "asc")}>
                                            Estudiante
                                            <ChevronsUpDown size={14} strokeWidth={1.75} aria-hidden="true" />
                                        </button>
                                    </th>
                                    <th scope="col" className="num">Edad</th>
                                    <th scope="col">Nivel escolar</th>
                                    <th scope="col">Instrumento</th>
                                    <th scope="col">Centro educativo</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="actions"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {filasPagina.map((e) => (
                                    <tr key={e.id}>
                                        <td className="c-main">
                                            <div className="who">
                                                <span className="avatar avatar--sm" aria-hidden="true">{iniciales(e.nombreCompleto)}</span>
                                                <div>
                                                    <b>{e.nombreCompleto}</b>
                                                    <span>{e.numeroIdentificacion || "—"}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="num" data-label="Edad">{e.edad}</td>
                                        <td data-label="Nivel">{e.nivelEscolar || "—"}</td>
                                        <td data-label="Instrumento">{e.instrumento ? <span className="inst">{e.instrumento}</span> : "—"}</td>
                                        <td data-label="Centro">{e.centroEducativo || "—"}</td>
                                        <td>
                                            <StatusBadge tone={e.activo ? "success" : "neutral"}>
                                                {e.activo ? "Activo" : "Inactivo"}
                                            </StatusBadge>
                                        </td>
                                        <td className="actions">
                                            <button
                                                type="button"
                                                className="sg-btn sg-btn--ghost sg-btn--sm"
                                                onClick={() => abrirEditar(e)}
                                                aria-label={`Editar a ${e.nombreCompleto}`}
                                            >
                                                <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                                Editar
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {!cargando && estudiantesFiltrados.length > 0 && (
                    <div className="table-foot">
                        <Pagination
                            page={pagina}
                            pageSize={TAMANO_PAGINA}
                            total={estudiantesFiltrados.length}
                            onChange={setPagina}
                        />
                    </div>
                )}
            </div>
        </>
    );
}

export default Estudiantes;
