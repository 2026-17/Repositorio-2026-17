import { useEffect, useRef, useState } from "react";
import {
    NavLink,
    useLocation,
} from "react-router-dom";
import {
    Bell,
    BookOpen,
    CalendarDays,
    ChartColumn,
    ChevronDown,
    ClipboardList,
    GraduationCap,
    LogOut,
    Menu,
    Music,
    PanelLeftClose,
    PanelLeftOpen,
    UserPlus,
    Users,
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
 * Un enlace puede ser:
 * - directo: { to, label, icon }
 * - desplegable: { label, icon, children }
 *
 * El ícono se muestra en el menú lateral (y es lo único visible cuando
 * el menú está contraído).
 */
const SUBNAV_POR_ROL = {
    [ROLES.ADMINISTRADOR]: [
        {
            to: "/admin/usuarios/crear",
            label: "Crear usuario",
            icon: UserPlus,
        },
    ],

    [ROLES.DIRECTOR_ACADEMICO]: [
        {
            label: "Período académico",
            icon: CalendarDays,
            children: [
                {
                    to: "/director-academico/periodos-academicos/crear",
                    label: "Crear",
                },
                {
                    to: "/director-academico/periodos-academicos/modificar",
                    label: "Consultar / Modificar",
                },
            ],
        },
        {
            label: "Cursos",
            icon: BookOpen,
            children: [
                {
                    to: "/director-academico/cursos/crear",
                    label: "Crear",
                },
                {
                    to: "/director-academico/cursos/modificar",
                    label: "Modificar",
                },
                {
                    to: "/director-academico/cursos/desactivar",
                    label: "Desactivar",
                },
            ],
        },
        {
            to: "/director-academico/profesores",
            label: "Profesores",
            icon: GraduationCap,
        },
        {
            to: "/director-academico/estudiantes/instrumentos",
            label: "Asignar instrumento",
            icon: Music,
        },
    ],

    [ROLES.ADMINISTRATIVO]: [
        {
            label: "Estudiantes",
            icon: Users,
            children: [
                {
                    to: "/admin/administracion/estudiantes",
                    label: "Consultar",
                },
                {
                    to: "/admin/administracion/estudiantes/crear",
                    label: "Registrar",
                },
                {
                    to: "/admin/administracion/estudiantes/instrumentos",
                    label: "Asignar instrumento",
                },
            ],
        },
        {
            label: "Matrícula y pagos",
            icon: ClipboardList,
            children: [
                {
                    to: "/admin/administracion/matricula",
                    label: "Matrícula",
                },
                {
                    to: "/admin/administracion/becas-pagos",
                    label: "Becas y pagos",
                },
            ],
        },
        {
            to: "/admin/administracion/reportes",
            label: "Reportes",
            icon: ChartColumn,
        },
    ],

    [ROLES.PROFESOR]: [],

    [ROLES.JUNTA_DIRECTIVA]: [],

    /*
     * HU-002 - Solicitud de matrícula.
     * HU-012 - Notificaciones del estudiante.
     */
    [ROLES.ESTUDIANTE]: [
        {
            to: "/estudiante/matricula",
            label: "Matrícula",
            icon: ClipboardList,
        },
        {
            to: "/estudiante/notificaciones",
            label: "Notificaciones",
            icon: Bell,
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
 * - navegación correspondiente al rol;
 * - submenús para secciones agrupadas.
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

    const location =
        useLocation();

    const [
        menuAbierto,
        setMenuAbierto,
    ] = useState(false);

    const [
        subnavAbierto,
        setSubnavAbierto,
    ] = useState(false);

    const [
        submenuAbierto,
        setSubmenuAbierto,
    ] = useState(null);

    const [
        posicionSubmenu,
        setPosicionSubmenu,
    ] = useState(null);

    /*
     * Menú lateral contraído (solo íconos) en escritorio.
     * Se recuerda en este navegador; si localStorage no está
     * disponible, simplemente arranca expandido.
     */
    const [
        menuContraido,
        setMenuContraido,
    ] = useState(() => {
        try {
            return localStorage.getItem("sigac-menu") === "contraido";
        } catch {
            return false;
        }
    });

    function alternarMenuContraido() {
        setMenuContraido((prev) => {
            const siguiente = !prev;

            try {
                localStorage.setItem(
                    "sigac-menu",
                    siguiente ? "contraido" : "expandido"
                );
            } catch {
                /* Sin almacenamiento: solo dura esta visita. */
            }

            return siguiente;
        });

        setSubmenuAbierto(null);
        setPosicionSubmenu(null);
    }

    const menuRef =
        useRef(null);

    const subnavRef =
        useRef(null);

    /*
     * Cierra los menús cuando se hace clic
     * fuera de ellos.
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
                setSubmenuAbierto(null);
                setPosicionSubmenu(null);
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
     * Cierra la sesión en backend y frontend.
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

    /*
     * Cierra la navegación móvil y cualquier
     * submenu abierto.
     */
    function cerrarSubnavMovil() {
        setSubnavAbierto(false);
        setSubmenuAbierto(null);
        setPosicionSubmenu(null);
    }

    /*
     * Alterna la apertura de un submenu.
     *
     * En escritorio se calcula su posición para
     * evitar problemas con overflow del nav.
     */
    function alternarSubmenu(
        label,
        evento
    ) {
        const boton =
            evento.currentTarget;

        const esMovil =
            window
                .matchMedia(
                    "(max-width: 768px)"
                )
                .matches;

        setSubmenuAbierto((prev) => {
            const siguiente =
                prev === label
                    ? null
                    : label;

            if (
                siguiente &&
                !esMovil
            ) {
                const rect =
                    boton.getBoundingClientRect();

                setPosicionSubmenu({
                    top:
                        rect.bottom + 4,
                    left:
                    rect.left,
                });
            } else {
                setPosicionSubmenu(
                    null
                );
            }

            return siguiente;
        });
    }

    const enlaces =
        SUBNAV_POR_ROL[rol] || [];

    return (
        <>
            {/* =====================================================
                Menú lateral: marca, secciones del rol y botón
                para contraer (solo escritorio)
               ===================================================== */}
            <aside
                className={
                    `sidebar ${
                        menuContraido
                            ? "sidebar--collapsed"
                            : ""
                    }`
                }
            >
                <div className="header-left sidebar-brand">

                    <div className="header-logo">
                        SIG
                    </div>

                    <span className="header-brand sidebar-label">
                        SIGAC
                    </span>

                </div>

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
                                    (prev) =>
                                        !prev
                                )
                            }
                        >
                            {subnavAbierto
                                ? (
                                    <X size={20} strokeWidth={1.75} />
                                )
                                : (
                                    <Menu size={20} strokeWidth={1.75} />
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
                                (enlace) => {
                                    const Icono =
                                        enlace.icon;

                                    /*
                                     * Enlace con submenu.
                                     */
                                    if (
                                        enlace.children
                                    ) {
                                        const activo =
                                            enlace.children.some(
                                                (hijo) =>
                                                    location.pathname ===
                                                        hijo.to ||
                                                    location.pathname.startsWith(
                                                        `${hijo.to}/`
                                                    )
                                            );

                                        const abierto =
                                            submenuAbierto ===
                                            enlace.label;

                                        return (
                                            <div
                                                className="subnav-dropdown"
                                                key={
                                                    enlace.label
                                                }
                                            >
                                                <button
                                                    type="button"
                                                    className={
                                                        `subnav-item subnav-item--dropdown ${
                                                            activo
                                                                ? "active"
                                                                : ""
                                                        }`
                                                    }
                                                    aria-haspopup="true"
                                                    aria-expanded={
                                                        abierto
                                                    }
                                                    onClick={(
                                                        event
                                                    ) =>
                                                        alternarSubmenu(
                                                            enlace.label,
                                                            event
                                                        )
                                                    }
                                                >
                                                    {Icono && (
                                                        <Icono size={20} strokeWidth={1.75} aria-hidden="true" />
                                                    )}

                                                    <span className="sidebar-label">
                                                        {enlace.label}
                                                    </span>

                                                    <ChevronDown
                                                        size={
                                                            14
                                                        }
                                                        className={
                                                            `subnav-dropdown-chevron ${
                                                                abierto
                                                                    ? "subnav-dropdown-chevron--open"
                                                                    : ""
                                                            }`
                                                        }
                                                    />
                                                </button>

                                                {abierto && (
                                                    <div
                                                        className="subnav-dropdown-panel"
                                                        role="menu"
                                                        style={
                                                            posicionSubmenu
                                                                ? {
                                                                    position:
                                                                        "fixed",
                                                                    top:
                                                                    posicionSubmenu.top,
                                                                    left:
                                                                    posicionSubmenu.left,
                                                                }
                                                                : undefined
                                                        }
                                                    >
                                                        {enlace.children.map(
                                                            (
                                                                hijo
                                                            ) => (
                                                                <NavLink
                                                                    key={
                                                                        hijo.to
                                                                    }
                                                                    to={
                                                                        hijo.to
                                                                    }
                                                                    end
                                                                    className={({
                                                                                    isActive,
                                                                                }) =>
                                                                        `subnav-dropdown-link ${
                                                                            isActive
                                                                                ? "active"
                                                                                : ""
                                                                        }`
                                                                    }
                                                                    onClick={
                                                                        cerrarSubnavMovil
                                                                    }
                                                                >
                                                                    {
                                                                        hijo.label
                                                                    }
                                                                </NavLink>
                                                            )
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    }

                                    /*
                                     * Enlace directo.
                                     */
                                    return (
                                        <NavLink
                                            key={
                                                enlace.to
                                            }
                                            to={
                                                enlace.to
                                            }
                                            end
                                            className={({
                                                            isActive,
                                                        }) =>
                                                `subnav-item ${
                                                    isActive
                                                        ? "active"
                                                        : ""
                                                }`
                                            }
                                            onClick={
                                                cerrarSubnavMovil
                                            }
                                        >
                                            {Icono && (
                                                <Icono size={20} strokeWidth={1.75} aria-hidden="true" />
                                            )}

                                            <span className="sidebar-label">
                                                {enlace.label}
                                            </span>
                                        </NavLink>
                                    );
                                }
                            )}
                        </div>
                    </nav>
                )}

                <button
                    type="button"
                    className="sidebar-toggle"
                    aria-expanded={!menuContraido}
                    aria-label={
                        menuContraido
                            ? "Expandir menú"
                            : "Contraer menú"
                    }
                    title={
                        menuContraido
                            ? "Expandir menú"
                            : "Contraer menú"
                    }
                    onClick={alternarMenuContraido}
                >
                    {menuContraido
                        ? <PanelLeftOpen size={20} strokeWidth={1.75} />
                        : <PanelLeftClose size={20} strokeWidth={1.75} />}
                </button>
            </aside>

            {/* =====================================================
                Header principal
               ===================================================== */}
            <header className="header">
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
        </>
    );
}

export default Header;
