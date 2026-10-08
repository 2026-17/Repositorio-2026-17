import { useEffect, useMemo, useState } from "react";
import { Ban, BookOpen, Music2, Search, X } from "lucide-react";
import { API_URL } from "../../config/api";
import "./desactivarCurso.css";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";


import { useDialog } from "../../components/dialog/useDialog";

const PAGE_SIZE = 10;

function DesactivarCurso() {
    const dialog = useDialog();

    const token = localStorage.getItem("token");

    const [cursos, setCursos] = useState([]);
    const [cargando, setCargando] = useState(true);

    const [busqueda, setBusqueda] = useState("");
    const [categoria, setCategoria] = useState("Todas");
    const [pagina, setPagina] = useState(1);

    const [desactivando, setDesactivando] = useState(false);

    useEffect(() => {
        cargarCursos();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    async function cargarCursos() {

        setCargando(true);

        try {

            const response = await fetch(`${API_URL}/cursos`, {
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            });

            if (response.status === 401) {
                throw new Error(
                    "La sesión ha expirado. Inicie sesión nuevamente."
                );
            }

            if (response.status === 403) {
                throw new Error(
                    "No tiene permisos para ver el listado de cursos."
                );
            }

            if (!response.ok) {
                throw new Error(
                    "No fue posible cargar el listado de cursos."
                );
            }

            const data = await response.json();
            setCursos(data);

        } catch (e) {

            dialog.error(
                e.message ||
                "Ocurrió un error al cargar los cursos."
            );

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
                c.nombre.toLowerCase().includes(texto) ||
                c.codigo.toLowerCase().includes(texto);

            const coincideCategoria =
                categoria === "Todas" || c.categoria === categoria;

            return coincideTexto && coincideCategoria;
        });
    }, [cursos, busqueda, categoria]);

    const cursosPagina = cursosFiltrados.slice(
        (pagina - 1) * PAGE_SIZE,
        pagina * PAGE_SIZE
    );

    function handleBusqueda(e) {
        setBusqueda(e.target.value);
        setPagina(1);
    }

    function handleCategoria(e) {
        setCategoria(e.target.value);
        setPagina(1);
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
        <div className="desactivar-curso">
            <PageHead
                title="Desactivar curso"
                sub="Desactive un curso de la malla curricular sin eliminar su información histórica."
            />

            <div className="panel">
                <div className="filters" role="search">
                    <Field id="txtBuscarCurso" label="Buscar" icon={Search} className="f-search">
                        <input
                            id="txtBuscarCurso"
                            type="search"
                            className="sg-input"
                            placeholder="Nombre o código del curso"
                            value={busqueda}
                            onChange={handleBusqueda}
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

export default DesactivarCurso;
