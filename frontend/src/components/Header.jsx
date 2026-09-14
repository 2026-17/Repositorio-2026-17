import { useEffect, useRef, useState } from "react";
import { NavLink } from "react-router-dom";
import { ChevronDown, LogOut, Menu, X } from "lucide-react";
import { API_URL } from "../config/api";
import { etiquetaRol, ROLES } from "../constants/roles";
import "../pages/AdminSis/styles/admin_Sis.css";

/* Enlaces de la subnavegación, agrupados por rol.
   Cada rol define aquí sus propias secciones; los que aún no tienen
   páginas quedan con un arreglo vacío listos para cuando existan. */
const SUBNAV_POR_ROL = {
    [ROLES.ADMINISTRADOR]: [
        { to: "/admin/usuarios/crear", label: "Crear usuario" },
    ],
    [ROLES.DIRECTOR_ACADEMICO]: [
        { to: "/director-academico/cursos/crear", label: "Crear curso" },
    ],
    [ROLES.ADMINISTRATIVO]: [
        { to: "/admin/administracion/estudiantes", label: "Estudiantes" },
        { to: "/admin/administracion/matricula", label: "Matricula" },
        { to: "/admin/administracion/profesores", label: "Profesores" },
        { to: "/admin/administracion/reportes", label: "Reportes" },
    ],
    [ROLES.PROFESOR]: [],
    [ROLES.JUNTA_DIRECTIVA]: [],
    [ROLES.ESTUDIANTE]: [],
};

/* Header global de las páginas internas del sistema.
   Muestra el menú de usuario y, debajo, la subnavegación con las
   secciones que le corresponden al rol autenticado. */
function Header({ titulo = "" }) {
    const token = localStorage.getItem("token");
    const rol = localStorage.getItem("rol");
    const nombre = localStorage.getItem("nombre");
    const correo = localStorage.getItem("correo");

    const [menuAbierto, setMenuAbierto] = useState(false);
    const [subnavAbierto, setSubnavAbierto] = useState(false);
    const menuRef = useRef(null);
    const subnavRef = useRef(null);

    // Cierra el menú al hacer clic fuera de él.
    useEffect(() => {
        function alClicFuera(e) {
            if (menuRef.current && !menuRef.current.contains(e.target)) {
                setMenuAbierto(false);
            }
            if (subnavRef.current && !subnavRef.current.contains(e.target)) {
                setSubnavAbierto(false);
            }
        }
        document.addEventListener("mousedown", alClicFuera);
        return () => document.removeEventListener("mousedown", alClicFuera);
    }, []);

    function limpiarSesionLocal() {
        localStorage.removeItem("token");
        localStorage.removeItem("rol");
        localStorage.removeItem("cedula");
        localStorage.removeItem("nombre");
        localStorage.removeItem("correo");
        window.location.href = "/";
    }

    async function cerrarSesion() {
        try {
            await fetch(`${API_URL}/auth/logout`, {
                method: "POST",
                headers: { Authorization: `Bearer ${token}` },
            });
        } catch {
            // Si falla la llamada al backend, igual cerramos sesión localmente
        } finally {
            limpiarSesionLocal();
        }
    }

    const enlaces = SUBNAV_POR_ROL[rol] || [];

    return (
        <>
            {/* ---------- Header (D06) ---------- */}
            <header className="header">
                <div className="header-left">
                    <div className="header-logo">SIG</div>
                    <span className="header-brand">SIGAC</span>
                </div>

                <div className="header-title">{titulo}</div>

                <div className="header-right">
                    {token && (
                        <div className="header-user" ref={menuRef}>
                            <button
                                type="button"
                                className="header-user-trigger"
                                aria-haspopup="true"
                                aria-expanded={menuAbierto}
                                onClick={() => setMenuAbierto((prev) => !prev)}
                            >
                                {nombre}
                                <ChevronDown size={16} className="chevron" />
                            </button>

                            {menuAbierto && (
                                <div className="user-dropdown" role="menu">
                                    <p className="user-dropdown-name">{nombre}</p>
                                    <p className="user-dropdown-role">{etiquetaRol(rol)}</p>
                                    {correo && (
                                        <a href={`mailto:${correo}`} className="user-dropdown-email">
                                            {correo}
                                        </a>
                                    )}
                                    <hr className="user-dropdown-divider" />
                                    <button
                                        type="button"
                                        className="user-dropdown-logout"
                                        onClick={cerrarSesion}
                                    >
                                        <LogOut size={16} />
                                        Cerrar sesión
                                    </button>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </header>

            {/* ---------- Subnavegación por rol ---------- */}
            {enlaces.length > 0 && (
                <nav className="subnav" aria-label="Secciones del rol autenticado" ref={subnavRef}>
                    <button
                        type="button"
                        className="subnav-toggle"
                        aria-haspopup="true"
                        aria-expanded={subnavAbierto}
                        aria-label="Alternar menú de secciones"
                        onClick={() => setSubnavAbierto((prev) => !prev)}
                    >
                        {subnavAbierto ? <X size={22} /> : <Menu size={22} />}
                    </button>

                    <div className={`subnav-links ${subnavAbierto ? "subnav-links--open" : ""}`}>
                        {enlaces.map((enlace) => (
                            <NavLink
                                key={enlace.to}
                                to={enlace.to}
                                className={({ isActive }) =>
                                    `subnav-item ${isActive ? "active" : ""}`
                                }
                                onClick={() => setSubnavAbierto(false)}
                            >
                                {enlace.label}
                            </NavLink>
                        ))}
                    </div>
                </nav>
            )}
        </>
    );
}

export default Header;
