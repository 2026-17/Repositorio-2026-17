import { useState } from "react";
import { API_URL } from "../../config/api";
import "./crearCurso.css";

const ESTADOS = [
    { value: "PLANIFICADO", label: "Planificado" },
    { value: "MATRICULA", label: "Matrícula" },
    { value: "EN_CURSO", label: "Activo (en curso)" },
    { value: "CERRADO", label: "Cerrado" },
];

function CrearPeriodoAcademico() {

    const token = localStorage.getItem("token");

    const [form, setForm] = useState({
        nombre: "",
        fechaInicio: "",
        fechaFin: "",
        fechaInicioMatricula: "",
        fechaFinMatricula: "",
        estado: "PLANIFICADO",
    });

    const [guardando, setGuardando] = useState(false);

    const [error, setError] = useState("");
    const [mensajeExito, setMensajeExito] = useState("");
    const [errores, setErrores] = useState({});

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
        setError("");
        setMensajeExito("");
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

        if (
            form.fechaInicioMatricula &&
            form.fechaFinMatricula &&
            form.fechaFinMatricula < form.fechaInicioMatricula
        ) {
            siguientesErrores.fechaFinMatricula =
                "La fecha de fin de matrícula no puede ser anterior a la de inicio.";
        }

        if (!form.estado) {
            siguientesErrores.estado = "Seleccione el estado del período.";
        }

        return siguientesErrores;
    }

    async function handleSubmit(e) {

        e.preventDefault();

        setError("");
        setMensajeExito("");
        const erroresValidacion = validarFormulario();

        if (Object.keys(erroresValidacion).length > 0) {
            setErrores(erroresValidacion);
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

            setMensajeExito(
                `El período "${data.nombre || payload.nombre}" fue creado correctamente.`
            );

            setForm({
                nombre: "",
                fechaInicio: "",
                fechaFin: "",
                fechaInicioMatricula: "",
                fechaFinMatricula: "",
                estado: "PLANIFICADO",
            });

        } catch (e) {

            setError(
                e.message ||
                "Ocurrió un error al crear el período académico."
            );

        } finally {

            setGuardando(false);
        }
    }

    function limpiarFormulario() {

        setForm({
            nombre: "",
            fechaInicio: "",
            fechaFin: "",
            fechaInicioMatricula: "",
            fechaFinMatricula: "",
            estado: "PLANIFICADO",
        });

        setError("");
        setMensajeExito("");
        setErrores({});
    }

    return (
        <div className="container">

            <div className="curso-page">

                <div className="curso-page__header">
                    <div>
                        <h1>Crear período académico</h1>

                        <p>
                            Defina el nombre, las fechas y el estado del
                            nuevo período para habilitar la organización
                            de cursos, grupos, matrícula y pagos.
                        </p>
                    </div>
                </div>

                {error && (
                    <div
                        className="curso-message curso-message--error"
                        role="alert"
                    >
                        {error}
                    </div>
                )}

                {mensajeExito && (
                    <div
                        className="curso-message curso-message--success"
                        role="status"
                    >
                        {mensajeExito}
                    </div>
                )}

                <form
                    className="curso-form"
                    onSubmit={handleSubmit}
                    noValidate
                >

                    <div className="curso-form__section">

                        <h2>Información del período</h2>

                        <div className="curso-form__grid">

                            <div className={`curso-field curso-field--full${errores.nombre ? " curso-field--error" : ""}`}>
                                <label htmlFor="nombre">
                                    Nombre del período *
                                </label>

                                <input
                                    id="nombre"
                                    name="nombre"
                                    type="text"
                                    maxLength="100"
                                    value={form.nombre}
                                    onChange={handleChange}
                                    placeholder="Ej. I Cuatrimestre 2027"
                                    autoComplete="off"
                                    aria-invalid={Boolean(errores.nombre)}
                                    aria-describedby={errores.nombre ? "nombre-error" : undefined}
                                    required
                                />

                                {errores.nombre ? (
                                    <span className="curso-field__error" id="nombre-error" role="alert">
                                        {errores.nombre}
                                    </span>
                                ) : (
                                    <small>
                                        Debe ser un nombre único.
                                    </small>
                                )}
                            </div>

                            <div className={`curso-field${errores.fechaInicio ? " curso-field--error" : ""}`}>
                                <label htmlFor="fechaInicio">
                                    Fecha de inicio *
                                </label>

                                <input
                                    id="fechaInicio"
                                    name="fechaInicio"
                                    type="date"
                                    value={form.fechaInicio}
                                    onChange={handleChange}
                                    aria-invalid={Boolean(errores.fechaInicio)}
                                    aria-describedby={errores.fechaInicio ? "fechaInicio-error" : undefined}
                                    required
                                />

                                {errores.fechaInicio && (
                                    <span className="curso-field__error" id="fechaInicio-error" role="alert">
                                        {errores.fechaInicio}
                                    </span>
                                )}
                            </div>

                            <div className={`curso-field${errores.fechaFin ? " curso-field--error" : ""}`}>
                                <label htmlFor="fechaFin">
                                    Fecha de fin *
                                </label>

                                <input
                                    id="fechaFin"
                                    name="fechaFin"
                                    type="date"
                                    value={form.fechaFin}
                                    onChange={handleChange}
                                    aria-invalid={Boolean(errores.fechaFin)}
                                    aria-describedby={errores.fechaFin ? "fechaFin-error" : undefined}
                                    required
                                />

                                {errores.fechaFin && (
                                    <span className="curso-field__error" id="fechaFin-error" role="alert">
                                        {errores.fechaFin}
                                    </span>
                                )}
                            </div>

                            <div className={`curso-field${errores.estado ? " curso-field--error" : ""}`}>
                                <label htmlFor="estado">
                                    Estado *
                                </label>

                                <select
                                    id="estado"
                                    name="estado"
                                    value={form.estado}
                                    onChange={handleChange}
                                    aria-invalid={Boolean(errores.estado)}
                                    aria-describedby={errores.estado ? "estado-error" : undefined}
                                    required
                                >
                                    {ESTADOS.map((opcion) => (
                                        <option key={opcion.value} value={opcion.value}>
                                            {opcion.label}
                                        </option>
                                    ))}
                                </select>

                                {errores.estado && (
                                    <span className="curso-field__error" id="estado-error" role="alert">
                                        {errores.estado}
                                    </span>
                                )}
                            </div>

                        </div>
                    </div>

                    <div className="curso-form__section">

                        <h2>Fechas de matrícula</h2>

                        <div className="curso-form__grid">

                            <div className={`curso-field${errores.fechaInicioMatricula ? " curso-field--error" : ""}`}>
                                <label htmlFor="fechaInicioMatricula">
                                    Inicio de matrícula
                                </label>

                                <input
                                    id="fechaInicioMatricula"
                                    name="fechaInicioMatricula"
                                    type="date"
                                    value={form.fechaInicioMatricula}
                                    onChange={handleChange}
                                    aria-invalid={Boolean(errores.fechaInicioMatricula)}
                                    aria-describedby={errores.fechaInicioMatricula ? "fechaInicioMatricula-error" : undefined}
                                />

                                {errores.fechaInicioMatricula && (
                                    <span className="curso-field__error" id="fechaInicioMatricula-error" role="alert">
                                        {errores.fechaInicioMatricula}
                                    </span>
                                )}
                            </div>

                            <div className={`curso-field${errores.fechaFinMatricula ? " curso-field--error" : ""}`}>
                                <label htmlFor="fechaFinMatricula">
                                    Fin de matrícula
                                </label>

                                <input
                                    id="fechaFinMatricula"
                                    name="fechaFinMatricula"
                                    type="date"
                                    value={form.fechaFinMatricula}
                                    onChange={handleChange}
                                    aria-invalid={Boolean(errores.fechaFinMatricula)}
                                    aria-describedby={errores.fechaFinMatricula ? "fechaFinMatricula-error" : undefined}
                                />

                                {errores.fechaFinMatricula && (
                                    <span className="curso-field__error" id="fechaFinMatricula-error" role="alert">
                                        {errores.fechaFinMatricula}
                                    </span>
                                )}
                            </div>

                        </div>
                    </div>

                    <p className="curso-required">
                        * Campos obligatorios
                    </p>

                    <div className="curso-form__actions">

                        <button
                            type="button"
                            className="btn curso-btn--secondary"
                            onClick={limpiarFormulario}
                            disabled={guardando}
                        >
                            Limpiar
                        </button>

                        <button
                            type="submit"
                            className="btn btn--primary"
                            disabled={guardando}
                        >
                            {guardando
                                ? "Creando período..."
                                : "Crear período académico"}
                        </button>

                    </div>

                </form>

            </div>
        </div>
    );
}

export default CrearPeriodoAcademico;
