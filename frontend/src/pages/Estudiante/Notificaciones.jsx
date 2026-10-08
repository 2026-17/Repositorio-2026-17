import { useCallback, useEffect, useState } from "react";
import { API_URL } from "../../config/api";
import { Bell, Check, RefreshCw } from "lucide-react";
import "./notificaciones.css";
import EmptyState from "../../components/ui/EmptyState";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";

import { useDialog } from "../../components/dialog/useDialog";

function formatearFecha(fecha) {
    if (!fecha) {
        return "No registrada";
    }

    const valor = new Date(fecha);

    if (Number.isNaN(valor.getTime())) {
        return fecha;
    }

    return valor.toLocaleString("es-CR", {
        dateStyle: "medium",
        timeStyle: "short",
    });
}

function Notificaciones() {
    const dialog = useDialog();
    const token = localStorage.getItem("token");

    const [notificaciones, setNotificaciones] = useState([]);
    const [cargando, setCargando] = useState(true);
    const [procesandoId, setProcesandoId] = useState(null);

    const cargarNotificaciones = useCallback(async () => {
        setCargando(true);

        try {
            const response = await fetch(
                `${API_URL}/notificaciones-estudiante/mias`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }
            );

            let data = {};

            try {
                data = await response.json();
            } catch {
                data = {};
            }

            if (response.status === 401) {
                throw new Error(
                    "La sesión ha expirado. Inicie sesión nuevamente."
                );
            }

            if (response.status === 403) {
                throw new Error(
                    "No tiene permisos para consultar estas notificaciones."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.message ||
                    "No fue posible consultar las notificaciones."
                );
            }

            setNotificaciones(
                Array.isArray(data) ? data : []
            );
        } catch (errorConsulta) {
            setNotificaciones([]);

            dialog.error(
                errorConsulta.message ||
                "Ocurrió un error al consultar las notificaciones."
            );
        } finally {
            setCargando(false);
        }
    }, [token, dialog]);

    useEffect(() => {
        const timer = setTimeout(() => {
            void cargarNotificaciones();
        }, 0);

        return () => clearTimeout(timer);
    }, [cargarNotificaciones]);

    async function marcarComoLeida(notificacion) {
        if (notificacion.leida) {
            return;
        }

        setProcesandoId(
            notificacion.idNotificacion
        );

        try {
            const response = await fetch(
                `${API_URL}/notificaciones-estudiante/${notificacion.idNotificacion}/leer`,
                {
                    method: "PATCH",
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }
            );

            let data = {};

            try {
                data = await response.json();
            } catch {
                data = {};
            }

            if (response.status === 401) {
                throw new Error(
                    "La sesión ha expirado. Inicie sesión nuevamente."
                );
            }

            if (response.status === 403) {
                throw new Error(
                    "No tiene permisos para modificar esta notificación."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.message ||
                    "No fue posible marcar la notificación como leída."
                );
            }

            setNotificaciones((prev) =>
                prev.map((item) =>
                    item.idNotificacion ===
                    notificacion.idNotificacion
                        ? data
                        : item
                )
            );
        } catch (errorActualizacion) {
            dialog.error(
                errorActualizacion.message ||
                "Ocurrió un error al actualizar la notificación."
            );
        } finally {
            setProcesandoId(null);
        }
    }

    function textoResultado(tipo) {
        if (tipo === "MATRICULA_APROBADA") {
            return "Matrícula aprobada";
        }

        if (tipo === "MATRICULA_RECHAZADA") {
            return "Matrícula rechazada";
        }

        return "Notificación";
    }

    // Color de la insignia según el resultado
    function tonoResultado(tipo) {
        if (tipo === "MATRICULA_APROBADA") return "success";
        if (tipo === "MATRICULA_RECHAZADA") return "danger";
        return "info";
    }

    const noLeidas = notificaciones.filter((n) => !n.leida).length;

    return (
        <div className="notificaciones">
            <PageHead
                title="Mis notificaciones"
                sub="Consulte las novedades relacionadas con sus solicitudes de matrícula."
                actions={(
                    <button
                        type="button"
                        className="sg-btn sg-btn--secondary"
                        onClick={() => {
                            void cargarNotificaciones();
                        }}
                        disabled={cargando || procesandoId !== null}
                    >
                        <RefreshCw size={18} strokeWidth={1.75} aria-hidden="true" />
                        {cargando ? "Actualizando…" : "Actualizar notificaciones"}
                    </button>
                )}
            />

            <section className="panel" aria-labelledby="titulo-avisos">
                <div className="panel-head">
                    <h2 id="titulo-avisos">Avisos</h2>
                    {noLeidas > 0 && (
                        <StatusBadge tone="primary" icon={null}>
                            {noLeidas === 1 ? "1 sin leer" : `${noLeidas} sin leer`}
                        </StatusBadge>
                    )}
                </div>

                {cargando ? (
                    <p className="muted notificaciones__cargando">Cargando notificaciones...</p>
                ) : notificaciones.length === 0 ? (
                    <EmptyState
                        icon={Bell}
                        title="Sin notificaciones"
                        description="Actualmente no tiene notificaciones relacionadas con sus solicitudes de matrícula."
                    />
                ) : (
                    <ul className="notificaciones__lista">
                        {notificaciones.map((notificacion) => {
                            const procesando = procesandoId === notificacion.idNotificacion;

                            return (
                                <li
                                    key={notificacion.idNotificacion}
                                    className={notificacion.leida ? "notice" : "notice notice--no-leida"}
                                >
                                    <div className="notice-cabecera">
                                        <time dateTime={notificacion.fechaNotificacion}>
                                            {formatearFecha(notificacion.fechaNotificacion)}
                                        </time>
                                        <span className="inline-actions">
                                            <StatusBadge tone={tonoResultado(notificacion.tipo)}>
                                                {textoResultado(notificacion.tipo)}
                                            </StatusBadge>
                                            {!notificacion.leida && (
                                                <StatusBadge tone="primary" icon={null}>No leída</StatusBadge>
                                            )}
                                        </span>
                                    </div>

                                    <h3>{notificacion.asunto}</h3>
                                    <p>{notificacion.mensaje}</p>

                                    <div className="notice-pie">
                                        <span className="muted">
                                            {notificacion.leida
                                                ? `Leída${notificacion.fechaLectura ? ` el ${formatearFecha(notificacion.fechaLectura)}` : ""}`
                                                : "Pendiente de leer"}
                                        </span>
                                        {!notificacion.leida && (
                                            <button
                                                type="button"
                                                className="sg-btn sg-btn--ghost sg-btn--sm"
                                                onClick={() => marcarComoLeida(notificacion)}
                                                disabled={procesando}
                                            >
                                                <Check size={16} strokeWidth={1.75} aria-hidden="true" />
                                                {procesando ? "Actualizando…" : "Marcar como leída"}
                                            </button>
                                        )}
                                    </div>
                                </li>
                            );
                        })}
                    </ul>
                )}
            </section>
        </div>
    );
}

export default Notificaciones;
