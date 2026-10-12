import { Music } from "lucide-react";

/*
 * Estructura de las pantallas públicas (como el prototipo): panel ciruela
 * decorativo a la izquierda y el formulario a la derecha. En móvil el panel
 * se oculta y la marca aparece arriba del formulario.
 */
export default function AuthLayout({ children }) {
    return (
        <div className="auth">
            <aside className="auth-side" aria-hidden="true">
                <div className="auth-brand">
                    <span className="sb-mark"><Music size={20} strokeWidth={1.75} /></span>
                    <span>
                        <span className="sb-name">SIGAC</span>
                        <span className="sb-sub">ACUSAB</span>
                    </span>
                </div>

                <div className="staff"><i /><i /><i /><i /><i /></div>

                <div className="auth-blocks"><i /><i /><i /><i /></div>

                <div className="auth-quote">
                    <h2>Bienvenido al Sistema de Matrícula (SIGAC)</h2>
                    <p>
                        El Sistema Integral de Gestión Académica y Cultural (SIGAC)
                        es un sistema de la Asociación Cultural de Santa Bárbara.
                    </p>
                </div>

                <p className="auth-foot">Asociación Cultural de Santa Bárbara · Heredia</p>
            </aside>

            <main className="auth-main">
                <div className="auth-card">
                    <div className="auth-mobile-brand" aria-hidden="true">
                        <span className="sb-mark"><Music size={20} strokeWidth={1.75} /></span>
                        <span>
                            <span className="sb-name">SIGAC</span>
                            <span className="sb-sub">ACUSAB</span>
                        </span>
                    </div>
                    {children}
                </div>
            </main>
        </div>
    );
}
