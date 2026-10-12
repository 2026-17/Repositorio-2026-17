import {
    BrowserRouter,
    Routes,
    Route,
    Navigate,
    Outlet,
} from "react-router-dom";

import Login from "./pages/Publico/login";
import RecuperarContrasena from "./pages/Publico/RecuperarContrasena";
import CambiarContrasenaTemporal from "./pages/Publico/CambiarContrasenaTemporal";
import Notificaciones from "./pages/Estudiante/Notificaciones";
import ConsultaCursos from "./pages/Estudiante/ConsultaCursos";
import MiHorario from "./pages/Estudiante/MiHorario";
import SeleccionCursos from "./pages/Estudiante/SeleccionCursos";
import CreateUsers from "./pages/AdminSis/createUsers";
import CreateStudent from "./pages/AdminSis/createStudent";
import Estudiantes from "./pages/AdminSis/Estudiantes";
import EditarEstudiante from "./pages/AdminSis/EditarEstudiante";

import CrearCurso from "./pages/DirectorAcademico/CrearCurso";
import ModificarCurso from "./pages/DirectorAcademico/ModificarCurso";
import DesactivarCurso from "./pages/DirectorAcademico/DesactivarCurso";
import CrearGrupo from "./pages/DirectorAcademico/CrearGrupo";
import AsignarProfesores from "./pages/DirectorAcademico/AsignarProfesores";
import AsignarInstrumento from "./pages/DirectorAcademico/AsignarInstrumento.jsx";
import AsignarInstrumentoAdministrativo from "./pages/Administrativa/AsignarInstrumento.jsx";
import CrearPeriodoAcademico from "./pages/DirectorAcademico/CrearPeriodoAcademico";
import ModificarPeriodoAcademico from "./pages/DirectorAcademico/ModificarPeriodoAcademico";

import BecasPagos from "./pages/Administrativa/Estudiantes.jsx";
import Matricula from "./pages/Administrativa/Matricula.jsx";
import Profesores from "./pages/DirectorAcademico/Profesores.jsx";
import RegistrarProfesor from "./pages/DirectorAcademico/RegistrarProfesor.jsx";
import Reportes from "./pages/Administrativa/Reportes.jsx";

import Header from "./components/Header";
import { cambioContrasenaPendiente, RUTA_CAMBIO_CONTRASENA_TEMPORAL } from "./utils/sesion";

function Layout() {
    // Con la contraseña temporal no se entra al sistema hasta cambiarla
    if (cambioContrasenaPendiente()) {
        return <Navigate to={RUTA_CAMBIO_CONTRASENA_TEMPORAL} replace />;
    }

    return (
        <Header>
            <Outlet />
        </Header>
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

                <Route
                    path="/recuperar-contrasena"
                    element={<RecuperarContrasena />}
                />

                {/* Primer inicio de sesión: cambio de la contraseña temporal */}
                <Route
                    path={RUTA_CAMBIO_CONTRASENA_TEMPORAL}
                    element={<CambiarContrasenaTemporal />}
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

                    {/* Crear grupo de un curso en un período */}
                    <Route
                        path="/director-academico/grupos/crear"
                        element={<CrearGrupo />}
                    />

                    {/* HU-014 - Asignar profesores a cursos y grupos */}
                    <Route
                        path="/director-academico/asignar-profesores"
                        element={<AsignarProfesores />}
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
                    <Route
                        path="/director-academico/profesores/registrar"
                        element={<RegistrarProfesor />}
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

                    {/* HU-10 - Consultar cursos disponibles de un período académico */}
                    <Route
                        path="/estudiante/cursos-disponibles"
                        element={<ConsultaCursos />}
                    />

                    {/* Horario semanal del estudiante */}
                    <Route
                        path="/estudiante/horario"
                        element={<MiHorario />}
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
