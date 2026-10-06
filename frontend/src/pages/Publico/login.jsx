import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import acusabIcon from "../../assets/acusabIcon.png";
import slide1 from "../../assets/slideshow/slide1.jpg";
import slide2 from "../../assets/slideshow/slide2.jpg";
import slide3 from "../../assets/slideshow/slide3.jpg";

import { API_URL } from "../../config/api";
import { useDialog } from "../../components/dialog/useDialog";

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
    const dialog = useDialog();

    const [form, setForm] = useState({
        cedula: "",
        contrasena: "",
    });

    const [cargando, setCargando] =
        useState(false);

    async function handleSubmit(event) {
        event.preventDefault();

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
                dialog.error(
                    "La cédula o la contraseña son incorrectas.",
                    { titulo: "No se pudo iniciar sesión" }
                );
                return;
            }

            const data =
                await response.json();

            /*
             * Información de sesión utilizada
             * por el Header y las páginas internas.
             */
            localStorage.setItem(
                "token",
                data.token || ""
            );

            localStorage.setItem(
                "rol",
                data.rol || ""
            );

            localStorage.setItem(
                "cedula",
                form.cedula
            );

            localStorage.setItem(
                "nombre",
                data.nombre || ""
            );

            localStorage.setItem(
                "correo",
                data.correo || ""
            );

            /*
             * Redirección según el rol
             * devuelto por el backend.
             */
            const destino =
                RUTAS_POR_ROL[
                    data.rol
                    ];

            if (destino) {
                navigate(
                    destino,
                    {
                        replace: true,
                    }
                );
            } else {
                dialog.aviso(
                    "Aún no hay una página asignada para este rol.",
                    { titulo: "Inicio de sesión exitoso" }
                );
            }
        } catch {
            dialog.error(
                "No se pudo conectar con el servidor. Intente de nuevo en unos momentos.",
                { titulo: "Sin conexión" }
            );
        } finally {
            setCargando(false);
        }
    }

    function actualizarCampo(
        campo,
        valor
    ) {
        setForm((prev) => ({
            ...prev,
            [campo]: valor,
        }));
    }

    return (
        <div className="app-container">
            {/* ================================================
                Header corporativo
               ================================================ */}
            <header className="sigac-header">
                <img
                    src={acusabIcon}
                    alt="Logo ACUSAB"
                    className="header-logo"
                />

                <h1 className="header-title">
                    SIGAC
                </h1>

                <div className="header-spacer" />
            </header>

            {/* ================================================
                Contenido principal
               ================================================ */}
            <main className="split-layout">

                {/* ============================================
                    Panel izquierdo - Inicio de sesión
                   ============================================ */}
                <section className="form-panel">
                    <div className="login-card">

                        <h2 className="login-title">
                            Iniciar sesión
                        </h2>

                        <form
                            onSubmit={
                                handleSubmit
                            }
                            className="login-form"
                        >
                            <p className="login-greeting">
                                ¡Bienvenido de nuevo!
                            </p>

                            <div className="form-group">
                                <label
                                    htmlFor="cedula"
                                    className="form-label"
                                >
                                    Cédula
                                </label>

                                <input
                                    id="cedula"
                                    type="text"
                                    className="form-input"
                                    value={
                                        form.cedula
                                    }
                                    onChange={(
                                        event
                                    ) =>
                                        actualizarCampo(
                                            "cedula",
                                            event.target
                                                .value
                                        )
                                    }
                                    autoComplete="username"
                                    required
                                />
                            </div>

                            <div className="form-group">
                                <label
                                    htmlFor="contrasena"
                                    className="form-label"
                                >
                                    Contraseña
                                </label>

                                <input
                                    id="contrasena"
                                    type="password"
                                    className="form-input"
                                    value={
                                        form.contrasena
                                    }
                                    onChange={(
                                        event
                                    ) =>
                                        actualizarCampo(
                                            "contrasena",
                                            event.target
                                                .value
                                        )
                                    }
                                    autoComplete="current-password"
                                    required
                                />

                                <Link
                                    to="/recuperar-contrasena"
                                    className="login-olvido"
                                >
                                    ¿Olvidó su contraseña?
                                </Link>
                            </div>

                            <div className="form-actions">
                                <button
                                    type="submit"
                                    disabled={
                                        cargando
                                    }
                                    className="btn-primary"
                                >
                                    {cargando
                                        ? "Ingresando..."
                                        : "Ingresar"}
                                </button>
                            </div>
                        </form>
                    </div>
                </section>

                {/* ============================================
                    Panel derecho - Slideshow
                   ============================================ */}
                <section className="slideshow-panel">

                    <div
                        className="slide slide-1"
                        style={{
                            backgroundImage:
                                `url(${slide1})`,
                        }}
                    />

                    <div
                        className="slide slide-2"
                        style={{
                            backgroundImage:
                                `url(${slide2})`,
                        }}
                    />

                    <div
                        className="slide slide-3"
                        style={{
                            backgroundImage:
                                `url(${slide3})`,
                        }}
                    />

                    <div className="slideshow-overlay" />
                </section>

            </main>
        </div>
    );
}

export default Login;