import { Link } from "react-router-dom";
import { ChevronRight, CircleHelp } from "lucide-react";

import "./inicio.css";
import EmptyState from "../../components/ui/EmptyState";
import PageHead from "../../components/ui/PageHead";
import Panel from "../../components/ui/Panel";
import { MENU_POR_ROL } from "../../constants/menu";
import { etiquetaRol } from "../../constants/roles";

/*
 * Pantalla de inicio de cada rol (también se abre con el botón "Ayuda").
 * Explica qué hace cada opción del menú lateral; cada tarjeta lleva a su pantalla.
 */
function Inicio() {
    const rol = localStorage.getItem("rol");
    const nombre = localStorage.getItem("nombre");
    const grupos = MENU_POR_ROL[rol] || [];

    return (
        <div className="inicio">
            <PageHead
                title={nombre ? `Bienvenido(a), ${nombre}` : "Bienvenido(a)"}
                sub={`Ingresó como ${etiquetaRol(rol)}. Aquí se explica qué puede hacer con cada opción del menú; seleccione una para ir a esa pantalla.`}
            />

            {grupos.length === 0 && (
                <Panel>
                    <EmptyState
                        icon={CircleHelp}
                        title="Aún no hay opciones para su rol"
                        description="Cuando se habiliten pantallas para su rol, aparecerán en el menú y se explicarán aquí."
                    />
                </Panel>
            )}

            {grupos.map((grupo) => (
                <Panel key={grupo.titulo} title={grupo.titulo}>
                    <ul className="inicio__lista">
                        {grupo.items.map((item) => {
                            const Icono = item.icon;
                            return (
                                <li key={item.to}>
                                    <Link to={item.to} className="inicio__opcion">
                                        <span className="inicio__icono" aria-hidden="true">
                                            <Icono size={20} strokeWidth={1.75} />
                                        </span>
                                        <span className="inicio__texto">
                                            <b>{item.label}</b>
                                            <span>{item.descripcion}</span>
                                        </span>
                                        <ChevronRight size={18} strokeWidth={1.75} className="inicio__flecha" aria-hidden="true" />
                                    </Link>
                                </li>
                            );
                        })}
                    </ul>
                </Panel>
            ))}
        </div>
    );
}

export default Inicio;
