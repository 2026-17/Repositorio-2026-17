import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { API_URL } from "../../config/api.js";
import "./Profesores.css";

// Por ahora la clase de puesto es un dato fijo (no hay catálogo con varias opciones).
const CLASE_PUESTO_DEFAULT =
    "Profesor de Enseñanza Técnica Profesional(III y IV Ciclos, Enseñanza Especial y Escuela Laboratorio) (G. de E)";

const initialForm = {
    idTipoIdentificacion: "",
    numeroIdentificacion: "",
    nombres: "",
    primerApellido: "",
    segundoApellido: "",
    telefono: "",
    correo: "",
    direccion: "",
    gradoAcademico: "",
    especialidades: [],
    empleador: "",
    clasePuesto: CLASE_PUESTO_DEFAULT,
    fechaIngreso: "",
    contrasena: "",
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
    const [profesores, setProfesores] = useState([]);
    const [tipos, setTipos] = useState([]);
    const [gradosAcademicos, setGradosAcademicos] = useState([]);
    const [instrumentos, setInstrumentos] = useState([]);
    const [cargando, setCargando] = useState(true);
    const [error, setError] = useState("");
    const [exito, setExito] = useState("");

    const [filtroActivo, setFiltroActivo] = useState("true");
    const [busqueda, setBusqueda] = useState("");
    const [recargar, setRecargar] = useState(0);

    const [formAbierto, setFormAbierto] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [form, setForm] = useState(initialForm);
    const [errores, setErrores] = useState({});
    const [guardando, setGuardando] = useState(false);

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
                if (!response.ok) throw new Error("No se pudo cargar el catálogo de tipos de identificación");
                setTipos(await response.json());
            } catch (e) {
                setError(e.message);
            }
        }
        cargarTipos();
    }, [getHeaders]);

    useEffect(() => {
        async function cargarGradosAcademicos() {
            try {
                const response = await fetch(`${API_URL}/grados-academicos`, { headers: getHeaders() });
                if (!response.ok) throw new Error("No se pudo cargar el catálogo de grados académicos");
                setGradosAcademicos(await response.json());
            } catch (e) {
                setError(e.message);
            }
        }
        cargarGradosAcademicos();
    }, [getHeaders]);

    useEffect(() => {
        async function cargarInstrumentos() {
            try {
                const response = await fetch(`${API_URL}/instrumentos`, { headers: getHeaders() });
                if (!response.ok) throw new Error("No se pudo cargar el catálogo de especialidades");
                setInstrumentos(await response.json());
            } catch (e) {
                setError(e.message);
            }
        }
        cargarInstrumentos();
    }, [getHeaders]);

    useEffect(() => {
        let cancelado = false;

        async function cargarProfesores() {
            setCargando(true);
            try {
                const query = filtroActivo ? `?activo=${filtroActivo}` : "";
                const response = await fetch(`${API_URL}/profesores${query}`, { headers: getHeaders() });
                if (!response.ok) throw new Error("No se pudo cargar la lista de profesores");
                const data = await response.json();
                if (!cancelado) setProfesores(data);
            } catch (e) {
                if (!cancelado) setError(e.message);
            } finally {
                if (!cancelado) setCargando(false);
            }
        }

        cargarProfesores();
        return () => { cancelado = true; };
    }, [filtroActivo, getHeaders, recargar]);

    const profesoresFiltrados = useMemo(() => {
        const texto = busqueda.trim().toLowerCase();
        if (!texto) return profesores;
        return profesores.filter((p) =>
            nombreCompleto(p).toLowerCase().includes(texto) ||
            (p.numeroIdentificacion || "").toLowerCase().includes(texto) ||
            (p.correo || "").toLowerCase().includes(texto)
        );
    }, [profesores, busqueda]);

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

    function abrirNuevo() {
        setEditingId(null);
        setForm(initialForm);
        setErrores({});
        setExito("");
        setFormAbierto(true);
        requestAnimationFrame(() => formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
    }

    function abrirEditar(p) {
        setEditingId(p.id);
        setForm({
            idTipoIdentificacion: String(p.tipoIdentificacion?.id ?? ""),
            numeroIdentificacion: p.numeroIdentificacion || "",
            nombres: p.nombres || "",
            primerApellido: p.primerApellido || "",
            segundoApellido: p.segundoApellido || "",
            telefono: p.telefono || "",
            correo: p.correo || "",
            direccion: p.direccion || "",
            gradoAcademico: p.gradoAcademico || "",
            especialidades: especialidadesDesdeTexto(p.especialidad),
            empleador: p.empleador || "",
            clasePuesto: p.clasePuesto || CLASE_PUESTO_DEFAULT,
            fechaIngreso: p.fechaIngreso || "",
        });
        setErrores({});
        setExito("");
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
        if (form.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correo)) {
            nuevosErrores.correo = "Ingrese un correo electrónico válido.";
        }
        if (!editingId) {
            if (!form.correo.trim()) nuevosErrores.correo = "El correo es obligatorio para crear la cuenta de usuario.";
            if (!form.contrasena || form.contrasena.length < 8) {
                nuevosErrores.contrasena = "Ingrese una contraseña de al menos 8 caracteres.";
            }
        }
        setErrores(nuevosErrores);
        return Object.keys(nuevosErrores).length === 0;
    }

    async function handleSubmit(e) {
        e.preventDefault();
        setError("");
        setExito("");
        if (!validar()) return;

        const payloadBase = {
            idTipoIdentificacion: Number(form.idTipoIdentificacion),
            numeroIdentificacion: form.numeroIdentificacion.trim(),
            nombres: form.nombres.trim(),
            primerApellido: form.primerApellido.trim(),
            segundoApellido: form.segundoApellido.trim() || null,
            telefono: form.telefono.trim() || null,
            correo: form.correo.trim() || null,
            direccion: form.direccion.trim() || null,
            gradoAcademico: form.gradoAcademico.trim() || null,
            especialidad: especialidadesATexto(form.especialidades) || null,
            empleador: form.empleador.trim() || null,
            clasePuesto: form.clasePuesto.trim() || null,
            fechaIngreso: form.fechaIngreso || null,
        };

        const payload = editingId
            ? payloadBase
            : {
                profesor: payloadBase,
                usuario: {
                    cedula: payloadBase.numeroIdentificacion,
                    nombre: [form.nombres.trim(), form.primerApellido.trim(), form.segundoApellido.trim()]
                        .filter(Boolean)
                        .join(" "),
                    correo: payloadBase.correo,
                    contrasena: form.contrasena,
                    // El backend siempre fija el rol PROFESOR y el estado ACTIVO
                    // (ver ProfesorService.registrar), pero UsuarioCreateRequest
                    // los exige con @NotNull, así que mandamos un valor cualquiera.
                    idRol: 1,
                    idEstadoUsuario: 1,
                },
            };

        const url = editingId ? `${API_URL}/profesores/${editingId}` : `${API_URL}/profesores`;
        const method = editingId ? "PUT" : "POST";

        setGuardando(true);
        try {
            const response = await fetch(url, { method, headers: getHeaders(), body: JSON.stringify(payload) });
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                const mensaje = body.detail || body.message || "No fue posible guardar el profesor.";
                if (response.status === 409) {
                    setErrores((err) => ({ ...err, numeroIdentificacion: mensaje }));
                }
                throw new Error(mensaje);
            }

            setExito(editingId ? "Información del profesor actualizada correctamente." : "Profesor registrado correctamente.");
            cerrarFormulario();
            setRecargar((n) => n + 1);
        } catch (e) {
            setError(e.message);
        } finally {
            setGuardando(false);
        }
    }

    async function darDeBaja(p) {
        if (!window.confirm(`¿Dar de baja a ${nombreCompleto(p)}? Se conservará su historial, pero quedará inactivo.`)) return;
        setError("");
        setExito("");
        try {
            const response = await fetch(`${API_URL}/profesores/${p.id}/baja`, { method: "PATCH", headers: getHeaders() });
            if (!response.ok) throw new Error("No fue posible dar de baja al profesor.");
            setExito("Profesor dado de baja correctamente.");
            setRecargar((n) => n + 1);
        } catch (e) {
            setError(e.message);
        }
    }

    async function reactivar(p) {
        if (!window.confirm(`¿Reactivar a ${nombreCompleto(p)} para que pueda volver a impartir cursos?`)) return;
        setError("");
        setExito("");
        try {
            const response = await fetch(`${API_URL}/profesores/${p.id}/reactivar`, { method: "PATCH", headers: getHeaders() });
            if (!response.ok) throw new Error("No fue posible reactivar al profesor.");
            setExito("Profesor reactivado correctamente.");
            setRecargar((n) => n + 1);
        } catch (e) {
            setError(e.message);
        }
    }

    return (
        <div className="container">
            <div className="page-title">
                <h1>Profesores</h1>
                <p>Registre, consulte y administre la información académica y de contacto de los profesores.</p>
            </div>

            {error && <div className="banner banner-danger" role="alert">{error}</div>}
            {exito && <div className="banner banner-success" role="status">{exito}</div>}

            <div className="page-toolbar">
                <div className="page-toolbar__filters">
                    <input
                        type="text"
                        className="search-input"
                        placeholder="Buscar por nombre, identificación o correo..."
                        value={busqueda}
                        onChange={(e) => setBusqueda(e.target.value)}
                    />
                    <select
                        className="filter-select"
                        value={filtroActivo}
                        onChange={(e) => setFiltroActivo(e.target.value)}
                    >
                        <option value="true">Activos</option>
                        <option value="false">Inactivos</option>
                        <option value="">Todos</option>
                    </select>
                </div>
                <button type="button" className="btn btn--primary" onClick={formAbierto ? cerrarFormulario : abrirNuevo}>
                    {formAbierto ? "Cancelar" : "+ Nuevo profesor"}
                </button>
            </div>

            {formAbierto && (
                <div className="card" ref={formRef}>
                    <h2 className="card-title">{editingId ? "Modificar profesor" : "Registrar profesor"}</h2>
                    <form onSubmit={handleSubmit} noValidate>
                        <div className="grid-2">
                            <div className="field">
                                <label className="field-label">Tipo de identificación <span className="required">*</span></label>
                                <select
                                    className={`input ${errores.idTipoIdentificacion ? "input-error" : ""}`}
                                    value={form.idTipoIdentificacion}
                                    onChange={(e) => setField("idTipoIdentificacion", e.target.value)}
                                >
                                    <option value="">Seleccione una opción</option>
                                    {tipos.map((t) => (
                                        <option key={t.id} value={t.id}>{t.nombre}</option>
                                    ))}
                                </select>
                                {errores.idTipoIdentificacion && <p className="field-error">{errores.idTipoIdentificacion}</p>}
                            </div>
                            <div className="field">
                                <label className="field-label">Cédula / Número de identificación <span className="required">*</span></label>
                                <input
                                    className={`input ${errores.numeroIdentificacion ? "input-error" : ""}`}
                                    maxLength={40}
                                    value={form.numeroIdentificacion}
                                    onChange={(e) => setField("numeroIdentificacion", e.target.value)}
                                />
                                {errores.numeroIdentificacion && <p className="field-error">{errores.numeroIdentificacion}</p>}
                            </div>
                        </div>

                        <div className="grid-3">
                            <div className="field">
                                <label className="field-label">Nombre <span className="required">*</span></label>
                                <input
                                    className={`input ${errores.nombres ? "input-error" : ""}`}
                                    maxLength={100}
                                    value={form.nombres}
                                    onChange={(e) => setField("nombres", e.target.value)}
                                />
                                {errores.nombres && <p className="field-error">{errores.nombres}</p>}
                            </div>
                            <div className="field">
                                <label className="field-label">Primer apellido <span className="required">*</span></label>
                                <input
                                    className={`input ${errores.primerApellido ? "input-error" : ""}`}
                                    maxLength={80}
                                    value={form.primerApellido}
                                    onChange={(e) => setField("primerApellido", e.target.value)}
                                />
                                {errores.primerApellido && <p className="field-error">{errores.primerApellido}</p>}
                            </div>
                            <div className="field">
                                <label className="field-label">Segundo apellido</label>
                                <input
                                    className="input"
                                    maxLength={80}
                                    value={form.segundoApellido}
                                    onChange={(e) => setField("segundoApellido", e.target.value)}
                                />
                            </div>
                        </div>

                        <div className="grid-2">
                            <div className="field">
                                <label className="field-label">Teléfono de contacto</label>
                                <input
                                    className="input"
                                    maxLength={30}
                                    placeholder="8888-4321"
                                    value={form.telefono}
                                    onChange={(e) => setField("telefono", e.target.value)}
                                />
                            </div>
                            <div className="field">
                                <label className="field-label">Correo de contacto</label>
                                <input
                                    className={`input ${errores.correo ? "input-error" : ""}`}
                                    type="email"
                                    maxLength={254}
                                    placeholder="nombre@correo.com"
                                    value={form.correo}
                                    onChange={(e) => setField("correo", e.target.value)}
                                />
                                {errores.correo && <p className="field-error">{errores.correo}</p>}
                            </div>
                        </div>

                        <div className="field">
                            <label className="field-label">Residencia / Dirección</label>
                            <textarea
                                className="input"
                                maxLength={500}
                                placeholder="Provincia, cantón, distrito y señas exactas"
                                value={form.direccion}
                                onChange={(e) => setField("direccion", e.target.value)}
                            />
                        </div>

                        <div className="grid-3">
                            <div className="field">
                                <label className="field-label">Grado académico</label>
                                <select
                                    className="input"
                                    value={form.gradoAcademico}
                                    onChange={(e) => setField("gradoAcademico", e.target.value)}
                                >
                                    <option value="">Seleccione una opción</option>
                                    {gradosAcademicos.map((g) => (
                                        <option key={g.id} value={g.nombre}>{g.nombre}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="field">
                                <label className="field-label">Grupo profesional / Empleador</label>
                                <input
                                    className="input"
                                    maxLength={180}
                                    placeholder="VT, VAU u otro colegio profesional / institución"
                                    value={form.empleador}
                                    onChange={(e) => setField("empleador", e.target.value)}
                                />
                            </div>
                            <div className="field">
                                <label className="field-label">Clase de puesto</label>
                                <input
                                    className="input"
                                    maxLength={255}
                                    value={form.clasePuesto}
                                    onChange={(e) => setField("clasePuesto", e.target.value)}
                                    disabled={!editingId}
                                />
                                {!editingId && (
                                    <p className="field-hint">Se asigna automáticamente y solo puede modificarse al editar el profesor.</p>
                                )}
                            </div>
                        </div>

                        <div className="field">
                            <label className="field-label">Especialidad(es)</label>
                            <div className="especialidades-checklist">
                                {instrumentos.map((i) => (
                                    <label key={i.id} className="especialidad-chip">
                                        <input
                                            type="checkbox"
                                            checked={form.especialidades.includes(i.nombre)}
                                            onChange={() => toggleEspecialidad(i.nombre)}
                                        />
                                        {i.nombre}
                                    </label>
                                ))}
                            </div>
                        </div>

                        <div className="grid-2">
                            <div className="field">
                                <label className="field-label">Fecha de ingreso</label>
                                <input
                                    className="input"
                                    type="date"
                                    value={form.fechaIngreso}
                                    onChange={(e) => setField("fechaIngreso", e.target.value)}
                                />
                            </div>
                        </div>

                        {!editingId && (
                            <>
                                <h3 className="card-subtitle">Cuenta de usuario</h3>
                                <div className="grid-2">
                                    <div className="field">
                                        <label className="field-label">Contraseña temporal <span className="required">*</span></label>
                                        <input
                                            className={`input ${errores.contrasena ? "input-error" : ""}`}
                                            type="password"
                                            minLength={8}
                                            placeholder="Mínimo 8 caracteres"
                                            value={form.contrasena}
                                            onChange={(e) => setField("contrasena", e.target.value)}
                                        />
                                        {errores.contrasena && <p className="field-error">{errores.contrasena}</p>}
                                    </div>
                                </div>
                                <p className="field-hint">
                                    Se creará automáticamente con rol Profesor y estado Activo.
                                </p>
                            </>
                        )}

                        <div className="form-actions">
                            <button type="button" className="btn btn--secondary" onClick={cerrarFormulario}>Cancelar</button>
                            <button type="submit" className="btn btn--primary" disabled={guardando}>
                                {guardando ? "Guardando..." : editingId ? "Guardar cambios" : "Registrar profesor"}
                            </button>
                        </div>
                    </form>
                </div>
            )}

            <div className="table-wrap">
                <table className="table">
                    <thead>
                    <tr>
                        <th>Identificación</th>
                        <th>Nombre</th>
                        <th>Grupo profesional</th>
                        <th>Clase de puesto</th>
                        <th>Especialidad</th>
                        <th>Contacto</th>
                        <th>Grado académico</th>
                        <th>Estado</th>
                        <th>Acciones</th>
                    </tr>
                    </thead>
                    <tbody>
                    {cargando ? (
                        <tr><td className="empty" colSpan={9}>Cargando profesores...</td></tr>
                    ) : profesoresFiltrados.length === 0 ? (
                        <tr><td className="empty" colSpan={9}>No hay profesores que coincidan con la búsqueda.</td></tr>
                    ) : (
                        profesoresFiltrados.map((p) => (
                            <tr key={p.id}>
                                <td>{p.tipoIdentificacion?.codigo + ": " || " "} {p.numeroIdentificacion}</td>
                                <td>{nombreCompleto(p)}</td>
                                <td>{p.empleador || "—"}</td>
                                <td>{p.clasePuesto || "—"}</td>
                                <td>{p.especialidad || "—"}</td>
                                <td>{p.telefono || "—"}<br />{p.correo || ""}</td>
                                <td>{p.gradoAcademico || "—"}</td>
                                <td>
                                        <span className={`badge ${p.activo ? "badge-activo" : "badge-inactivo"}`}>
                                            {p.activo ? "Activo" : "Inactivo"}
                                        </span>
                                </td>
                                <td>
                                    <div className="row-actions">
                                        <button type="button" className="btn btn--secondary btn-sm" onClick={() => abrirEditar(p)}>
                                            Editar
                                        </button>
                                        {p.activo ? (
                                            <button type="button" className="btn btn--secondary btn-sm" onClick={() => darDeBaja(p)}>
                                                Dar de baja
                                            </button>
                                        ) : (
                                            <button type="button" className="btn btn--secondary btn-sm" onClick={() => reactivar(p)}>
                                                Reactivar
                                            </button>
                                        )}
                                    </div>
                                </td>
                            </tr>
                        ))
                    )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default Profesores;
