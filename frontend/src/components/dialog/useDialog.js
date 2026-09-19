import { useContext } from "react";

import { DialogContext } from "./DialogContext";

/*
 * Acceso a los diálogos centrales del sistema.
 *
 *   const dialog = useDialog();
 *
 *   await dialog.exito("Curso creado correctamente.");
 *   await dialog.error("No fue posible guardar el curso.");
 *   await dialog.aviso("El período seleccionado ya finalizó.");
 *   await dialog.validacion(["La cédula es obligatoria."]);
 *
 *   if (await dialog.confirmar("¿Eliminar al profesor?", { peligro: true })) {
 *       ...
 *   }
 *
 * Todas las funciones devuelven una promesa que se resuelve al cerrar el
 * diálogo. confirmar() resuelve true (aceptar) o false (cancelar / Esc).
 */
export function useDialog() {
    const contexto = useContext(DialogContext);

    if (!contexto) {
        throw new Error(
            "useDialog debe usarse dentro de <DialogProvider>."
        );
    }

    return contexto;
}
