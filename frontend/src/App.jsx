import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";

import Login from "./pages/Publico/login";
import CreateUsers from "./pages/AdminSis/createUsers";
import CreateStudent from "./pages/AdminSis/createStudent";
import CrearCurso from "./pages/DirectorAcademico/CrearCurso";
import DesactivarCurso from "./pages/DirectorAcademico/DesactivarCurso";
import CrearPeriodoAcademico from "./pages/DirectorAcademico/CrearPeriodoAcademico";
import ModificarPeriodoAcademico from "./pages/DirectorAcademico/ModificarPeriodoAcademico";
import Matricula from "./pages/AdminSis/Matricula";
import Profesores from "./pages/AdminSis/Profesores";
import Reportes from "./pages/AdminSis/Reportes";

import Header from "./components/Header";
import Footer from "./components/Footer";
import "./components/layout.css";

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

                {/* Rutas con Layout general (header global) */}
                <Route element={<Layout />}>

                    {/* Administrador */}
                    <Route
                        path="/admin/usuarios/crear"
                        element={<CreateUsers />}
                    />

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

                    {/* Director Académico - Crear período académico */}
                    <Route
                        path="/director-academico/periodos-academicos/crear"
                        element={<CrearPeriodoAcademico />}
                    />

                    {/* Director Académico - Consultar/modificar período académico */}
                    <Route
                        path="/director-academico/periodos-academicos/modificar"
                        element={<ModificarPeriodoAcademico />}
                    />

                    {/* Administración Académica */}
                    <Route
                        path="/admin/administracion/estudiantes"
                        element={<CreateStudent />}
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
