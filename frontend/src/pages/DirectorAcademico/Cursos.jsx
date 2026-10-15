import { useEffect, useMemo, useState } from "react";
import { Ban, BookOpen, Music2, Pencil, Plus, Search, X } from "lucide-react";
import { API_URL } from "../../config/api";
import "./cursos.css";
import CrearCurso from "./CrearCurso";
import EditarCurso from "./EditarCurso";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";
import { paginar } from "../../components/ui/utilidades";
import { ATAJO_BUSCADOR, useAtajoBuscador } from "../../hooks/useAtajoBuscador";

import { useDialog } from "../../components/dialog/useDialog";

const PAGE_SIZE = 10;

async function obtenerCursos() {
    const token = localStorage.getItem("token");
    const response = await fetch(`${API_URL}/cursos`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    if (response.status === 401) {
        throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
    }
    if (response.status === 403) {
        throw new Error("No tiene permisos para ver el listado de cursos.");
    }
    if (!response.ok) {
        throw new Error("No fue posible cargar el listado de cursos.");
    }

    const datos = await response.json();
    return Array.isArray(datos) ? datos : [];
}

// Lleva a la vista el formulario que se acaba de abrir sobre la lista.
function mostrarFormulario() {
    requestAnimationFrame(() =>
        document.getElementById("form-curso")?.scrollIntoView({
            behavior: "smooth",
            block: "start",
        })
    );
}

/*
 * Cursos de la malla curricular en una sola pantalla:
 * HU-028 crear ("Nuevo curso"), HU-030 modificar y HU-031 desactivar.
 */
function Cursos() {
    const dialog = useDialog();

    const token = localStorage.getItem("token");

    // Ctrl+K o "/" enfocan el buscador
    useAtajoBuscador("txtBuscarCurso");
    const [cursos, setCursos] = useState([]);
    const [cargando, setCargando] = useState(true);

    const [busqueda, setBusqueda] = useState("");
    const [categoria, setCategoria] = useState("Todas");
    const [pagina, setPagina] = useState(1);

    const [formAbierto, setFormAbierto] = useState(false);
    const [idCursoEditando, setIdCursoEditando] = useState(null);
    const [desactivando, setDesactivando] = useState(false);

    useEffect(() => {
        let activo = true;

        obtenerCursos()
            .then((datos) => { if (activo) setCursos(datos); })
            .catch((e) => { if (activo) dialog.error(e.message || "Ocurrió un error al cargar los cursos."); })
            .finally(() => { if (activo) setCargando(false); });

        return () => { activo = false; };
    }, [dialog]);

    // Recarga después de crear o modificar un curso.
    async function recargarCursos() {
        setCargando(true);
        try {
            setCursos(await obtenerCursos());
        } catch (e) {
            dialog.error(e.message || "Ocurrió un error al cargar los cursos.");
        } finally {
            setCargando(false);
        }
    }

    const categorias = useMemo(() => {

        const unicas = Array.from(
            new Set(
                cursos
                    .map((c) => c.categoria)
                    .filter((c) => c && c.trim() !== "")
            )
        ).sort();

        return ["Todas", ...unicas];
    }, [cursos]);

    const cursosFiltrados = useMemo(() => {

        const texto = busqueda.trim().toLowerCase();

        return cursos.filter((c) => {
            const coincideTexto =
                !texto ||
                (c.nombre || "").toLowerCase().includes(texto) ||
                (c.codigo || "").toLowerCase().includes(texto);

            const coincideCategoria =
                categoria === "Todas" || c.categoria === categoria;

            return coincideTexto && coincideCategoria;
        });
    }, [cursos, busqueda, categoria]);

    const cursosPagina = paginar(cursosFiltrados, pagina, PAGE_SIZE);

    function handleBusqueda(e) {
        setBusqueda(e.target.value);
        setPagina(1);
    }

    function handleCategoria(e) {
        setCategoria(e.target.value);
        setPagina(1);
    }

    function abrirNuevo() {
        setIdCursoEditando(null);
        setFormAbierto(true);
        mostrarFormulario();
    }

    function abrirEditar(curso) {
        setIdCursoEditando(curso.idCurso);
        setFormAbierto(true);
        mostrarFormulario();
    }

    function cerrarFormulario() {
        setFormAbierto(false);
        setIdCursoEditando(null);
    }

    function formularioGuardado() {
        cerrarFormulario();
        void recargarCursos();
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    async function desactivarCurso(curso) {

        const confirmado = await dialog.confirmar(
            `${curso.nombre} (${curso.codigo}) dejará de aparecer en la oferta académica vigente. Su información histórica no será eliminada.`,
            {
                titulo: "¿Desactivar este curso?",
                textoAceptar: "Sí, desactivar",
                peligro: true,
            }
        );

        if (!confirmado) {
            return;
        }

        setDesactivando(true);

        try {

            const response = await fetch(
                `${API_URL}/cursos/${curso.idCurso}/desactivar`,
                {
                    method: "PATCH",
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }
            );

            let data = {};

            try {
                data = await response.json();
            } catch {
                // La respuesta puede venir sin cuerpo.
            }

            if (response.status === 401) {
                throw new Error(
                    "La sesión ha expirado. Inicie sesión nuevamente."
                );
            }

            if (response.status === 403) {
                throw new Error(
                    "No tiene permisos para desactivar cursos."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.message ||
                    "No fue posible desactivar el curso."
                );
            }

            dialog.exito(
                `"${curso.nombre}" fue desactivado. Su información histórica se conserva.`
            );

            // Si se estaba modificando ese curso, el formulario ya no aplica.
            if (idCursoEditando === curso.idCurso) cerrarFormulario();

            // Se quita de la lista localmente para no recargar todo.
            setCursos((prev) =>
                prev.filter((c) => c.idCurso !== curso.idCurso)
            );

        } catch (e) {

            dialog.error(
                e.message ||
                "Ocurrió un error al desactivar el curso."
            );

        } finally {

            setDesactivando(false);
        }
    }

    const hayFiltros = Boolean(busqueda.trim()) || categoria !== "Todas";

    function quitarFiltros() {
        setBusqueda("");
        setCategoria("Todas");
        setPagina(1);
    }

    return (
        <div className="cursos">
            <PageHead
                title="Cursos"
                sub="Cree, modifique y desactive los cursos de la malla curricular. Al desactivar un curso su información histórica se conserva."
                actions={(
                    <button type="button" className="sg-btn sg-btn--primary" onClick={abrirNuevo}>
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Nuevo curso
                    </button>
                )}
            />

            {formAbierto && (
                <section id="form-curso" className="form-incrustado">
                    {idCursoEditando ? (
                        <EditarCurso
                            key={idCursoEditando}
                            idCurso={idCursoEditando}
                            onCancel={cerrarFormulario}
                            onSuccess={formularioGuardado}
                        />
                    ) : (
                        <CrearCurso
                            onCancel={cerrarFormulario}
                            onSuccess={formularioGuardado}
                        />
                    )}
                </section>
            )}

            <div className="panel">
                <div className="filters" role="search">
                    <Field id="txtBuscarCurso" label="Buscar" icon={Search} className="f-search">
                        <input
                            id="txtBuscarCurso"
                            type="search"
                            className="sg-input"
                            placeholder="Nombre o código del curso (Ctrl+K)"
                            value={busqueda}
                            onChange={handleBusqueda}
                            aria-keyshortcuts={ATAJO_BUSCADOR}
                        />
                    </Field>
                    <Field id="cmbCategoriaCurso" label="Categoría" select>
                        <select id="cmbCategoriaCurso" className="sg-input sg-select" value={categoria} onChange={handleCategoria}>
                            {categorias.map((c) => (
                                <option key={c} value={c}>{c}</option>
                            ))}
                        </select>
                    </Field>
                </div>

                <div className="chips">
                    <span className="result-count" aria-live="polite">
                        <b>{cursosFiltrados.length}</b>
                        {cursosFiltrados.length === 1 ? " curso activo" : " cursos activos"}
                    </span>
                    {busqueda.trim() && (
                        <span className="chip">
                            Búsqueda: {busqueda.trim()}
                            <button type="button" aria-label="Quitar filtro de búsqueda" onClick={() => { setBusqueda(""); setPagina(1); }}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                    {categoria !== "Todas" && (
                        <span className="chip">
                            Categoría: {categoria}
                            <button type="button" aria-label="Quitar filtro de categoría" onClick={() => { setCategoria("Todas"); setPagina(1); }}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                    {hayFiltros && (
                        <button type="button" className="link-btn chips-clear" onClick={quitarFiltros}>Quitar filtros</button>
                    )}
                </div>

                {cargando ? (
                    <p className="muted lista-cargando" role="status">Cargando cursos...</p>
                ) : cursosPagina.length === 0 ? (
                    <EmptyState
                        icon={BookOpen}
                        title={busqueda.trim() ? `Ningún curso coincide con «${busqueda.trim()}»` : "No hay cursos activos con estos filtros"}
                        description="Revise la ortografía, busque por código o quite algún filtro."
                        action={hayFiltros && <button type="button" className="sg-btn sg-btn--secondary" onClick={quitarFiltros}>Quitar filtros</button>}
                    />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards">
                            <caption className="sr-only">Cursos activos, página {pagina}</caption>
                            <thead>
                                <tr>
                                    <th scope="col">Curso</th>
                                    <th scope="col">Categoría</th>
                                    <th scope="col">Docente</th>
                                    <th scope="col" className="num">Cupo máximo</th>
                                    <th scope="col" className="actions"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {cursosPagina.map((curso) => (
                                    <tr key={curso.idCurso}>
                                        <td className="c-main">
                                            <div className="who">
                                                <span className="avatar avatar--sm avatar--icono" aria-hidden="true">
                                                    <Music2 size={16} strokeWidth={1.75} />
                                                </span>
                                                <div>
                                                    <b>{curso.nombre}</b>
                                                    <span>{curso.codigo}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td data-label="Categoría">{curso.categoria ? <span className="inst">{curso.categoria}</span> : "—"}</td>
                                        <td data-label="Docente">{curso.docente || "—"}</td>
                                        <td className="num" data-label="Cupo máximo">{curso.cupoMaximo ?? "—"}</td>
                                        <td className="actions">
                                            <div className="row-actions">
                                                <button
                                                    type="button"
                                                    className="sg-btn sg-btn--ghost sg-btn--sm"
                                                    onClick={() => abrirEditar(curso)}
                                                    aria-label={`Modificar ${curso.nombre}`}
                                                >
                                                    <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                                    Modificar
                                                </button>
                                                <button
                                                    type="button"
                                                    className="sg-btn sg-btn--ghost sg-btn--sm btn-peligro"
                                                    onClick={() => desactivarCurso(curso)}
                                                    disabled={desactivando}
                                                    aria-label={`Desactivar ${curso.nombre}`}
                                                >
                                                    <Ban size={16} strokeWidth={1.75} aria-hidden="true" />
                                                    Desactivar
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {!cargando && cursosFiltrados.length > 0 && (
                    <div className="table-foot">
                        <Pagination page={pagina} pageSize={PAGE_SIZE} total={cursosFiltrados.length} onChange={setPagina} />
                    </div>
                )}
            </div>
        </div>
    );
}

export default Cursos;
