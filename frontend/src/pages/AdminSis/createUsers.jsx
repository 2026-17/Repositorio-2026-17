import { useEffect, useState, useCallback } from "react";
import { API_URL } from "../../config/api";
import { useDialog } from "../../components/dialog/useDialog";

function CreateUsers() {

    const dialog = useDialog();
    const [roles, setRoles] = useState([]);
    const [estados, setEstados] = useState([]);
    const [form, setForm] = useState({
        cedula: "",
        nombre: "",
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
                const [rolesRes, estadosRes] = await Promise.all([
                    fetch(`${API_URL}/roles`, { headers: getHeaders() }),
                    fetch(`${API_URL}/estados-usuarios`, { headers: getHeaders() }),
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
                dialog.error(e.message);
            }
        }

        cargarCatalogos();
    }, [getHeaders, dialog]);


    async function handleSubmit(e) {
        e.preventDefault();

        const payload = {
            cedula: form.cedula,
            nombre: form.nombre,
            correo: form.correo,
            contrasena: form.contrasena,
            idRol: Number(form.idRol),
            idEstadoUsuario: Number(form.idEstadoUsuario),
        };

        let response;

        try {
            response = await fetch(`${API_URL}/usuarios`, {
                method: "POST",
                headers: getHeaders(),
                body: JSON.stringify(payload),
            });
        } catch {
            dialog.error("No se pudo conectar con el servidor.");
            return;
        }

        if (!response.ok) {
            dialog.error("No fue posible crear el usuario. Verifique los datos e intente de nuevo.");
            return;
        }

        dialog.exito("El usuario fue creado correctamente.");
        setForm({
            cedula: "",
            nombre: "",
            correo: "",
            contrasena: "",
            idRol: "",
            idEstadoUsuario: "",
        });
    }

    return (
        <div className="container">
            <h2>Crear usuario</h2>

            <form onSubmit={handleSubmit}>
                <label>Cédula</label>
                <input
                    type="text"
                    value={form.cedula}
                    onChange={(e) => setForm({...form, cedula: e.target.value})}
                    required
                />

                <br/><br/>

                <label>Nombre</label>
                <input
                    type="text"
                    value={form.nombre}
                    onChange={(e) => setForm({...form, nombre: e.target.value})}
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