import { BrowserRouter, Routes, Route, Link } from "react-router-dom";
import Login from "./pages/Publico/login";
import CreateUsers from "./pages/AdminSis/createUsers";

function App() {
    return (
        <BrowserRouter>
            <nav>
                <Link to="/login">Login</Link> |{" "}
                <Link to="/admin/usuarios/crear">Crear usuario</Link>
            </nav>

            <Routes>
                <Route path="/" element={<Login />} />
                <Route path="/login" element={<Login />} />
                <Route path="/admin/usuarios/crear" element={<CreateUsers />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;