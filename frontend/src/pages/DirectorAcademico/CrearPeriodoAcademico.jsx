import { useState } from "react";
import { API_URL } from "../../config/api";
import { CalendarPlus } from "lucide-react";
import "./crearCurso.css";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import { listaErrores } from "../../components/ui/utilidades";

import { useDialog } from "../../components/dialog/useDialog";
import { formularioModificado, useAvisoCambiosSinGuardar } from "../../hooks/useAvisoCambiosSinGuardar";

const FORM_VACIO = {
    nombre: "",
    fechaInicio: "",
    fechaFin: "",
    fechaInicioMatricula: "",
    fechaFinMatricula: "",
    estado: "PLANIFICADO",
};

const ESTADOS = [
    { value: "PLANIFICADO", label: "Planificado" },
    { value: "MATRICULA", label: "Matrícula" },
    { value: "EN_CURSO", label: "Activo (en curso)" },
    { value: "CERRADO", label: "Cerrado" },
];

function CrearPeriodoAcademico() {
    const dialog = useDialog();

    const token = localStorage.getItem("token");

    const [form, setForm] = useState(FORM_VACIO);

    const [guardando, setGuardando] = useState(false);

    // Avisa al salir con datos escritos que no se han guardado
    useAvisoCambiosSinGuardar(formularioModificado(form, FORM_VACIO));

    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);

    function handleChange(e) {

        const { name, value } = e.target;

        setForm((prev) => ({
            ...prev,
            [name]: value,
        }));

        // Limpiar el error del campo que el usuario está corrigiendo.
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

            const response = await fetch(`${API_URL}/periodos-academicos`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify(payload),
            });

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
                    "No tiene permisos para crear períodos académicos."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.message ||
                    "No fue posible crear el período académico."
                );
            }

            dialog.exito(
                `El período "${data.nombre || payload.nombre}" fue creado correctamente.`
            );

            setForm(FORM_VACIO);

        } catch (e) {

            dialog.error(
                e.message ||
                "Ocurrió un error al crear el período académico."
            );

        } finally {

            setGuardando(false);
        }
    }

    function limpiarFormulario() {

        setForm(FORM_VACIO);

        setErrores({});
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores);

    return (
        <div className="curso-page">
            <PageHead
                title="Crear período académico"
                sub="Defina el nombre, las fechas y el estado del nuevo período para habilitar la organización de cursos, grupos, matrícula y pagos."
            />

            <form onSubmit={handleSubmit} noValidate>
                {resumen.length > 0 && (
                    <Alert
                        key={intento}
                        tone="danger"
                        autoFocus
                        className="form-alert"
                        title={resumen.length === 1 ? "Revise 1 dato antes de crear el período" : `Revise ${resumen.length} datos antes de crear el período`}
                        errors={resumen}
                    />
                )}

                <section className="panel">
                    <div className="panel-head"><h2>Información del período</h2></div>
                    <div className="panel-body form-grid">
                        <Field id="nombre" label="Nombre del período" hint="Debe ser un nombre único." error={errores.nombre} className="span-all">
                            <input id="nombre" name="nombre" type="text" className="sg-input" maxLength="100" value={form.nombre} onChange={handleChange} placeholder="Ej. I Cuatrimestre 2027" autoComplete="off" {...aria("nombre")} />
                        </Field>
                        <Field id="fechaInicio" label="Fecha de inicio" error={errores.fechaInicio}>
                            <input id="fechaInicio" name="fechaInicio" type="date" className="sg-input" value={form.fechaInicio} onChange={handleChange} {...aria("fechaInicio")} />
                        </Field>
                        <Field id="fechaFin" label="Fecha de fin" error={errores.fechaFin}>
                            <input id="fechaFin" name="fechaFin" type="date" className="sg-input" value={form.fechaFin} onChange={handleChange} {...aria("fechaFin")} />
                        </Field>
                        <Field id="estado" label="Estado" error={errores.estado} select>
                            <select id="estado" name="estado" className="sg-input sg-select" value={form.estado} onChange={handleChange} {...aria("estado")}>
                                {ESTADOS.map((opcion) => (
                                    <option key={opcion.value} value={opcion.value}>
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
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={limpiarFormulario} disabled={guardando}>
                            Limpiar
                        </button>
                        <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando} aria-busy={guardando || undefined}>
                            {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <CalendarPlus size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {guardando ? "Creando período…" : "Crear período académico"}
                        </button>
                    </div>
                </section>
            </form>
        </div>
    );
}

export default CrearPeriodoAcademico;
