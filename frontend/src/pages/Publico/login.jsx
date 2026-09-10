import { useState } from "react";
import "./publico.css";

function Login() {
    const backend = "http://localhost:8080";

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
            const response = await fetch(`${backend}/auth/login`, {
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
            alert("Login exitoso");
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
                <img src="/logo-acusab.png" alt="Logo ACUSAB" className="header-logo" />
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
                    {/* Nota: Reemplaza estas URLs con las imágenes culturales o de tu institución */}
                    <div className="slide slide-1"></div>
                    <div className="slide slide-2"></div>
                    <div className="slide slide-3"></div>

                    {/* Overlay opcional para darle un tinte corporativo a las imágenes */}
                    <div className="slideshow-overlay"></div>
                </section>

            </main>
        </div>
    );
}

export default Login;