import { BrowserRouter, Route, Routes } from "react-router-dom";
import Login from "./pages/Public/login";
import CreateUsers from "./pages/AdminSis/createUsers";
import CreateStudent from "./pages/AdminSis/createStudent";
import AdminLayout from "./components/AdminLayout";

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Login />} />
                <Route path="/login" element={<Login />} />
                <Route path="/admin" element={<AdminLayout />}>
                    <Route path="usuarios/crear" element={<CreateUsers />} />
                    <Route path="estudiantes/crear" element={<CreateStudent />} />
                </Route>
            </Routes>
        </BrowserRouter>
    );
}

export default App;
