/*
 * Logo de SIGAC: es el mismo archivo que el favicon (public/favicon.png), así
 * el menú, las pantallas públicas y la pestaña del navegador muestran lo mismo.
 * Va dentro de un .sb-mark; el texto "SIGAC" está al lado, por eso alt vacío.
 */
export default function LogoSigac() {
    return <img className="sb-logo" src="/favicon.png?v=2" alt="" />;
}
