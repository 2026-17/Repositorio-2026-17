import {
    BrowserRouter,
    Routes,
    Route,
    Outlet,
} from "react-router-dom";

import Login from "./pages/Publico/login";

import CreateUsers from "./pages/AdminSis/createUsers";
import CreateStudent from "./pages/AdminSis/createStudent";
import Matricula from "./pages/AdminSis/Matricula";
import Profesores from "./pages/AdminSis/Profesores";
import Reportes from "./pages/AdminSis/Reportes";

import CrearCurso from "./pages/DirectorAcademico/CrearCurso";
import DesactivarCurso from "./pages/DirectorAcademico/DesactivarCurso";
import CrearPeriodoAcademico from "./pages/DirectorAcademico/CrearPeriodoAcademico";
import ModificarPeriodoAcademico from "./pages/DirectorAcademico/ModificarPeriodoAcademico";

import Notificaciones from "./pages/Estudiante/Notificaciones";

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

                {/* =====================================================
                    Rutas públicas
                   ===================================================== */}
                <Route
                    path="/"
                    element={<Login />}
                />

                <Route
                    path="/login"
                    element={<Login />}
                />

                {/* =====================================================
                    Rutas internas con layout general
                   ===================================================== */}
                <Route element={<Layout />}>

                    {/* =================================================
                        ADMINISTRADOR
                       ================================================= */}
                    <Route
                        path="/admin/usuarios/crear"
                        element={<CreateUsers />}
                    />

                    {/* =================================================
                        DIRECTOR ACADÉMICO
                       ================================================= */}

                    {/* HU-028 - Crear curso */}
                    <Route
                        path="/director-academico/cursos/crear"
                        element={<CrearCurso />}
                    />

                    {/* HU-031 - Desactivar curso */}
                    <Route
                        path="/director-academico/cursos/desactivar"
                        element={<DesactivarCurso />}
                    />

                    {/* Crear período académico */}
                    <Route
                        path="/director-academico/periodos-academicos/crear"
                        element={<CrearPeriodoAcademico />}
                    />

                    {/* Consultar / modificar período académico */}
                    <Route
                        path="/director-academico/periodos-academicos/modificar"
                        element={<ModificarPeriodoAcademico />}
                    />

                    {/* =================================================
                        ADMINISTRATIVO
                       ================================================= */}

                    <Route
                        path="/admin/administracion/estudiantes"
                        element={<CreateStudent />}
                    />

                    {/* HU-012 - Confirmar matrícula */}
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

                    {/* =================================================
                        ESTUDIANTE
                       ================================================= */}

                    {/* HU-012 - Notificaciones de matrícula */}
                    <Route
                        path="/estudiante/notificaciones"
                        element={<Notificaciones />}
                    />

                </Route>

            </Routes>
        </BrowserRouter>
    );
}

export default App;