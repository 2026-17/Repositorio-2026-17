import { Link } from "react-router-dom";
import acusabIcon from "../assets/acusabIcon.png";

function Nav() {
    const token = localStorage.getItem("token");
    const rol = localStorage.getItem("rol");
    const cedula = localStorage.getItem("cedula");

    function cerrarSesion() {
        localStorage.removeItem("token");
        localStorage.removeItem("rol");
        localStorage.removeItem("cedula");
        window.location.href = "/";
    }

    return (
        <nav className="nav">
            <div className="nav__inner container">
                <Link to="/" className="nav__logo">
                    <img src={acusabIcon} className="nav__logo_img" alt="Logo ACUSAB" />
                    SIGAC
                </Link>

                <ul className="nav__links" role="list">
                    {/* Admin */}
                    {rol === "ADMINISTRADOR" && <>
                        <li><Link to="/admin/usuarios/crear" className="nav__link">Crear usuario</Link></li>
                        {/* TODO: agregar link a Dashboard cuando exista la página */}
                    </>}

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

                <div className="nav__actions">
                    {!token && <Link to="/login" className="btn btn--primary">Iniciar sesión</Link>}
                    {token && (
                        <>
                            <span className="nav__user">{cedula}</span>
                            <button className="btn btn--primary" onClick={cerrarSesion}>Cerrar sesión</button>
                        </>
                    )}
                </div>
            </div>
        </nav>
    );
}

export default Nav;
