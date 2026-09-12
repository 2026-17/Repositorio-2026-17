import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";
import Login from "./pages/Publico/login";
import CreateUsers from "./pages/AdminSis/createUsers";
import Nav from "./components/Nav";
import Footer from "./components/Footer";
import "./components/layout.css";
import Layout_Administrador from "./components/Administrador/Layout_Administrador";
import Estudiantes from "./pages/AdminSis/Estudiantes";
import Matricula from "./pages/AdminSis/Matricula";
import Pagos from "./pages/AdminSis/Pagos";
import Profesores from "./pages/AdminSis/Profesores";
import Reportes from "./pages/AdminSis/Reportes";

/* Layout fijo (header + footer) para las páginas internas del sistema.
   El Login queda fuera de este layout porque ya tiene su propio header a pantalla completa. */
function Layout() {
    return (
        <>
            <Nav />
            <main className="app-main">
                <Outlet />
            </main>
            <Footer />
        </>
    );
}

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Login />} />
                <Route path="/login" element={<Login />} />

                <Route element={<Layout />}>
                    <Route path="/admin/usuarios/crear" element={<CreateUsers />} />
                    {/* TODO: agregar aquí las demás rutas internas a medida que se creen */}
                </Route>
                <Route element={<Layout_Administrador />}>
                    <Route path="/admin/gestion-financiera/estudiantes" element={<Estudiantes />} />
                    <Route path="/admin/gestion-financiera/matricula" element={<Matricula />} />
                    <Route path="/admin/gestion-financiera/pagos" element={<Pagos />} />
                    <Route path="/admin/gestion-financiera/profesores" element={<Profesores />} />
                    <Route path="/admin/gestion-financiera/reportes" element={<Reportes />} />
                </Route>
            </Routes>
        </BrowserRouter>
    );
}

export default App;