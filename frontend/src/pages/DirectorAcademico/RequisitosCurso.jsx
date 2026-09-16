import { useEffect, useMemo, useState } from "react";
import { API_URL } from "../../config/api";
import "./requisitosCurso.css";

function RequisitosCurso() {
    const token = localStorage.getItem("token");

    const [planes, setPlanes] = useState([]);
    const [planId, setPlanId] = useState("");
    const [cursoId, setCursoId] = useState("");
    const [configuracion, setConfiguracion] = useState(null);

    const [cargandoPlanes, setCargandoPlanes] = useState(true);
    const [cargandoConfiguracion, setCargandoConfiguracion] = useState(false);
    const [guardando, setGuardando] = useState(false);
    const [error, setError] = useState("");
    const [mensajeExito, setMensajeExito] = useState("");

    useEffect(() => {
        cargarPlanes();
    }, []);

    useEffect(() => {
        if (planId) {
            cargarCursosDelPlan();
        } else {
            setConfiguracion(null);
            setCursoId("");
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [planId]);

    useEffect(() => {
        if (planId && cursoId) {
            cargarConfiguracion();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [cursoId]);

    async function cargarPlanes() {
        setCargandoPlanes(true);
        setError("");
        try {
            const response = await fetch(`${API_URL}/requisitos-cursos/planes`, {
                headers: { Authorization: `Bearer ${token}` },
            });
            const data = await response.json().catch(() => []);
            if (!response.ok) throw new Error(data.message || "No fue posible cargar los planes de estudio.");
            setPlanes(data);
            if (data.length > 0) setPlanId(String(data[0].idPlanEstudio));
        } catch (e) {
            setError(e.message || "Ocurrió un error al cargar los planes de estudio.");
        } finally {
            setCargandoPlanes(false);
        }
    }

    async function cargarCursosDelPlan() {
        setCargandoConfiguracion(true);
        setError("");
        try {
            // Se consulta temporalmente el primer curso del plan para obtener
            // la lista de cursos disponibles. Luego el usuario elige el curso.
            const responsePlanes = await fetch(`${API_URL}/requisitos-cursos/planes`, {
                headers: { Authorization: `Bearer ${token}` },
            });
            if (!responsePlanes.ok) throw new Error("No fue posible validar el plan de estudio.");

            // La API de configuración necesita curso + plan. GET /cursos es
            // la fuente existente de cursos activos; se filtran por la malla
            // al consultar la configuración de cada curso no sería eficiente.
            // Por ello, usamos el endpoint de cursos del módulo y dejamos que
            // la selección se resuelva al cargar el primer curso válido.
            const response = await fetch(`${API_URL}/cursos`, {
                headers: { Authorization: `Bearer ${token}` },
            });
            const data = await response.json().catch(() => []);
            if (!response.ok) throw new Error("No fue posible cargar los cursos.");

            if (data.length === 0) {
                setCursoId("");
                setConfiguracion(null);
                return;
            }

            // El backend de configuración valida que el curso pertenezca al
            // plan; se selecciona el primero y, si no pertenece, el usuario
            // recibirá el mensaje correspondiente.
            setCursoId(String(data[0].idCurso));
        } catch (e) {
            setError(e.message || "Ocurrió un error al cargar los cursos.");
            setCursoId("");
            setConfiguracion(null);
        } finally {
            setCargandoConfiguracion(false);
        }
    }

    async function cargarConfiguracion() {
        setCargandoConfiguracion(true);
        setError("");
        try {
            const response = await fetch(
                `${API_URL}/requisitos-cursos?idPlanEstudio=${planId}&idCurso=${cursoId}`,
                { headers: { Authorization: `Bearer ${token}` } }
            );
            const data = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(data.message || "El curso no pertenece al plan de estudio seleccionado.");
            setConfiguracion(data);
        } catch (e) {
            setError(e.message || "No fue posible cargar la configuración del curso.");
            setConfiguracion(null);
        } finally {
            setCargandoConfiguracion(false);
        }
    }

    function alternar(id, tipo) {
        setConfiguracion((prev) => {
            if (!prev) return prev;
            const campo = tipo === "pre" ? "prerrequisitos" : "correquisitos";
            const actual = prev[campo] || [];
            const existe = actual.includes(id);
            return {
                ...prev,
                [campo]: existe ? actual.filter((item) => item !== id) : [...actual, id],
            };
        });
    }

    async function guardarConfiguracion() {
        if (!configuracion) return;
        setGuardando(true);
        setError("");
        setMensajeExito("");

        try {
            const response = await fetch(`${API_URL}/requisitos-cursos`, {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({
                    idPlanEstudio: Number(planId),
                    idCurso: Number(cursoId),
                    prerrequisitos: configuracion.prerrequisitos || [],
                    correquisitos: configuracion.correquisitos || [],
                }),
            });

            const data = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(data.message || "No fue posible guardar la configuración.");

            setConfiguracion(data);
            setMensajeExito("La configuración de prerrequisitos y correquisitos se guardó correctamente.");
            setTimeout(() => setMensajeExito(""), 4000);
        } catch (e) {
            setError(e.message || "Ocurrió un error al guardar la configuración.");
        } finally {
            setGuardando(false);
        }
    }

    const cursoSeleccionado = useMemo(() => {
        if (!configuracion) return null;
        return configuracion.cursos?.find((curso) => String(curso.idCurso) === String(cursoId));
    }, [configuracion, cursoId]);

    return (
        <div className="container">
            <div className="requisitos-curso">
                <header className="requisitos-curso__header">
                    <h1>Configurar requisitos del curso</h1>
                    <p>
                        Define los cursos prerrequisito y correquisito para {" "}
                        <strong>
                            {configuracion?.nombreCurso || "el curso seleccionado"}
                            {configuracion?.codigoCurso ? ` (${configuracion.codigoCurso})` : ""}
                        </strong>
                    </p>
                </header>

                <div className="requisitos-curso__selectores">
                    <label>
                        Plan de estudio
                        <select
                            value={planId}
                            onChange={(e) => setPlanId(e.target.value)}
                            disabled={cargandoPlanes || guardando}
                        >
                            <option value="">Seleccione un plan</option>
                            {planes.map((plan) => (
                                <option key={plan.idPlanEstudio} value={plan.idPlanEstudio}>
                                    {plan.codigo} — {plan.nombre} (v{plan.version})
                                </option>
                            ))}
                        </select>
                    </label>

                    {configuracion?.cursos && (
                        <label>
                            Curso
                            <select
                                value={cursoId}
                                onChange={(e) => setCursoId(e.target.value)}
                                disabled={guardando || cargandoConfiguracion}
                            >
                                {configuracion.cursos.map((curso) => (
                                    <option key={curso.idCurso} value={curso.idCurso}>
                                        {curso.codigo} — {curso.nombre}
                                    </option>
                                ))}
                                {cursoSeleccionado && (
                                    <option value={cursoSeleccionado.idCurso}>
                                        {cursoSeleccionado.codigo} — {cursoSeleccionado.nombre}
                                    </option>
                                )}
                            </select>
                        </label>
                    )}
                </div>

                {error && <div className="requisitos-curso__mensaje requisitos-curso__mensaje--error" role="alert">{error}</div>}
                {mensajeExito && <div className="requisitos-curso__mensaje" role="status">{mensajeExito}</div>}

                {cargandoPlanes || cargandoConfiguracion ? (
                    <div className="requisitos-curso__cargando">Cargando configuración...</div>
                ) : configuracion ? (
                    <>
                        <div className="requisitos-curso__grid">
                            <section>
                                <h2>Prerrequisitos</h2>
                                <p>Cursos que el estudiante debe haber aprobado antes</p>
                                <div className="requisitos-curso__lista">
                                    {configuracion.cursos.map((curso) => (
                                        <label className="requisito-card" key={`pre-${curso.idCurso}`}>
                                            <input
                                                type="checkbox"
                                                checked={(configuracion.prerrequisitos || []).includes(curso.idCurso)}
                                                onChange={() => alternar(curso.idCurso, "pre")}
                                                disabled={guardando}
                                            />
                                            <span>
                                                <strong>{curso.nombre}</strong>
                                                <small>{curso.codigo} · Nivel {curso.nivel}</small>
                                            </span>
                                        </label>
                                    ))}
                                    {configuracion.cursos.length === 0 && <div className="requisitos-curso__vacio">No hay otros cursos disponibles en este plan.</div>}
                                </div>
                            </section>

                            <section>
                                <h2>Correquisitos</h2>
                                <p>Cursos que deben cursarse en simultáneo</p>
                                <div className="requisitos-curso__lista">
                                    {configuracion.cursos.map((curso) => (
                                        <label className="requisito-card" key={`co-${curso.idCurso}`}>
                                            <input
                                                type="checkbox"
                                                checked={(configuracion.correquisitos || []).includes(curso.idCurso)}
                                                onChange={() => alternar(curso.idCurso, "co")}
                                                disabled={guardando}
                                            />
                                            <span>
                                                <strong>{curso.nombre}</strong>
                                                <small>{curso.codigo} · Nivel {curso.nivel}</small>
                                            </span>
                                        </label>
                                    ))}
                                    {configuracion.cursos.length === 0 && <div className="requisitos-curso__vacio">No hay otros cursos disponibles en este plan.</div>}
                                </div>
                            </section>
                        </div>

                        <div className="requisitos-curso__acciones">
                            <button type="button" className="btn-secundario" onClick={cargarConfiguracion} disabled={guardando}>
                                Cancelar
                            </button>
                            <button type="button" className="btn-principal" onClick={guardarConfiguracion} disabled={guardando}>
                                {guardando ? "Guardando..." : "Guardar configuración"}
                            </button>
                        </div>
                    </>
                ) : (
                    <div className="requisitos-curso__vacio">Seleccione un plan y un curso para configurar sus requisitos.</div>
                )}
            </div>
        </div>
    );
}

export default RequisitosCurso;
