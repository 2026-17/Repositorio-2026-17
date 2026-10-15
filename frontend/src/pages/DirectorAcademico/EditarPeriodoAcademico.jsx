import { useState } from "react";
import { Save } from "lucide-react";
import { API_URL } from "../../config/api";
import "./crearCurso.css";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import StatusBadge from "../../components/ui/StatusBadge";
import { listaErrores } from "../../components/ui/utilidades";

import { useDialog } from "../../components/dialog/useDialog";
import { formularioModificado, useAvisoCambiosSinGuardar } from "../../hooks/useAvisoCambiosSinGuardar";
import {
    ESTADOS_PERIODO,
    ETIQUETA_ESTADO_PERIODO,
    TONO_ESTADO_PERIODO,
    cambioEstadoPermitido,
} from "./estadosPeriodo";

function formDesdePeriodo(periodo) {
    return {
        nombre: periodo.nombre || "",
        fechaInicio: periodo.fechaInicio || "",
        fechaFin: periodo.fechaFin || "",
        fechaInicioMatricula: periodo.fechaInicioMatricula || "",
        fechaFinMatricula: periodo.fechaFinMatricula || "",
        estado: periodo.estado || "PLANIFICADO",
    };
}

/*
 * Modificar un período académico. Se abre dentro de la pantalla
 * Períodos académicos (botón "Modificar" de la lista).
 */
function EditarPeriodoAcademico({ periodo, onCancel, onSuccess }) {
    const dialog = useDialog();

    const token = localStorage.getItem("token");

    const [formInicial] = useState(() => formDesdePeriodo(periodo));
    const [form, setForm] = useState(formInicial);
    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);
    const [guardando, setGuardando] = useState(false);

    // Avisa al salir de la pantalla o cerrar la pestaña con cambios pendientes
    const hayCambios = formularioModificado(form, formInicial);
    const { confirmarDescarte, permitirSalida } = useAvisoCambiosSinGuardar(hayCambios);

    async function cancelar() {
        if (hayCambios && !(await confirmarDescarte())) return;
        permitirSalida();
        onCancel();
    }

    function handleChange(e) {

        const { name, value } = e.target;

        setForm((prev) => ({
            ...prev,
            [name]: value,
        }));

        setErrores((prev) => {
            if (!prev[name]) {
                return prev;
            }

            const siguiente = { ...prev };
            delete siguiente[name];
            return siguiente;
        });
    }

    function validarFormulario() {
        const siguientesErrores = {};

        if (!form.nombre.trim()) {
            siguientesErrores.nombre = "Ingrese el nombre del período académico.";
        } else if (form.nombre.trim().length > 100) {
            siguientesErrores.nombre = "El nombre no puede superar los 100 caracteres.";
        }

        if (!form.fechaInicio) {
            siguientesErrores.fechaInicio = "Ingrese la fecha de inicio.";
        }

        if (!form.fechaFin) {
            siguientesErrores.fechaFin = "Ingrese la fecha de fin.";
        } else if (form.fechaInicio && form.fechaFin <= form.fechaInicio) {
            siguientesErrores.fechaFin = "La fecha de fin debe ser posterior a la fecha de inicio.";
        }

        // Las fechas de matrícula van las dos o ninguna
        if (form.fechaInicioMatricula && !form.fechaFinMatricula) {
            siguientesErrores.fechaFinMatricula =
                "Indique también la fecha de fin de matrícula, o deje ambas vacías.";
        } else if (!form.fechaInicioMatricula && form.fechaFinMatricula) {
            siguientesErrores.fechaInicioMatricula =
                "Indique también la fecha de inicio de matrícula, o deje ambas vacías.";
        } else if (
            form.fechaInicioMatricula &&
            form.fechaFinMatricula &&
            form.fechaFinMatricula < form.fechaInicioMatricula
        ) {
            siguientesErrores.fechaFinMatricula =
                "La fecha de fin de matrícula no puede ser anterior a la de inicio.";
        } else if (form.fechaFinMatricula && form.fechaFin && form.fechaFinMatricula > form.fechaFin) {
            siguientesErrores.fechaFinMatricula =
                "La fecha de fin de matrícula no puede ser posterior a la fecha de fin del período.";
        }

        if (!form.estado) {
            siguientesErrores.estado = "Seleccione el estado del período.";
        } else if (!cambioEstadoPermitido(periodo.estado, form.estado)) {
            siguientesErrores.estado = form.estado === "CANCELADO"
                ? "Solo se puede cancelar un período en estado Planificado o Matrícula."
                : "El estado del período solo puede avanzar: Planificado → Matrícula → En curso → Cerrado.";
        }

        return siguientesErrores;
    }

    async function handleSubmit(e) {

        e.preventDefault();

        const erroresValidacion = validarFormulario();

        if (Object.keys(erroresValidacion).length > 0) {
            setErrores(erroresValidacion);
            setIntento(n => n + 1);
            return;
        }

        setErrores({});
        const payload = {
            nombre: form.nombre.trim(),
            fechaInicio: form.fechaInicio,
            fechaFin: form.fechaFin,
            fechaInicioMatricula: form.fechaInicioMatricula || null,
            fechaFinMatricula: form.fechaFinMatricula || null,
            estado: form.estado,
        };

        setGuardando(true);

        try {

            const response = await fetch(
                `${API_URL}/periodos-academicos/${periodo.id}`,
                {
                    method: "PUT",
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${token}`,
                    },
                    body: JSON.stringify(payload),
                }
            );

            let data = {};

            try {
                data = await response.json();
            } catch {
                // La respuesta puede venir sin cuerpo.
            }

            if (response.status === 401) {
                throw new Error(
                    "La sesión ha expirado. Inicie sesión nuevamente."
                );
            }

            if (response.status === 403) {
                throw new Error(
                    "No tiene permisos para modificar períodos académicos."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.message ||
                    "No fue posible modificar el período académico."
                );
            }

            dialog.exito(
                `El período "${data.nombre || payload.nombre}" fue modificado correctamente.`
            );

            permitirSalida();
            onSuccess();

        } catch (e) {

            dialog.error(
                e.message ||
                "Ocurrió un error al modificar el período académico."
            );

        } finally {

            setGuardando(false);
        }
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores);

    return (
        <div className="curso-page">
            <h2 className="form-section-title form-titulo">Modificar período académico</h2>

            <form onSubmit={handleSubmit} noValidate>
                {resumen.length > 0 && (
                    <Alert
                        key={intento}
                        tone="danger"
                        autoFocus
                        className="form-alert"
                        title={resumen.length === 1 ? "Revise 1 dato antes de guardar" : `Revise ${resumen.length} datos antes de guardar`}
                        errors={resumen}
                    />
                )}

                <section className="panel">
                    <div className="panel-head">
                        <div>
                            <h2>{periodo.codigo}</h2>
                            <p className="panel-head-sub">{periodo.nombre}</p>
                        </div>
                        <StatusBadge tone={TONO_ESTADO_PERIODO[periodo.estado] || "neutral"}>
                            {ETIQUETA_ESTADO_PERIODO[periodo.estado] || periodo.estado}
                        </StatusBadge>
                    </div>
                    <div className="panel-body form-grid">
                        <Field id="nombre" label="Nombre del período" error={errores.nombre} className="span-all">
                            <input id="nombre" name="nombre" type="text" className="sg-input" maxLength="100" value={form.nombre} onChange={handleChange} autoComplete="off" {...aria("nombre")} />
                        </Field>
                        <Field id="fechaInicio" label="Fecha de inicio" error={errores.fechaInicio}>
                            <input id="fechaInicio" name="fechaInicio" type="date" className="sg-input" value={form.fechaInicio} onChange={handleChange} {...aria("fechaInicio")} />
                        </Field>
                        <Field id="fechaFin" label="Fecha de fin" error={errores.fechaFin}>
                            <input id="fechaFin" name="fechaFin" type="date" className="sg-input" value={form.fechaFin} onChange={handleChange} {...aria("fechaFin")} />
                        </Field>
                        <Field id="estado" label="Estado" error={errores.estado} select>
                            <select id="estado" name="estado" className="sg-input sg-select" value={form.estado} onChange={handleChange} {...aria("estado")}>
                                {ESTADOS_PERIODO.map((opcion) => (
                                    <option
                                        key={opcion.value}
                                        value={opcion.value}
                                        disabled={!cambioEstadoPermitido(periodo.estado, opcion.value)}
                                    >
                                        {opcion.label}
                                    </option>
                                ))}
                            </select>
                        </Field>
                    </div>
                </section>

                <section className="panel">
                    <div className="panel-head">
                        <div>
                            <h2>Fechas de matrícula <span className="sg-field__optional">(opcional)</span></h2>
                            <p className="panel-head-sub">Período en que los estudiantes pueden matricular cursos.</p>
                        </div>
                    </div>
                    <div className="panel-body form-grid">
                        <Field id="fechaInicioMatricula" label="Inicio de matrícula" optional error={errores.fechaInicioMatricula}>
                            <input id="fechaInicioMatricula" name="fechaInicioMatricula" type="date" className="sg-input" value={form.fechaInicioMatricula} onChange={handleChange} {...aria("fechaInicioMatricula")} />
                        </Field>
                        <Field id="fechaFinMatricula" label="Fin de matrícula" optional error={errores.fechaFinMatricula}>
                            <input id="fechaFinMatricula" name="fechaFinMatricula" type="date" className="sg-input" value={form.fechaFinMatricula} onChange={handleChange} {...aria("fechaFinMatricula")} />
                        </Field>
                    </div>
                    <div className="panel-foot">
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={cancelar} disabled={guardando}>
                            Cancelar
                        </button>
                        <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando} aria-busy={guardando || undefined}>
                            {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <Save size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {guardando ? "Guardando cambios…" : "Guardar cambios"}
                        </button>
                    </div>
                </section>
            </form>
        </div>
    );
}

export default EditarPeriodoAcademico;
