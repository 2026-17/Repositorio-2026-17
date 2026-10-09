import { useCallback, useEffect, useState } from "react";
import { CalendarClock, RefreshCw, UserCheck, UserMinus } from "lucide-react";
import { API_URL } from "../../config/api";
import Alert from "../../components/ui/Alert";
import PageHead from "../../components/ui/PageHead";
import "./asignarProfesores.css";

const DIAS = ["", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];

function nombreCompleto(nombre) {
    return nombre || "Sin asignar";
}

function AsignarProfesores() {
    const [asignaciones, setAsignaciones] = useState([]);
    const [profesores, setProfesores] = useState([]);
    const [selecciones, setSelecciones] = useState({});
    const [cargando, setCargando] = useState(true);
    const [ocupado, setOcupado] = useState(null);
    const [error, setError] = useState("");
    const [mensaje, setMensaje] = useState("");

    const headers = {
        Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
        "Content-Type": "application/json",
    };

    const cargar = useCallback(async () => {
        setCargando(true);
        setError("");
        try {
            const [asignacionesResponse, profesoresResponse] = await Promise.all([
                fetch(`${API_URL}/asignaciones-profesores`, { headers }),
                fetch(`${API_URL}/asignaciones-profesores/profesores-activos`, { headers }),
            ]);
            const parse = async (response) => {
                const data = await response.json().catch(() => ({}));
                if (!response.ok) throw new Error(data.message || "No se pudo cargar la información académica.");
                return data;
            };
            const [lista, activos] = await Promise.all([
                parse(asignacionesResponse),
                parse(profesoresResponse),
            ]);
            setAsignaciones(lista);
            setProfesores(activos);
            setSelecciones((prev) => {
                const next = { ...prev };
                lista.forEach((item) => {
                    if (item.idProfesor && !next[item.idGrupo]) next[item.idGrupo] = String(item.idProfesor);
                });
                return next;
            });
        } catch (e) {
            setError(e.message || "No se pudo cargar la lista de asignaciones.");
        } finally {
            setCargando(false);
        }
    }, []);

    useEffect(() => {
        cargar();
    }, [cargar]);

    async function asignar(item) {
        const idProfesor = selecciones[item.idGrupo];
        if (!idProfesor) {
            setError("Seleccioná un profesor activo antes de guardar la asignación.");
            return;
        }
        setOcupado(item.idGrupo);
        setError("");
        setMensaje("");
        try {
            const response = await fetch(`${API_URL}/asignaciones-profesores/grupos/${item.idGrupo}`, {
                method: "PATCH",
                headers,
                body: JSON.stringify({ idProfesor: Number(idProfesor) }),
            });
            const data = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(data.message || "No se pudo guardar la asignación.");
            setMensaje(`Asignación guardada: ${data.nombreProfesor} — ${data.codigoCurso} / ${data.codigoGrupo}.`);
            await cargar();
        } catch (e) {
            setError(e.message || "No se pudo guardar la asignación.");
        } finally {
            setOcupado(null);
        }
    }

    async function eliminar(item) {
        if (!item.idProfesor) {
            setError("Este grupo no tiene una asignación que eliminar.");
            return;
        }
        const ok = window.confirm(`¿Eliminar la asignación de ${item.nombreProfesor} al grupo ${item.codigoGrupo}? Solo se permite antes del inicio del período.`);
        if (!ok) return;
        setOcupado(item.idGrupo);
        setError("");
        setMensaje("");
        try {
            const response = await fetch(`${API_URL}/asignaciones-profesores/grupos/${item.idGrupo}`, {
                method: "DELETE",
                headers,
            });
            if (!response.ok) {
                const data = await response.json().catch(() => ({}));
                throw new Error(data.message || "No se pudo eliminar la asignación.");
            }
            setMensaje(`Se eliminó la asignación del grupo ${item.codigoGrupo}.`);
            await cargar();
        } catch (e) {
            setError(e.message || "No se pudo eliminar la asignación.");
        } finally {
            setOcupado(null);
        }
    }

    return (
        <div className="asignar-profesores-page">
            <PageHead
                title="Asignar profesores a cursos y grupos"
                sub="Asigne un profesor responsable a cada grupo y revise los horarios antes de guardar."
            />
            <div className="asignar-profesores-toolbar">
                <p><CalendarClock size={17} /> Solo se permiten cambios antes del inicio del período académico.</p>
                <button type="button" className="sg-btn sg-btn--secondary" onClick={cargar} disabled={cargando || ocupado !== null}>
                    <RefreshCw size={16} /> Actualizar
                </button>
            </div>
            {error && <Alert tone="danger" title="No se pudo completar la operación">{error}</Alert>}
            {mensaje && <Alert tone="success" title="Operación completada">{mensaje}</Alert>}
            {cargando ? (
                <div className="asignar-profesores-empty">Cargando grupos y profesores activos…</div>
            ) : asignaciones.length === 0 ? (
                <div className="asignar-profesores-empty">No hay grupos planificados o publicados para asignar.</div>
            ) : (
                <div className="asignar-profesores-table-wrap">
                    <table className="asignar-profesores-table">
                        <thead>
                            <tr>
                                <th>Período</th>
                                <th>Curso</th>
                                <th>Grupo</th>
                                <th>Horario</th>
                                <th>Profesor responsable</th>
                                <th>Acciones</th>
                            </tr>
                        </thead>
                        <tbody>
                            {asignaciones.map((item) => (
                                <tr key={item.idGrupo}>
                                    <td>
                                        <strong>{item.nombrePeriodo}</strong>
                                        <small>Inicia: {item.inicioPeriodo || "Sin fecha"}</small>
                                    </td>
                                    <td>
                                        <strong>{item.codigoCurso}</strong>
                                        <small>{item.nombreCurso}</small>
                                    </td>
                                    <td>{item.codigoGrupo}</td>
                                    <td>
                                        {item.horarios?.length ? item.horarios.map((h) => (
                                            <small key={h.idHorario}>
                                                {DIAS[Number(h.diaSemana)]}: {String(h.horaInicio).slice(0, 5)}–{String(h.horaFin).slice(0, 5)}
                                            </small>
                                        )) : <small>Sin horario registrado</small>}
                                    </td>
                                    <td>
                                        <select
                                            className="sg-input sg-select asignar-profesores-select"
                                            value={selecciones[item.idGrupo] || ""}
                                            onChange={(e) => setSelecciones((prev) => ({ ...prev, [item.idGrupo]: e.target.value }))}
                                            disabled={ocupado !== null || !item.inicioPeriodo || new Date(`${item.inicioPeriodo}T00:00:00`) <= new Date(new Date().toDateString())}
                                            aria-label={`Profesor para grupo ${item.codigoGrupo}`}
                                        >
                                            <option value="">Seleccione un profesor activo</option>
                                            {profesores.map((p) => (
                                                <option key={p.idProfesor} value={p.idProfesor}>
                                                    {nombreCompleto(p.nombreCompleto)}{p.especialidad ? ` — ${p.especialidad}` : ""}
                                                </option>
                                            ))}
                                        </select>
                                        {item.idProfesor && <small>Actual: {item.nombreProfesor}</small>}
                                    </td>
                                    <td>
                                        <div className="asignar-profesores-actions">
                                            <button type="button" className="sg-btn sg-btn--primary sg-btn--sm" onClick={() => asignar(item)} disabled={ocupado !== null || !item.inicioPeriodo || new Date(`${item.inicioPeriodo}T00:00:00`) <= new Date(new Date().toDateString())}>
                                                <UserCheck size={15} /> {ocupado === item.idGrupo ? "Guardando…" : "Asignar"}
                                            </button>
                                            {item.idProfesor && (
                                                <button type="button" className="sg-btn sg-btn--secondary sg-btn--sm" onClick={() => eliminar(item)} disabled={ocupado !== null}>
                                                    <UserMinus size={15} /> Quitar
                                                </button>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}

export default AsignarProfesores;
