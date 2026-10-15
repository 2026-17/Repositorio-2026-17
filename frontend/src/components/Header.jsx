import { useEffect, useRef, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import {
    Bell,
    ChevronRight,
    CircleHelp,
    LogOut,
    Menu,
    Moon,
    PanelLeft,
    Sun,
} from "lucide-react";

import { API_URL } from "../config/api";
import { MENU_POR_ROL, RUTA_INICIO } from "../constants/menu";
import { etiquetaRol, ROLES } from "../constants/roles";
import { cargarNotificaciones, useNotificacionesEstudiante } from "../hooks/useNotificacionesEstudiante";
import { limpiarSesion } from "../utils/sesion";
import { iniciales } from "./ui/utilidades";
import LogoSigac from "./LogoSigac";

// Migas y título de la pantalla de inicio (no forma parte del menú del rol).
const SECCION_INICIO = { grupo: { titulo: "Ayuda" }, item: { to: RUTA_INICIO, label: "Inicio" } };

// Busca la sección actual para las migas y el título de la pestaña.
function seccionActual(grupos, pathname) {
    let mejor = null;
    for (const grupo of grupos) {
        for (const item of grupo.items) {
            const coincide = pathname === item.to || pathname.startsWith(`${item.to}/`);
            if (coincide && (!mejor || item.to.length > mejor.item.to.length)) {
                mejor = { grupo, item };
            }
        }
    }
    return mejor;
}

function leerPreferencia(clave, porDefecto) {
    try {
        const valor = localStorage.getItem(clave);
        return valor === null ? porDefecto : valor;
    } catch {
        return porDefecto;
    }
}

function guardarPreferencia(clave, valor) {
    try {
        localStorage.setItem(clave, valor);
    } catch {
        /* Sin almacenamiento: la preferencia solo dura esta visita. */
    }
}

function temaInicial() {
    const actual = document.documentElement.getAttribute("data-theme");
    if (actual) return actual;
    return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

/*
 * Estructura de las páginas internas (como el prototipo):
 * menú lateral + barra superior + contenido.
 *
 * Incluye identificación del sistema, navegación del rol, usuario
 * autenticado con cierre de sesión, migas y cambio de tema.
 */
function Header({ children }) {
    const token = localStorage.getItem("token");
    const rol = localStorage.getItem("rol");
    const nombre = localStorage.getItem("nombre");
    const correo = localStorage.getItem("correo");

    const location = useLocation();

    const [menuAbierto, setMenuAbierto] = useState(false);
    const [contraido, setContraido] = useState(() => leerPreferencia("sigac-menu", "expandido") === "contraido");
    const [tema, setTema] = useState(temaInicial);
    const primerRender = useRef(true);

    const esEstudiante = rol === ROLES.ESTUDIANTE;
    const { noLeidas } = useNotificacionesEstudiante();

    const grupos = MENU_POR_ROL[rol] || [];
    const enInicio = location.pathname === RUTA_INICIO;
    const actual = enInicio ? SECCION_INICIO : seccionActual(grupos, location.pathname);

    // Tema claro / oscuro (se recuerda en este navegador)
    useEffect(() => {
        document.documentElement.setAttribute("data-theme", tema);
    }, [tema]);

    // Al cambiar de pantalla: título de la pestaña y foco en el h1
    useEffect(() => {
        document.title = actual ? `${actual.item.label} · SIGAC` : "SIGAC";
        if (primerRender.current) {
            primerRender.current = false;
            return;
        }
        const titulo = document.getElementById("page-title");
        if (titulo) titulo.focus({ preventScroll: true });
        window.scrollTo(0, 0);
    }, [location.pathname, actual]);

    // Contador de la campana: solo el estudiante tiene notificaciones
    useEffect(() => {
        if (!esEstudiante) return;
        cargarNotificaciones().catch(() => {
            /* La campana queda sin contador; la pantalla de notificaciones muestra el error. */
        });
    }, [esEstudiante]);

    // Esc cierra el menú en móvil
    useEffect(() => {
        if (!menuAbierto) return undefined;
        function alPresionar(event) {
            if (event.key === "Escape") setMenuAbierto(false);
        }
        document.addEventListener("keydown", alPresionar);
        return () => document.removeEventListener("keydown", alPresionar);
    }, [menuAbierto]);

    function alternarContraido() {
        setContraido((prev) => {
            guardarPreferencia("sigac-menu", prev ? "expandido" : "contraido");
            return !prev;
        });
    }

    function alternarTema() {
        setTema((prev) => {
            const siguiente = prev === "dark" ? "light" : "dark";
            guardarPreferencia("sigac-tema", siguiente);
            return siguiente;
        });
    }

    /*
     * Elimina los datos de autenticación
     * almacenados localmente.
     */
    function limpiarSesionLocal() {
        limpiarSesion();

        window.location.href = "/";
    }

    /*
     * Cierra la sesión en backend y frontend.
     */
    async function cerrarSesion() {
        try {
            await fetch(`${API_URL}/auth/logout`, {
                method: "POST",
                headers: { Authorization: `Bearer ${token}` },
            });
        } catch {
            /*
             * Si falla la llamada al backend,
             * la sesión local igualmente se elimina.
             */
        } finally {
            limpiarSesionLocal();
        }
    }

    const etiquetaUsuario = nombre || "Usuario";

    return (
        <div className="app">
            <a href="#contenido" className="skip-link">Saltar al contenido</a>

            {/* =====================================================
                Menú lateral
               ===================================================== */}
            <aside
                id="sidebar"
                className={`sidebar${contraido ? " is-collapsed" : ""}${menuAbierto ? " is-open" : ""}`}
                aria-label="Menú principal"
            >
                <div className="sb-brand">
                    <span className="sb-mark sb-mark--logo" aria-hidden="true">
                        <LogoSigac />
                    </span>
                    <span className="sb-brand-text">
                        <span className="sb-name">SIGAC</span>
                        <span className="sb-sub">ACUSAB</span>
                    </span>
                </div>

                <nav className="sb-nav" aria-label="Secciones del rol autenticado">
                    {grupos.map((grupo) => (
                        <div key={grupo.titulo} className="sb-group">
                            <p className="sb-group-title">{grupo.titulo}</p>
                            {grupo.items.map((item) => {
                                const Icono = item.icon;
                                const activo = actual?.item.to === item.to;
                                if (item.proximamente) {
                                    // Aún no disponible: visible, pero sin enlace
                                    return (
                                        <span
                                            key={item.to}
                                            className="sb-item is-disabled"
                                            aria-disabled="true"
                                            title={contraido ? `${item.label} (próximamente)` : undefined}
                                        >
                                            <Icono size={20} strokeWidth={1.75} aria-hidden="true" />
                                            <span className="sb-label">{item.label}</span>
                                            <span className="sb-soon">Próximamente</span>
                                        </span>
                                    );
                                }
                                return (
                                    <NavLink
                                        key={item.to}
                                        to={item.to}
                                        end
                                        className="sb-item"
                                        aria-current={activo ? "page" : undefined}
                                        title={contraido ? item.label : undefined}
                                        onClick={() => setMenuAbierto(false)}
                                    >
                                        <Icono size={20} strokeWidth={1.75} aria-hidden="true" />
                                        <span className="sb-label">{item.label}</span>
                                    </NavLink>
                                );
                            })}
                        </div>
                    ))}
                </nav>

                <div className="sb-foot">
                    {/* Lleva a la pantalla de inicio, que explica cada opción del menú */}
                    <NavLink
                        to={RUTA_INICIO}
                        className="sb-item"
                        aria-current={enInicio ? "page" : undefined}
                        title={contraido ? "Ayuda" : undefined}
                        onClick={() => setMenuAbierto(false)}
                    >
                        <CircleHelp size={20} strokeWidth={1.75} aria-hidden="true" />
                        <span className="sb-label">Ayuda</span>
                    </NavLink>

                    <button
                        type="button"
                        className="sb-toggle"
                        onClick={alternarContraido}
                        aria-expanded={!contraido}
                        aria-controls="sidebar"
                        title={contraido ? "Expandir menú" : undefined}
                    >
                        <PanelLeft size={20} strokeWidth={1.75} aria-hidden="true" />
                        <span className="sb-label">{contraido ? "Expandir menú" : "Contraer menú"}</span>
                    </button>

                    {token && (
                        <div className="sb-user">
                            <span className="avatar avatar--sidebar" aria-hidden="true">
                                {iniciales(etiquetaUsuario)}
                            </span>
                            <div className="sb-user-info" title={correo || undefined}>
                                <b>{etiquetaUsuario}</b>
                                <span>{etiquetaRol(rol)}</span>
                            </div>
                            <button
                                type="button"
                                onClick={cerrarSesion}
                                aria-label="Cerrar sesión"
                                title="Cerrar sesión"
                            >
                                <LogOut size={18} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </div>
                    )}
                </div>
            </aside>

            <div
                className={`scrim${menuAbierto ? " is-open" : ""}`}
                onClick={() => setMenuAbierto(false)}
                aria-hidden="true"
            />

            <div className="app-main">
                {/* =====================================================
                    Barra superior
                   ===================================================== */}
                <header className="topbar">
                    <button
                        type="button"
                        className="tb-btn tb-menu"
                        onClick={() => setMenuAbierto(true)}
                        aria-label="Abrir menú"
                        aria-expanded={menuAbierto}
                        aria-controls="sidebar"
                    >
                        <Menu size={20} strokeWidth={1.75} aria-hidden="true" />
                    </button>

                    <nav className="tb-crumbs" aria-label="Ubicación">
                        <span className="crumb-root">SIGAC</span>
                        {actual && (
                            <>
                                <ChevronRight size={14} strokeWidth={1.75} className="crumb-root" aria-hidden="true" />
                                <span className="crumb-root">{actual.grupo.titulo}</span>
                                <ChevronRight size={14} strokeWidth={1.75} className="crumb-root" aria-hidden="true" />
                                <b>{actual.item.label}</b>
                            </>
                        )}
                    </nav>

                    <div className="tb-right">
                        {esEstudiante && (
                            <Link
                                to="/estudiante/notificaciones"
                                className="tb-btn"
                                aria-label={noLeidas > 0 ? `Notificaciones, ${noLeidas} sin leer` : "Notificaciones"}
                                title={noLeidas > 0 ? `Notificaciones (${noLeidas} sin leer)` : "Notificaciones"}
                            >
                                <Bell size={20} strokeWidth={1.75} aria-hidden="true" />
                                {noLeidas > 0 && (
                                    <span className="tb-count" aria-hidden="true">
                                        {noLeidas > 99 ? "99+" : noLeidas}
                                    </span>
                                )}
                            </Link>
                        )}
                        <button
                            type="button"
                            className="tb-btn"
                            onClick={alternarTema}
                            aria-label={tema === "dark" ? "Cambiar a modo claro" : "Cambiar a modo oscuro"}
                            title={tema === "dark" ? "Cambiar a modo claro" : "Cambiar a modo oscuro"}
                        >
                            {tema === "dark"
                                ? <Sun size={20} strokeWidth={1.75} aria-hidden="true" />
                                : <Moon size={20} strokeWidth={1.75} aria-hidden="true" />}
                        </button>
                    </div>
                </header>

                <main className="content" id="contenido" tabIndex={-1}>
                    {children}
                </main>
            </div>
        </div>
    );
}

export default Header;
