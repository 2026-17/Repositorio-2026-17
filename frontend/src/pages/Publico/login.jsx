import { useState } from "react";
import { useNavigate } from "react-router-dom";
import acusabIcon from "../../assets/acusabIcon.png";
import slide1 from "../../assets/slideshow/slide1.jpg";
import slide2 from "../../assets/slideshow/slide2.jpeg";
import slide3 from "../../assets/slideshow/slide3.jpg";
import { API_URL } from "../../config/api";
import "./publico.css";

// Ruta a la que se redirige según el rol tras el login.
// TODO: agregar el resto de rutas cuando existan las páginas de cada rol.
const RUTAS_POR_ROL = {
    ADMINISTRADOR: "/admin/administracion/estudiantes",
    DIRECTOR_ACADEMICO: "/director-academico/cursos/crear",
};

function Login() {

    const navigate = useNavigate();

    const [form, setForm] = useState({
        cedula: "",
        contrasena: "",
    });
    const [error, setError] = useState("");
    const [cargando, setCargando] = useState(false);

    async function handleSubmit(e) {
        e.preventDefault();
        setError("");
        setCargando(true);

        try {
            const response = await fetch(`${API_URL}/auth/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(form),
            });

            if (!response.ok) {
                setError("Credenciales inválidas");
                return;
            }

            const data = await response.json();
            localStorage.setItem("token", data.token || "");
            localStorage.setItem("rol", data.rol || "");
            localStorage.setItem("cedula", form.cedula);
            localStorage.setItem("nombre", data.nombre || "");
            localStorage.setItem("correo", data.correo || "");

            const destino = RUTAS_POR_ROL[data.rol];
            if (destino) {
                navigate(destino);
            } else {
                alert("Login exitoso. Aún no hay una página asignada para tu rol.");
            }
        } catch {
            setError("No se pudo conectar con el servidor");
        } finally {
            setCargando(false);
        }
    }

    return (
        <div className="app-container">
            {/* Header corporativo */}
            <header className="sigac-header">
                <img src={acusabIcon} alt="Logo ACUSAB" className="header-logo" />
                <h1 className="header-title">SIGAC</h1>
                <div className="header-spacer"></div>
            </header>

            {/* Contenedor principal dividido */}
            <main className="split-layout">

                {/* Panel Izquierdo: Formulario de Login */}
                <section className="form-panel">
                    <div className="login-card">
                        <h2 className="login-title">Iniciar sesión</h2>

                        <form onSubmit={handleSubmit} className="login-form">
                            <p className="login-greeting">¡Bienvenido de nuevo!</p>

                            <div className="form-group">
                                <label className="form-label">Cédula</label>
                                <input
                                    type="text"
                                    className="form-input"
                                    value={form.cedula}
                                    onChange={(e) => setForm({...form, cedula: e.target.value})}
                                    required
                                />
                            </div>

                            <div className="form-group">
                                <label className="form-label">Contraseña</label>
                                <input
                                    type="password"
                                    className="form-input"
                                    value={form.contrasena}
                                    onChange={(e) => setForm({...form, contrasena: e.target.value})}
                                    required
                                />
                            </div>

                            <div className="form-actions">
                                <button type="submit" disabled={cargando} className="btn-primary">
                                    {cargando ? "Ingresando..." : "Ingresar"}
                                </button>
                            </div>
                        </form>

                        {error && <p className="error-message">{error}</p>}
                    </div>
                </section>

                {/* Panel Derecho: Slideshow de Imágenes */}
                <section className="slideshow-panel">
                    <div className="slide slide-1" style={{ backgroundImage: `url(${slide1})` }}></div>
                    <div className="slide slide-2" style={{ backgroundImage: `url(${slide2})` }}></div>
                    <div className="slide slide-3" style={{ backgroundImage: `url(${slide3})` }}></div>

                    {/* Overlay opcional para darle un tinte corporativo a las imágenes */}
                    <div className="slideshow-overlay"></div>
                </section>

            </main>
        </div>
    );
}

export default Login;