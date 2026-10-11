import { useCallback, useEffect, useMemo, useState } from "react";
import { CalendarClock, RefreshCw, UserCheck, UserMinus } from "lucide-react";
import { API_URL } from "../../config/api";
import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";
import { useDialog } from "../../components/dialog/useDialog";
import StatusBadge from "../../components/ui/StatusBadge";
import "./asignarProfesores.css";

const DIAS = ["", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
const TAMANO_PAGINA = 10;

function nombreCompleto(nombre) {
    return nombre || "Sin asignar";
}

function fechaActualCostaRica() {
    const partes = new Intl.DateTimeFormat("en-CA", {
        timeZone: "America/Costa_Rica",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    }).formatToParts(new Date());
    const obtener = (tipo) => partes.find((parte) => parte.type === tipo)?.value || "";
    return `${obtener("year")}-${obtener("month")}-${obtener("day")}`;
}

function AsignarProfesores() {
    const dialog = useDialog();
    const [asignaciones, setAsignaciones] = useState([]);
    const [profesores, setProfesores] = useState([]);
    const [selecciones, setSelecciones] = useState({});
    const [cargando, setCargando] = useState(true);
    const [ocupado, setOcupado] = useState(null);
    const [error, setError] = useState("");
    const [mensaje, setMensaje] = useState("");
    const [busqueda, setBusqueda] = useState("");
    const [periodoSeleccionado, setPeriodoSeleccionado] = useState("");
    const [estadoSeleccionado, setEstadoSeleccionado] = useState("");
    const [pagina, setPagina] = useState(1);

    const cargar = useCallback(async () => {
        setCargando(true);
        setError("");
        try {
            const headers = {
                Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
                "Content-Type": "application/json",
            };
            const [asignacionesResponse, profesoresResponse] = await Promise.all([
                fetch(`${API_URL}/asignaciones-profesores`, { headers }),
                fetch(`${API_URL}/asignaciones-profesores/profesores-activos`, { headers }),
            ]);
            const parse = async (response) => {
                const data = await response.json().catch(() => ({}));
                if (!response.ok) {
                    throw new Error(data.message || "No se pudo cargar la información académica.");
                }
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
                    if (item.idProfesor) next[item.idGrupo] = String(item.idProfesor);
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

    const periodos = useMemo(() => {
        const unicos = new Map();
        asignaciones.forEach((item) => {
            if (item.idPeriodo && item.nombrePeriodo) {
                unicos.set(String(item.idPeriodo), {
                    id: String(item.idPeriodo),
                    nombre: item.nombrePeriodo,
                });
            }
        });
        return [...unicos.values()].sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
    }, [asignaciones]);

    const asignacionesFiltradas = useMemo(() => {
        const termino = busqueda.trim().toLocaleLowerCase("es");
        return asignaciones.filter((item) => {
            const coincideBusqueda = !termino || [
                item.codigoCurso,
                item.nombreCurso,
                item.codigoGrupo,
                item.nombreProfesor,
                item.nombrePeriodo,
            ].some((valor) => String(valor || "").toLocaleLowerCase("es").includes(termino));
            const coincidePeriodo = !periodoSeleccionado || String(item.idPeriodo) === periodoSeleccionado;
            const tieneHorario = Array.isArray(item.horarios) && item.horarios.length > 0;
            const tieneProfesor = Boolean(item.idProfesor);
            const coincideEstado = !estadoSeleccionado
                || (estadoSeleccionado === "ASIGNADO" && tieneProfesor)
                || (estadoSeleccionado === "PENDIENTE" && !tieneProfesor && tieneHorario)
                || (estadoSeleccionado === "SIN_HORARIO" && !tieneHorario);
            return coincideBusqueda && coincidePeriodo && coincideEstado;
        });
    }, [asignaciones, busqueda, periodoSeleccionado, estadoSeleccionado]);

    const totalPaginas = Math.max(1, Math.ceil(asignacionesFiltradas.length / TAMANO_PAGINA));
    const paginaActual = Math.min(pagina, totalPaginas);
    const asignacionesPagina = asignacionesFiltradas.slice(
        (paginaActual - 1) * TAMANO_PAGINA,
        paginaActual * TAMANO_PAGINA
    );

    function limpiarFiltros() {
        setBusqueda("");
        setPeriodoSeleccionado("");
        setEstadoSeleccionado("");
        setPagina(1);
    }

    function fechaPeriodoPendiente(item) {
        return Boolean(item.inicioPeriodo) && item.inicioPeriodo > fechaActualCostaRica();
    }

    function puedeAsignar(item) {
        return fechaPeriodoPendiente(item)
            && Array.isArray(item.horarios)
            && item.horarios.length > 0;
    }

    function motivoBloqueo(item) {
        if (!Array.isArray(item.horarios) || item.horarios.length === 0) {
            return "Registre el horario del grupo en la pantalla de gestión de grupos y horarios antes de asignar un profesor.";
        }
        if (!item.inicioPeriodo) {
            return "El grupo no tiene una fecha de inicio válida para determinar el plazo de asignación.";
        }
        if (!fechaPeriodoPendiente(item)) {
            return "El período ya inició; la asignación solo puede modificarse antes de esa fecha.";
        }
        return "";
    }

    async function asignar(item) {
        const idProfesor = selecciones[item.idGrupo];
        if (!idProfesor) {
            setError("Seleccione un profesor activo antes de guardar la asignación.");
            return;
        }
        if (!puedeAsignar(item)) {
            setError(motivoBloqueo(item));
            return;
        }
        setOcupado(item.idGrupo);
        setError("");
        setMensaje("");
        try {
            const response = await fetch(`${API_URL}/asignaciones-profesores/grupos/${item.idGrupo}`, {
                method: "PATCH",
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
                    "Content-Type": "application/json",
                },
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
        if (!fechaPeriodoPendiente(item)) {
            setError("La asignación solo puede quitarse antes del inicio del período académico.");
            return;
        }
        const confirmado = await dialog.confirmar(
            `¿Quitar a ${item.nombreProfesor} como profesor del grupo ${item.codigoGrupo}?`,
            {
                peligro: true,
                textoAceptar: "Quitar asignación",
            }
        );
        if (!confirmado) return;

        setOcupado(item.idGrupo);
        setError("");
        setMensaje("");
        try {
            const response = await fetch(`${API_URL}/asignaciones-profesores/grupos/${item.idGrupo}`, {
                method: "DELETE",
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
                    "Content-Type": "application/json",
                },
            });
            if (!response.ok) {
                const data = await response.json().catch(() => ({}));
                throw new Error(data.message || "No se pudo eliminar la asignación.");
            }
            setMensaje(`Se eliminó la asignación del grupo ${item.codigoGrupo}.`);
            setSelecciones((prev) => {
                const next = { ...prev };
                delete next[item.idGrupo];
                return next;
            });
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
                actions={[
                    <button
                        key="actualizar"
                        id="btnActualizarAsignaciones"
                        type="button"
                        className="sg-btn sg-btn--secondary"
                        onClick={cargar}
                        disabled={cargando || ocupado !== null}
                    >
                        <RefreshCw size={20} strokeWidth={1.75} aria-hidden="true" />
                        Actualizar
                    </button>,
                ]}
            />

            <div className="asignar-profesores-nota">
                <CalendarClock size={20} strokeWidth={1.75} aria-hidden="true" />
                <p>Las asignaciones solo se pueden modificar antes del inicio del período académico. Los grupos deben tener horario registrado.</p>
            </div>

            {error && (
                <Alert tone="danger" title="No se pudo completar la operación" onDismiss={() => setError("")} className="asignar-profesores-alert">
                    {error}
                </Alert>
            )}
            {mensaje && (
                <Alert tone="success" title="Operación completada" onDismiss={() => setMensaje("")} className="asignar-profesores-alert">
                    {mensaje}
                </Alert>
            )}

            <section className="panel">
                <div className="filters" role="search" aria-label="Filtros de asignaciones docentes">
                    <Field id="txtBuscarAsignacion" label="Buscar" className="f-search" hint="Curso, grupo, período o profesor">
                        <input
                            id="txtBuscarAsignacion"
                            type="search"
                            className="sg-input"
                            value={busqueda}
                            onChange={(event) => {
                                setBusqueda(event.target.value);
                                setPagina(1);
                            }}
                            placeholder="Escriba un curso, grupo o profesor"
                            autoComplete="off"
                        />
                    </Field>
                    <Field id="cmbPeriodoAsignacion" label="Período" select>
                        <select
                            id="cmbPeriodoAsignacion"
                            className="sg-input sg-select"
                            value={periodoSeleccionado}
                            onChange={(event) => {
                                setPeriodoSeleccionado(event.target.value);
                                setPagina(1);
                            }}
                        >
                            <option value="">Todos los períodos</option>
                            {periodos.map((periodo) => (
                                <option key={periodo.id} value={periodo.id}>{periodo.nombre}</option>
                            ))}
                        </select>
                    </Field>
                    <Field id="cmbEstadoAsignacion" label="Estado de asignación" select>
                        <select
                            id="cmbEstadoAsignacion"
                            className="sg-input sg-select"
                            value={estadoSeleccionado}
                            onChange={(event) => {
                                setEstadoSeleccionado(event.target.value);
                                setPagina(1);
                            }}
                        >
                            <option value="">Todos los estados</option>
                            <option value="ASIGNADO">Con profesor asignado</option>
                            <option value="PENDIENTE">Pendiente de asignar</option>
                            <option value="SIN_HORARIO">Sin horario registrado</option>
                        </select>
                    </Field>
                    <div className="f-actions">
                        <button type="button" className="link-btn" onClick={limpiarFiltros} disabled={!busqueda && !periodoSeleccionado && !estadoSeleccionado}>
                            Limpiar filtros
                        </button>
                    </div>
                </div>

                <div className="chips">
                    <span className="result-count" aria-live="polite">
                        <strong>{asignacionesFiltradas.length}</strong>
                        {asignacionesFiltradas.length === 1 ? " resultado" : " resultados"}
                    </span>
                    {(busqueda || periodoSeleccionado || estadoSeleccionado) && (
                        <button type="button" className="link-btn" onClick={limpiarFiltros}>
                            Quitar filtros
                        </button>
                    )}
                </div>

                {cargando ? (
                    <div className="panel-body" aria-busy="true">Cargando grupos y profesores activos…</div>
                ) : asignacionesFiltradas.length === 0 ? (
                    <EmptyState
                        title={asignaciones.length === 0 ? "No hay grupos para asignar" : "No hay resultados con estos filtros"}
                        description={asignaciones.length === 0
                            ? "Los grupos planificados o publicados de los períodos académicos aparecerán aquí."
                            : "Pruebe con otro término o quite los filtros para ver otras asignaciones."}
                        action={asignaciones.length > 0
                            ? <button type="button" className="sg-btn sg-btn--secondary" onClick={limpiarFiltros}>Quitar filtros</button>
                            : null}
                    />
                ) : (
                    <>
                        <div className="table-wrap">
                            <table id="tblAsignacionesProfesores" className="dtable asignar-profesores-table">
                                <caption className="sr-only">Asignaciones de profesores a cursos y grupos</caption>
                                <thead>
                                    <tr>
                                        <th scope="col">Período</th>
                                        <th scope="col">Curso</th>
                                        <th scope="col">Grupo</th>
                                        <th scope="col">Horario</th>
                                        <th scope="col">Profesor responsable</th>
                                        <th scope="col">Estado</th>
                                        <th scope="col" className="actions">Acciones</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {asignacionesPagina.map((item) => {
                                        const horarioRegistrado = Array.isArray(item.horarios) && item.horarios.length > 0;
                                        const plazoAbierto = fechaPeriodoPendiente(item);
                                        const habilitado = puedeAsignar(item);
                                        const mismaAsignacion = Boolean(
                                            item.idProfesor
                                            && String(item.idProfesor) === selecciones[item.idGrupo]
                                        );
                                        const motivo = motivoBloqueo(item);
                                        return (
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
                                                    {horarioRegistrado ? item.horarios.map((horario) => (
                                                        <small key={horario.idHorario}>
                                                            {DIAS[Number(horario.diaSemana)]}: {String(horario.horaInicio).slice(0, 5)}–{String(horario.horaFin).slice(0, 5)}
                                                        </small>
                                                    )) : (
                                                        <span className="asignar-profesores-sin-horario">Sin horario registrado</span>
                                                    )}
                                                </td>
                                                <td>
                                                    <label className="sr-only" htmlFor={`cmbProfesorGrupo-${item.idGrupo}`}>
                                                        Profesor responsable del grupo {item.codigoGrupo}
                                                    </label>
                                                    <select
                                                        id={`cmbProfesorGrupo-${item.idGrupo}`}
                                                        className="sg-input sg-select asignar-profesores-select"
                                                        value={selecciones[item.idGrupo] || ""}
                                                        onChange={(event) => setSelecciones((prev) => ({ ...prev, [item.idGrupo]: event.target.value }))}
                                                        disabled={ocupado !== null || !habilitado}
                                                    >
                                                        <option value="">Seleccione un profesor activo</option>
                                                        {profesores.map((profesor) => (
                                                            <option key={profesor.idProfesor} value={profesor.idProfesor}>
                                                                {nombreCompleto(profesor.nombreCompleto)}{profesor.especialidad ? ` — ${profesor.especialidad}` : ""}
                                                            </option>
                                                        ))}
                                                    </select>
                                                    {item.idProfesor && <small className="asignar-profesores-actual">Actual: {item.nombreProfesor}</small>}
                                                </td>
                                                <td>
                                                    {item.idProfesor ? (
                                                        <StatusBadge tone="success" icon={UserCheck}>Asignado</StatusBadge>
                                                    ) : horarioRegistrado ? (
                                                        <StatusBadge tone="warning">Pendiente</StatusBadge>
                                                    ) : (
                                                        <StatusBadge tone="neutral" icon={CalendarClock}>Sin horario</StatusBadge>
                                                    )}
                                                </td>
                                                <td className="actions">
                                                    <div className="row-actions asignar-profesores-actions">
                                                        <button
                                                            id={`btnAsignarProfesor-${item.idGrupo}`}
                                                            type="button"
                                                            className="sg-btn sg-btn--primary sg-btn--sm"
                                                            onClick={() => asignar(item)}
                                                            disabled={ocupado !== null || !habilitado || mismaAsignacion}
                                                            title={mismaAsignacion
                                                                ? "Seleccione un profesor diferente para cambiar la asignación."
                                                                : !habilitado ? motivo : "Guardar la asignación del profesor"}
                                                        >
                                                            <UserCheck size={20} strokeWidth={1.75} aria-hidden="true" />
                                                            {ocupado === item.idGrupo ? "Guardando…" : item.idProfesor ? "Cambiar" : "Asignar"}
                                                        </button>
                                                        {item.idProfesor && (
                                                            <button
                                                                id={`btnQuitarProfesor-${item.idGrupo}`}
                                                                type="button"
                                                                className="sg-btn sg-btn--danger sg-btn--sm"
                                                                onClick={() => eliminar(item)}
                                                                disabled={ocupado !== null || !plazoAbierto}
                                                                title={!plazoAbierto ? "El período ya inició o no tiene fecha válida." : !horarioRegistrado ? motivo : "Quitar la asignación"}
                                                            >
                                                                <UserMinus size={20} strokeWidth={1.75} aria-hidden="true" />
                                                                Quitar
                                                            </button>
                                                        )}
                                                    </div>
                                                    {!habilitado && <small className="asignar-profesores-bloqueo">{motivo}</small>}
                                                    {habilitado && mismaAsignacion && (
                                                        <small className="asignar-profesores-bloqueo">El profesor seleccionado ya está asignado.</small>
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                        <div className="table-foot">
                            <Pagination
                                page={paginaActual}
                                pageSize={TAMANO_PAGINA}
                                total={asignacionesFiltradas.length}
                                onChange={setPagina}
                            />
                        </div>
                    </>
                )}
            </section>
        </div>
    );
}

export default AsignarProfesores;
