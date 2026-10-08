import { useState } from "react";
import { ChevronDown, ListChecks, RotateCcw } from "lucide-react";
import { API_URL } from "../../config/api";
import CamposRequisitosCurso from "./CamposRequisitosCurso";
import "./requisitosCurso.css";

import { useDialog } from "../../components/dialog/useDialog";

export default function RequisitosCursoOpcionales({ idPrograma, idCurso, value, onChange, disabled }) {
    const dialog = useDialog();
    const [abierto, setAbierto] = useState(false);
    const [datos, setDatos] = useState(null);
    const [cargando, setCargando] = useState(false);

    async function cargar() {
        setCargando(true);
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
            dialog.error(e.message);
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
        <section className="panel requisitos-panel" aria-labelledby="requisitos-titulo">
            <div className="panel-head">
                <div>
                    <h2 id="requisitos-titulo">Requisitos del curso <span className="sg-field__optional">(opcional)</span></h2>
                    <p className="panel-head-sub">
                        {idPrograma
                            ? "Configure prerrequisitos, correquisitos y restricciones de edad para este curso."
                            : "Seleccione primero el programa académico."}
                    </p>
                </div>
                <button
                    type="button"
                    className="sg-btn sg-btn--secondary sg-btn--sm"
                    disabled={disabled || !idPrograma}
                    aria-expanded={abierto}
                    onClick={() => {
                        setAbierto(!abierto);
                        if (!abierto && !datos && !cargando) cargar();
                    }}
                >
                    {abierto ? <ChevronDown size={16} strokeWidth={1.75} className="girar" aria-hidden="true" /> : <ListChecks size={16} strokeWidth={1.75} aria-hidden="true" />}
                    {abierto ? "Ocultar configuración" : "Configurar requisitos"}
                </button>
            </div>

            {abierto && (
                <div className="panel-body">
                    {cargando && (
                        <p className="muted requisitos-curso__cargando" role="status">Cargando configuración...</p>
                    )}
                    {configuracion && (
                        <>
                            <CamposRequisitosCurso
                                configuracion={configuracion}
                                guardando={disabled || cargando}
                                setConfiguracion={next => onChange({
                                    prerrequisitos: next.prerrequisitos || [],
                                    correquisitos: next.correquisitos || [],
                                    edadMinima: next.edadMinima ?? null,
                                    edadMaxima: next.edadMaxima ?? null,
                                })}
                            />
                            <div className="requisitos-curso__pie">
                                <p className="muted">La configuración se aplica al guardar el curso.</p>
                                <button
                                    type="button"
                                    className="sg-btn sg-btn--ghost sg-btn--sm"
                                    disabled={disabled || !value}
                                    onClick={() => onChange(undefined)}
                                >
                                    <RotateCcw size={16} strokeWidth={1.75} aria-hidden="true" />
                                    Deshacer cambios en requisitos
                                </button>
                            </div>
                        </>
                    )}
                </div>
            )}
        </section>
    );
}
