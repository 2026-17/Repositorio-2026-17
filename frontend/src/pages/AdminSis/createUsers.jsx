import { useEffect, useState, useCallback } from "react";

function CreateUsers() {
    const backend = "http://localhost:8080";

    const [roles, setRoles] = useState([]);
    const [estados, setEstados] = useState([]);
    const [error, setError] = useState("");
    const [form, setForm] = useState({
        cedula: "",
        correo: "",
        contrasena: "",
        idRol: "",
        idEstadoUsuario: "",
    });

    const getHeaders = useCallback(() => {
        const token = localStorage.getItem("token");
        return token
            ? { "Content-Type": "application/json", Authorization: `Bearer ${token}` }
            : { "Content-Type": "application/json" };
    }, []);

    useEffect(() => {
        async function cargarCatalogos() {
            try {
                setError("");
                const [rolesRes, estadosRes] = await Promise.all([
                    fetch(`${backend}/roles`, { headers: getHeaders() }),
                    fetch(`${backend}/estados-usuarios`, { headers: getHeaders() }),
                ]);

                if (!rolesRes.ok || !estadosRes.ok) {
                    throw new Error("No se pudieron cargar los catálogos");
                }

                const [rolesData, estadosData] = await Promise.all([
                    rolesRes.json(),
                    estadosRes.json(),
                ]);

                setRoles(rolesData);
                setEstados(estadosData);
            } catch (e) {
                setError(e.message);
            }
        }

        cargarCatalogos();
    }, [backend, getHeaders]);


    async function handleSubmit(e) {
        e.preventDefault();

        const payload = {
            cedula: form.cedula,
            correo: form.correo,
            contrasena: form.contrasena,
            idRol: Number(form.idRol),
            idEstadoUsuario: Number(form.idEstadoUsuario),
        };

        const response = await fetch("http://localhost:8080/usuarios", {
            method: "POST",
            headers: getHeaders(),
            body: JSON.stringify(payload),
        });

        if (!response.ok) {
            alert("Error al crear usuario");
            return;
        }

        alert("Usuario creado");
        setForm({
            cedula: "",
            correo: "",
            contrasena: "",
            idRol: "",
            idEstadoUsuario: "",
        });
    }

    return (
        <div className="container">
            <h2>Crear usuario</h2>
            {error && <p>{error}</p>}

            <form onSubmit={handleSubmit}>
                <label>Cédula</label>
                <input
                    type="text"
                    value={form.cedula}
                    onChange={(e) => setForm({...form, cedula: e.target.value})}
                    required
                />

                <br/><br/>

                <label>Correo</label>
                <input
                    type="email"
                    value={form.correo}
                    onChange={(e) => setForm({...form, correo: e.target.value})}
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

                <label>Rol</label>
                <select
                    value={form.idRol}
                    onChange={(e) => setForm({...form, idRol: e.target.value})}
                    required
                >
                    <option value="">Seleccione un rol</option>
                    {roles.map((rol) => (
                        <option key={rol.id} value={rol.id}>
                            {rol.nombre}
                        </option>
                    ))}
                </select>

                <br/><br/>

                <label>Estado de usuario</label>
                <select
                    value={form.idEstadoUsuario}
                    onChange={(e) => setForm({...form, idEstadoUsuario: e.target.value})}
                    required
                >
                    <option value="">Seleccione un estado</option>
                    {estados.map((estado) => (
                        <option key={estado.id} value={estado.id}>
                            {estado.nombre}
                        </option>
                    ))}
                </select>

                <br/><br/>

                <button type="submit">Crear usuario</button>
            </form>
        </div>
    );
}

export default CreateUsers;