import { Outlet } from "react-router-dom";
import Header_Administrador from "./Header_Administrador";
import Footer from "../Footer";
import "../../pages/AdminSis/styles/admin_Sis.css";

/* Layout compartido por las páginas del módulo "Gestión Financiera"
   (Estudiantes, Matricula, Pagos, Profesores, Reportes).
   Header con menú de usuario + subnavegación arriba, Footer institucional abajo. */
function Layout_Administrador() {
    return (
        <div className="sigac-app">
            <Header_Administrador />
            <Outlet />
            <Footer />
        </div>
    );
}

export default Layout_Administrador;