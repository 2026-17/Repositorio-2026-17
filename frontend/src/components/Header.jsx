import { useEffect, useRef, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { ChevronDown, LogOut, Menu, X } from "lucide-react";
import { API_URL } from "../config/api";
import { etiquetaRol, ROLES } from "../constants/roles";
import "../pages/AdminSis/styles/admin_Sis.css";

/* Enlaces de la subnavegación, agrupados por rol.
   Cada rol define aquí sus propias secciones; los que aún no tienen
   páginas quedan con un arreglo vacío listos para cuando existan.

   Un enlace puede ser directo ({ to, label }) o un desplegable con
   varias opciones ({ label, children: [{ to, label }, ...] }). */
const SUBNAV_POR_ROL = {
    [ROLES.ADMINISTRADOR]: [
        { to: "/admin/usuarios/crear", label: "Crear usuario" },
    ],
    [ROLES.DIRECTOR_ACADEMICO]: [
        {
            label: "Período académico",
            children: [
                { to: "/director-academico/periodos-academicos/crear", label: "Crear" },
                { to: "/director-academico/periodos-academicos/modificar", label: "Consultar / Modificar" },
            ],
        },
        { to: "/director-academico/cursos/crear", label: "Crear curso" },
        { to: "/director-academico/cursos/desactivar", label: "Desactivar curso" },
    ],
    [ROLES.ADMINISTRATIVO]: [
        { to: "/admin/administracion/estudiantes", label: "Registrar estudiante" },
        { to: "/admin/administracion/becas-pagos", label: "Becas y pagos" },
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
    const location = useLocation();

    const [menuAbierto, setMenuAbierto] = useState(false);
    const [subnavAbierto, setSubnavAbierto] = useState(false);
    const [submenuAbierto, setSubmenuAbierto] = useState(null);
    const [posicionSubmenu, setPosicionSubmenu] = useState(null);
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
                setSubmenuAbierto(null);
                setPosicionSubmenu(null);
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

    function cerrarSubnavMovil() {
        setSubnavAbierto(false);
        setSubmenuAbierto(null);
        setPosicionSubmenu(null);
    }

    /* El panel del desplegable usa position: fixed (calculado aquí)
       para escapar del recorte que provoca el overflow-x: auto de
       .subnav. En el menú móvil (flyout en columna) no aplica: ahí
       el panel se muestra en línea, controlado solo por CSS. */
    function alternarSubmenu(label, evento) {

        // Se captura el nodo aquí, de forma síncrona: dentro del
        // callback de abajo el evento sintético ya perdió
        // currentTarget, porque React lo ejecuta en el render
        // posterior al del clic.
        const boton = evento.currentTarget;
        const esMovil = window.matchMedia("(max-width: 768px)").matches;

        setSubmenuAbierto((prev) => {
            const siguiente = prev === label ? null : label;

            if (siguiente && !esMovil) {
                const rect = boton.getBoundingClientRect();

                setPosicionSubmenu({
                    top: rect.bottom + 4,
                    left: rect.left,
                });
            } else {
                setPosicionSubmenu(null);
            }

            return siguiente;
        });
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
                        {enlaces.map((enlace) => {

                            if (enlace.children) {

                                const activo = enlace.children.some(
                                    (hijo) => location.pathname === hijo.to
                                );

                                const abierto = submenuAbierto === enlace.label;

                                return (
                                    <div className="subnav-dropdown" key={enlace.label}>
                                        <button
                                            type="button"
                                            className={`subnav-item subnav-item--dropdown ${activo ? "active" : ""}`}
                                            aria-haspopup="true"
                                            aria-expanded={abierto}
                                            onClick={(e) => alternarSubmenu(enlace.label, e)}
                                        >
                                            {enlace.label}
                                            <ChevronDown
                                                size={14}
                                                className={`subnav-dropdown-chevron ${abierto ? "subnav-dropdown-chevron--open" : ""}`}
                                            />
                                        </button>

                                        {abierto && (
                                            <div
                                                className="subnav-dropdown-panel"
                                                role="menu"
                                                style={
                                                    posicionSubmenu
                                                        ? {
                                                              position: "fixed",
                                                              top: posicionSubmenu.top,
                                                              left: posicionSubmenu.left,
                                                          }
                                                        : undefined
                                                }
                                            >
                                                {enlace.children.map((hijo) => (
                                                    <NavLink
                                                        key={hijo.to}
                                                        to={hijo.to}
                                                        className={({ isActive }) =>
                                                            `subnav-dropdown-link ${isActive ? "active" : ""}`
                                                        }
                                                        onClick={cerrarSubnavMovil}
                                                    >
                                                        {hijo.label}
                                                    </NavLink>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                );
                            }

                            return (
                                <NavLink
                                    key={enlace.to}
                                    to={enlace.to}
                                    className={({ isActive }) =>
                                        `subnav-item ${isActive ? "active" : ""}`
                                    }
                                    onClick={cerrarSubnavMovil}
                                >
                                    {enlace.label}
                                </NavLink>
                            );
                        })}
                    </div>
                </nav>
            )}
        </>
    );
}

export default Header;
