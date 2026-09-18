import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Edit3, Plus, Search, UserRound } from "lucide-react";
import "./styles/admin_Sis.css";
import "./styles/Estudiantes.css";

function Estudiantes() {
    const [estudiantes, setEstudiantes] = useState([]);
    const [busqueda, setBusqueda] = useState("");
    const [cargando, setCargando] = useState(true);
    const [error, setError] = useState("");

    const cargarEstudiantes = useCallback(async (termino = "") => {
        try {
            setCargando(true);
            setError("");

            const token = localStorage.getItem("token");
            const query = termino.trim()
                ? `?texto=${encodeURIComponent(termino.trim())}`
                : "";

            const response = await fetch(`/api/estudiantes/listado${query}`, {
                headers: token ? { Authorization: `Bearer ${token}` } : {},
            });

            if (!response.ok) {
                throw new Error("No fue posible cargar los estudiantes.");
            }

            setEstudiantes(await response.json());
        } catch (e) {
            setError(e.message);
            setEstudiantes([]);
        } finally {
            setCargando(false);
        }
    }, []);

    useEffect(() => {
        const temporizador = setTimeout(
            () => cargarEstudiantes(busqueda),
            300
        );

        return () => clearTimeout(temporizador);
    }, [busqueda, cargarEstudiantes]);

    return (
        <div className="content">
            <div className="page-title">
                <h1>Estudiantes</h1>
                <p>Registra, consulta y actualiza la información de los estudiantes</p>
            </div>

            {error && (
                <div className="banner banner-danger" role="alert">
                    {error}
                </div>
            )}

            <section className="card students-card">
                <div className="card-header-row">
                    <div>
                        <h2 className="card-title">Estudiantes registrados</h2>
                        <p className="card-subtitle">
                            Total de resultados: {estudiantes.length}
                        </p>
                    </div>

                    <Link
                        className="btn btn-primary"
                        to="/admin/estudiantes/crear"
                    >
                        <Plus size={16} />
                        Nuevo estudiante
                    </Link>
                </div>

                <div className="students-search-row">
                    <div className="students-search">
                        <Search size={17} aria-hidden="true" />
                        <input
                            id="txtBuscarEstudiante"
                            className="input students-search-input"
                            type="search"
                            value={busqueda}
                            onChange={(e) => setBusqueda(e.target.value)}
                            placeholder="Buscar por nombre o cédula"
                            aria-label="Buscar por nombre o cédula"
                        />
                    </div>

                    <span className="students-count">
                        {cargando
                            ? "Cargando..."
                            : `${estudiantes.length} resultados`}
                    </span>
                </div>

                <div className="students-table-wrap">
                    <table className="students-table">
                        <thead>
                            <tr>
                                <th>Nombre completo</th>
                                <th>Cédula</th>
                                <th>Edad</th>
                                <th>Nivel escolar</th>
                                <th>Instrumento</th>
                                <th>Centro educativo</th>
                                <th>Estado</th>
                                <th>Acciones</th>
                            </tr>
                        </thead>

                        <tbody>
                            {!cargando && estudiantes.length === 0 && (
                                <tr>
                                    <td colSpan="8" className="students-empty">
                                        <UserRound size={24} />
                                        <span>
                                            {busqueda.trim()
                                                ? "No se encontraron estudiantes."
                                                : "No hay estudiantes registrados."}
                                        </span>
                                    </td>
                                </tr>
                            )}

                            {estudiantes.map((estudiante) => (
                                <tr key={estudiante.id}>
                                    <td className="student-name">
                                        {estudiante.nombreCompleto}
                                    </td>
                                    <td>{estudiante.numeroIdentificacion}</td>
                                    <td>{estudiante.edad}</td>
                                    <td>{estudiante.nivelEscolar || "—"}</td>
                                    <td>{estudiante.instrumento || "—"}</td>
                                    <td>{estudiante.centroEducativo || "—"}</td>
                                    <td>
                                        <span
                                            className={
                                                `student-status ${estudiante.activo
                                                    ? "active"
                                                    : "inactive"}`
                                            }
                                        >
                                            {estudiante.activo
                                                ? "Activo"
                                                : "Inactivo"}
                                        </span>
                                    </td>
                                    <td>
                                        <div className="students-actions">
                                            <Link
                                                to={`/admin/administracion/estudiantes/${estudiante.id}/editar`}
                                                className="icon-btn"
                                                title={`Editar ${estudiante.nombreCompleto}`}
                                                aria-label={`Editar ${estudiante.nombreCompleto}`}
                                            >
                                                <Edit3 size={15} />
                                            </Link>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </section>
        </div>
    );
}

export default Estudiantes;
