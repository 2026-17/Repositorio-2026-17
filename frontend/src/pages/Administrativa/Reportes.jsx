import { ChartColumn } from "lucide-react";
import EmptyState from "../../components/ui/EmptyState";
import PageHead from "../../components/ui/PageHead";

function Reportes() {
    return (
        <>
            <PageHead title="Reportes" sub="Indicadores y listados de la escuela." />
            <div className="panel">
                <EmptyState
                    icon={ChartColumn}
                    title="Sección en construcción"
                    description="Aquí se mostrarán los reportes de matrícula, pagos y estudiantes."
                />
            </div>
        </>
    );
}

export default Reportes;
