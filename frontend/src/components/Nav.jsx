import { useState } from "react";
import { Link } from "react-router-dom";
import acusabIcon from "../assets/acusabIcon.png"
import { User, Menu, X } from "lucide-react";

const backend = "http://localhost:8080";

function Nav() {
    const token = localStorage.getItem("token");
    const rol = localStorage.getItem("rol");
    const nombre = localStorage.getItem("nombre");

    const [menuAbierto, setMenuAbierto] = useState(false);

    function toggleMenu() {
        setMenuAbierto((prev) => !prev);
    }

    function limpiarSesionLocal() {
        localStorage.removeItem("token");
        localStorage.removeItem("rol");
        localStorage.removeItem("cedula");
        localStorage.removeItem("nombre");
        window.location.href = "/";
    }

    async function cerrarSesion() {
        try {
            await fetch(`${backend}/auth/logout`, {
                method: "POST",
                headers: { Authorization: `Bearer ${token}` },
            });
        } catch {
            // Si falla la llamada al backend, igual cerramos sesión localmente
        } finally {
            limpiarSesionLocal();
        }
    }

    return (
        <nav className="nav">
            <div className="nav__inner container">
                {/* Logo e Identidad Corporativa (Estándar D01) */}
                <Link className="nav__logo" onClick={() => setMenuAbierto(false)}>
                    <img src={acusabIcon} className="nav__logo_img" alt="Logo ACUSAB" />
                    SIGAC
                </Link>

                {/* Botón Hamburguesa en Móvil */}
                <button
                    className="nav__toggle"
                    onClick={toggleMenu}
                    aria-label="Alternar menú de navegación"
                >
                    {menuAbierto ? <X size={24} /> : <Menu size={24} />}
                </button>

                {/* Enlaces de Navegación por Rol */}
                <ul className={`nav__links ${menuAbierto ? "nav__links--open" : ""}`} role="list">
                    {/* Admin */}
                    {rol === "ADMINISTRADOR" && (
                        <>
                            <li>
                                <Link
                                    to="/admin/usuarios/crear"
                                    className="nav__link"
                                    onClick={() => setMenuAbierto(false)}
                                >
                                    Crear usuario
                                </Link>
                            </li>
                        </>
                    )}

                    {/* Director académico */}
                    {rol === "DIRECTOR_ACADEMICO" && <>
                        {/* TODO: agregar links de Director Académico cuando existan las páginas */}
                    </>}

                    {/* Administrativo */}
                    {rol === "ADMINISTRATIVO" && <>
                        {/* TODO: agregar links de Administrativo cuando existan las páginas */}
                    </>}

                    {/* Profesor */}
                    {rol === "PROFESOR" && <>
                        {/* TODO: agregar links de Profesor cuando existan las páginas */}
                    </>}

                    {/* Junta directiva */}
                    {rol === "JUNTA_DIRECTIVA" && <>
                        {/* TODO: agregar links de Junta Directiva cuando existan las páginas */}
                    </>}

                    {/* Estudiante */}
                    {rol === "ESTUDIANTE" && <>
                        {/* TODO: agregar links de Estudiante cuando existan las páginas */}
                    </>}
                </ul>

                {/* Acciones de Sesión */}
                <div className="nav__actions">
                    {!token && (
                        <Link to="/login" className="btn btn--secondary">
                            Iniciar sesión
                        </Link>
                    )}
                    {token && (
                        <>
                            <span className="nav__user">
                               <User size={18} />
                                {nombre}
                            </span>
                            <button className="btn btn--danger" onClick={cerrarSesion}>
                                Cerrar sesión
                            </button>
                        </>
                    )}
                </div>
            </div>
        </nav>
    );
}

export default Nav;