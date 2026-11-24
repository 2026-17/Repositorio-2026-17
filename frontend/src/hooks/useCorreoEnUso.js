import { useCallback, useRef, useState } from "react";

import { API_URL } from "../config/api";
import { esCorreoValido } from "../utils/validaciones";

/*
 * Varias cuentas pueden compartir un correo (por ejemplo, hermanos). Al salir
 * del campo se consulta quién más lo usa, solo para informar: nunca bloquea
 * guardar, y si la consulta falla simplemente no se muestra el aviso.
 * excluirId: id del estudiante que se está editando.
 */
export function useCorreoEnUso(excluirId) {
    const [cuentas, setCuentas] = useState([]);
    // Solo vale la respuesta de la última consulta
    const ultima = useRef(0);

    const limpiar = useCallback(() => {
        ultima.current += 1;
        setCuentas([]);
    }, []);

    const consultar = useCallback(async (valor) => {
        const consulta = ++ultima.current;
        const correo = (valor || "").trim();
        if (!esCorreoValido(correo)) {
            setCuentas([]);
            return;
        }
        try {
            const parametros = new URLSearchParams({ correo });
            if (excluirId != null) parametros.set("excluirId", String(excluirId));
            const token = localStorage.getItem("token");
            const respuesta = await fetch(`${API_URL}/estudiantes/correo-en-uso?${parametros}`, {
                headers: token ? { Authorization: `Bearer ${token}` } : {},
            });
            const lista = respuesta.ok ? await respuesta.json() : [];
            if (consulta === ultima.current) setCuentas(Array.isArray(lista) ? lista : []);
        } catch {
            if (consulta === ultima.current) setCuentas([]);
        }
    }, [excluirId]);

    return { cuentas, consultar, limpiar };
}
