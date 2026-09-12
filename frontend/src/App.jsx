import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";
import Login from "./pages/Publico/login";
import CreateUsers from "./pages/AdminSis/createUsers";
import Nav from "./components/Nav";
import Footer from "./components/Footer";
import "./components/layout.css";

/* Layout fijo (header + footer) para las páginas internas del sistema.
   El Login queda fuera de este layout porque ya tiene su propio header a pantalla completa. */
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
                <Route path="/" element={<Login />} />
                <Route path="/login" element={<Login />} />

                <Route element={<Layout />}>
                    <Route path="/admin/usuarios/crear" element={<CreateUsers />} />
                    {/* T-ODO: agregar aquí las demás rutas internas a medida que se creen */}
                </Route>
            </Routes>
        </BrowserRouter>
    );
}

export default App;