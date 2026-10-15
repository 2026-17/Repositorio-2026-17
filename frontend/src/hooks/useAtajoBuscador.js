import { useEffect } from "react";

// Teclas del atajo, para aria-keyshortcuts del buscador
export const ATAJO_BUSCADOR = "Control+K Meta+K /";

function escribiendoEnCampo(elemento) {
    if (!elemento) return false;
    const etiqueta = elemento.tagName;
    return etiqueta === "INPUT" || etiqueta === "TEXTAREA" || etiqueta === "SELECT" || elemento.isContentEditable;
}

/*
 * Ctrl+K (⌘K en Mac) o "/" llevan el foco al buscador de la pantalla.
 * "/" solo cuando no se está escribiendo en otro campo, y nada se hace si
 * hay un diálogo abierto.
 */
export function useAtajoBuscador(idBuscador) {
    useEffect(() => {
        function alPresionar(event) {
            const conControl = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k";
            const barra = event.key === "/" && !event.ctrlKey && !event.metaKey && !event.altKey
                && !escribiendoEnCampo(document.activeElement);
            if (!conControl && !barra) return;
            if (document.querySelector(".dialog-backdrop")) return;

            const buscador = document.getElementById(idBuscador);
            if (!buscador || buscador.disabled) return;
            event.preventDefault();
            buscador.focus();
            buscador.select?.();
        }

        document.addEventListener("keydown", alPresionar);
        return () => document.removeEventListener("keydown", alPresionar);
    }, [idBuscador]);
}
