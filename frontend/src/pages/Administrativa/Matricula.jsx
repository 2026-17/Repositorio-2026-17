import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { API_URL } from "../../config/api";
import "../AdminSis/styles/admin_Sis.css";

async function leerRespuesta(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}

function validarEstadoRespuesta(
    response,
    data,
    mensajePorDefecto
) {
    if (response.status === 401) {
        throw new Error(
            "La sesión ha expirado. Inicie sesión nuevamente."
        );
    }

    if (response.status === 403) {
        throw new Error(
            "No tiene permisos para gestionar matrículas."
        );
    }

    if (!response.ok) {
        throw new Error(
            data.message || mensajePorDefecto
        );
    }
}

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

function textoEstadoRequisito(valor) {
    return valor ? "Validado" : "Pendiente";
}

function Matricula() {
    const navigate = useNavigate();
    const token = localStorage.getItem("token");

    const [pestanaActiva, setPestanaActiva] =
        useState("pendientes");

    const [solicitudes, setSolicitudes] = useState([]);
    const [historial, setHistorial] = useState([]);

    const [cargando, setCargando] = useState(true);
    const [procesandoId, setProcesandoId] = useState(null);

    const [observaciones, setObservaciones] = useState({});

    const [errorGeneral, setErrorGeneral] = useState("");
    const [mensajeExito, setMensajeExito] = useState("");

    const cargarDatos = useCallback(async () => {
        setCargando(true);
        setErrorGeneral("");

        try {
            const [
                responsePendientes,
                responseHistorial,
            ] = await Promise.all([
                fetch(
                    `${API_URL}/autorizaciones-matricula/pendientes`,
                    {
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                    }
                ),
                fetch(
                    `${API_URL}/autorizaciones-matricula/historial`,
                    {
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                    }
                ),
            ]);

            const [
                dataPendientes,
                dataHistorial,
            ] = await Promise.all([
                leerRespuesta(responsePendientes),
                leerRespuesta(responseHistorial),
            ]);

            validarEstadoRespuesta(
                responsePendientes,
                dataPendientes,
                "No fue posible consultar las solicitudes pendientes."
            );

            validarEstadoRespuesta(
                responseHistorial,
                dataHistorial,
                "No fue posible consultar el historial de matrículas."
            );

            setSolicitudes(
                Array.isArray(dataPendientes)
                    ? dataPendientes
                    : []
            );

            setHistorial(
                Array.isArray(dataHistorial)
                    ? dataHistorial
                    : []
            );
        } catch (error) {
            setSolicitudes([]);
            setHistorial([]);

            setErrorGeneral(
                error.message ||
                "Ocurrió un error al consultar las matrículas."
            );
        } finally {
            setCargando(false);
        }
    }, [token]);

    useEffect(() => {
        const timer = setTimeout(() => {
            void cargarDatos();
        }, 0);

        return () => clearTimeout(timer);
    }, [cargarDatos]);

    function cambiarObservacion(
        idAutorizacion,
        valor
    ) {
        setObservaciones((prev) => ({
            ...prev,
            [idAutorizacion]: valor,
        }));

        setErrorGeneral("");
        setMensajeExito("");
    }

    function requisitosValidos(solicitud) {
        return (
            solicitud.mensualidadesAlDia === true &&
            solicitud.pagoMatriculaConfirmado === true &&
            solicitud.documentacionCompleta === true &&
            solicitud.idDocumentoComprobanteMatricula != null
        );
    }

    async function confirmarSolicitud(
        solicitud,
        decision
    ) {
        setErrorGeneral("");
        setMensajeExito("");

        if (
            decision === "APROBAR" &&
            !requisitosValidos(solicitud)
        ) {
            setErrorGeneral(
                "No se puede aprobar esta solicitud porque todavía existen requisitos administrativos pendientes."
            );
            return;
        }

        const observacion =
            observaciones[solicitud.idAutorizacion] || "";

        if (
            decision === "RECHAZAR" &&
            !observacion.trim()
        ) {
            setErrorGeneral(
                "Debe indicar una observación administrativa para rechazar la solicitud."
            );
            return;
        }

        const textoDecision =
            decision === "APROBAR"
                ? "aprobar"
                : "rechazar";

        const confirmado = window.confirm(
            `¿Está seguro de que desea ${textoDecision} la solicitud de matrícula de ${solicitud.estudiante}?`
        );

        if (!confirmado) {
            return;
        }

        setProcesandoId(
            solicitud.idAutorizacion
        );

        try {
            const response = await fetch(
                `${API_URL}/autorizaciones-matricula/${solicitud.idAutorizacion}/confirmar`,
                {
                    method: "PATCH",
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${token}`,
                    },
                    body: JSON.stringify({
                        decision,
                        observaciones:
                            observacion.trim(),
                    }),
                }
            );

            const data =
                await leerRespuesta(response);

            validarEstadoRespuesta(
                response,
                data,
                "No fue posible procesar la solicitud de matrícula."
            );

            const mensaje =
                decision === "APROBAR"
                    ? "Solicitud de matrícula aprobada correctamente. La decisión fue registrada en el historial y se generó la notificación para el estudiante."
                    : "Solicitud de matrícula rechazada correctamente. La decisión fue registrada en el historial y se generó la notificación para el estudiante.";

            setMensajeExito(mensaje);

            setObservaciones((prev) => {
                const siguientes = {
                    ...prev,
                };

                delete siguientes[
                    solicitud.idAutorizacion
                    ];

                return siguientes;
            });

            await cargarDatos();
        } catch (error) {
            setErrorGeneral(
                error.message ||
                "Ocurrió un error al procesar la solicitud."
            );
        } finally {
            setProcesandoId(null);
        }
    }

    function renderRequisitos(solicitud) {
        return (
            <div>
                <p>
                    <strong>Requisitos:</strong>
                </p>

                <p>
                    Mensualidades al día:{" "}
                    <strong>
                        {textoEstadoRequisito(
                            solicitud.mensualidadesAlDia
                        )}
                    </strong>
                </p>

                <p>
                    Pago de matrícula:{" "}
                    <strong>
                        {textoEstadoRequisito(
                            solicitud.pagoMatriculaConfirmado
                        )}
                    </strong>
                </p>

                <p>
                    Documentación:{" "}
                    <strong>
                        {textoEstadoRequisito(
                            solicitud.documentacionCompleta
                        )}
                    </strong>
                </p>

                <p>
                    Comprobante:{" "}
                    <strong>
                        {solicitud.comprobanteMatricula ||
                            "No registrado"}
                    </strong>
                </p>
            </div>
        );
    }

    function renderPendientes() {
        if (solicitudes.length === 0) {
            return (
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
                        Sin solicitudes pendientes
                    </h2>

                    <p
                        style={{
                            marginBottom: 0,
                        }}
                    >
                        No existen solicitudes de matrícula
                        pendientes de revisión en este momento.
                        Puede consultar las decisiones anteriores
                        desde la pestaña Historial.
                    </p>
                </div>
            );
        }

        return (
            <div
                style={{
                    display: "grid",
                    gap: "1rem",
                }}
            >
                {solicitudes.map((solicitud) => {
                    const puedeAprobar =
                        requisitosValidos(
                            solicitud
                        );

                    const procesando =
                        procesandoId ===
                        solicitud.idAutorizacion;

                    return (
                        <section
                            key={
                                solicitud.idAutorizacion
                            }
                            style={{
                                border: "1px solid #ccc",
                                borderRadius: "8px",
                                padding: "1rem",
                            }}
                        >
                            <div
                                style={{
                                    display: "flex",
                                    justifyContent:
                                        "space-between",
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
                                            solicitud.estudiante
                                        }
                                    </h2>

                                    <p>
                                        <strong>
                                            Solicitud:
                                        </strong>{" "}
                                        #
                                        {
                                            solicitud.idAutorizacion
                                        }
                                    </p>

                                    <p>
                                        <strong>
                                            Período:
                                        </strong>{" "}
                                        {
                                            solicitud.periodo
                                        }
                                        {solicitud.codigoPeriodo
                                            ? ` (${solicitud.codigoPeriodo})`
                                            : ""}
                                    </p>

                                    <p>
                                        <strong>
                                            Estado:
                                        </strong>{" "}
                                        {
                                            solicitud.estado
                                        }
                                    </p>
                                </div>

                                {renderRequisitos(
                                    solicitud
                                )}
                            </div>

                            {!puedeAprobar && (
                                <div
                                    style={{
                                        marginTop:
                                            "0.75rem",
                                        padding:
                                            "0.75rem",
                                        border:
                                            "1px solid #d6a600",
                                        borderRadius:
                                            "6px",
                                    }}
                                >
                                    Esta solicitud no puede
                                    aprobarse hasta que todos
                                    los requisitos
                                    administrativos estén
                                    validados.
                                </div>
                            )}

                            <div
                                style={{
                                    marginTop: "1rem",
                                }}
                            >
                                <label
                                    htmlFor={`observaciones-${solicitud.idAutorizacion}`}
                                >
                                    <strong>
                                        Observaciones
                                        administrativas
                                    </strong>
                                </label>

                                <textarea
                                    id={`observaciones-${solicitud.idAutorizacion}`}
                                    value={
                                        observaciones[
                                            solicitud
                                                .idAutorizacion
                                            ] || ""
                                    }
                                    onChange={(event) =>
                                        cambiarObservacion(
                                            solicitud.idAutorizacion,
                                            event.target
                                                .value
                                        )
                                    }
                                    maxLength={1500}
                                    rows={4}
                                    placeholder="Ingrese observaciones sobre la revisión de la solicitud..."
                                    disabled={
                                        procesando
                                    }
                                    style={{
                                        display: "block",
                                        width: "100%",
                                        marginTop:
                                            "0.5rem",
                                        boxSizing:
                                            "border-box",
                                    }}
                                />

                                <small>
                                    {
                                        (
                                            observaciones[
                                                solicitud
                                                    .idAutorizacion
                                                ] || ""
                                        ).length
                                    }
                                    /1500
                                </small>
                            </div>

                            <div
                                style={{
                                    display: "flex",
                                    gap: "0.75rem",
                                    marginTop: "1rem",
                                    flexWrap: "wrap",
                                }}
                            >
                                <button
                                    type="button"
                                    onClick={() =>
                                        confirmarSolicitud(
                                            solicitud,
                                            "APROBAR"
                                        )
                                    }
                                    disabled={
                                        procesando ||
                                        !puedeAprobar
                                    }
                                >
                                    {procesando
                                        ? "Procesando..."
                                        : "Aprobar"}
                                </button>

                                <button
                                    type="button"
                                    onClick={() =>
                                        confirmarSolicitud(
                                            solicitud,
                                            "RECHAZAR"
                                        )
                                    }
                                    disabled={
                                        procesando
                                    }
                                >
                                    {procesando
                                        ? "Procesando..."
                                        : "Rechazar"}
                                </button>
                            </div>
                        </section>
                    );
                })}
            </div>
        );
    }

    function renderHistorial() {
        if (historial.length === 0) {
            return (
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
                        Historial vacío
                    </h2>

                    <p
                        style={{
                            marginBottom: 0,
                        }}
                    >
                        Todavía no existen solicitudes de
                        matrícula aprobadas o rechazadas.
                    </p>
                </div>
            );
        }

        return (
            <div
                style={{
                    display: "grid",
                    gap: "1rem",
                }}
            >
                {historial.map((solicitud) => (
                    <section
                        key={
                            solicitud.idAutorizacion
                        }
                        style={{
                            border: "1px solid #ccc",
                            borderRadius: "8px",
                            padding: "1rem",
                        }}
                    >
                        <div
                            style={{
                                display: "flex",
                                justifyContent:
                                    "space-between",
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
                                        solicitud.estudiante
                                    }
                                </h2>

                                <p>
                                    <strong>
                                        Solicitud:
                                    </strong>{" "}
                                    #
                                    {
                                        solicitud.idAutorizacion
                                    }
                                </p>

                                <p>
                                    <strong>
                                        Período:
                                    </strong>{" "}
                                    {
                                        solicitud.periodo
                                    }
                                    {solicitud.codigoPeriodo
                                        ? ` (${solicitud.codigoPeriodo})`
                                        : ""}
                                </p>

                                <p>
                                    <strong>
                                        Decisión:
                                    </strong>{" "}
                                    {solicitud.estado}
                                </p>

                                <p>
                                    <strong>
                                        Fecha de
                                        confirmación:
                                    </strong>{" "}
                                    {formatearFecha(
                                        solicitud.fechaRevision
                                    )}
                                </p>

                                <p>
                                    <strong>
                                        Revisado por:
                                    </strong>{" "}
                                    {solicitud.revisadoPor ||
                                        "No registrado"}
                                </p>
                            </div>

                            {renderRequisitos(
                                solicitud
                            )}
                        </div>

                        <div
                            style={{
                                marginTop: "1rem",
                                padding: "0.85rem",
                                border: "1px solid #ddd",
                                borderRadius: "6px",
                            }}
                        >
                            <strong>
                                Observaciones
                                administrativas
                            </strong>

                            <p
                                style={{
                                    marginBottom: 0,
                                }}
                            >
                                {solicitud.observaciones ||
                                    "Sin observaciones registradas."}
                            </p>
                        </div>
                    </section>
                ))}
            </div>
        );
    }

    return (
        <div className="content">
            <div className="page-title">
                <h1>Confirmar matrícula</h1>

                <p>
                    Revise las solicitudes pendientes,
                    apruebe o rechace cada matrícula y
                    consulte las decisiones registradas.
                </p>
            </div>

            <div
                style={{
                    display: "flex",
                    justifyContent:
                        "space-between",
                    alignItems: "center",
                    gap: "0.75rem",
                    flexWrap: "wrap",
                    marginBottom: "1rem",
                }}
            >
                <button
                    type="button"
                    onClick={() =>
                        navigate(
                            "/admin/administracion/estudiantes"
                        )
                    }
                    disabled={
                        procesandoId !== null
                    }
                >
                    ← Volver a administración
                </button>

                <button
                    type="button"
                    onClick={() => {
                        void cargarDatos();
                    }}
                    disabled={
                        cargando ||
                        procesandoId !== null
                    }
                >
                    {cargando
                        ? "Actualizando..."
                        : "Actualizar"}
                </button>
            </div>

            {errorGeneral && (
                <div
                    role="alert"
                    style={{
                        marginBottom: "1rem",
                        padding: "0.85rem",
                        border:
                            "1px solid #c62828",
                        borderRadius: "6px",
                    }}
                >
                    {errorGeneral}
                </div>
            )}

            {mensajeExito && (
                <div
                    role="status"
                    style={{
                        marginBottom: "1rem",
                        padding: "0.85rem",
                        border:
                            "1px solid #2e7d32",
                        borderRadius: "6px",
                    }}
                >
                    {mensajeExito}
                </div>
            )}

            <div
                role="tablist"
                aria-label="Gestión de matrículas"
                style={{
                    display: "flex",
                    gap: "0.75rem",
                    marginBottom: "1.25rem",
                    flexWrap: "wrap",
                }}
            >
                <button
                    type="button"
                    role="tab"
                    aria-selected={
                        pestanaActiva ===
                        "pendientes"
                    }
                    onClick={() =>
                        setPestanaActiva(
                            "pendientes"
                        )
                    }
                    disabled={
                        procesandoId !== null
                    }
                    style={{
                        fontWeight:
                            pestanaActiva ===
                            "pendientes"
                                ? "bold"
                                : "normal",
                    }}
                >
                    Pendientes (
                    {solicitudes.length})
                </button>

                <button
                    type="button"
                    role="tab"
                    aria-selected={
                        pestanaActiva ===
                        "historial"
                    }
                    onClick={() =>
                        setPestanaActiva(
                            "historial"
                        )
                    }
                    disabled={
                        procesandoId !== null
                    }
                    style={{
                        fontWeight:
                            pestanaActiva ===
                            "historial"
                                ? "bold"
                                : "normal",
                    }}
                >
                    Historial ({historial.length})
                </button>
            </div>

            {cargando ? (
                <p>
                    Cargando información de
                    matrícula...
                </p>
            ) : pestanaActiva ===
            "pendientes" ? (
                renderPendientes()
            ) : (
                renderHistorial()
            )}
        </div>
    );
}

export default Matricula;
