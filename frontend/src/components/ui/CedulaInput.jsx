import { useLayoutEffect, useRef } from "react";

import {
    aplicarMascaraConCursor,
    formatearCedula,
    pareceCedula,
    PLACEHOLDER_CEDULA,
    posicionTrasDigitos,
} from "../../utils/cedula";

// Solo dígitos, espacios o guiones (lo que se puede escribir en una cédula)
const SOLO_DIGITOS = /^[\d\s-]*$/;

/*
 * Campo de identificación con la máscara de la cédula nacional (1-1111-1111).
 *
 * - mascara: true cuando el tipo es cédula nacional. Si es false (DIMEX,
 *   pasaporte, etc.) el campo queda libre, igual que un input normal.
 *   "auto" (buscadores o campos sin tipo): aplica la máscara solo mientras lo
 *   escrito sean dígitos de una cédula; con letras o más de 9 dígitos queda libre.
 * - onChange recibe el texto (no el evento). El valor queda con guiones en el
 *   formulario; al enviar se limpia con limpiarIdentificacion().
 * En modo automático se usan el placeholder, inputMode y autoComplete que reciba el campo.
 * El resto de props (id, className, aria-*, maxLength…) pasan al <input>.
 */
export default function CedulaInput({ value, onChange, mascara = true, placeholder, maxLength, inputMode, autoComplete, type = "text", ...rest }) {
    const ref = useRef(null);
    const cursor = useRef(null);
    const automatico = mascara === "auto";
    const conMascara = automatico ? pareceCedula(value) : Boolean(mascara);

    // Después de aplicar la máscara, el cursor vuelve a quedar junto al mismo dígito
    useLayoutEffect(() => {
        if (cursor.current === null || !ref.current) return;
        if (document.activeElement === ref.current) {
            ref.current.setSelectionRange(cursor.current, cursor.current);
        }
        cursor.current = null;
    });

    function alCambiar(e) {
        const texto = e.target.value;

        // Modo automático: si ya no parece cédula (letras o un 10.º dígito) se deja libre
        if (automatico && !pareceCedula(texto)) {
            onChange(SOLO_DIGITOS.test(texto) ? texto.replace(/\D/g, "") : texto);
            return;
        }
        if (!conMascara && !automatico) {
            onChange(texto);
            return;
        }

        const resultado = aplicarMascaraConCursor(texto, e.target.selectionStart);
        cursor.current = resultado.cursor;
        onChange(resultado.valor);
    }

    // Borrar sobre un guion borra el dígito de al lado (si no, el guion "vuelve" solo)
    function alTeclear(e) {
        const input = e.currentTarget;
        const { selectionStart: inicio, selectionEnd: fin } = input;
        if (inicio !== fin) return;
        const texto = input.value;
        let quitar = -1;
        if (e.key === "Backspace" && texto[inicio - 1] === "-") quitar = inicio - 2;
        if (e.key === "Delete" && texto[inicio] === "-") quitar = inicio + 1;
        if (quitar < 0 || quitar >= texto.length) return;

        e.preventDefault();
        const nuevo = formatearCedula(texto.slice(0, quitar) + texto.slice(quitar + 1));
        cursor.current = posicionTrasDigitos(nuevo, texto.slice(0, quitar).replace(/\D/g, "").length);
        onChange(nuevo);
    }

    if (!conMascara) {
        return (
            <input
                {...rest}
                ref={ref}
                type={type}
                value={value}
                placeholder={placeholder}
                maxLength={maxLength}
                inputMode={inputMode}
                autoComplete={autoComplete}
                onChange={alCambiar}
            />
        );
    }

    return (
        <input
            {...rest}
            ref={ref}
            type={type}
            // En modo automático (p. ej. un buscador por nombre o cédula) se respetan los del campo
            inputMode={automatico ? inputMode : "numeric"}
            autoComplete={automatico ? autoComplete : "off"}
            placeholder={automatico ? placeholder : PLACEHOLDER_CEDULA}
            value={formatearCedula(value)}
            onChange={alCambiar}
            onKeyDown={alTeclear}
        />
    );
}
