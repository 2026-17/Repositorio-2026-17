import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";

import Login from "./pages/Publico/login";
import CreateUsers from "./pages/AdminSis/createUsers";
import CreateStudent from "./pages/AdminSis/createStudent";
import CrearCurso from "./pages/DirectorAcademico/CrearCurso";
import DesactivarCurso from "./pages/DirectorAcademico/DesactivarCurso";
import RequisitosCurso from "./pages/DirectorAcademico/RequisitosCurso";
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
            <main className="app-main"><Outlet /></main>
            <Footer />
        </div>
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

                    <Route path="/director-academico/cursos/crear" element={<CrearCurso />} />
                    <Route path="/director-academico/cursos/desactivar" element={<DesactivarCurso />} />
                    {/* Director Académico - HU-032 */}
                    <Route path="/director-academico/cursos/requisitos" element={<RequisitosCurso />} />

                    <Route path="/director-academico/periodos-academicos/crear" element={<CrearPeriodoAcademico />} />
                    <Route path="/director-academico/periodos-academicos/modificar" element={<ModificarPeriodoAcademico />} />

                    <Route path="/admin/administracion/estudiantes" element={<CreateStudent />} />
                    <Route path="/admin/administracion/matricula" element={<Matricula />} />
                    <Route path="/admin/administracion/profesores" element={<Profesores />} />
                    <Route path="/admin/administracion/reportes" element={<Reportes />} />
                </Route>
            </Routes>
        </BrowserRouter>
    );
}

export default App;
