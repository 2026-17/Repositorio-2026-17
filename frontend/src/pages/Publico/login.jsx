import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { API_URL } from "../../config/api";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import PasswordField from "../../components/ui/PasswordField";
import { listaErrores } from "../../components/ui/utilidades";
import { leerError } from "../../utils/api";
import { guardarSesion, RUTA_CAMBIO_CONTRASENA_TEMPORAL, RUTAS_POR_ROL } from "../../utils/sesion";
import AuthLayout from "./AuthLayout";

import "./publico.css";

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
                const error = await leerError(response, null);
                const generico = error.status >= 500 || !error.delBackend;
                setFallo({
                    titulo: "No se pudo iniciar sesión",
                    mensaje: generico
                        ? "No se pudo iniciar sesión. Intente de nuevo en unos momentos."
                        : error.message,
                });
                return;
            }

            const data =
                await response.json();

            /*
             * Información de sesión utilizada
             * por el Header y las páginas internas.
             */
            guardarSesion(data, form.cedula);

            /*
             * Ingresó con la contraseña temporal: debe cambiarla
             * antes de usar el sistema (el backend no le deja
             * hacer nada más con este token).
             */
            if (data.requiereCambioContrasena) {
                navigate(RUTA_CAMBIO_CONTRASENA_TEMPORAL, { replace: true });
                return;
            }

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
