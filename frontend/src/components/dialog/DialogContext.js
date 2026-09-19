import { createContext } from "react";

/*
 * Contexto del sistema de diálogos centrales del SIGAC.
 * El valor lo provee DialogProvider; se consume con useDialog().
 */
export const DialogContext = createContext(null);
