import { useEffect, useRef, useState } from "react";
import { NavLink } from "react-router-dom";
import {
    ChevronDown,
    LogOut,
    Menu,
    X,
} from "lucide-react";

import { API_URL } from "../config/api";
import {
    etiquetaRol,
    ROLES,
} from "../constants/roles";

import "../pages/AdminSis/styles/admin_Sis.css";

/*
 * Enlaces de subnavegación agrupados por rol.
 *
 * Cada usuario visualiza únicamente las secciones
 * correspondientes a su rol dentro del sistema.
 */
const SUBNAV_POR_ROL = {
    [ROLES.ADMINISTRADOR]: [
        {
            to: "/admin/usuarios/crear",
            label: "Crear usuario",
        },
        {
            to: "/admin/estudiantes/crear",
            label: "Registrar estudiante",
        },
    ],

    [ROLES.DIRECTOR_ACADEMICO]: [
        {
            to: "/director-academico/cursos/crear",
            label: "Crear curso",
        },
        {
            to: "/director-academico/cursos/desactivar",
            label: "Desactivar curso",
        },
    ],

    [ROLES.ADMINISTRATIVO]: [
        {
            to: "/admin/administracion/estudiantes",
            label: "Estudiantes",
        },
        {
            to: "/admin/administracion/encargados",
            label: "Encargados",
        },
        {
            to: "/admin/administracion/matricula",
            label: "Matrícula",
        },
        {
            to: "/admin/administracion/profesores",
            label: "Profesores",
        },
        {
            to: "/admin/administracion/reportes",
            label: "Reportes",
        },
    ],

    [ROLES.PROFESOR]: [],

    [ROLES.JUNTA_DIRECTIVA]: [],

    /*
     * HU-012 - Notificaciones del estudiante.
     */
    [ROLES.ESTUDIANTE]: [
        {
            to: "/estudiante/notificaciones",
            label: "Notificaciones",
        },
    ],
};

/*
 * Header global de las páginas internas.
 *
 * Incluye:
 * - identificación del sistema;
 * - información del usuario autenticado;
 * - cierre de sesión;
 * - navegación correspondiente al rol.
 */
function Header({ titulo = "" }) {
    const token =
        localStorage.getItem("token");

    const rol =
        localStorage.getItem("rol");

    const nombre =
        localStorage.getItem("nombre");

    const correo =
        localStorage.getItem("correo");

    const [menuAbierto, setMenuAbierto] =
        useState(false);

    const [
        subnavAbierto,
        setSubnavAbierto,
    ] = useState(false);

    const menuRef =
        useRef(null);

    const subnavRef =
        useRef(null);

    /*
     * Cierra los menús cuando el usuario hace
     * clic fuera de ellos.
     */
    useEffect(() => {
        function alClicFuera(event) {
            if (
                menuRef.current &&
                !menuRef.current.contains(
                    event.target
                )
            ) {
                setMenuAbierto(false);
            }

            if (
                subnavRef.current &&
                !subnavRef.current.contains(
                    event.target
                )
            ) {
                setSubnavAbierto(false);
            }
        }

        document.addEventListener(
            "mousedown",
            alClicFuera
        );

        return () =>
            document.removeEventListener(
                "mousedown",
                alClicFuera
            );
    }, []);

    /*
     * Elimina los datos de autenticación
     * almacenados localmente.
     */
    function limpiarSesionLocal() {
        localStorage.removeItem("token");
        localStorage.removeItem("rol");
        localStorage.removeItem("cedula");
        localStorage.removeItem("nombre");
        localStorage.removeItem("correo");

        window.location.href = "/";
    }

    /*
     * Cierra la sesión tanto en backend como
     * en el almacenamiento local.
     */
    async function cerrarSesion() {
        try {
            await fetch(
                `${API_URL}/auth/logout`,
                {
                    method: "POST",
                    headers: {
                        Authorization:
                            `Bearer ${token}`,
                    },
                }
            );
        } catch {
            /*
             * Si falla la llamada al backend,
             * la sesión local igualmente se elimina.
             */
        } finally {
            limpiarSesionLocal();
        }
    }

    const enlaces =
        SUBNAV_POR_ROL[rol] || [];

    return (
        <>
            {/* =====================================================
                Header principal
               ===================================================== */}
            <header className="header">
                <div className="header-left">
                    <div className="header-logo">
                        SIG
                    </div>

                    <span className="header-brand">
                        SIGAC
                    </span>
                </div>

                <div className="header-title">
                    {titulo}
                </div>

                <div className="header-right">
                    {token && (
                        <div
                            className="header-user"
                            ref={menuRef}
                        >
                            <button
                                type="button"
                                className="header-user-trigger"
                                aria-haspopup="true"
                                aria-expanded={
                                    menuAbierto
                                }
                                onClick={() =>
                                    setMenuAbierto(
                                        (prev) =>
                                            !prev
                                    )
                                }
                            >
                                {nombre}

                                <ChevronDown
                                    size={16}
                                    className="chevron"
                                />
                            </button>

                            {menuAbierto && (
                                <div
                                    className="user-dropdown"
                                    role="menu"
                                >
                                    <p className="user-dropdown-name">
                                        {nombre}
                                    </p>

                                    <p className="user-dropdown-role">
                                        {etiquetaRol(
                                            rol
                                        )}
                                    </p>

                                    {correo && (
                                        <a
                                            href={`mailto:${correo}`}
                                            className="user-dropdown-email"
                                        >
                                            {correo}
                                        </a>
                                    )}

                                    <hr className="user-dropdown-divider" />

                                    <button
                                        type="button"
                                        className="user-dropdown-logout"
                                        onClick={
                                            cerrarSesion
                                        }
                                    >
                                        <LogOut
                                            size={16}
                                        />

                                        Cerrar sesión
                                    </button>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </header>

            {/* =====================================================
                Subnavegación por rol
               ===================================================== */}
            {enlaces.length > 0 && (
                <nav
                    className="subnav"
                    aria-label="Secciones del rol autenticado"
                    ref={subnavRef}
                >
                    <button
                        type="button"
                        className="subnav-toggle"
                        aria-haspopup="true"
                        aria-expanded={
                            subnavAbierto
                        }
                        aria-label="Alternar menú de secciones"
                        onClick={() =>
                            setSubnavAbierto(
                                (prev) => !prev
                            )
                        }
                    >
                        {subnavAbierto
                            ? (
                                <X size={22} />
                            )
                            : (
                                <Menu size={22} />
                            )}
                    </button>

                    <div
                        className={
                            `subnav-links ${
                                subnavAbierto
                                    ? "subnav-links--open"
                                    : ""
                            }`
                        }
                    >
                        {enlaces.map(
                            (enlace) => (
                                <NavLink
                                    key={
                                        enlace.to
                                    }
                                    to={
                                        enlace.to
                                    }
                                    className={({
                                                    isActive,
                                                }) =>
                                        `subnav-item ${
                                            isActive
                                                ? "active"
                                                : ""
                                        }`
                                    }
                                    onClick={() =>
                                        setSubnavAbierto(
                                            false
                                        )
                                    }
                                >
                                    {
                                        enlace.label
                                    }
                                </NavLink>
                            )
                        )}
                    </div>
                </nav>
            )}
        </>
    );
}

export default Header;