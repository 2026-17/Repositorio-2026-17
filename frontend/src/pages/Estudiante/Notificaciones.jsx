import { useCallback, useEffect, useState } from "react";
import { API_URL } from "../../config/api";

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

    return (
        <div className="content">
            <div className="page-title">
                <h1>Mis notificaciones</h1>

                <p>
                    Consulte las novedades relacionadas con
                    sus solicitudes de matrícula.
                </p>
            </div>

            <div
                style={{
                    marginBottom: "1rem",
                }}
            >
                <button
                    type="button"
                    onClick={() => {
                        void cargarNotificaciones();
                    }}
                    disabled={
                        cargando ||
                        procesandoId !== null
                    }
                >
                    {cargando
                        ? "Actualizando..."
                        : "Actualizar notificaciones"}
                </button>
            </div>

            {cargando ? (
                <p>Cargando notificaciones...</p>
            ) : notificaciones.length === 0 ? (
                <div
                    style={{
                        border: "1px solid #ddd",
                        borderRadius: "8px",
                        padding: "1.25rem",
                    }}
                >
                    <h2
                        style={{
                            marginTop: 0,
                        }}
                    >
                        Sin notificaciones
                    </h2>

                    <p
                        style={{
                            marginBottom: 0,
                        }}
                    >
                        Actualmente no tiene notificaciones
                        relacionadas con sus solicitudes de
                        matrícula.
                    </p>
                </div>
            ) : (
                <div
                    style={{
                        display: "grid",
                        gap: "1rem",
                    }}
                >
                    {notificaciones.map(
                        (notificacion) => {
                            const procesando =
                                procesandoId ===
                                notificacion.idNotificacion;

                            return (
                                <section
                                    key={
                                        notificacion.idNotificacion
                                    }
                                    style={{
                                        border:
                                            notificacion.leida
                                                ? "1px solid #ccc"
                                                : "2px solid #6f2788",
                                        borderRadius: "8px",
                                        padding: "1rem",
                                    }}
                                >
                                    <div
                                        style={{
                                            display: "flex",
                                            justifyContent:
                                                "space-between",
                                            alignItems:
                                                "flex-start",
                                            gap: "1rem",
                                            flexWrap: "wrap",
                                        }}
                                    >
                                        <div>
                                            <h2
                                                style={{
                                                    marginTop: 0,
                                                }}
                                            >
                                                {
                                                    notificacion.asunto
                                                }
                                            </h2>

                                            <p>
                                                <strong>
                                                    Resultado:
                                                </strong>{" "}
                                                {textoResultado(
                                                    notificacion.tipo
                                                )}
                                            </p>

                                            <p>
                                                <strong>
                                                    Estado:
                                                </strong>{" "}
                                                {notificacion.leida
                                                    ? "Leída"
                                                    : "No leída"}
                                            </p>

                                            <p>
                                                <strong>
                                                    Fecha:
                                                </strong>{" "}
                                                {formatearFecha(
                                                    notificacion.fechaNotificacion
                                                )}
                                            </p>

                                            {notificacion.leida &&
                                                notificacion.fechaLectura && (
                                                    <p>
                                                        <strong>
                                                            Leída el:
                                                        </strong>{" "}
                                                        {formatearFecha(
                                                            notificacion.fechaLectura
                                                        )}
                                                    </p>
                                                )}
                                        </div>
                                    </div>

                                    <div
                                        style={{
                                            marginTop: "0.75rem",
                                            padding: "0.85rem",
                                            border: "1px solid #ddd",
                                            borderRadius: "6px",
                                        }}
                                    >
                                        <p
                                            style={{
                                                margin: 0,
                                            }}
                                        >
                                            {
                                                notificacion.mensaje
                                            }
                                        </p>
                                    </div>

                                    {!notificacion.leida && (
                                        <div
                                            style={{
                                                marginTop: "1rem",
                                            }}
                                        >
                                            <button
                                                type="button"
                                                onClick={() =>
                                                    marcarComoLeida(
                                                        notificacion
                                                    )
                                                }
                                                disabled={
                                                    procesando
                                                }
                                            >
                                                {procesando
                                                    ? "Actualizando..."
                                                    : "Marcar como leída"}
                                            </button>
                                        </div>
                                    )}
                                </section>
                            );
                        }
                    )}
                </div>
            )}
        </div>
    );
}

export default Notificaciones;