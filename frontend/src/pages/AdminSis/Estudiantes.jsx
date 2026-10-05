import { useCallback, useEffect, useMemo, useState } from "react";
import { Edit3, Plus, UserCheck, UserX } from "lucide-react";
import { API_URL } from "../../config/api.js";
import CreateStudent from "./createStudent";
import EditarEstudiante from "./EditarEstudiante";
import "./styles/admin_Sis.css";
import "./styles/Estudiantes.css";

import { useDialog } from "../../components/dialog/useDialog";

function Estudiantes() {
    const dialog = useDialog();
    const [estudiantes, setEstudiantes] = useState([]);
    const [busqueda, setBusqueda] = useState("");
    const [filtroActivo, setFiltroActivo] = useState("true");
    const [cargando, setCargando] = useState(true);
    const [formAbierto, setFormAbierto] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [bajaEstudiante, setBajaEstudiante] = useState(null);
    const [motivoBaja, setMotivoBaja] = useState("");
    const [bajaError, setBajaError] = useState("");
    const [bajaCargando, setBajaCargando] = useState(false);
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
        if (!texto) return estudiantes;

        return estudiantes.filter((e) =>
            (e.nombreCompleto || "").toLowerCase().includes(texto) ||
            (e.numeroIdentificacion || "").toLowerCase().includes(texto)
        );
    }, [estudiantes, busqueda]);

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

    function abrirBaja(estudiante) {
        setFormAbierto(false);
        setEditingId(null);
        setBajaEstudiante(estudiante);
        setMotivoBaja("");
        setBajaError("");

        // El formulario se renderiza al inicio de la página; llevarlo a la vista.
        requestAnimationFrame(() => {
            document.getElementById("form-baja-estudiante")?.scrollIntoView({
                behavior: "smooth",
                block: "start",
            });
            document.getElementById("txtMotivoBaja")?.focus({ preventScroll: true });
        });
    }

    function cerrarBaja() {
        if (bajaCargando) return;
        setBajaEstudiante(null);
        setMotivoBaja("");
        setBajaError("");
    }

    async function confirmarBaja() {
        const motivo = motivoBaja.trim();

        if (!motivo) {
            setBajaError("El motivo de baja es obligatorio.");
            return;
        }

        if (motivo.length > 500) {
            setBajaError("El motivo de baja no puede superar 500 caracteres.");
            return;
        }

        const confirmado = await dialog.confirmar(
            `¿Confirma la baja de ${bajaEstudiante.nombreCompleto}?`,
            {
                peligro: true,
                textoAceptar: "Dar de baja",
            }
        );

        if (!confirmado) return;

        setBajaCargando(true);
        setBajaError("");

        try {
            const response = await fetch(
                `${API_URL}/estudiantes/${bajaEstudiante.id}/baja`,
                {
                    method: "PATCH",
                    headers: {
                        ...getHeaders(),
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify({ motivoBaja: motivo }),
                }
            );

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(data.message || "No se pudo dar de baja al estudiante.");
            }

            setBajaEstudiante(null);
            setMotivoBaja("");
            setRecargar((n) => n + 1);

            await dialog.exito(
                `Baja realizada correctamente. Matrículas canceladas: ${data.matriculasCanceladas}. Cursos retirados: ${data.cursosRetirados}.`
            );
        } catch (e) {
            setBajaError(e.message);
        } finally {
            setBajaCargando(false);
        }
    }

    async function confirmarReactivacion(estudiante) {
        const confirmado = await dialog.confirmar(
            `¿Desea reactivar a ${estudiante.nombreCompleto}?`,
            {
                textoAceptar: "Reactivar estudiante",
            }
        );

        if (!confirmado) return;

        try {
            const response = await fetch(
                `${API_URL}/estudiantes/${estudiante.id}/reactivar`,
                {
                    method: "PATCH",
                    headers: getHeaders(),
                }
            );
            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(
                    data.message || "No se pudo reactivar al estudiante."
                );
            }

            setRecargar((n) => n + 1);
            await dialog.exito(
                data.mensaje ||
                "Estudiante reactivado. Las matrículas y cursos cancelados no se restauran automáticamente."
            );
        } catch (e) {
            dialog.error(e.message);
        }
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

    return (
        <div className="content students-management-page">
            <div className="page-title">
                <h1>Estudiantes</h1>
                <p>Registre, consulte y administre la información académica y de contacto de los estudiantes.</p>
            </div>

            {bajaEstudiante && (
                <section id="form-baja-estudiante" className="student-baja-form" aria-labelledby="titulo-baja-estudiante">
                    <div className="student-baja-form__header">
                        <div>
                            <h2 id="titulo-baja-estudiante">Dar de baja estudiante</h2>
                            <p>
                                {bajaEstudiante.nombreCompleto} — {bajaEstudiante.numeroIdentificacion}
                            </p>
                        </div>
                    </div>

                    <div className="form-group">
                        <label htmlFor="txtMotivoBaja">Motivo de baja *</label>
                        <textarea
                            id="txtMotivoBaja"
                            value={motivoBaja}
                            onChange={(e) => {
                                setMotivoBaja(e.target.value);
                                if (bajaError) setBajaError("");
                            }}
                            maxLength={500}
                            rows={4}
                            className={bajaError ? "input-error" : ""}
                            placeholder="Indique el motivo de la baja"
                            disabled={bajaCargando}
                        />
                        <small>{motivoBaja.length}/500</small>
                        {bajaError && <p className="form-error">{bajaError}</p>}
                    </div>

                    <div className="form-actions">
                        <button
                            type="button"
                            className="btn btn--secondary"
                            onClick={cerrarBaja}
                            disabled={bajaCargando}
                        >
                            Cancelar
                        </button>
                        <button
                            type="button"
                            className="btn btn--danger"
                            onClick={confirmarBaja}
                            disabled={bajaCargando}
                        >
                            <UserX size={16} />
                            {bajaCargando ? "Procesando..." : "Dar de baja"}
                        </button>
                    </div>
                </section>
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
                                            {e.activo ? (
                                                <button
                                                    type="button"
                                                    className="btn btn--danger btn-sm"
                                                    onClick={() => abrirBaja(e)}
                                                >
                                                    <UserX size={14} />
                                                    Dar de baja
                                                </button>
                                            ) : (
                                                <button
                                                    type="button"
                                                    className="btn btn--success btn-sm"
                                                    onClick={() => confirmarReactivacion(e)}
                                                >
                                                    <UserCheck size={14} />
                                                    Reactivar
                                                </button>
                                            )}
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
