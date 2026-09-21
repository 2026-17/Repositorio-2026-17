import {
    BrowserRouter,
    Routes,
    Route,
    Outlet,
} from "react-router-dom";

import Login from "./pages/Publico/login";
import Notificaciones from "./pages/Estudiante/Notificaciones";
import SeleccionCursos from "./pages/Estudiante/SeleccionCursos";
import CreateUsers from "./pages/AdminSis/createUsers";
import CreateStudent from "./pages/AdminSis/createStudent";
import Estudiantes from "./pages/AdminSis/Estudiantes";
import EditarEstudiante from "./pages/AdminSis/EditarEstudiante";

import CrearCurso from "./pages/DirectorAcademico/CrearCurso";
import ModificarCurso from "./pages/DirectorAcademico/ModificarCurso";
import DesactivarCurso from "./pages/DirectorAcademico/DesactivarCurso";
import AsignarInstrumento from "./pages/DirectorAcademico/AsignarInstrumento.jsx";
import AsignarInstrumentoAdministrativo from "./pages/Administrativa/AsignarInstrumento.jsx";
import CrearPeriodoAcademico from "./pages/DirectorAcademico/CrearPeriodoAcademico";
import ModificarPeriodoAcademico from "./pages/DirectorAcademico/ModificarPeriodoAcademico";

import BecasPagos from "./pages/Administrativa/Estudiantes.jsx";
import Matricula from "./pages/Administrativa/Matricula.jsx";
import Profesores from "./pages/DirectorAcademico/Profesores.jsx";
import Reportes from "./pages/Administrativa/Reportes.jsx";

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

                    {/* HU-030 - Modificar curso */}
                    <Route
                        path="/director-academico/cursos/modificar"
                        element={<ModificarCurso />}
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
                    {/* Administración Académica - HU-026 */}
                    <Route
                        path="/director-academico/estudiantes/instrumentos"
                        element={<AsignarInstrumento />}
                    />
                    <Route
                        path="/director-academico/profesores"
                        element={<Profesores />}
                    />

                    {/* =================================================
                        ADMINISTRATIVO
                       ================================================= */}

                    <Route
                        path="/admin/administracion/estudiantes"
                        element={<Estudiantes />}
                    />

                    <Route
                        path="/admin/administracion/estudiantes/crear"
                        element={<CreateStudent />}
                    />

                    <Route
                        path="/admin/administracion/estudiantes/:id/editar"
                        element={<EditarEstudiante />}
                    />

                    {/* Administrativo - HU-026 Asignar instrumento al estudiante */}
                    <Route
                        path="/admin/administracion/estudiantes/instrumentos"
                        element={<AsignarInstrumentoAdministrativo />}
                    />

                    {/* Administrativo - HU-015 Asignar beca y registrar pago */}
                    <Route
                        path="/admin/administracion/becas-pagos"
                        element={<BecasPagos />}
                    />


                    <Route
                        path="/admin/administracion/matricula"
                        element={<Matricula />}
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

                    {/* HU-002 - Solicitar matrícula de cursos */}
                    <Route
                        path="/estudiante/matricula"
                        element={<SeleccionCursos />}
                    />

                </Route>

            </Routes>
        </BrowserRouter>
    );
}

export default App;
