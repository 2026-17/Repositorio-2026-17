import { BrowserRouter, Routes, Route, Link } from "react-router-dom";
import Login from "./pages/Public/login";
import CreateUsers from "./pages/AdminSis/createUsers";
import CreateStudent from "./pages/AdminSis/createStudent";

function App() {
    return (
        <BrowserRouter>
            <nav>
                <Link to="/login">Login</Link> |{" "}
                <Link to="/admin/usuarios/crear">Crear usuario</Link> |{" "}
                <Link to="/admin/estudiantes/crear">Registrar estudiante</Link>
            </nav>

            <Routes>
                <Route path="/" element={<Login />} />
                <Route path="/login" element={<Login />} />
                <Route path="/admin/usuarios/crear" element={<CreateUsers />} />
                <Route path="/admin/estudiantes/crear" element={<CreateStudent />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
