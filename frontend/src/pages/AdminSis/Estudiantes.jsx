import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Edit3, Plus, Search, UserRound } from "lucide-react";
import "./styles/admin_Sis.css";
import "./styles/Estudiantes.css";

function Estudiantes() {
    const [estudiantes, setEstudiantes] = useState([]);
    const [busqueda, setBusqueda] = useState("");
    const [filtroActivo, setFiltroActivo] = useState("true");
    const [cargando, setCargando] = useState(true);
    const [error, setError] = useState("");

    const cargarEstudiantes = useCallback(async (termino = "") => {
        try {
            setCargando(true);
            setError("");

            const token = localStorage.getItem("token");
            const params = new URLSearchParams();
            if (termino.trim()) params.set("texto", termino.trim());
            if (filtroActivo) params.set("activo", filtroActivo);
            const query = params.toString() ? `?${params.toString()}` : "";

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
    }, [filtroActivo]);

    useEffect(() => {
        const temporizador = setTimeout(
            () => cargarEstudiantes(busqueda),
            300
        );

        return (
        <div className="container">
            <div className="page-title">
                <h1>Estudiantes</h1>
                <p>Registre, consulte y administre la información académica y de contacto de los estudiantes.</p>
            </div>

            {error && (
                <div className="banner banner-danger" role="alert">
                    {error}
                </div>
            )}

            <div className="page-toolbar">
                <div className="page-toolbar__filters">
                    <input
                        id="txtBuscarEstudiante"
                        type="text"
                        className="search-input"
                        value={busqueda}
                        onChange={(e) => setBusqueda(e.target.value)}
                        placeholder="Buscar por nombre, identificación o correo..."
                        aria-label="Buscar por nombre, identificación o correo"
                    />
                    <select
                        id="cmbEstadoEstudiante"
                        className="filter-select"
                        value={filtroActivo}
                        onChange={(e) => setFiltroActivo(e.target.value)}
                        aria-label="Filtrar estudiantes por estado"
                    >
                        <option value="true">Activos</option>
                        <option value="false">Inactivos</option>
                        <option value="">Todos</option>
                    </select>
                </div>

                <Link
                    className="btn btn--primary"
                    to="/admin/administracion/estudiantes/crear"
                >
                    <Plus size={16} />
                    + Nuevo estudiante
                </Link>
            </div>

            <div className="students-table-wrap">
                <table className="students-table">
                    <thead>
                        <tr>
                            <th>Identificación</th>
                            <th>Nombre</th>
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
                                <td>{estudiante.numeroIdentificacion}</td>
                                <td className="student-name">{estudiante.nombreCompleto}</td>
                                <td>{estudiante.edad}</td>
                                <td>{estudiante.nivelEscolar || "—"}</td>
                                <td>{estudiante.instrumento || "—"}</td>
                                <td>{estudiante.centroEducativo || "—"}</td>
                                <td>
                                    <span className={`student-status ${estudiante.activo ? "active" : "inactive"}`}>
                                        {estudiante.activo ? "Activo" : "Inactivo"}
                                    </span>
                                </td>
                                <td>
                                    <div className="students-actions">
                                        <Link
                                            to={`/admin/administracion/estudiantes/${estudiante.id}/editar`}
                                            className="btn btn-sm btn--secondary"
                                            title={`Editar ${estudiante.nombreCompleto}`}
                                            aria-label={`Editar ${estudiante.nombreCompleto}`}
                                        >
                                            <Edit3 size={14} />
                                            Editar
                                        </Link>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {cargando && (
                <div className="banner banner-info">
                    Cargando estudiantes...
                </div>
            )}
        </div>
    );
}

export default Estudiantes;
