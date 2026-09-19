import { useCallback, useEffect, useMemo, useState } from "react";
import { Edit3, Plus } from "lucide-react";
import { API_URL } from "../../config/api.js";
import CreateStudent from "./createStudent";
import EditarEstudiante from "./EditarEstudiante";
import "./styles/admin_Sis.css";
import "../Administrativa/Profesores.css";
import "./styles/Estudiantes.css";

function Estudiantes() {
    const [estudiantes, setEstudiantes] = useState([]);
    const [busqueda, setBusqueda] = useState("");
    const [filtroActivo, setFiltroActivo] = useState("true");
    const [cargando, setCargando] = useState(true);
    const [error, setError] = useState("");
    const [exito, setExito] = useState("");
    const [formAbierto, setFormAbierto] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [recargar, setRecargar] = useState(0);

    const getHeaders = useCallback(() => {
        const token = localStorage.getItem("token");
        return token ? { Authorization: `Bearer ${token}` } : {};
    }, []);

    const cargarEstudiantes = useCallback(async () => {
        setCargando(true);
        setError("");
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
            setError(e.message);
        } finally {
            setCargando(false);
        }
    }, [filtroActivo, getHeaders, recargar]);

    useEffect(() => {
        cargarEstudiantes();
    }, [cargarEstudiantes]);

    const estudiantesFiltrados = useMemo(() => {
        const texto = busqueda.trim().toLowerCase();
        if (!texto) return estudiantes;

        return estudiantes.filter((e) =>
            (e.nombreCompleto || "").toLowerCase().includes(texto) ||
            (e.numeroIdentificacion || "").toLowerCase().includes(texto)
        );
    }, [estudiantes, busqueda]);

    function abrirNuevo() {
        setEditingId(null);
        setError("");
        setExito("");
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
        setError("");
        setExito("");
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
        setError("");
    }

    function formularioGuardado() {
        setFormAbierto(false);
        setEditingId(null);
        setExito(editingId
            ? "Información del estudiante actualizada correctamente."
            : "Estudiante registrado correctamente.");
        setRecargar((n) => n + 1);
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    return (
        <div className="content">
            <div className="page-title">
                <h1>Estudiantes</h1>
                <p>Registre, consulte y administre la información académica y de contacto de los estudiantes.</p>
            </div>

            {error && (
                <div className="banner banner-danger" role="alert">
                    {error}
                </div>
            )}

            {exito && (
                <div className="banner banner-success" role="alert">
                    {exito}
                </div>
            )}

            {formAbierto && (
                <section id="form-estudiante">
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

            <div className="page-toolbar">
                <div className="page-toolbar__filters">
                    <input
                        id="txtBuscarEstudiante"
                        className="search-input"
                        type="search"
                        value={busqueda}
                        onChange={(e) => setBusqueda(e.target.value)}
                        placeholder="Buscar por nombre, identificac."
                        aria-label="Buscar por nombre o identificación"
                    />
                    <select
                        id="cmbEstadoEstudiante"
                        className="filter-select"
                        value={filtroActivo}
                        onChange={(e) => setFiltroActivo(e.target.value)}
                        aria-label="Filtrar por estado"
                    >
                        <option value="true">Activos</option>
                        <option value="false">Inactivos</option>
                        <option value="">Todos</option>
                    </select>
                </div>

                <button type="button" className="btn btn--primary" onClick={abrirNuevo}>
                    <Plus size={16} />
                    Nuevo estudiante
                </button>
            </div>

            <div className="table-wrap">
                <table className="table">
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
                        {cargando ? (
                            <tr>
                                <td className="empty" colSpan={8}>Cargando estudiantes...</td>
                            </tr>
                        ) : estudiantesFiltrados.length === 0 ? (
                            <tr>
                                <td className="empty" colSpan={8}>
                                    No hay estudiantes que coincidan con la búsqueda.
                                </td>
                            </tr>
                        ) : (
                            estudiantesFiltrados.map((e) => (
                                <tr key={e.id}>
                                    <td>{e.numeroIdentificacion || "—"}</td>
                                    <td>{e.nombreCompleto}</td>
                                    <td>{e.edad}</td>
                                    <td>{e.nivelEscolar || "—"}</td>
                                    <td>{e.instrumento || "—"}</td>
                                    <td>{e.centroEducativo || "—"}</td>
                                    <td>
                                        <span className={`badge ${e.activo ? "badge-activo" : "badge-inactivo"}`}>
                                            {e.activo ? "Activo" : "Inactivo"}
                                        </span>
                                    </td>
                                    <td>
                                        <div className="row-actions">
                                            <button
                                                type="button"
                                                className="btn btn--secondary btn-sm"
                                                onClick={() => abrirEditar(e)}
                                            >
                                                <Edit3 size={14} />
                                                Editar
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default Estudiantes;
