import { useState } from "react";

function Login() {
    const backend = "http://localhost:8080";

    const [form, setForm] = useState({
        cedula: "",
        contrasena: "",
    });
    const [error, setError] = useState("");
    const [cargando, setCargando] = useState(false);

    async function handleSubmit(e) {
        e.preventDefault();
        setError("");
        setCargando(true);

        try {
            const response = await fetch(`${backend}/auth/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(form),
            });

            if (!response.ok) {
                setError("Credenciales inválidas");
                return;
            }

            const data = await response.json();
            localStorage.setItem("token", data.token || "");
            localStorage.setItem("rol", data.rol || "");
            alert("Login exitoso");
        } catch {
            setError("No se pudo conectar con el servidor");
        } finally {
            setCargando(false);
        }
    }

    return (
        <main>
            <h2>Iniciar sesión</h2>

            <form onSubmit={handleSubmit}>

                <p>Hola</p>

                <label>Cédula</label>
                <input
                    type="text"
                    value={form.cedula}
                    onChange={(e) => setForm({...form, cedula: e.target.value})}
                    required
                />

                <br/><br/>

                <label>Contraseña</label>
                <input
                    type="password"
                    value={form.contrasena}
                    onChange={(e) => setForm({...form, contrasena: e.target.value})}
                    required
                />

                <br/><br/>

                <button type="submit" disabled={cargando}>
                    {cargando ? "Ingresando..." : "Ingresar"}
                </button>
            </form>

            {error && <p>{error}</p>}
        </main>
    );
}

export default Login;