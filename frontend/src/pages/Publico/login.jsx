import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { API_URL } from "../../config/api";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import PasswordField from "../../components/ui/PasswordField";
import { listaErrores } from "../../components/ui/utilidades";
import AuthLayout from "./AuthLayout";

import "./publico.css";

/*
 * Ruta de redirección según el rol
 * después de iniciar sesión.
 */
const RUTAS_POR_ROL = {
    ADMINISTRADOR:
        "/admin/usuarios/crear",

    DIRECTOR_ACADEMICO:
        "/director-academico/cursos/crear",

    ADMINISTRATIVO:
        "/admin/administracion/estudiantes",

    /*
     * HU-012
     * El estudiante ingresa directamente
     * a sus notificaciones de matrícula.
     */
    ESTUDIANTE:
        "/estudiante/notificaciones",
};

function Login() {
    const navigate = useNavigate();

    const [form, setForm] = useState({
        cedula: "",
        contrasena: "",
    });

    const [errores, setErrores] = useState({});

    // Error del servidor: { titulo, mensaje }
    const [fallo, setFallo] = useState(null);

    const [cargando, setCargando] =
        useState(false);

    function validar() {
        const nuevos = {};
        if (!form.cedula.trim()) nuevos.cedula = "Escriba su número de cédula.";
        if (!form.contrasena) nuevos.contrasena = "Escriba su contraseña.";
        return nuevos;
    }

    async function handleSubmit(event) {
        event.preventDefault();

        const nuevos = validar();
        setErrores(nuevos);
        setFallo(null);
        if (Object.keys(nuevos).length > 0) return;

        setCargando(true);

        try {
            const response = await fetch(
                `${API_URL}/auth/login`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type":
                            "application/json",
                    },
                    body: JSON.stringify(
                        form
                    ),
                }
            );

            if (!response.ok) {
                setFallo({
                    titulo: "Cédula o contraseña incorrectas",
                    mensaje: "Revise los datos e intente de nuevo.",
                });
                return;
            }

            const data =
                await response.json();

            /*
             * Información de sesión utilizada
             * por el Header y las páginas internas.
             */
            localStorage.setItem("token", data.token || "");
            localStorage.setItem("rol", data.rol || "");
            localStorage.setItem("cedula", form.cedula);
            localStorage.setItem("nombre", data.nombre || "");
            localStorage.setItem("correo", data.correo || "");

            /*
             * Redirección según el rol
             * devuelto por el backend.
             */
            const destino = RUTAS_POR_ROL[data.rol];

            if (destino) {
                navigate(destino, { replace: true });
            } else {
                setFallo({
                    titulo: "Inicio de sesión exitoso",
                    mensaje: "Aún no hay una página asignada para este rol.",
                    tono: "warning",
                });
            }
        } catch {
            setFallo({
                titulo: "Sin conexión",
                mensaje: "No se pudo conectar con el servidor. Intente de nuevo en unos momentos.",
            });
        } finally {
            setCargando(false);
        }
    }

    function actualizarCampo(campo, valor) {
        setForm((prev) => ({ ...prev, [campo]: valor }));
        setErrores((prev) => ({ ...prev, [campo]: "" }));
    }

    const resumen = listaErrores(errores);

    return (
        <AuthLayout>
            <h1>Ingresar a SIGAC</h1>
            <p className="lead">Use la cédula y la contraseña de su cuenta en la escuela.</p>

            <form onSubmit={handleSubmit} className="auth-form" noValidate>
                {resumen.length > 0 && (
                    <Alert tone="danger" title="Revise los datos para ingresar" errors={resumen} autoFocus />
                )}

                {fallo && (
                    <Alert tone={fallo.tono || "danger"} title={fallo.titulo} autoFocus>
                        {fallo.mensaje}
                    </Alert>
                )}

                <Field id="cedula" label="Cédula" error={errores.cedula}>
                    <input
                        id="cedula"
                        type="text"
                        className="sg-input"
                        inputMode="numeric"
                        value={form.cedula}
                        onChange={(event) => actualizarCampo("cedula", event.target.value)}
                        autoComplete="username"
                        aria-invalid={errores.cedula ? "true" : undefined}
                        aria-describedby={errores.cedula ? "cedula-error" : undefined}
                    />
                </Field>

                <PasswordField
                    id="contrasena"
                    label="Contraseña"
                    value={form.contrasena}
                    onChange={(event) => actualizarCampo("contrasena", event.target.value)}
                    autoComplete="current-password"
                    error={errores.contrasena}
                />

                <div className="auth-row">
                    <Link to="/recuperar-contrasena" className="link-btn">
                        ¿Olvidó su contraseña?
                    </Link>
                </div>

                <button
                    type="submit"
                    disabled={cargando}
                    aria-busy={cargando || undefined}
                    className={`sg-btn sg-btn--primary sg-btn--lg auth-submit${cargando ? " is-loading" : ""}`}
                >
                    {cargando && <span className="sg-spinner" aria-hidden="true" />}
                    {cargando ? "Ingresando…" : "Ingresar"}
                </button>
            </form>
        </AuthLayout>
    );
}

export default Login;
