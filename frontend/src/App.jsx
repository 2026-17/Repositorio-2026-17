// import { BrowserRouter, Routes, Route, Link } from "react-router-dom";
import Login from "./pages/Publico/login";
// import CreateUsers from "./pages/AdminSis/createUsers";

function App() {
    // De momento se muestra únicamente el Login. Se comenta el enrutamiento
    // y la navegación hasta que se habiliten las demás vistas.
    return <Login />;

    // return (
    //     <BrowserRouter>
    //         <nav>
    //             <Link to="/login">Login</Link> |{" "}
    //             <Link to="/admin/usuarios/crear">Crear usuario</Link>
    //         </nav>

    //         <Routes>
    //             <Route path="/" element={<Login />} />
    //             <Route path="/login" element={<Login />} />
    //             <Route path="/admin/usuarios/crear" element={<CreateUsers />} />
    //         </Routes>
    //     </BrowserRouter>
    // );
}

export default App;