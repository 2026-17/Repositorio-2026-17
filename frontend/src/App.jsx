import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";

import Login from "./pages/Publico/login";
import CreateUsers from "./pages/AdminSis/createUsers";
import CrearCurso from "./pages/DirectorAcademico/CrearCurso";
import DesactivarCurso from "./pages/DirectorAcademico/DesactivarCurso";

import Nav from "./components/Nav";
import Footer from "./components/Footer";

import Layout_Administrador from "./components/Administrador/Layout_Administrador";
import Estudiantes from "./pages/AdminSis/Estudiantes";
import Matricula from "./pages/AdminSis/Matricula";
import Profesores from "./pages/AdminSis/Profesores";
import Reportes from "./pages/AdminSis/Reportes";

import "./components/layout.css";


/*
 * Layout general de las páginas internas del sistema.
 * El Login queda fuera porque utiliza su propio diseño.
 */
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

                {/* Rutas públicas */}
                <Route
                    path="/"
                    element={<Login />}
                />

                <Route
                    path="/login"
                    element={<Login />}
                />


                {/* Rutas internas generales */}
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

                </Route>


                {/* Nuevas rutas administrativas provenientes de main */}
                <Route element={<Layout_Administrador />}>
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