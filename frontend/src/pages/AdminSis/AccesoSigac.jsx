import { useState } from "react";
import { Copy, KeyRound } from "lucide-react";

import Alert from "../../components/ui/Alert";
import { useDialog } from "../../components/dialog/useDialog";
import { API_URL } from "../../config/api";
import { lanzarError, mensajeDeExcepcion } from "../../utils/api";

import "./styles/accesoSigac.css";

/*
 * «Acceso a SIGAC» en Editar estudiante: cuando el código de «Olvidé mi
 * contraseña» no le llega al estudiante (por ejemplo, cae en Correo no
 * deseado), la administración genera una contraseña temporal y se la entrega
 * en persona o por WhatsApp. No se envía por correo.
 *
 * La contraseña vive solo en el estado de este componente: se muestra una
 * vez y, al salir de la pantalla o recargar, ya no se ve.
 */
export default function AccesoSigac({ idEstudiante, nombre, activo }) {
    const dialog = useDialog();
    const [generando, setGenerando] = useState(false);
    const [contrasena, setContrasena] = useState(null);

    async function generar() {
        const confirmado = await dialog.confirmar(
            `Se generará una contraseña temporal para ${nombre}. La contraseña actual dejará de funcionar. `
            + "Entréguesela personalmente o por WhatsApp; al entrar se le pedirá cambiarla.",
            { titulo: "Generar contraseña temporal", textoAceptar: "Generar contraseña" }
        );
        if (!confirmado) return;

        setGenerando(true);
        setContrasena(null);
        try {
            const token = localStorage.getItem("token");
            const respuesta = await fetch(`${API_URL}/estudiantes/${idEstudiante}/contrasena-temporal`, {
                method: "POST",
                headers: token ? { Authorization: `Bearer ${token}` } : {},
                cache: "no-store",
            });
            if (!respuesta.ok) await lanzarError(respuesta, "No fue posible generar la contraseña temporal.");
            const datos = await respuesta.json();
            setContrasena(datos.contrasenaTemporal);
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No fue posible generar la contraseña temporal."));
        } finally {
            setGenerando(false);
        }
    }

    async function copiar() {
        try {
            await navigator.clipboard.writeText(contrasena);
            dialog.exito("Contraseña copiada.");
        } catch {
            dialog.aviso("No se pudo copiar. Seleccione la contraseña y cópiela a mano.");
        }
    }

    return (
        <section className="panel" id="seccion-acceso" aria-labelledby="titulo-acceso">
            <div className="panel-head"><h2 id="titulo-acceso">Acceso a SIGAC</h2></div>
            <div className="panel-body acceso-sigac">
                <p className="muted">
                    Si al estudiante no le llega el correo para recuperar su contraseña, genere una
                    contraseña temporal y entréguesela personalmente o por WhatsApp.
                </p>

                {contrasena && (
                    <Alert tone="success" title="Contraseña temporal generada">
                        <p
                            className="acceso-sigac__contrasena"
                            aria-label={`Contraseña temporal: ${contrasena.split("").join(" ")}`}
                        >
                            {contrasena}
                        </p>
                        <p>Esta contraseña no se volverá a mostrar. Al iniciar sesión, el estudiante deberá cambiarla.</p>
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={copiar}>
                            <Copy size={18} strokeWidth={1.75} aria-hidden="true" />
                            Copiar
                        </button>
                    </Alert>
                )}

                <div className="inline-actions">
                    <button
                        type="button"
                        className={`sg-btn sg-btn--secondary${generando ? " is-loading" : ""}`}
                        onClick={generar}
                        disabled={generando || !activo}
                        aria-busy={generando || undefined}
                    >
                        {generando
                            ? <span className="sg-spinner" aria-hidden="true" />
                            : <KeyRound size={18} strokeWidth={1.75} aria-hidden="true" />}
                        {generando ? "Generando…" : "Generar contraseña temporal"}
                    </button>
                    {!activo && <span className="muted">Un estudiante inactivo no puede recibir una contraseña temporal.</span>}
                </div>
            </div>
        </section>
    );
}
