import { useState } from "react";
import { API_URL } from "../../config/api";
import CamposRequisitosCurso from "./CamposRequisitosCurso";
import "./requisitosCurso.css";

export default function RequisitosCursoOpcionales({ idPrograma, idCurso, value, onChange, disabled }) {
    const [abierto, setAbierto] = useState(false);
    const [datos, setDatos] = useState(null);
    const [cargando, setCargando] = useState(false);
    const [error, setError] = useState("");

    async function cargar() {
        setCargando(true);
        setError("");
        try {
            const params = new URLSearchParams({ idPrograma });
            if (idCurso) params.set("idCurso", idCurso);
            const response = await fetch(`${API_URL}/requisitos-cursos/opciones?${params}`, {
                headers: { Authorization: `Bearer ${localStorage.getItem("token")}` },
            });
            const body = await response.json();
            if (!response.ok) throw new Error(body.message || "No fue posible consultar los requisitos.");
            setDatos(body);
        } catch (e) {
            setError(e.message);
        } finally {
            setCargando(false);
        }
    }

    const configuracion = datos ? { ...datos, ...value } : null;
    if (configuracion) {
        configuracion.cursos = [...datos.cursos];
        for (const id of [...(configuracion.prerrequisitos || []), ...(configuracion.correquisitos || [])]) {
            if (!configuracion.cursos.some(c => c.idCurso === id)) {
                configuracion.cursos.push({ idCurso: id, nombre: `Curso ${id} (ya no disponible en el plan)` });
            }
        }
    }

    return (
        <section className="requisitos-curso requisitos-curso--integrado">
            <header className="requisitos-curso__header">
                <h2>Requisitos del curso</h2>
                <p>Opcional. Configure prerrequisitos, correquisitos y restricciones de edad para este curso.</p>
            </header>
            <button type="button" className="btn-secundario" disabled={disabled || !idPrograma}
                aria-expanded={abierto} onClick={() => {
                    setAbierto(!abierto);
                    if (!abierto && !datos && !cargando) cargar();
                }}>
                {abierto ? "Ocultar configuración" : "Configurar requisitos"}
            </button>
            {!idPrograma && <p>Seleccione primero el programa académico.</p>}
            {abierto && <>
                {cargando && <div className="requisitos-curso__cargando" role="status">Cargando configuración...</div>}
                {error && <div className="requisitos-curso__mensaje requisitos-curso__mensaje--error" role="alert">
                    <p>{error}</p><button type="button" className="btn-secundario" onClick={cargar} disabled={disabled || cargando}>Reintentar</button>
                </div>}
                {configuracion && <>
                    <CamposRequisitosCurso configuracion={configuracion} guardando={disabled || cargando}
                        setConfiguracion={next => onChange({
                            prerrequisitos: next.prerrequisitos || [],
                            correquisitos: next.correquisitos || [],
                            edadMinima: next.edadMinima ?? null,
                            edadMaxima: next.edadMaxima ?? null,
                        })} />
                    <div className="requisitos-curso__acciones">
                        <button type="button" className="btn-secundario" disabled={disabled || !value}
                            onClick={() => onChange(undefined)}>Deshacer cambios en requisitos</button>
                    </div>
                    <p className="requisitos-curso__nota">La configuración se aplica al guardar el curso.</p>
                </>}
            </>}
        </section>
    );
}
