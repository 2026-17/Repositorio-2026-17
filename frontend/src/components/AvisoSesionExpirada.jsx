import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { LogIn, TriangleAlert, UserX } from "lucide-react";

import Toast from "./ui/Toast";
import {
    descartarAvisoSesion,
    obtenerEstadoSesion,
    suscribirEstadoSesion,
} from "../utils/vigilanteSesion";

import "./dialog/dialog.css";

const MENSAJE_POR_DEFECTO =
    "Por seguridad, la sesión se cierra después de una hora sin volver a iniciar sesión. "
    + "Inicie sesión de nuevo para continuar.";

// En estas pantallas no hay nada que perder: la sesión vencida se borra sin aviso.
const RUTAS_SIN_AVISO = ["/", "/login", "/recuperar-contrasena"];

/*
 * Aviso de sesión (va en el layout raíz, así sale en cualquier pantalla):
 * - a 2 minutos de vencer: un aviso flotante que no bloquea;
 * - al vencer (o si el backend responde 401): una ventana que solo se cierra
 *   con «Iniciar sesión», que lleva al login y luego de vuelta a esta pantalla.
 */
export default function AvisoSesionExpirada() {
    const estado = useSyncExternalStore(suscribirEstadoSesion, obtenerEstadoSesion);
    const { pathname, search, hash } = useLocation();
    const enPantallaPublica = RUTAS_SIN_AVISO.includes(pathname);

    const [avisoCerrado, setAvisoCerrado] = useState(null);

    // En el login (o al recuperar la contraseña) no se muestra nada.
    useEffect(() => {
        if (estado && enPantallaPublica) descartarAvisoSesion();
    }, [estado, enPantallaPublica]);

    if (!estado || enPantallaPublica) return null;

    if (estado.tipo === "por-vencer") {
        if (avisoCerrado === estado.vence) return null;
        return (
            <div className="toast-region toast-region--sesion">
                <Toast tone="warning" onDismiss={() => setAvisoCerrado(estado.vence)}>
                    Su sesión vence en 2 minutos. Guarde lo que esté haciendo.
                </Toast>
            </div>
        );
    }

    return (
        <VentanaSesionExpirada
            mensaje={estado.mensaje || MENSAJE_POR_DEFECTO}
            cuentaInactiva={estado.codigo === "CUENTA_INACTIVA"}
            volver={`${pathname}${search}${hash}`}
        />
    );
}

/*
 * cuentaInactiva: la administración desactivó la cuenta mientras se usaba.
 * Volver a ingresar no va a funcionar, así que cambia el título y el botón
 * solo lleva al login (sin ?volver=).
 */
function VentanaSesionExpirada({ mensaje, cuentaInactiva, volver }) {
    const navigate = useNavigate();
    const botonRef = useRef(null);

    // La ruta se guarda al aparecer la ventana, antes de que algo redirija.
    const [ruta] = useState(volver);

    useEffect(() => {
        botonRef.current?.focus();

        const overflowAnterior = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        // Esc no cierra y Tab no sale de la ventana (solo tiene un botón).
        function alTeclear(evento) {
            if (evento.key === "Escape" || evento.key === "Tab") {
                evento.preventDefault();
                evento.stopImmediatePropagation();
                botonRef.current?.focus();
            }
        }

        window.addEventListener("keydown", alTeclear, true);
        return () => {
            window.removeEventListener("keydown", alTeclear, true);
            document.body.style.overflow = overflowAnterior;
        };
    }, []);

    function iniciarSesion() {
        descartarAvisoSesion();
        navigate(
            cuentaInactiva ? "/login" : `/login?volver=${encodeURIComponent(ruta)}`,
            { replace: true }
        );
    }

    const Icono = cuentaInactiva ? UserX : TriangleAlert;

    return (
        <div className="dialog-backdrop dialog-backdrop--sesion">
            <div
                className={`dialog ${cuentaInactiva ? "dialog--error" : "dialog--aviso"}`}
                role="alertdialog"
                aria-modal="true"
                aria-labelledby="sesion-expirada-titulo"
                aria-describedby="sesion-expirada-mensaje"
            >
                <div className="dialog-head">
                    <span className="dialog-head-icon" aria-hidden="true">
                        <Icono size={20} strokeWidth={1.75} />
                    </span>
                    <h2 id="sesion-expirada-titulo">
                        {cuentaInactiva ? "Su cuenta está inactiva" : "Su sesión expiró"}
                    </h2>
                </div>

                <div className="dialog-body">
                    <p className="dialog-message" id="sesion-expirada-mensaje">
                        {mensaje}
                    </p>
                </div>

                <div className="dialog-foot">
                    <button
                        type="button"
                        ref={botonRef}
                        className="sg-btn sg-btn--primary"
                        onClick={iniciarSesion}
                    >
                        {cuentaInactiva ? "Aceptar" : (
                            <>
                                <LogIn size={18} strokeWidth={1.75} aria-hidden="true" />
                                Iniciar sesión
                            </>
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}
