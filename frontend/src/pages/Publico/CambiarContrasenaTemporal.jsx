import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { LogOut } from "lucide-react";

import { API_URL } from "../../config/api";
import Alert from "../../components/ui/Alert";
import PasswordField from "../../components/ui/PasswordField";
import RequisitosContrasena from "../../components/ui/RequisitosContrasena";
import { LONGITUD_MAXIMA_CONTRASENA, revisarContrasena } from "../../components/ui/politicaContrasena";
import {
    cambioContrasenaPendiente,
    guardarSesion,
    limpiarSesion,
    RUTAS_POR_ROL,
} from "../../utils/sesion";
import AuthLayout from "./AuthLayout";

import "./publico.css";

/*
 * Primer inicio de sesión: la cuenta se creó con una contraseña temporal
 * enviada por correo y el usuario debe cambiarla antes de usar SIGAC.
 * El token de esta sesión solo sirve para este cambio (ver
 * JwtAuthenticationFilter); al guardar, el backend devuelve uno normal.
 */
function CambiarContrasenaTemporal() {
    const navigate = useNavigate();

    const [actual, setActual] = useState("");
    const [contrasena, setContrasena] = useState("");
    const [confirmacion, setConfirmacion] = useState("");
    const [error, setError] = useState("");
    const [cargando, setCargando] = useState(false);

    const token = localStorage.getItem("token");
    const nombre = localStorage.getItem("nombre");

    const requisitos = revisarContrasena(contrasena);
    const contrasenaValida = requisitos.every((r) => r.ok);
    const coinciden = confirmacion.length > 0 && contrasena === confirmacion;

    // Sin sesión: a ingresar. Sin cambio pendiente: a su página de inicio.
    if (!token) return <Navigate to="/login" replace />;
    if (!cambioContrasenaPendiente()) {
        return <Navigate to={RUTAS_POR_ROL[localStorage.getItem("rol")] || "/login"} replace />;
    }

    function volverALogin() {
        limpiarSesion();
        navigate("/login", { replace: true });
    }

    async function salir() {
        try {
            await fetch(`${API_URL}/auth/logout`, {
                method: "POST",
                headers: { Authorization: `Bearer ${token}` },
            });
        } catch {
            // La sesión local se elimina de todos modos
        }
        volverALogin();
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (!actual) {
            setError("Escriba la contraseña temporal que recibió por correo.");
            return;
        }
        setError("");
        setCargando(true);
        try {
            const response = await fetch(`${API_URL}/auth/contrasena-temporal/cambiar`, {
                method: "POST",
                headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
                body: JSON.stringify({ contrasenaActual: actual, contrasenaNueva: contrasena }),
            });

            // La sesión venció: hay que ingresar de nuevo con la contraseña temporal
            if (response.status === 401 || response.status === 403) {
                volverALogin();
                return;
            }

            const data = await response.json().catch(() => null);
            if (!response.ok) {
                setError(data?.message || "No se pudo cambiar la contraseña. Intente de nuevo.");
                return;
            }

            guardarSesion(data, localStorage.getItem("cedula") || "");
            navigate(RUTAS_POR_ROL[data.rol] || "/login", { replace: true });
        } catch {
            setError("No se pudo conectar con el servidor. Intente de nuevo en unos momentos.");
        } finally {
            setCargando(false);
        }
    }

    return (
        <AuthLayout>
            <h1>Cree su contraseña</h1>
            <p className="lead">
                {nombre ? `Hola, ${nombre}. ` : ""}
                Ingresó con la contraseña temporal que le enviamos por correo. Antes de continuar,
                cámbiela por una contraseña personal que solo usted conozca.
            </p>

            <form onSubmit={handleSubmit} className="auth-form" noValidate>
                {error && <Alert tone="danger" title={error} autoFocus />}

                <PasswordField
                    id="contrasenaTemporal"
                    label="Contraseña temporal"
                    hint="La que recibió en el correo de bienvenida."
                    value={actual}
                    onChange={(e) => { setActual(e.target.value); setError(""); }}
                    autoComplete="current-password"
                />

                <PasswordField
                    id="nuevaContrasena"
                    label="Contraseña nueva"
                    value={contrasena}
                    onChange={(e) => setContrasena(e.target.value.slice(0, LONGITUD_MAXIMA_CONTRASENA))}
                    autoComplete="new-password"
                    describedBy="requisitos-contrasena"
                />

                <RequisitosContrasena id="requisitos-contrasena" requisitos={requisitos} />

                <PasswordField
                    id="confirmarContrasena"
                    label="Confirme la contraseña nueva"
                    value={confirmacion}
                    onChange={(e) => setConfirmacion(e.target.value.slice(0, LONGITUD_MAXIMA_CONTRASENA))}
                    autoComplete="new-password"
                    error={confirmacion.length > 0 && !coinciden ? "Las contraseñas no coinciden." : ""}
                />

                <button
                    type="submit"
                    className={`sg-btn sg-btn--primary sg-btn--lg auth-submit${cargando ? " is-loading" : ""}`}
                    disabled={cargando || !contrasenaValida || !coinciden}
                    aria-busy={cargando || undefined}
                >
                    {cargando && <span className="sg-spinner" aria-hidden="true" />}
                    {cargando ? "Guardando…" : "Guardar y continuar"}
                </button>

                <button type="button" className="link-btn auth-alt" onClick={salir}>
                    <LogOut size={16} strokeWidth={1.75} aria-hidden="true" />
                    Cerrar sesión
                </button>
            </form>
        </AuthLayout>
    );
}

export default CambiarContrasenaTemporal;
