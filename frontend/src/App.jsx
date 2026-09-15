import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";

import Login from "./pages/Publico/login";
import CreateUsers from "./pages/AdminSis/createUsers";
import CrearCurso from "./pages/DirectorAcademico/CrearCurso";
import DesactivarCurso from "./pages/DirectorAcademico/DesactivarCurso";
import Estudiantes from "./pages/AdminSis/Estudiantes";
import Matricula from "./pages/AdminSis/Matricula";
import Profesores from "./pages/AdminSis/Profesores";
import Reportes from "./pages/AdminSis/Reportes";

import Header from "./components/Header";
import Footer from "./components/Footer";
import "./components/layout.css";

// IMPORTAR CON SUS RUTAS REALES
import AdminLayout from "./ruta/real/AdminLayout";
import CreateStudent from "./ruta/real/CreateStudent";

function Layout() {
    return (
        <div className="sigac-app">
            <Header />

            <main className="app-main">
                <Outlet />
            </main>

            <Footer />
        </div>
    );
}

function App() {
    return (
        <BrowserRouter>
            <Routes>

                {/* Rutas públicas */}
                <Route path="/" element={<Login />} />
                <Route path="/login" element={<Login />} />

                {/* Administrador */}
                <Route path="/admin" element={<AdminLayout />}>
                    <Route
                        path="usuarios/crear"
                        element={<CreateUsers />}
                    />

                    <Route
                        path="estudiantes/crear"
                        element={<CreateStudent />}
                    />
                </Route>

                {/* Rutas con Layout general */}
                <Route element={<Layout />}>

                    {/* Director Académico - HU-028 */}
                    <Route
                        path="/director-academico/cursos/crear"
                        element={<CrearCurso />}
                    />

                    {/* Director Académico - HU-031 */}
                    <Route
                        path="/director-academico/cursos/desactivar"
                        element={<DesactivarCurso />}
                    />

                    {/* Administración Académica */}
                    <Route
                        path="/admin/administracion/estudiantes"
                        element={<Estudiantes />}
                    />

                    <Route
                        path="/admin/administracion/matricula"
                        element={<Matricula />}
                    />

                    <Route
                        path="/admin/administracion/profesores"
                        element={<Profesores />}
                    />

                    <Route
                        path="/admin/administracion/reportes"
                        element={<Reportes />}
                    />

                </Route>

            </Routes>
        </BrowserRouter>
    );
}

export default App;
