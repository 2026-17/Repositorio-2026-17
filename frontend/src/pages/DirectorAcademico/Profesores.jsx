import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { API_URL } from "../../config/api.js";
import { ChevronsUpDown, GraduationCap, Pencil, Plus, Save, Search, X } from "lucide-react";
import "./Profesores.css";
import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import CedulaInput from "../../components/ui/CedulaInput";
import { ATAJO_BUSCADOR, useAtajoBuscador } from "../../hooks/useAtajoBuscador";
import { ajustarIdentificacionAlTipo, esCedulaNacional, formatearCedula, identificacionCoincide, mostrarIdentificacion, textoDeBusqueda } from "../../utils/cedula";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";
import StatusBadge from "../../components/ui/StatusBadge";
import { iniciales, listaErrores, paginar } from "../../components/ui/utilidades";
import { campoDeConflicto, lanzarError, leerError } from "../../utils/api";
import {
    MENSAJE_NOMBRE,
    MENSAJE_TELEFONO,
    errorIdentificacion,
    esCorreoValido,
    esNombreValido,
    esTelefonoValido,
    hoyISO,
    limpiarIdentificacion,
    limpiarTelefono,
} from "../../utils/validaciones";

const TAMANO_PAGINA = 10;

// Prefijo de los ids del formulario (para los enlaces del resumen de errores)
const P = "prof-";

// Por ahora la clase de puesto es un dato fijo (no hay catálogo con varias opciones).
const CLASE_PUESTO_DEFAULT =
    "Profesor de Enseñanza Técnica Profesional(III y IV Ciclos, Enseñanza Especial y Escuela Laboratorio) (G. de E)";

import { useDialog } from "../../components/dialog/useDialog";
import UbicacionSelects from "../../components/UbicacionSelects";
import { UBICACION_VACIA, ubicacionDesdeRespuesta } from "../../components/ubicacion";

const initialForm = {
    idTipoIdentificacion: "",
    numeroIdentificacion: "",
    nombres: "",
    primerApellido: "",
    segundoApellido: "",
    telefono: "",
    correo: "",
    ubicacion: UBICACION_VACIA,
    direccion: "",
    gradoAcademico: "",
    especialidades: [],
    empleador: "",
    clasePuesto: CLASE_PUESTO_DEFAULT,
    fechaIngreso: "",
};

function nombreCompleto(p) {
    return [p.nombres, p.primerApellido, p.segundoApellido].filter(Boolean).join(" ");
}

// El backend sigue guardando "especialidad" como un único texto; para permitir
// varias especialidades por profesor las unimos/separamos por coma en ese campo.
function especialidadesDesdeTexto(texto) {
    return (texto || "")
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
}

function especialidadesATexto(lista) {
    return lista.filter(Boolean).join(", ");
}

function Profesores() {
    const dialog = useDialog();

    // Ctrl+K o "/" enfocan el buscador
    useAtajoBuscador("txtBuscarProfesor");
    const [profesores, setProfesores] = useState([]);
    const [tipos, setTipos] = useState([]);
    const [gradosAcademicos, setGradosAcademicos] = useState([]);
    const [instrumentos, setInstrumentos] = useState([]);
    const [cargando, setCargando] = useState(true);

    const [filtroActivo, setFiltroActivo] = useState("true");
    const [busqueda, setBusqueda] = useState("");
    const [recargar, setRecargar] = useState(0);

    const [formAbierto, setFormAbierto] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [form, setForm] = useState(initialForm);
    const [errores, setErrores] = useState({});
    const [guardando, setGuardando] = useState(false);
    const [intento, setIntento] = useState(0);
    const [orden, setOrden] = useState("asc");
    const [pagina, setPagina] = useState(1);

    const formRef = useRef(null);

    const getHeaders = useCallback(() => {
        const token = localStorage.getItem("token");
        return token
            ? { "Content-Type": "application/json", Authorization: `Bearer ${token}` }
            : { "Content-Type": "application/json" };
    }, []);

    useEffect(() => {
        async function cargarTipos() {
            try {
                const response = await fetch(`${API_URL}/tipos-identificacion`, { headers: getHeaders() });
                if (!response.ok) await lanzarError(response, "No se pudo cargar el catálogo de tipos de identificación.");
                setTipos(await response.json());
            } catch (e) {
                dialog.error(e.message);
            }
        }
        cargarTipos();
    }, [getHeaders, dialog]);

    useEffect(() => {
        async function cargarGradosAcademicos() {
            try {
                const response = await fetch(`${API_URL}/grados-academicos`, { headers: getHeaders() });
                if (!response.ok) await lanzarError(response, "No se pudo cargar el catálogo de grados académicos.");
                setGradosAcademicos(await response.json());
            } catch (e) {
                dialog.error(e.message);
            }
        }
        cargarGradosAcademicos();
    }, [getHeaders, dialog]);

    useEffect(() => {
        async function cargarInstrumentos() {
            try {
                const response = await fetch(`${API_URL}/instrumentos`, { headers: getHeaders() });
                if (!response.ok) await lanzarError(response, "No se pudo cargar el catálogo de especialidades.");
                setInstrumentos(await response.json());
            } catch (e) {
                dialog.error(e.message);
            }
        }
        cargarInstrumentos();
    }, [getHeaders, dialog]);

    useEffect(() => {
        let cancelado = false;

        async function cargarProfesores() {
            setCargando(true);
            try {
                const query = filtroActivo ? `?activo=${filtroActivo}` : "";
                const response = await fetch(`${API_URL}/profesores${query}`, { headers: getHeaders() });
                if (!response.ok) await lanzarError(response, "No se pudo cargar la lista de profesores.");
                const data = await response.json();
                if (!cancelado) setProfesores(data);
            } catch (e) {
                if (!cancelado) dialog.error(e.message);
            } finally {
                if (!cancelado) setCargando(false);
            }
        }

        cargarProfesores();
        return () => { cancelado = true; };
    }, [filtroActivo, getHeaders, recargar, dialog]);

    const profesoresFiltrados = useMemo(() => {
        // La cédula se puede buscar con o sin guiones
        const texto = textoDeBusqueda(busqueda);
        if (!texto) return profesores;
        return profesores.filter((p) =>
            nombreCompleto(p).toLowerCase().includes(texto) ||
            identificacionCoincide(p.numeroIdentificacion, texto) ||
            (p.correo || "").toLowerCase().includes(texto)
        );
    }, [profesores, busqueda]);

    const profesoresOrdenados = useMemo(() => {
        const lista = [...profesoresFiltrados];
        lista.sort((a, b) => {
            const c = nombreCompleto(a).localeCompare(nombreCompleto(b), "es");
            return orden === "asc" ? c : -c;
        });
        return lista;
    }, [profesoresFiltrados, orden]);

    function setField(name, value) {
        setForm((f) => ({ ...f, [name]: value }));
        setErrores((e) => ({ ...e, [name]: "" }));
    }

    function toggleEspecialidad(nombre) {
        setForm((f) => {
            const yaEsta = f.especialidades.includes(nombre);
            const especialidades = yaEsta
                ? f.especialidades.filter((e) => e !== nombre)
                : [...f.especialidades, nombre];
            return { ...f, especialidades };
        });
        setErrores((e) => ({ ...e, especialidades: "" }));
    }

    function abrirEditar(p) {
        setEditingId(p.id);
        setForm({
            idTipoIdentificacion: String(p.tipoIdentificacion?.id ?? ""),
            // La cédula nacional se muestra con su máscara (1-1111-1111)
            numeroIdentificacion: esCedulaNacional(p.tipoIdentificacion)
                ? formatearCedula(p.numeroIdentificacion)
                : p.numeroIdentificacion || "",
            nombres: p.nombres || "",
            primerApellido: p.primerApellido || "",
            segundoApellido: p.segundoApellido || "",
            telefono: p.telefono || "",
            correo: p.correo || "",
            ubicacion: ubicacionDesdeRespuesta(p.ubicacion),
            direccion: p.direccion || "",
            gradoAcademico: p.gradoAcademico || "",
            especialidades: especialidadesDesdeTexto(p.especialidad),
            empleador: p.empleador || "",
            clasePuesto: p.clasePuesto || CLASE_PUESTO_DEFAULT,
            fechaIngreso: p.fechaIngreso || "",
        });
        setErrores({});
        setFormAbierto(true);
        requestAnimationFrame(() => formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
    }

    function cerrarFormulario() {
        setFormAbierto(false);
        setEditingId(null);
        setForm(initialForm);
        setErrores({});
    }

    function validar() {
        const nuevosErrores = {};
        if (!form.idTipoIdentificacion) nuevosErrores.idTipoIdentificacion = "Seleccione el tipo de identificación.";
        if (!form.numeroIdentificacion.trim()) nuevosErrores.numeroIdentificacion = "Ingrese el número de identificación.";
        if (!form.nombres.trim()) nuevosErrores.nombres = "Ingrese el nombre.";
        if (!form.primerApellido.trim()) nuevosErrores.primerApellido = "Ingrese el primer apellido.";
        if (form.correo.trim() && !esCorreoValido(form.correo)) {
            nuevosErrores.correo = "Ingrese un correo electrónico válido.";
        }
        const tipo = tipos.find((t) => String(t.id) === String(form.idTipoIdentificacion));
        if (form.numeroIdentificacion.trim() && errorIdentificacion(tipo, form.numeroIdentificacion)) {
            nuevosErrores.numeroIdentificacion = errorIdentificacion(tipo, form.numeroIdentificacion);
        }
        ["nombres", "primerApellido", "segundoApellido"].forEach((campo) => {
            if (form[campo].trim() && !esNombreValido(form[campo])) nuevosErrores[campo] = MENSAJE_NOMBRE;
        });
        if (form.telefono.trim() && !esTelefonoValido(form.telefono)) nuevosErrores.telefono = MENSAJE_TELEFONO;
        if (form.fechaIngreso && form.fechaIngreso > hoyISO()) nuevosErrores.fechaIngreso = "La fecha de ingreso no puede ser futura.";
        // La ubicación es opcional, pero si se empieza hay que llegar hasta el distrito
        if (form.ubicacion.idProvincia && !form.ubicacion.codigoDistrito) {
            nuevosErrores.codigoDistrito = "Complete cantón y distrito, o deje la provincia sin seleccionar.";
        }
        setErrores(nuevosErrores);
        if (Object.keys(nuevosErrores).length > 0) {
            setIntento((n) => n + 1);
            return false;
        }
        return true;
    }

    async function handleSubmit(e) {
        e.preventDefault();
        if (!validar()) return;

        const payloadBase = {
            idTipoIdentificacion: Number(form.idTipoIdentificacion),
            // Identificación y teléfono sin espacios ni guiones
            numeroIdentificacion: limpiarIdentificacion(form.numeroIdentificacion),
            nombres: form.nombres.trim(),
            primerApellido: form.primerApellido.trim(),
            segundoApellido: form.segundoApellido.trim() || null,
            telefono: limpiarTelefono(form.telefono) || null,
            correo: form.correo.trim() || null,
            codigoDistrito: form.ubicacion.codigoDistrito ? Number(form.ubicacion.codigoDistrito) : null,
            direccion: form.direccion.trim() || null,
            gradoAcademico: form.gradoAcademico.trim() || null,
            especialidad: especialidadesATexto(form.especialidades) || null,
            empleador: form.empleador.trim() || null,
            clasePuesto: form.clasePuesto.trim() || null,
            fechaIngreso: form.fechaIngreso || null,
        };

        const payload = payloadBase;

        const url = `${API_URL}/profesores/${editingId}`;
        const method = "PUT";

        setGuardando(true);
        try {
            const response = await fetch(url, { method, headers: getHeaders(), body: JSON.stringify(payload) });
            if (!response.ok) {
                const error = await leerError(response, "No fue posible guardar el profesor.");
                const porCampo = { ...error.fields };
                if (error.status === 409) {
                    const campo = campoDeConflicto(error.message);
                    if (campo === "correo") porCampo.correo = error.message;
                    if (campo === "identificacion") porCampo.numeroIdentificacion = error.message;
                }
                if (Object.keys(porCampo).length > 0) {
                    setErrores((err) => ({ ...err, ...porCampo }));
                    setIntento((n) => n + 1);
                    return;
                }
                throw new Error(error.message);
            }

            dialog.exito("Información del profesor actualizada correctamente.");
            cerrarFormulario();
            setRecargar((n) => n + 1);
        } catch (e) {
            dialog.error(e.message);
        } finally {
            setGuardando(false);
        }
    }

    async function darDeBaja(p) {
        const confirmado = await dialog.confirmar(
            `¿Dar de baja a ${nombreCompleto(p)}? Se conservará su historial, pero quedará inactivo.`,
            { titulo: "Dar de baja profesor", textoAceptar: "Dar de baja" }
        );
        if (!confirmado) return;
        try {
            const response = await fetch(`${API_URL}/profesores/${p.id}/baja`, { method: "PATCH", headers: getHeaders() });
            if (!response.ok) await lanzarError(response, "No fue posible dar de baja al profesor.");
            dialog.exito("Profesor dado de baja correctamente.");
            setRecargar((n) => n + 1);
        } catch (e) {
            dialog.error(e.message);
        }
    }

    async function reactivar(p) {
        const confirmado = await dialog.confirmar(
            `¿Reactivar a ${nombreCompleto(p)} para que pueda volver a impartir cursos?`,
            { titulo: "Reactivar profesor", textoAceptar: "Reactivar" }
        );
        if (!confirmado) return;
        try {
            const response = await fetch(`${API_URL}/profesores/${p.id}/reactivar`, { method: "PATCH", headers: getHeaders() });
            if (!response.ok) await lanzarError(response, "No fue posible reactivar al profesor.");
            dialog.exito("Profesor reactivado correctamente.");
            setRecargar((n) => n + 1);
        } catch (e) {
            dialog.error(e.message);
        }
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${P}${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores, P);
    const filasPagina = paginar(profesoresOrdenados, pagina, TAMANO_PAGINA);
    const hayFiltros = Boolean(busqueda.trim()) || filtroActivo !== "true";

    function quitarFiltros() {
        setBusqueda("");
        setFiltroActivo("true");
        setPagina(1);
    }

    return (
        <div className="profesores">
            <PageHead
                title="Profesores"
                sub="Registre, consulte y administre la información académica y de contacto de los profesores."
                actions={formAbierto ? (
                    <button type="button" className="sg-btn sg-btn--secondary" onClick={cerrarFormulario}>
                        <X size={18} strokeWidth={1.75} aria-hidden="true" />
                        Cancelar
                    </button>
                ) : (
                    <Link to="/director-academico/profesores/registrar" className="sg-btn sg-btn--primary">
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Nuevo profesor
                    </Link>
                )}
            />

            {formAbierto && (
                <form ref={formRef} onSubmit={handleSubmit} noValidate className="form-incrustado">
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
                            <h2>Modificar profesor</h2>
                        </div>

                        <div className="panel-body">
                            <div className="form-section">
                                <h3 className="form-section-title">Datos personales</h3>
                                <p className="form-section-sub">Identificación y contacto del profesor.</p>
                                <div className="form-grid">
                                    <Field id={`${P}idTipoIdentificacion`} errorKey="idTipoIdentificacion" label="Tipo de identificación" error={errores.idTipoIdentificacion} select>
                                        <select id={`${P}idTipoIdentificacion`} className="sg-input sg-select" value={form.idTipoIdentificacion} onChange={(e) => {
                                            const tipo = tipos.find((t) => String(t.id) === e.target.value);
                                            setField("idTipoIdentificacion", e.target.value);
                                            setField("numeroIdentificacion", ajustarIdentificacionAlTipo(form.numeroIdentificacion, tipo));
                                        }} {...aria("idTipoIdentificacion")}>
                                            <option value="">Seleccione</option>
                                            {tipos.map((t) => (
                                                <option key={t.id} value={t.id}>{t.nombre}</option>
                                            ))}
                                        </select>
                                    </Field>
                                    <Field id={`${P}numeroIdentificacion`} errorKey="numeroIdentificacion" label="Cédula / Número de identificación" error={errores.numeroIdentificacion}>
                                        <CedulaInput id={`${P}numeroIdentificacion`} className="sg-input" mascara={esCedulaNacional(tipos.find((t) => String(t.id) === String(form.idTipoIdentificacion)))} maxLength={40} value={form.numeroIdentificacion} onChange={(valor) => setField("numeroIdentificacion", valor)} {...aria("numeroIdentificacion")} />
                                    </Field>
                                    <Field id={`${P}nombres`} errorKey="nombres" label="Nombre" error={errores.nombres}>
                                        <input id={`${P}nombres`} className="sg-input" maxLength={100} value={form.nombres} onChange={(e) => setField("nombres", e.target.value)} {...aria("nombres")} />
                                    </Field>
                                    <Field id={`${P}primerApellido`} errorKey="primerApellido" label="Primer apellido" error={errores.primerApellido}>
                                        <input id={`${P}primerApellido`} className="sg-input" maxLength={80} value={form.primerApellido} onChange={(e) => setField("primerApellido", e.target.value)} {...aria("primerApellido")} />
                                    </Field>
                                    <Field id={`${P}segundoApellido`} errorKey="segundoApellido" label="Segundo apellido" optional error={errores.segundoApellido}>
                                        <input id={`${P}segundoApellido`} className="sg-input" maxLength={80} value={form.segundoApellido} onChange={(e) => setField("segundoApellido", e.target.value)} {...aria("segundoApellido")} />
                                    </Field>
                                    <Field id={`${P}telefono`} errorKey="telefono" label="Teléfono de contacto" optional hint="8 dígitos, puede escribirlo con guion." error={errores.telefono}>
                                        <input id={`${P}telefono`} className="sg-input" type="tel" inputMode="tel" maxLength={9} placeholder="8888-4321" value={form.telefono} onChange={(e) => setField("telefono", e.target.value)} {...aria("telefono")} />
                                    </Field>
                                    <Field
                                        id={`${P}correo`}
                                        errorKey="correo"
                                        label="Correo de contacto"
                                        optional
                                        error={errores.correo}
                                        className="span-all"
                                    >
                                        <input id={`${P}correo`} className="sg-input" type="email" maxLength={254} placeholder="nombre@correo.com" value={form.correo} onChange={(e) => setField("correo", e.target.value)} {...aria("correo")} />
                                    </Field>
                                    <UbicacionSelects
                                        idPrefix={P}
                                        value={form.ubicacion}
                                        errors={errores}
                                        onChange={(ubicacion) => {
                                            setForm((f) => ({ ...f, ubicacion }));
                                            setErrores((e) => ({ ...e, codigoDistrito: "" }));
                                        }}
                                    />
                                    <Field id={`${P}direccion`} label="Dirección exacta (señas)" optional className="span-all">
                                        <textarea id={`${P}direccion`} className="sg-input" maxLength={500} placeholder="Ej.: 200 m norte de la iglesia, casa verde a mano derecha" value={form.direccion} onChange={(e) => setField("direccion", e.target.value)} />
                                    </Field>
                                </div>
                            </div>

                            <div className="form-section">
                                <h3 className="form-section-title">Información académica y laboral</h3>
                                <p className="form-section-sub">Grado, puesto y los instrumentos que puede impartir.</p>
                                <div className="form-grid">
                                    <Field id={`${P}gradoAcademico`} label="Grado académico" optional select>
                                        <select id={`${P}gradoAcademico`} className="sg-input sg-select" value={form.gradoAcademico} onChange={(e) => setField("gradoAcademico", e.target.value)}>
                                            <option value="">Seleccione</option>
                                            {gradosAcademicos.map((g) => (
                                                <option key={g.id} value={g.nombre}>{g.nombre}</option>
                                            ))}
                                        </select>
                                    </Field>
                                    <Field id={`${P}empleador`} label="Grupo profesional / Empleador" optional>
                                        <input id={`${P}empleador`} className="sg-input" maxLength={180} placeholder="VT, VAU u otro colegio profesional / institución" value={form.empleador} onChange={(e) => setField("empleador", e.target.value)} />
                                    </Field>
                                    <Field
                                        id={`${P}clasePuesto`}
                                        label="Clase de puesto"
                                        className="span-all"
                                    >
                                        <input id={`${P}clasePuesto`} className="sg-input" maxLength={255} value={form.clasePuesto} onChange={(e) => setField("clasePuesto", e.target.value)} />
                                    </Field>
                                    <Field id={`${P}fechaIngreso`} errorKey="fechaIngreso" label="Fecha de ingreso" optional hint="Si la deja vacía se usa la fecha de hoy." error={errores.fechaIngreso}>
                                        <input id={`${P}fechaIngreso`} className="sg-input" type="date" max={hoyISO()} value={form.fechaIngreso} onChange={(e) => setField("fechaIngreso", e.target.value)} {...aria("fechaIngreso")} />
                                    </Field>
                                    <fieldset className="fieldset span-all">
                                        <legend>Especialidad(es) <span className="sg-field__optional">(opcional)</span></legend>
                                        <div className="especialidades-checklist">
                                            {instrumentos.map((i) => {
                                                const marcada = form.especialidades.includes(i.nombre);
                                                return (
                                                    <label key={i.id} className={`choice choice--compacta${marcada ? " is-checked" : ""}`}>
                                                        <input
                                                            type="checkbox"
                                                            checked={marcada}
                                                            onChange={() => toggleEspecialidad(i.nombre)}
                                                        />
                                                        <span className="choice-main"><b>{i.nombre}</b></span>
                                                    </label>
                                                );
                                            })}
                                        </div>
                                    </fieldset>
                                </div>
                            </div>
                        </div>

                        <div className="panel-foot">
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={cerrarFormulario}>Cancelar</button>
                            <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando} aria-busy={guardando || undefined}>
                                {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <Save size={18} strokeWidth={1.75} aria-hidden="true" />}
                                {guardando ? "Guardando…" : "Guardar cambios"}
                            </button>
                        </div>
                    </section>
                </form>
            )}

            <div className="panel">
                <div className="filters" role="search">
                    <Field id="txtBuscarProfesor" label="Buscar" icon={Search} className="f-search">
                        <CedulaInput
                            id="txtBuscarProfesor"
                            type="search"
                            className="sg-input"
                            mascara="auto"
                            placeholder="Nombre, identificación o correo (Ctrl+K)"
                            value={busqueda}
                            onChange={(valor) => { setBusqueda(valor); setPagina(1); }}
                            aria-keyshortcuts={ATAJO_BUSCADOR}
                        />
                    </Field>
                    <Field id="cmbEstadoProfesor" label="Estado" select>
                        <select
                            id="cmbEstadoProfesor"
                            className="sg-input sg-select"
                            value={filtroActivo}
                            onChange={(e) => { setFiltroActivo(e.target.value); setPagina(1); }}
                        >
                            <option value="true">Activos</option>
                            <option value="false">Inactivos</option>
                            <option value="">Todos</option>
                        </select>
                    </Field>
                </div>

                <div className="chips">
                    <span className="result-count" aria-live="polite">
                        <b>{profesoresOrdenados.length}</b>
                        {profesoresOrdenados.length === 1 ? " resultado" : " resultados"}
                    </span>
                    {busqueda.trim() && (
                        <span className="chip">
                            Búsqueda: {busqueda.trim()}
                            <button type="button" aria-label="Quitar filtro de búsqueda" onClick={() => { setBusqueda(""); setPagina(1); }}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                    {filtroActivo !== "true" && (
                        <span className="chip">
                            Estado: {filtroActivo === "false" ? "Inactivos" : "Todos"}
                            <button type="button" aria-label="Quitar filtro de estado" onClick={() => { setFiltroActivo("true"); setPagina(1); }}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                    {hayFiltros && (
                        <button type="button" className="link-btn chips-clear" onClick={quitarFiltros}>Quitar filtros</button>
                    )}
                </div>

                {cargando ? (
                    <div className="table-wrap" aria-busy="true">
                        <table className="dtable">
                            <caption className="sr-only">Cargando profesores...</caption>
                            <tbody>
                                {[0, 1, 2, 3].map((i) => (
                                    <tr key={i}>{[0, 1, 2, 3, 4, 5].map((j) => <td key={j}><span className="skeleton" /></td>)}</tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : profesoresOrdenados.length === 0 ? (
                    <EmptyState
                        icon={GraduationCap}
                        title={busqueda.trim() ? `Ningún profesor coincide con «${busqueda.trim()}»` : "No hay profesores con estos filtros"}
                        description="Revise la ortografía, busque por identificación o correo, o quite algún filtro."
                        action={hayFiltros && <button type="button" className="sg-btn sg-btn--secondary" onClick={quitarFiltros}>Quitar filtros</button>}
                    />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards dtable--compacta">
                            <caption className="sr-only">Lista de profesores, página {pagina}</caption>
                            <thead>
                                <tr>
                                    <th scope="col" aria-sort={orden === "asc" ? "ascending" : "descending"}>
                                        <button type="button" onClick={() => setOrden(orden === "asc" ? "desc" : "asc")}>
                                            Profesor
                                            <ChevronsUpDown size={14} strokeWidth={1.75} aria-hidden="true" />
                                        </button>
                                    </th>
                                    <th scope="col">Especialidad y grado</th>
                                    <th scope="col">Grupo profesional</th>
                                    <th scope="col">Contacto</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="actions"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {filasPagina.map((p) => (
                                    <tr key={p.id}>
                                        <td className="c-main">
                                            <div className="who">
                                                <span className="avatar avatar--sm" aria-hidden="true">{iniciales(nombreCompleto(p))}</span>
                                                <div>
                                                    <b>{nombreCompleto(p)}</b>
                                                    <span>{p.tipoIdentificacion?.codigo ? `${p.tipoIdentificacion.codigo}: ` : ""}{mostrarIdentificacion(p.numeroIdentificacion, p.tipoIdentificacion)}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td data-label="Especialidad">
                                            {p.especialidad ? <span className="inst">{p.especialidad}</span> : "—"}
                                            {p.gradoAcademico && <span className="sub">{p.gradoAcademico}</span>}
                                        </td>
                                        <td data-label="Grupo">
                                            {p.empleador || "—"}
                                            {p.clasePuesto && <span className="sub clase-puesto" title={p.clasePuesto}>{p.clasePuesto}</span>}
                                        </td>
                                        <td data-label="Contacto">
                                            <span className="tnum">{p.telefono || "—"}</span>
                                            {p.correo && <span className="sub">{p.correo}</span>}
                                        </td>
                                        <td>
                                            <StatusBadge tone={p.activo ? "success" : "neutral"}>{p.activo ? "Activo" : "Inactivo"}</StatusBadge>
                                        </td>
                                        <td className="actions">
                                            <span className="row-actions">
                                                <button type="button" className="sg-btn sg-btn--ghost sg-btn--sm" onClick={() => abrirEditar(p)} aria-label={`Editar a ${nombreCompleto(p)}`}>
                                                    <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                                    Editar
                                                </button>
                                                {p.activo ? (
                                                    <button type="button" className="sg-btn sg-btn--ghost sg-btn--sm btn-peligro" onClick={() => darDeBaja(p)}>
                                                        Dar de baja
                                                    </button>
                                                ) : (
                                                    <button type="button" className="sg-btn sg-btn--ghost sg-btn--sm" onClick={() => reactivar(p)}>
                                                        Reactivar
                                                    </button>
                                                )}
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {!cargando && profesoresOrdenados.length > 0 && (
                    <div className="table-foot">
                        <Pagination page={pagina} pageSize={TAMANO_PAGINA} total={profesoresOrdenados.length} onChange={setPagina} />
                    </div>
                )}
            </div>
        </div>
    );
}

export default Profesores;
