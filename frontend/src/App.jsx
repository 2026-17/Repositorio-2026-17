import { useEffect } from "react";
import {
    createBrowserRouter,
    createRoutesFromElements,
    Navigate,
    Outlet,
    Route,
    RouterProvider,
    useLocation,
} from "react-router-dom";

import Login from "./pages/Publico/login";
import RecuperarContrasena from "./pages/Publico/RecuperarContrasena";
import CambiarContrasenaTemporal from "./pages/Publico/CambiarContrasenaTemporal";
import Notificaciones from "./pages/Estudiante/Notificaciones";
import ConsultaCursos from "./pages/Estudiante/ConsultaCursos";
import MiHorario from "./pages/Estudiante/MiHorario";
import SeleccionCursos from "./pages/Estudiante/SeleccionCursos";
import CreateUsers from "./pages/AdminSis/createUsers";
import Estudiantes from "./pages/AdminSis/Estudiantes";

import Cursos from "./pages/DirectorAcademico/Cursos";
import CrearGrupo from "./pages/DirectorAcademico/CrearGrupo";
import AsignarProfesores from "./pages/DirectorAcademico/AsignarProfesores";
import AsignarInstrumento from "./pages/Comun/AsignarInstrumento.jsx";
import PeriodosAcademicos from "./pages/DirectorAcademico/PeriodosAcademicos";

import BecasPagos from "./pages/Administrativa/BecasPagos.jsx";
import Matricula from "./pages/Administrativa/Matricula.jsx";
import Profesores from "./pages/DirectorAcademico/Profesores.jsx";
import Reportes from "./pages/Administrativa/Reportes.jsx";

import Inicio from "./pages/Inicio/Inicio";
import NoEncontrada from "./pages/Publico/NoEncontrada";

import Header from "./components/Header";
import { useDialog } from "./components/dialog/useDialog";
import { RUTA_INICIO, tieneAcceso } from "./constants/menu";
import { cambioContrasenaPendiente, haySesion, RUTA_CAMBIO_CONTRASENA_TEMPORAL } from "./utils/sesion";

function Layout() {
    // Sin sesión no se entra al sistema
    if (!haySesion()) {
        return <Navigate to="/login" replace />;
    }

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

// Avisa que la sección no es del rol y lleva al inicio.
function SinPermiso() {
    const dialog = useDialog();

    useEffect(() => {
        dialog.aviso("No tiene permisos para acceder a esa sección.", { titulo: "Acceso restringido" });
    }, [dialog]);

    return <Navigate to={RUTA_INICIO} replace />;
}

/*
 * Guardia por rol: los permisos salen de MENU_POR_ROL (tieneAcceso), así un
 * rol solo abre las pantallas de su propio menú.
 */
function GuardiaRol() {
    const { pathname } = useLocation();
    const rol = localStorage.getItem("rol");

    if (!tieneAcceso(rol, pathname)) {
        return <SinPermiso />;
    }

    return <Outlet />;
}

const router = createBrowserRouter(
    createRoutesFromElements(
            <>

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

                    {/* Inicio de todos los roles: explica las opciones del menú (botón "Ayuda") */}
                    <Route
                        path={RUTA_INICIO}
                        element={<Inicio />}
                    />

                    {/* El resto de pantallas: solo los roles que las tienen en su menú */}
                    <Route element={<GuardiaRol />}>

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

                        {/* HU-028, HU-030, HU-031 - Crear, modificar y desactivar cursos */}
                        <Route
                            path="/director-academico/cursos"
                            element={<Cursos />}
                        />

                        {/* Crear, modificar y desactivar se hacen dentro de Cursos; las rutas viejas llevan ahí */}
                        {["crear", "modificar", "desactivar"].map((accion) => (
                            <Route
                                key={accion}
                                path={`/director-academico/cursos/${accion}`}
                                element={<Navigate to="/director-academico/cursos" replace />}
                            />
                        ))}

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

                        {/* Crear, consultar y modificar períodos académicos */}
                        <Route
                            path="/director-academico/periodos-academicos"
                            element={<PeriodosAcademicos />}
                        />

                        {/* Crear y modificar se hacen dentro de Períodos académicos; las rutas viejas llevan ahí */}
                        {["crear", "modificar"].map((accion) => (
                            <Route
                                key={accion}
                                path={`/director-academico/periodos-academicos/${accion}`}
                                element={<Navigate to="/director-academico/periodos-academicos" replace />}
                            />
                        ))}
                        {/* Administración Académica - HU-026 */}
                        <Route
                            path="/director-academico/estudiantes/instrumentos"
                            element={<AsignarInstrumento rutaCatalogo="/instrumentos" conProfesor coincidenciaExacta />}
                        />
                        <Route
                            path="/director-academico/profesores"
                            element={<Profesores />}
                        />
                        {/* Registrar se hace dentro de Profesores; la ruta vieja lleva ahí */}
                        <Route
                            path="/director-academico/profesores/registrar"
                            element={<Navigate to="/director-academico/profesores" replace />}
                        />

                        {/* =================================================
                            ADMINISTRATIVO
                           ================================================= */}

                        <Route
                            path="/admin/administracion/estudiantes"
                            element={<Estudiantes />}
                        />

                        {/* Registrar y editar se hacen dentro de Estudiantes; las rutas viejas llevan ahí */}
                        <Route
                            path="/admin/administracion/estudiantes/crear"
                            element={<Navigate to="/admin/administracion/estudiantes" replace />}
                        />

                        <Route
                            path="/admin/administracion/estudiantes/:id/editar"
                            element={<Navigate to="/admin/administracion/estudiantes" replace />}
                        />

                        {/* Administrativo - HU-026 Asignar instrumento al estudiante */}
                        <Route
                            path="/admin/administracion/estudiantes/instrumentos"
                            element={<AsignarInstrumento rutaCatalogo="/registro/catalogos/instrumentos" />}
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

                </Route>

                {/* Cualquier otra dirección */}
                <Route
                    path="*"
                    element={<NoEncontrada />}
                />
            </>
    )
);

function App() {
    return <RouterProvider router={router} />;
}

export default App;
