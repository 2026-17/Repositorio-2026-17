import { useCallback, useEffect, useRef } from "react";
import { useBlocker } from "react-router-dom";

import { useDialog } from "../components/dialog/useDialog";

/*
 * Avisa antes de perder cambios que todavía no se han guardado:
 *  - al ir a otra pantalla del sistema, con el diálogo "¿Descartar cambios?";
 *  - al cerrar o recargar la pestaña, con el aviso propio del navegador.
 *
 *   const { confirmarDescarte, permitirSalida } = useAvisoCambiosSinGuardar(hayCambios);
 *
 * confirmarDescarte() muestra el mismo diálogo (p. ej. en un botón "Cancelar")
 * y resuelve true si la persona acepta descartar.
 * permitirSalida() desactiva el aviso hasta el siguiente cambio; se usa
 * después de guardar con éxito si enseguida se navega a otra pantalla.
 */
export function useAvisoCambiosSinGuardar(hayCambios) {
    const dialog = useDialog();
    const activo = useRef(hayCambios);

    useEffect(() => {
        activo.current = hayCambios;
    }, [hayCambios]);

    const confirmarDescarte = useCallback(() => dialog.confirmar(
        "Hay cambios que todavía no se han guardado. Si continúa, esos cambios se perderán.",
        {
            titulo: "¿Descartar cambios?",
            textoAceptar: "Descartar cambios",
            textoCancelar: "Continuar editando",
            peligro: true,
        }
    ), [dialog]);

    const permitirSalida = useCallback(() => {
        activo.current = false;
    }, []);

    // Solo bloquea si cambia de pantalla (no por cambios de ?query o #hash).
    const blocker = useBlocker(useCallback(
        ({ currentLocation, nextLocation }) =>
            activo.current && currentLocation.pathname !== nextLocation.pathname,
        []
    ));

    // El objeto del bloqueo puede cambiar entre renders; la pregunta se hace una sola vez por intento.
    const ultimoBlocker = useRef(blocker);
    useEffect(() => {
        ultimoBlocker.current = blocker;
    });

    const bloqueado = blocker.state === "blocked";
    useEffect(() => {
        if (!bloqueado) return undefined;
        let vigente = true;
        confirmarDescarte().then((descartar) => {
            if (!vigente) return;
            if (descartar) {
                activo.current = false;
                ultimoBlocker.current.proceed?.();
            } else {
                ultimoBlocker.current.reset?.();
            }
        });
        return () => {
            vigente = false;
        };
    }, [bloqueado, confirmarDescarte]);

    // Cerrar o recargar la pestaña
    useEffect(() => {
        if (!hayCambios) return undefined;
        function alSalir(event) {
            if (!activo.current) return;
            event.preventDefault();
            event.returnValue = "";
        }
        window.addEventListener("beforeunload", alSalir);
        return () => window.removeEventListener("beforeunload", alSalir);
    }, [hayCambios]);

    return { confirmarDescarte, permitirSalida };
}

// true si el formulario ya no es igual a sus valores iniciales.
export function formularioModificado(actual, inicial) {
    return JSON.stringify(actual) !== JSON.stringify(inicial);
}
