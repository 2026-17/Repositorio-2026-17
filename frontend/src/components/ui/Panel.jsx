import { useId } from "react";

// Panel con encabezado opcional (título, subtítulo y acción a la derecha).
export default function Panel({ title, sub, action, className = "", children, ...rest }) {
    const id = useId();
    return (
        <section className={`panel ${className}`.trim()} aria-labelledby={title ? id : undefined} {...rest}>
            {title && (
                <div className="panel-head">
                    <div>
                        <h2 id={id}>{title}</h2>
                        {sub && <p className="panel-head-sub">{sub}</p>}
                    </div>
                    {action}
                </div>
            )}
            {children}
        </section>
    );
}
