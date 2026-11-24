import Alert from "./ui/Alert";

// Aviso informativo bajo el campo de correo; no es un error ni bloquea guardar.
export default function AvisoCorreoEnUso({ cuentas }) {
    if (!cuentas || cuentas.length === 0) return null;
    const lista = cuentas.map(c => `${c.nombreCompleto} (${c.cedulaEnmascarada})`).join(", ");
    return (
        <Alert tone="info" className="aviso-correo">
            Este correo también lo usan: {lista}. Los códigos de recuperación de cada cuenta llegarán a este mismo correo.
        </Alert>
    );
}
