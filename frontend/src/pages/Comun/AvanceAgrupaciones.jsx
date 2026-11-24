import { useEffect, useState } from "react";
import { Users } from "lucide-react";

import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import StatusBadge from "../../components/ui/StatusBadge";
import { API_URL } from "../../config/api.js";
import { lanzarError, mensajeDeExcepcion } from "../../utils/api";

/*
 * Avance académico (vista INTERNA: dirección y administración) - Agrupaciones.
 *
 * - Familias (D5): Conjunto Instrumental y Coro con el avance de su agrupación
 *   más adelantada. "Completo" = todos los niveles en UNA MISMA agrupación.
 * - Cada agrupación: último nivel aprobado y nivel siguiente con su motivo.
 */
export default function AvanceAgrupaciones({ idEstudiante, version }) {
    const [cargado, setCargado] = useState({ clave: null, datos: null, error: "" });
    const clave = `${idEstudiante}|${version}`;

    useEffect(() => {
        let activo = true;
        const token = localStorage.getItem("token");
        fetch(`${API_URL}/agrupaciones/estudiantes/${idEstudiante}/avance`, {
            headers: { Authorization: `Bearer ${token}` },
        })
            .then((r) => (r.ok ? r.json() : lanzarError(r, "No fue posible consultar el avance en agrupaciones.")))
            .then((datos) => { if (activo) setCargado({ clave, datos, error: "" }); })
            .catch((e) => {
                if (activo) setCargado({ clave, datos: null, error: mensajeDeExcepcion(e, "No fue posible consultar el avance en agrupaciones.") });
            });
        return () => { activo = false; };
    }, [idEstudiante, clave]);

    const cargando = cargado.clave !== clave;
    const familias = cargado.datos?.familias ?? [];
    const avances = (cargado.datos?.avances ?? []).filter((a) => a.aprobados.length > 0 || a.idAgrupacion != null);

    return (
        <section className="panel" aria-labelledby="t-avance-agr" aria-busy={cargando || undefined}>
            <div className="panel-head">
                <div>
                    <h2 id="t-avance-agr">Agrupaciones</h2>
                    <p className="panel-head-sub">
                        Uso interno. Cada agrupación avanza por separado. Conjunto Instrumental y Coro quedan completos
                        cuando todos sus niveles están aprobados en una misma agrupación.
                    </p>
                </div>
            </div>

            {cargado.error && !cargando && <Alert tone="danger" title={cargado.error} className="avance-alerta" />}

            {!cargando && !cargado.error && (
                <div className="panel-body avance-agrupaciones">
                    <ul className="avance-agrupaciones__familias">
                        {familias.map((f) => (
                            <li key={f.familia}>
                                <b>{f.nombreFamilia}</b>
                                {f.completa
                                    ? <StatusBadge tone="success">Completo</StatusBadge>
                                    : <StatusBadge tone="neutral" icon={null}>{f.ultimoNivel} de {f.nivelMaximo} niveles</StatusBadge>}
                                <span className="muted">
                                    {f.etiquetaUltimo
                                        ? `Más adelantado: ${f.etiquetaUltimo}${f.idAgrupacion == null ? " (historial de la certificación)" : ""}`
                                        : "Sin niveles aprobados"}
                                </span>
                            </li>
                        ))}
                    </ul>

                    {avances.length === 0 ? (
                        <EmptyState compact icon={Users} title="Sin agrupaciones" description="No hay agrupaciones activas ni historial en agrupaciones." />
                    ) : (
                        <div className="table-wrap">
                            <table className="dtable dtable--cards">
                                <caption className="sr-only">Avance por agrupación</caption>
                                <thead>
                                    <tr>
                                        <th scope="col">Agrupación</th>
                                        <th scope="col">Último aprobado</th>
                                        <th scope="col">Siguiente</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {avances.map((a) => {
                                        const ultimo = a.aprobados[a.aprobados.length - 1];
                                        return (
                                            <tr key={`${a.idAgrupacion ?? "sin"}-${a.familia}`}>
                                                <td className="c-main">
                                                    <b>{a.nombreAgrupacion}</b>
                                                    <span className="sub">{a.nombreFamilia}</span>
                                                </td>
                                                <td data-label="Último aprobado">
                                                    {ultimo ? `${ultimo.nombreCurso}${ultimo.nombrePeriodo ? ` · ${ultimo.nombrePeriodo}` : ""}` : "—"}
                                                </td>
                                                <td data-label="Siguiente">
                                                    {a.completado
                                                        ? <StatusBadge tone="success">Completó la agrupación</StatusBadge>
                                                        : <b>{a.nombreCursoSiguiente ?? "—"}</b>}
                                                    {a.motivo && <span className="sub">{a.motivo}</span>}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            )}
        </section>
    );
}
