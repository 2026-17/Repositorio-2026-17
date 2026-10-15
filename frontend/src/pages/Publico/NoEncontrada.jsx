import { Link } from "react-router-dom";
import { ArrowLeft, MapPinOff } from "lucide-react";

import Header from "../../components/Header";
import EmptyState from "../../components/ui/EmptyState";
import { RUTA_INICIO } from "../../constants/menu";
import { haySesion } from "../../utils/sesion";
import AuthLayout from "./AuthLayout";

import "./publico.css";

/*
 * Ruta inexistente. Con sesión se muestra dentro del layout interno y lleva
 * al inicio; sin sesión, en el layout público y lleva al inicio de sesión.
 */
function NoEncontrada() {
    const conSesion = haySesion();

    const contenido = (
        <EmptyState
            icon={MapPinOff}
            title="Página no encontrada"
            description="La dirección que intentó abrir no existe o fue cambiada. Revise el enlace o vuelva a una pantalla conocida."
            action={(
                <Link to={conSesion ? RUTA_INICIO : "/login"} className="sg-btn sg-btn--primary">
                    <ArrowLeft size={18} strokeWidth={1.75} aria-hidden="true" />
                    {conSesion ? "Volver al inicio" : "Ir a iniciar sesión"}
                </Link>
            )}
        />
    );

    if (conSesion) {
        return (
            <Header>
                <h1 className="sr-only" id="page-title" tabIndex={-1}>Página no encontrada</h1>
                <div className="panel">{contenido}</div>
            </Header>
        );
    }

    return (
        <AuthLayout>
            <h1 className="sr-only" id="page-title" tabIndex={-1}>Página no encontrada</h1>
            {contenido}
        </AuthLayout>
    );
}

export default NoEncontrada;
