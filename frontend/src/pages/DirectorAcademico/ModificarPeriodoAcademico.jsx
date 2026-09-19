import { useEffect, useState } from "react";
import { Search, CalendarRange } from "lucide-react";
import { API_URL } from "../../config/api";
import "./crearCurso.css";
import "./desactivarCurso.css";

import { useDialog } from "../../components/dialog/useDialog";

const ESTADOS = [
    { value: "PLANIFICADO", label: "Planificado" },
    { value: "MATRICULA", label: "Matrícula" },
    { value: "EN_CURSO", label: "Activo (en curso)" },
    { value: "CERRADO", label: "Cerrado" },
    { value: "CANCELADO", label: "Cancelado" },
];

const ETIQUETAS_ESTADO = Object.fromEntries(
    ESTADOS.map((e) => [e.value, e.label])
);

function ModificarPeriodoAcademico() {
    const dialog = useDialog();

    const token = localStorage.getItem("token");

    const [periodos, setPeriodos] = useState([]);
    const [cargando, setCargando] = useState(true);

    const [busqueda, setBusqueda] = useState("");

    const [periodoEnEdicion, setPeriodoEnEdicion] = useState(null);
    const [form, setForm] = useState(null);
    const [errores, setErrores] = useState({});
    const [guardando, setGuardando] = useState(false);

    useEffect(() => {
        cargarPeriodos();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    async function cargarPeriodos() {

        setCargando(true);

        try {

            const response = await fetch(`${API_URL}/periodos-academicos/todos`, {
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            });

            if (response.status === 401) {
                throw new Error(
                    "La sesión ha expirado. Inicie sesión nuevamente."
                );
            }

            if (response.status === 403) {
                throw new Error(
                    "No tiene permisos para consultar los períodos académicos."
                );
            }

            if (!response.ok) {
                throw new Error(
                    "No fue posible cargar los períodos académicos."
                );
            }

            const data = await response.json();
            setPeriodos(data);

        } catch (e) {

            dialog.error(
                e.message ||
                "Ocurrió un error al cargar los períodos académicos."
            );

        } finally {

            setCargando(false);
        }
    }

    const periodosFiltrados = periodos.filter((p) => {
        const texto = busqueda.trim().toLowerCase();

        return (
            !texto ||
            p.nombre.toLowerCase().includes(texto) ||
            (p.codigo || "").toLowerCase().includes(texto)
        );
    });

    function iniciarEdicion(periodo) {

        setPeriodoEnEdicion(periodo);
        setForm({
            nombre: periodo.nombre || "",
            fechaInicio: periodo.fechaInicio || "",
            fechaFin: periodo.fechaFin || "",
            fechaInicioMatricula: periodo.fechaInicioMatricula || "",
            fechaFinMatricula: periodo.fechaFinMatricula || "",
            estado: periodo.estado || "PLANIFICADO",
        });
        setErrores({});
    }

    function cancelarEdicion() {
        setPeriodoEnEdicion(null);
        setForm(null);
        setErrores({});
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

        const erroresValidacion = validarFormulario();

        if (Object.keys(erroresValidacion).length > 0) {
            setErrores(erroresValidacion);
            dialog.validacion(erroresValidacion);
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
                `${API_URL}/periodos-academicos/${periodoEnEdicion.id}`,
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

            setPeriodos((prev) =>
                prev.map((p) => (p.id === data.id ? data : p))
            );

            dialog.exito(
                `El período "${data.nombre}" fue modificado correctamente.`
            );

            setPeriodoEnEdicion(null);
            setForm(null);

        } catch (e) {

            dialog.error(
                e.message ||
                "Ocurrió un error al modificar el período académico."
            );

        } finally {

            setGuardando(false);
        }
    }

    return (
        <div className="container">

            <div className="curso-page">

                <div className="curso-page__header">
                    <div>
                        <h1>Consultar / Modificar período académico</h1>

                        <p>
                            Busque un período académico y actualice sus
                            fechas o estado. Su información histórica no
                            se elimina al modificarlo.
                        </p>
                    </div>
                </div>



                {!periodoEnEdicion && (
                    <>
                        <div className="desactivar-curso__filtros">
                            <div className="desactivar-curso__buscar">
                                <Search size={18} />
                                <input
                                    type="text"
                                    placeholder="Buscar por nombre o código"
                                    value={busqueda}
                                    onChange={(e) => setBusqueda(e.target.value)}
                                />
                            </div>
                        </div>

                        <div className="desactivar-curso__lista">

                            {cargando && (
                                <div className="desactivar-curso__vacio">
                                    Cargando períodos académicos...
                                </div>
                            )}

                            {!cargando && periodosFiltrados.length === 0 && (
                                <div className="desactivar-curso__vacio">
                                    No hay períodos académicos que coincidan
                                    con la búsqueda.
                                </div>
                            )}

                            {!cargando && periodosFiltrados.map((periodo) => (
                                <div className="curso-card" key={periodo.id}>
                                    <div className="curso-card__info">
                                        <div className="curso-card__titulo">
                                            <CalendarRange
                                                size={18}
                                                className="curso-card__icono"
                                            />
                                            <div>
                                                <h3>{periodo.nombre}</h3>
                                                <span className="curso-card__codigo">
                                                    {periodo.codigo}
                                                </span>
                                            </div>
                                        </div>
                                        <div className="curso-card__detalles">
                                            <span>
                                                {periodo.fechaInicio} — {periodo.fechaFin}
                                            </span>
                                            <span>
                                                Estado: {ETIQUETAS_ESTADO[periodo.estado] || periodo.estado}
                                            </span>
                                        </div>
                                    </div>
                                    <button
                                        className="btn btn--primary"
                                        onClick={() => iniciarEdicion(periodo)}
                                    >
                                        Modificar
                                    </button>
                                </div>
                            ))}

                        </div>
                    </>
                )}

                {periodoEnEdicion && form && (
                    <form
                        className="curso-form"
                        onSubmit={handleSubmit}
                        noValidate
                    >

                        <div className="curso-form__section">

                            <h2>Modificando: {periodoEnEdicion.codigo}</h2>

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
                                        autoComplete="off"
                                        aria-invalid={Boolean(errores.nombre)}
                                        aria-describedby={errores.nombre ? "nombre-error" : undefined}
                                        required
                                    />

                                    {errores.nombre && (
                                        <span className="curso-field__error" id="nombre-error" role="alert">
                                            {errores.nombre}
                                        </span>
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
                                onClick={cancelarEdicion}
                                disabled={guardando}
                            >
                                Volver al listado
                            </button>

                            <button
                                type="submit"
                                className="btn btn--primary"
                                disabled={guardando}
                            >
                                {guardando
                                    ? "Guardando cambios..."
                                    : "Guardar cambios"}
                            </button>

                        </div>

                    </form>
                )}

            </div>
        </div>
    );
}

export default ModificarPeriodoAcademico;
