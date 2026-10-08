import { useEffect, useState, useCallback } from "react";
import { UserPlus } from "lucide-react";
import { API_URL } from "../../config/api";
import { useDialog } from "../../components/dialog/useDialog";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import { listaErrores } from "../../components/ui/utilidades";
import "./styles/createUsers.css";

const FORM_VACIO = {
    cedula: "",
    nombre: "",
    correo: "",
    idRol: "",
    idEstadoUsuario: "",
};

// Prefijo de los ids (para los enlaces del resumen de errores)
const P = "usr-";

function CreateUsers() {
    const dialog = useDialog();

    const [roles, setRoles] = useState([]);
    const [estados, setEstados] = useState([]);
    const [error, setError] = useState("");
    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);
    const [guardando, setGuardando] = useState(false);
    const [form, setForm] = useState(FORM_VACIO);

    const getHeaders = useCallback(() => {
        const token = localStorage.getItem("token");
        return token
            ? { "Content-Type": "application/json", Authorization: `Bearer ${token}` }
            : { "Content-Type": "application/json" };
    }, []);

    useEffect(() => {
        async function cargarCatalogos() {
            try {
                setError("");
                const [rolesRes, estadosRes] = await Promise.all([
                    fetch(`${API_URL}/roles`, { headers: getHeaders() }),
                    fetch(`${API_URL}/estados-usuarios`, { headers: getHeaders() }),
                ]);

                if (!rolesRes.ok || !estadosRes.ok) {
                    throw new Error("No se pudieron cargar los catálogos");
                }

                const [rolesData, estadosData] = await Promise.all([
                    rolesRes.json(),
                    estadosRes.json(),
                ]);

                setRoles(rolesData);
                setEstados(estadosData);
            } catch (e) {
                setError(e.message);
            }
        }

        cargarCatalogos();
    }, [getHeaders]);

    function setCampo(campo, valor) {
        setForm((f) => ({ ...f, [campo]: valor }));
        setErrores((e) => ({ ...e, [campo]: "" }));
    }

    function validar() {
        const nuevos = {};
        if (!form.cedula.trim()) nuevos.cedula = "Escriba la cédula.";
        if (!form.nombre.trim()) nuevos.nombre = "Escriba el nombre.";
        if (!form.correo.trim()) nuevos.correo = "Escriba el correo electrónico.";
        else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correo.trim())) nuevos.correo = "Escriba un correo con el formato nombre@dominio.com";
        if (!form.idRol) nuevos.idRol = "Seleccione el rol.";
        if (!form.idEstadoUsuario) nuevos.idEstadoUsuario = "Seleccione el estado del usuario.";
        return nuevos;
    }

    async function handleSubmit(e) {
        e.preventDefault();

        const nuevos = validar();
        setErrores(nuevos);
        setIntento((n) => n + 1);
        if (Object.keys(nuevos).length > 0) return;

        const payload = {
            cedula: form.cedula,
            nombre: form.nombre,
            correo: form.correo,
            idRol: Number(form.idRol),
            idEstadoUsuario: Number(form.idEstadoUsuario),
        };

        try {
            setGuardando(true);
            const response = await fetch(`${API_URL}/usuarios`, {
                method: "POST",
                headers: getHeaders(),
                body: JSON.stringify(payload),
            });

            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                dialog.error(body.message || "Error al crear usuario");
                return;
            }

            dialog.exito(`Usuario creado. Se envió una contraseña temporal a ${payload.correo}.`);
            setForm(FORM_VACIO);
        } catch {
            dialog.error("No se pudo conectar con el servidor");
        } finally {
            setGuardando(false);
        }
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${P}${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores, P);

    return (
        <div className="crear-usuario">
            <PageHead title="Crear usuario" sub="Registre una cuenta de acceso a SIGAC con su rol y estado. La contraseña temporal se envía al correo del usuario." />

            {error && <Alert tone="danger" title={error} className="form-alert" />}

            <form onSubmit={handleSubmit} noValidate>
                {resumen.length > 0 && (
                    <Alert
                        key={intento}
                        tone="danger"
                        autoFocus
                        className="form-alert"
                        title={resumen.length === 1 ? "Revise 1 dato antes de continuar" : `Revise ${resumen.length} datos antes de continuar`}
                        errors={resumen}
                    />
                )}

                <section className="panel">
                    <div className="panel-head"><h2>Datos de la cuenta</h2></div>
                    <div className="panel-body form-grid">
                        <Field id={`${P}cedula`} errorKey="cedula" label="Cédula" error={errores.cedula}>
                            <input id={`${P}cedula`} className="sg-input" type="text" inputMode="numeric" autoComplete="off" value={form.cedula} onChange={(e) => setCampo("cedula", e.target.value)} {...aria("cedula")} />
                        </Field>
                        <Field id={`${P}nombre`} errorKey="nombre" label="Nombre" error={errores.nombre}>
                            <input id={`${P}nombre`} className="sg-input" type="text" autoComplete="name" value={form.nombre} onChange={(e) => setCampo("nombre", e.target.value)} {...aria("nombre")} />
                        </Field>
                        <Field id={`${P}correo`} errorKey="correo" label="Correo" hint="Se enviará una contraseña temporal a este correo." error={errores.correo}>
                            <input id={`${P}correo`} className="sg-input" type="email" autoComplete="email" value={form.correo} onChange={(e) => setCampo("correo", e.target.value)} placeholder="nombre@correo.com" {...aria("correo")} />
                        </Field>
                        <Field id={`${P}idRol`} errorKey="idRol" label="Rol" error={errores.idRol} select>
                            <select id={`${P}idRol`} className="sg-input sg-select" value={form.idRol} onChange={(e) => setCampo("idRol", e.target.value)} {...aria("idRol")}>
                                <option value="">Seleccione un rol</option>
                                {roles.map((rol) => (
                                    <option key={rol.id} value={rol.id}>
                                        {rol.nombre}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <Field id={`${P}idEstadoUsuario`} errorKey="idEstadoUsuario" label="Estado de usuario" error={errores.idEstadoUsuario} select>
                            <select id={`${P}idEstadoUsuario`} className="sg-input sg-select" value={form.idEstadoUsuario} onChange={(e) => setCampo("idEstadoUsuario", e.target.value)} {...aria("idEstadoUsuario")}>
                                <option value="">Seleccione un estado</option>
                                {estados.map((estado) => (
                                    <option key={estado.id} value={estado.id}>
                                        {estado.nombre}
                                    </option>
                                ))}
                            </select>
                        </Field>
                    </div>
                    <div className="panel-foot">
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={() => { setForm(FORM_VACIO); setErrores({}); }}>
                            Limpiar
                        </button>
                        <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando} aria-busy={guardando || undefined}>
                            {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <UserPlus size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {guardando ? "Creando…" : "Crear usuario"}
                        </button>
                    </div>
                </section>
            </form>
        </div>
    );
}

export default CreateUsers;
