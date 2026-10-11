// Encabezado de página del sistema de diseño: título, subtítulo y acciones.
export default function PageHead({ title, sub, actions, display = false }) {
    return (
        <div className="page-head">
            <div>
                <h1 className={`page-title${display ? " page-title--display" : ""}`} id="page-title" tabIndex={-1}>
                    {title}
                </h1>
                {sub && <p className="page-sub">{sub}</p>}
            </div>
            {actions && <div className="page-actions">{actions}</div>}
        </div>
    );
}
