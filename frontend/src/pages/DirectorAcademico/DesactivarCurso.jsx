import { useEffect, useMemo, useState } from "react";
import { Search, Users, GraduationCap, UserRound, AlertTriangle, Music2 } from "lucide-react";
import { API_URL } from "../../config/api";
import "./desactivarCurso.css";


const PAGE_SIZE = 5;

function DesactivarCurso() {

    const token = localStorage.getItem("token");

    const [cursos, setCursos] = useState([]);
    const [cargando, setCargando] = useState(true);
    const [error, setError] = useState("");
    const [mensajeExito, setMensajeExito] = useState("");

    const [busqueda, setBusqueda] = useState("");
    const [categoria, setCategoria] = useState("Todas");
    const [pagina, setPagina] = useState(1);

    const [cursoAConfirmar, setCursoAConfirmar] = useState(null);
    const [desactivando, setDesactivando] = useState(false);

    useEffect(() => {
        cargarCursos();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    async function cargarCursos() {

        setCargando(true);
        setError("");

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

            setError(
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

    const totalPaginas = Math.max(
        1,
        Math.ceil(cursosFiltrados.length / PAGE_SIZE)
    );

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

    function cancelarDesactivacion() {
        setCursoAConfirmar(null);
    }

    async function confirmarDesactivacion() {

        setDesactivando(true);
        setError("");
        setMensajeExito("");

        try {

            const response = await fetch(
                `${API_URL}/cursos/${cursoAConfirmar.idCurso}/desactivar`,
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

            setMensajeExito(
                `"${cursoAConfirmar.nombre}" fue desactivado. Su información histórica se conserva.`
            );

            // Se quita de la lista localmente para no recargar todo.
            setCursos((prev) =>
                prev.filter((c) => c.idCurso !== cursoAConfirmar.idCurso)
            );

            setCursoAConfirmar(null);

            setTimeout(() => setMensajeExito(""), 4000);

        } catch (e) {

            setError(
                e.message ||
                "Ocurrió un error al desactivar el curso."
            );

            setCursoAConfirmar(null);

        } finally {

            setDesactivando(false);
        }
    }

    return (
        <div className="container">

            <div className="desactivar-curso">

                <header className="desactivar-curso__header">
                    <h1>Desactivar curso</h1>
                    <p>
                        Desactive un curso de la malla curricular sin
                        eliminar su información histórica.
                    </p>
                </header>

                {error && (
                    <div
                        className="desactivar-curso__toast desactivar-curso__toast--error"
                        role="alert"
                    >
                        {error}
                    </div>
                )}

                {mensajeExito && (
                    <div className="desactivar-curso__toast" role="status">
                        {mensajeExito}
                    </div>
                )}

                <div className="desactivar-curso__filtros">
                    <div className="desactivar-curso__buscar">
                        <Search size={18} />
                        <input
                            type="text"
                            placeholder="Buscar por nombre o código"
                            value={busqueda}
                            onChange={handleBusqueda}
                        />
                    </div>
                    <select value={categoria} onChange={handleCategoria}>
                        {categorias.map((c) => (
                            <option key={c} value={c}>{c}</option>
                        ))}
                    </select>
                </div>

                <div className="desactivar-curso__lista">

                    {cargando && (
                        <div className="desactivar-curso__vacio">
                            Cargando cursos...
                        </div>
                    )}

                    {!cargando && cursosPagina.length === 0 && (
                        <div className="desactivar-curso__vacio">
                            No hay cursos activos que coincidan con la
                            búsqueda.
                        </div>
                    )}

                    {!cargando && cursosPagina.map((curso) => (
                        <div className="curso-card" key={curso.idCurso}>
                            <div className="curso-card__info">
                                <div className="curso-card__titulo">
                                    <Music2
                                        size={18}
                                        className="curso-card__icono"
                                    />
                                    <div>
                                        <h3>{curso.nombre}</h3>
                                        <span className="curso-card__codigo">
                                            {curso.codigo}
                                        </span>
                                    </div>
                                </div>
                                <div className="curso-card__detalles">
                                    {curso.categoria && (
                                        <span>
                                            <GraduationCap size={14} />
                                            {curso.categoria}
                                        </span>
                                    )}
                                    {curso.docente && (
                                        <span>
                                            <UserRound size={14} />
                                            Docente: {curso.docente}
                                        </span>
                                    )}
                                    {curso.cupoMaximo != null && (
                                        <span>
                                            <Users size={14} />
                                            Cupo máximo: {curso.cupoMaximo}
                                        </span>
                                    )}
                                </div>
                            </div>
                            <button
                                className="btn-desactivar"
                                onClick={() => setCursoAConfirmar(curso)}
                            >
                                Desactivar
                            </button>
                        </div>
                    ))}

                </div>

                {!cargando && totalPaginas > 1 && (
                    <div className="desactivar-curso__paginacion">
                        <button
                            disabled={pagina === 1}
                            onClick={() => setPagina((p) => p - 1)}
                        >
                            Anterior
                        </button>
                        <span>
                            Página {pagina} de {totalPaginas}
                        </span>
                        <button
                            disabled={pagina === totalPaginas}
                            onClick={() => setPagina((p) => p + 1)}
                        >
                            Siguiente
                        </button>
                    </div>
                )}

                {cursoAConfirmar && (
                    <div
                        className="modal-overlay"
                        role="dialog"
                        aria-modal="true"
                    >
                        <div className="modal">
                            <AlertTriangle
                                size={22}
                                className="modal__icono"
                            />
                            <h2>¿Desactivar este curso?</h2>
                            <p>
                                <strong>{cursoAConfirmar.nombre}</strong>
                                {" "}({cursoAConfirmar.codigo}) dejará de
                                aparecer en la oferta académica vigente.
                                Su información histórica no será
                                eliminada.
                            </p>
                            <div className="modal__acciones">
                                <button
                                    className="btn-secundario"
                                    onClick={cancelarDesactivacion}
                                    disabled={desactivando}
                                >
                                    Cancelar
                                </button>
                                <button
                                    className="btn-desactivar"
                                    onClick={confirmarDesactivacion}
                                    disabled={desactivando}
                                >
                                    {desactivando
                                        ? "Desactivando..."
                                        : "Sí, desactivar"}
                                </button>
                            </div>
                        </div>
                    </div>
                )}

            </div>

        </div>
    );
}

export default DesactivarCurso;
