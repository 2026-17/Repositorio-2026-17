import CamposRequisitosCurso from "./CamposRequisitosCurso";
import { useEffect, useState } from "react";
import { API_URL } from "../../config/api";
import "./requisitosCurso.css";

function RequisitosCurso() {
    const token = localStorage.getItem("token");
    const [planes, setPlanes] = useState([]);
    const [cursos, setCursos] = useState([]);
    const [planId, setPlanId] = useState("");
    const [cursoId, setCursoId] = useState("");
    const [configuracion, setConfiguracion] = useState(null);
    const [cargando, setCargando] = useState(true);
    const [guardando, setGuardando] = useState(false);
    const [error, setError] = useState("");
    const [mensajeExito, setMensajeExito] = useState("");

    useEffect(() => { cargarPlanes(); }, []);

    useEffect(() => {
        if (!planId) return;
        cargarCursos(planId);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [planId]);

    useEffect(() => {
        if (planId && cursoId) cargarConfiguracion(planId, cursoId);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [cursoId]);

    async function cargarPlanes() {
        setCargando(true);
        try {
            const response = await fetch(`${API_URL}/requisitos-cursos/planes`, {
                headers: { Authorization: `Bearer ${token}` },
            });
            const data = await response.json().catch(() => []);
            if (!response.ok) throw new Error(data.message || "No fue posible cargar los planes de estudio.");
            setPlanes(data);
            if (data.length) setPlanId(String(data[0].idPlanEstudio));
        } catch (e) {
            setError(e.message || "Ocurrió un error al cargar los planes de estudio.");
        } finally { setCargando(false); }
    }

    async function cargarCursos(idPlan) {
        setCargando(true);
        setError("");
        setConfiguracion(null);
        setCursoId("");
        try {
            const response = await fetch(`${API_URL}/requisitos-cursos/cursos?idPlanEstudio=${idPlan}`, {
                headers: { Authorization: `Bearer ${token}` },
            });
            const data = await response.json().catch(() => []);
            if (!response.ok) throw new Error(data.message || "No fue posible cargar los cursos del plan.");
            setCursos(data);
            if (data.length) setCursoId(String(data[0].idCurso));
        } catch (e) {
            setError(e.message || "Ocurrió un error al cargar los cursos.");
        } finally { setCargando(false); }
    }

    async function cargarConfiguracion(idPlan, idCurso) {
        setCargando(true);
        setError("");
        try {
            const response = await fetch(`${API_URL}/requisitos-cursos?idPlanEstudio=${idPlan}&idCurso=${idCurso}`, {
                headers: { Authorization: `Bearer ${token}` },
            });
            const data = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(data.message || "No fue posible cargar la configuración.");
            setConfiguracion(data);
        } catch (e) {
            setError(e.message || "No fue posible cargar la configuración del curso.");
            setConfiguracion(null);
        } finally { setCargando(false); }
    }

    async function guardarConfiguracion() {
        if (!configuracion) return;
        setGuardando(true); setError(""); setMensajeExito("");
        try {
            const response = await fetch(`${API_URL}/requisitos-cursos`, {
                method: "PUT",
                headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
                body: JSON.stringify({
                    idPlanEstudio: Number(planId),
                    idCurso: Number(cursoId),
                    edadMinima: configuracion.edadMinima ?? null,
                    edadMaxima: configuracion.edadMaxima ?? null,
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
        } finally { setGuardando(false); }
    }

    return (
        <div className="container">
            <div className="requisitos-curso">
                <header className="requisitos-curso__header">
                    <h1>Configurar requisitos del curso</h1>
                    <p>Define los cursos prerrequisito, correquisito y restricción de edad para <strong>
                        {configuracion?.nombreCurso || "el curso seleccionado"}
                        {configuracion?.codigoCurso ? ` (${configuracion.codigoCurso})` : ""}
                    </strong></p>
                </header>

                <div className="requisitos-curso__selectores">
                    <label>Plan de estudio
                        <select value={planId} onChange={(e) => setPlanId(e.target.value)} disabled={guardando}>
                            <option value="">Seleccione un plan</option>
                            {planes.map((plan) => <option key={plan.idPlanEstudio} value={plan.idPlanEstudio}>
                                {plan.codigo} — {plan.nombre} (v{plan.version})
                            </option>)}
                        </select>
                    </label>
                    <label>Curso
                        <select value={cursoId} onChange={(e) => setCursoId(e.target.value)} disabled={!planId || guardando || cargando}>
                            <option value="">Seleccione un curso</option>
                            {cursos.map((curso) => <option key={curso.idCurso} value={curso.idCurso}>
                                {curso.codigo} — {curso.nombre}
                            </option>)}
                        </select>
                    </label>
                </div>

                {error && <div className="requisitos-curso__mensaje requisitos-curso__mensaje--error" role="alert">{error}</div>}
                {mensajeExito && <div className="requisitos-curso__mensaje" role="status">{mensajeExito}</div>}

                {cargando ? <div className="requisitos-curso__cargando">Cargando configuración...</div> : configuracion ? (
                    <>
                        <CamposRequisitosCurso configuracion={configuracion} setConfiguracion={setConfiguracion} guardando={guardando} />
                        <div className="requisitos-curso__acciones">
                            <button type="button" className="btn-secundario" onClick={() => cargarConfiguracion(planId, cursoId)} disabled={guardando}>Cancelar</button>
                            <button type="button" className="btn-principal" onClick={guardarConfiguracion} disabled={guardando}>{guardando ? "Guardando..." : "Guardar configuración"}</button>
                        </div>
                    </>
                ) : <div className="requisitos-curso__vacio">Seleccione un plan y un curso para configurar sus requisitos.</div>}
            </div>
        </div>
    );
}

export default RequisitosCurso;
