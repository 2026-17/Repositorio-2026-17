import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";

import Login from "./pages/Publico/login";
import CreateUsers from "./pages/AdminSis/createUsers";
import CrearCurso from "./pages/DirectorAcademico/CrearCurso";

import Nav from "./components/Nav";
import Footer from "./components/Footer";

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


                {/* Rutas internas */}
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

                </Route>

            </Routes>

        </BrowserRouter>
    );
}

export default App;