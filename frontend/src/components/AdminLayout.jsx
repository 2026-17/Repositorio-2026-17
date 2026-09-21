import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";

function AdminLayout() {
    const [menuOpen, setMenuOpen] = useState(false);

    const navClass = ({ isActive }) => `admin-tab${isActive ? " admin-tab-active" : ""}`;

    return (
        <div className="sigac-app">
            <header className="header">
                <div className="header-left">
                    <div className="header-logo" aria-hidden="true">♪</div>
                    <span className="header-brand">SIGAC</span>
                </div>
                <span className="header-title">Matrícula académica</span>
                <div className="header-right">
                    <div className="header-avatar" aria-hidden="true">AD</div>
                    <span>Administrador</span>
                    <div className="user-menu-wrap">
                        <button id="btnMenuUsuario" className="user-menu-button" type="button" aria-expanded={menuOpen} aria-haspopup="menu" onClick={() => setMenuOpen(value => !value)}>
                            <span aria-hidden="true">⌄</span>
                            <span className="sr-only">Abrir menú de usuario</span>
                        </button>
                        {menuOpen && (
                            <div className="user-menu" role="menu">
                                <button id="btnPerfil" type="button" role="menuitem"><span aria-hidden="true">◉</span> Perfil</button>
                                <button id="btnCerrarSesion" type="button" role="menuitem"><span aria-hidden="true">↪</span> Cerrar sesión</button>
                            </div>
                        )}
                    </div>
                </div>
            </header>

            <nav className="admin-tabs" aria-label="Módulos administrativos">
                <NavLink id="tabUsuarios" className={navClass} to="/admin/usuarios/crear">Usuarios</NavLink>
                <NavLink id="tabRegistroEstudiante" className={navClass} to="/admin/estudiantes/crear">Registrar estudiante</NavLink>
            </nav>

            <Outlet />
        </div>
    );
}

export default AdminLayout;
