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

import CrearCurso from "./pages/DirectorAcademico/CrearCurso";
import ModificarCurso from "./pages/DirectorAcademico/ModificarCurso";
import DesactivarCurso from "./pages/DirectorAcademico/DesactivarCurso";
import CrearGrupo from "./pages/DirectorAcademico/CrearGrupo";
import AsignarProfesores from "./pages/DirectorAcademico/AsignarProfesores";
import AsignarInstrumento from "./pages/Comun/AsignarInstrumento.jsx";
import CrearPeriodoAcademico from "./pages/DirectorAcademico/CrearPeriodoAcademico";
import ModificarPeriodoAcademico from "./pages/DirectorAcademico/ModificarPeriodoAcademico";

import BecasPagos from "./pages/Administrativa/BecasPagos.jsx";
import Matricula from "./pages/Administrativa/Matricula.jsx";
import Profesores from "./pages/DirectorAcademico/Profesores.jsx";
import RegistrarProfesor from "./pages/DirectorAcademico/RegistrarProfesor.jsx";
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
                            element={<AsignarInstrumento rutaCatalogo="/instrumentos" conProfesor coincidenciaExacta />}
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
