/*
 * Reglas de las contraseñas nuevas. Deben coincidir con el backend:
 * PoliticaContrasena y los DTO de cambio de contraseña (12 a 16 caracteres,
 * con mayúscula, número y carácter especial).
 */
export const LONGITUD_MAXIMA_CONTRASENA = 16;

export const REQUISITOS_CONTRASENA = [
    { id: "longitud", texto: "Entre 12 y 16 caracteres", cumple: (pw) => pw.length >= 12 && pw.length <= 16 },
    { id: "mayuscula", texto: "Una letra mayúscula", cumple: (pw) => /[A-Z]/.test(pw) },
    { id: "numero", texto: "Un número", cumple: (pw) => /[0-9]/.test(pw) },
    { id: "especial", texto: "Un carácter especial", cumple: (pw) => /[^A-Za-z0-9]/.test(pw) },
];

// Cada requisito con su estado para la contraseña dada
export function revisarContrasena(contrasena) {
    return REQUISITOS_CONTRASENA.map((r) => ({ ...r, ok: r.cumple(contrasena) }));
}
