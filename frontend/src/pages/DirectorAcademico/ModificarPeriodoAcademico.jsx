import { useEffect, useState } from "react";
import { ArrowLeft, CalendarRange, Pencil, Save, Search } from "lucide-react";
import { API_URL } from "../../config/api";
import "./crearCurso.css";
import "./desactivarCurso.css";
import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";
import { listaErrores } from "../../components/ui/utilidades";

import { useDialog } from "../../components/dialog/useDialog";

const ESTADOS = [
    { value: "PLANIFICADO", label: "Planificado" },
    { value: "MATRICULA", label: "Matrícula" },
    { value: "EN_CURSO", label: "Activo (en curso)" },
    { value: "CERRADO", label: "Cerrado" },
    { value: "CANCELADO", label: "Cancelado" },
];

// Color de la insignia según el estado del período
const TONO_ESTADO = {
    PLANIFICADO: "info",
    MATRICULA: "warning",
    EN_CURSO: "success",
    CERRADO: "neutral",
    CANCELADO: "danger",
};

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
    const [intento, setIntento] = useState(0);
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

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores);

    return (
        <div className="curso-page">
            <PageHead
                title="Consultar / Modificar período académico"
                sub="Busque un período académico y actualice sus fechas o estado. Su información histórica no se elimina al modificarlo."
            />

            {!periodoEnEdicion && (
                <div className="panel">
                    <div className="filters" role="search">
                        <Field id="txtBuscarPeriodo" label="Buscar" icon={Search} className="f-search">
                            <input
                                id="txtBuscarPeriodo"
                                type="search"
                                className="sg-input"
                                placeholder="Nombre o código del período"
                                value={busqueda}
                                onChange={(e) => setBusqueda(e.target.value)}
                            />
                        </Field>
                    </div>

                    <div className="chips">
                        <span className="result-count" aria-live="polite">
                            <b>{cargando ? "…" : periodosFiltrados.length}</b>
                            {periodosFiltrados.length === 1 ? " período" : " períodos"}
                        </span>
                    </div>

                    {cargando ? (
                        <p className="muted lista-cargando" role="status">Cargando períodos académicos...</p>
                    ) : periodosFiltrados.length === 0 ? (
                        <EmptyState
                            icon={CalendarRange}
                            title={busqueda.trim() ? `Ningún período coincide con «${busqueda.trim()}»` : "No hay períodos académicos registrados"}
                            description="Revise la ortografía o busque por el código del período."
                        />
                    ) : (
                        <ul className="list-rows">
                            {periodosFiltrados.map((periodo) => (
                                <li key={periodo.id}>
                                    <div className="who">
                                        <span className="avatar avatar--sm avatar--icono" aria-hidden="true">
                                            <CalendarRange size={16} strokeWidth={1.75} />
                                        </span>
                                        <div>
                                            <b>{periodo.nombre}</b>
                                            <span>{periodo.codigo} · {periodo.fechaInicio} — {periodo.fechaFin}</span>
                                        </div>
                                    </div>
                                    <div className="inline-actions">
                                        <StatusBadge tone={TONO_ESTADO[periodo.estado] || "neutral"}>
                                            {ETIQUETAS_ESTADO[periodo.estado] || periodo.estado}
                                        </StatusBadge>
                                        <button type="button" className="sg-btn sg-btn--secondary sg-btn--sm" onClick={() => iniciarEdicion(periodo)}>
                                            <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                            Modificar
                                        </button>
                                    </div>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            )}

            {periodoEnEdicion && form && (
                <form onSubmit={handleSubmit} noValidate>
                    <button type="button" className="link-btn back-link" onClick={cancelarEdicion} disabled={guardando}>
                        <ArrowLeft size={16} strokeWidth={1.75} aria-hidden="true" />
                        Volver a la lista
                    </button>

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
                                <h2>Modificando: {periodoEnEdicion.codigo}</h2>
                                <p className="panel-head-sub">{periodoEnEdicion.nombre}</p>
                            </div>
                            <StatusBadge tone={TONO_ESTADO[periodoEnEdicion.estado] || "neutral"}>
                                {ETIQUETAS_ESTADO[periodoEnEdicion.estado] || periodoEnEdicion.estado}
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
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={cancelarEdicion} disabled={guardando}>
                                Cancelar
                            </button>
                            <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando} aria-busy={guardando || undefined}>
                                {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <Save size={18} strokeWidth={1.75} aria-hidden="true" />}
                                {guardando ? "Guardando cambios…" : "Guardar cambios"}
                            </button>
                        </div>
                    </section>
                </form>
            )}
        </div>
    );
}

export default ModificarPeriodoAcademico;
