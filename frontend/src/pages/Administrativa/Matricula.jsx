import { useCallback, useEffect, useRef, useState } from "react";
import { CheckCircle2, ClipboardCheck, Clock, History, RefreshCw, Save, Search, XCircle } from "lucide-react";
import { API_URL } from "../../config/api";
import "../AdminSis/styles/admin_Sis.css";
import "./matricula.css";
import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";
import { iniciales } from "../../components/ui/utilidades";

import { useDialog } from "../../components/dialog/useDialog";

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
            "No tiene permisos para gestionar preaprobaciones de matrícula."
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

// Insignia del estado de una preaprobación (texto legible + color)
const ETIQUETA_ESTADO = {
    PENDIENTE: "Pendiente",
    AUTORIZADA: "Habilitada",
    RECHAZADA: "Rechazada",
};

const TONO_ESTADO = {
    PENDIENTE: "warning",
    AUTORIZADA: "success",
    RECHAZADA: "danger",
};

function RequisitoItem({ etiqueta, cumplido, valor }) {
    return (
        <div
            className={`requisitos-list__item ${
                cumplido
                    ? "requisitos-list__item--ok"
                    : "requisitos-list__item--pending"
            }`}
        >
            {cumplido ? (
                <CheckCircle2 size={16} strokeWidth={1.75} aria-hidden="true" />
            ) : (
                <Clock size={16} strokeWidth={1.75} aria-hidden="true" />
            )}
            <span className="requisitos-list__label">{etiqueta}</span>
            <span className="requisitos-list__value">
                {valor ?? (cumplido ? "Validado" : "Pendiente")}
            </span>
        </div>
    );
}

function Matricula() {
    const dialog = useDialog();
    const token = localStorage.getItem("token");

    const [pestanaActiva, setPestanaActiva] =
        useState("pendientes");

    const [solicitudes, setSolicitudes] = useState([]);
    const [historial, setHistorial] = useState([]);

    const [cargando, setCargando] = useState(true);
    const [procesandoId, setProcesandoId] = useState(null);

    const [observaciones, setObservaciones] = useState({});

    // --- Iniciar/recalcular una preaprobación (Historia 1) ---
    const [periodos, setPeriodos] = useState([]);
    const [idPeriodoSeleccionado, setIdPeriodoSeleccionado] =
        useState("");

    const [textoBusquedaEstudiante, setTextoBusquedaEstudiante] =
        useState("");
    const [resultadosEstudiantes, setResultadosEstudiantes] =
        useState([]);
    const [buscandoEstudiantes, setBuscandoEstudiantes] =
        useState(false);
    const [estudianteSeleccionado, setEstudianteSeleccionado] =
        useState(null);

    const [creandoPreaprobacion, setCreandoPreaprobacion] =
        useState(false);

    const busquedaTimeoutRef = useRef(null);

    // --- Guardar el resultado de la revisión de documentación ---
    const [documentacionSeleccion, setDocumentacionSeleccion] =
        useState({});
    const [guardandoDocumentacionId, setGuardandoDocumentacionId] =
        useState(null);

    const cargarDatos = useCallback(async () => {
        setCargando(true);

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
                "No fue posible consultar las preaprobaciones pendientes."
            );

            validarEstadoRespuesta(
                responseHistorial,
                dataHistorial,
                "No fue posible consultar el historial de preaprobaciones."
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

            dialog.error(
                error.message ||
                "Ocurrió un error al consultar las preaprobaciones."
            );
        } finally {
            setCargando(false);
        }
    }, [token, dialog]);

    const cargarPeriodos = useCallback(async () => {
        try {
            const response = await fetch(
                `${API_URL}/periodos-academicos`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }
            );

            const data = await leerRespuesta(response);

            if (response.ok && Array.isArray(data)) {
                setPeriodos(data);
            }
        } catch {
            // La lista de periodos es un apoyo para el formulario;
            // si falla, el admin simplemente no puede crear una
            // preaprobación nueva hasta actualizar la página.
        }
    }, [token]);

    useEffect(() => {
        const timer = setTimeout(() => {
            void cargarDatos();
            void cargarPeriodos();
        }, 0);

        return () => clearTimeout(timer);
    }, [cargarDatos, cargarPeriodos]);

    function cambiarObservacion(
        idAutorizacion,
        valor
    ) {
        setObservaciones((prev) => ({
            ...prev,
            [idAutorizacion]: valor,
        }));

    }

    function requisitosValidos(solicitud) {
        return (
            solicitud.mensualidadesAlDia === true &&
            solicitud.pagoMatriculaConfirmado === true &&
            solicitud.documentacionCompleta === true &&
            solicitud.estudianteActivo === true &&
            solicitud.datosCompletos === true &&
            solicitud.idDocumentoComprobanteMatricula != null
        );
    }

    // --- Buscar estudiante para iniciar una preaprobación ---
    function cambiarTextoBusquedaEstudiante(valor) {
        setTextoBusquedaEstudiante(valor);
        setEstudianteSeleccionado(null);

        if (busquedaTimeoutRef.current) {
            clearTimeout(busquedaTimeoutRef.current);
        }

        if (!valor.trim()) {
            setResultadosEstudiantes([]);
            return;
        }

        busquedaTimeoutRef.current = setTimeout(async () => {
            setBuscandoEstudiantes(true);

            try {
                const response = await fetch(
                    `${API_URL}/estudiantes?texto=${encodeURIComponent(
                        valor.trim()
                    )}`,
                    {
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                    }
                );

                const data = await leerRespuesta(response);

                setResultadosEstudiantes(
                    response.ok && Array.isArray(data) ? data : []
                );
            } catch {
                setResultadosEstudiantes([]);
            } finally {
                setBuscandoEstudiantes(false);
            }
        }, 300);
    }

    function seleccionarEstudiante(estudiante) {
        setEstudianteSeleccionado(estudiante);
        setTextoBusquedaEstudiante(estudiante.nombreCompleto);
        setResultadosEstudiantes([]);
    }

    async function crearPreaprobacion() {

        if (!estudianteSeleccionado) {
            dialog.error(
                "Debe seleccionar un estudiante de la lista de resultados."
            );
            return;
        }

        if (!idPeriodoSeleccionado) {
            dialog.error(
                "Debe seleccionar el período académico vigente."
            );
            return;
        }

        setCreandoPreaprobacion(true);

        try {
            const response = await fetch(
                `${API_URL}/autorizaciones-matricula`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${token}`,
                    },
                    body: JSON.stringify({
                        idEstudiante: estudianteSeleccionado.id,
                        idPeriodo: Number(idPeriodoSeleccionado),
                    }),
                }
            );

            const data = await leerRespuesta(response);

            validarEstadoRespuesta(
                response,
                data,
                "No fue posible iniciar la preaprobación."
            );

            dialog.exito(
                `Se calcularon las comprobaciones automáticas para ${estudianteSeleccionado.nombreCompleto}. Revise el resultado en la pestaña Pendientes.`
            );

            setEstudianteSeleccionado(null);
            setTextoBusquedaEstudiante("");
            setResultadosEstudiantes([]);
            setPestanaActiva("pendientes");

            await cargarDatos();
        } catch (error) {
            dialog.error(
                error.message ||
                "Ocurrió un error al iniciar la preaprobación."
            );
        } finally {
            setCreandoPreaprobacion(false);
        }
    }

    // --- Registrar el resultado de la revisión de documentación ---
    function cambiarDocumentacionSeleccion(idAutorizacion, valor) {
        setDocumentacionSeleccion((prev) => ({
            ...prev,
            [idAutorizacion]: valor,
        }));
    }

    async function guardarDocumentacion(solicitud) {

        const completa =
            documentacionSeleccion[solicitud.idAutorizacion] ??
            solicitud.documentacionCompleta ??
            false;

        setGuardandoDocumentacionId(solicitud.idAutorizacion);

        try {
            const response = await fetch(
                `${API_URL}/autorizaciones-matricula/${solicitud.idAutorizacion}/documentacion`,
                {
                    method: "PATCH",
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${token}`,
                    },
                    body: JSON.stringify({
                        documentacionCompleta: completa,
                        observaciones:
                            observaciones[solicitud.idAutorizacion] || "",
                    }),
                }
            );

            const data = await leerRespuesta(response);

            validarEstadoRespuesta(
                response,
                data,
                "No fue posible registrar el resultado de la documentación."
            );

            dialog.exito(
                "Se registró el resultado de la revisión de documentación."
            );

            await cargarDatos();
        } catch (error) {
            dialog.error(
                error.message ||
                "Ocurrió un error al registrar la documentación."
            );
        } finally {
            setGuardandoDocumentacionId(null);
        }
    }

    async function confirmarSolicitud(
        solicitud,
        decision
    ) {

        if (
            decision === "APROBAR" &&
            !requisitosValidos(solicitud)
        ) {
            dialog.error(
                "No se puede habilitar la matrícula porque todavía existen requisitos pendientes."
            );
            return;
        }

        const observacion =
            observaciones[solicitud.idAutorizacion] || "";

        if (
            decision === "RECHAZAR" &&
            !observacion.trim()
        ) {
            dialog.error(
                "Debe indicar una observación administrativa para rechazar la solicitud."
            );
            return;
        }

        const textoDecision =
            decision === "APROBAR"
                ? "habilitar"
                : "rechazar";

        const confirmado = await dialog.confirmar(
            `¿Está seguro de que desea ${textoDecision} la matrícula de ${solicitud.estudiante}?`,
            {
                titulo: decision === "APROBAR"
                    ? "Habilitar matrícula"
                    : "Rechazar matrícula",
                textoAceptar: decision === "APROBAR"
                    ? "Habilitar"
                    : "Rechazar",
                peligro: decision !== "APROBAR",
            }
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
                "No fue posible procesar la preaprobación."
            );

            const mensaje =
                decision === "APROBAR"
                    ? "Estudiante habilitado para matricular en el período revisado. La decisión fue registrada en el historial y se notificó al estudiante."
                    : "Preaprobación rechazada correctamente. La decisión fue registrada en el historial y se notificó al estudiante.";

            dialog.exito(mensaje);

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
            dialog.error(
                error.message ||
                "Ocurrió un error al procesar la preaprobación."
            );
        } finally {
            setProcesandoId(null);
        }
    }

    function renderRequisitos(solicitud, interactivo) {
        const documentacionActual =
            documentacionSeleccion[solicitud.idAutorizacion] ??
            solicitud.documentacionCompleta ??
            false;

        return (
            <div className="review-block">
                <h3>Requisitos</h3>

                <div className="requisitos-list requisitos-list--bloque">
                    <RequisitoItem
                        etiqueta="Estudiante activo"
                        cumplido={solicitud.estudianteActivo === true}
                    />
                    <RequisitoItem
                        etiqueta="Datos personales completos"
                        cumplido={solicitud.datosCompletos === true}
                    />
                    <RequisitoItem
                        etiqueta="Mensualidades al día"
                        cumplido={solicitud.mensualidadesAlDia === true}
                    />
                    <RequisitoItem
                        etiqueta="Pago de matrícula"
                        cumplido={solicitud.pagoMatriculaConfirmado === true}
                        valor={
                            solicitud.comprobanteMatricula ||
                            (solicitud.pagoMatriculaConfirmado
                                ? "Validado"
                                : "No registrado")
                        }
                    />
                    {interactivo ? (
                        <label
                            className={`requisitos-list__item requisito-check ${documentacionActual ? "requisitos-list__item--ok" : "requisitos-list__item--pending"}`}
                            htmlFor={`documentacion-${solicitud.idAutorizacion}`}
                        >
                            {documentacionActual
                                ? <CheckCircle2 size={16} strokeWidth={1.75} aria-hidden="true" />
                                : <Clock size={16} strokeWidth={1.75} aria-hidden="true" />}
                            <span className="requisitos-list__label">Documentación completa</span>
                            <input
                                id={`documentacion-${solicitud.idAutorizacion}`}
                                type="checkbox"
                                checked={Boolean(documentacionActual)}
                                onChange={(event) =>
                                    cambiarDocumentacionSeleccion(
                                        solicitud.idAutorizacion,
                                        event.target.checked
                                    )
                                }
                                disabled={guardandoDocumentacionId === solicitud.idAutorizacion}
                            />
                        </label>
                    ) : (
                        <RequisitoItem
                            etiqueta="Documentación completa"
                            cumplido={solicitud.documentacionCompleta === true}
                        />
                    )}
                </div>

                {interactivo && (
                    <div className="requisitos-acciones">
                        <button
                            type="button"
                            className="sg-btn sg-btn--secondary sg-btn--sm"
                            onClick={() => guardarDocumentacion(solicitud)}
                            disabled={guardandoDocumentacionId === solicitud.idAutorizacion}
                        >
                            <Save size={16} strokeWidth={1.75} aria-hidden="true" />
                            {guardandoDocumentacionId === solicitud.idAutorizacion
                                ? "Guardando…"
                                : "Guardar documentación"}
                        </button>
                    </div>
                )}
            </div>
        );
    }

    function encabezadoSolicitud(solicitud) {
        return (
            <div className="panel-head">
                <div className="who">
                    <span className="avatar" aria-hidden="true">{iniciales(solicitud.estudiante)}</span>
                    <div>
                        <b>{solicitud.estudiante}</b>
                        <span>
                            Preaprobación #{solicitud.idAutorizacion} · {solicitud.periodo}
                            {solicitud.codigoPeriodo ? ` (${solicitud.codigoPeriodo})` : ""}
                        </span>
                    </div>
                </div>
                <StatusBadge tone={TONO_ESTADO[solicitud.estado] || "warning"}>
                    {ETIQUETA_ESTADO[solicitud.estado] || solicitud.estado}
                </StatusBadge>
            </div>
        );
    }

    function renderFormularioNuevaPreaprobacion() {
        return (
            <section className="panel">
                <div className="panel-head">
                    <div>
                        <h2>Iniciar preaprobación</h2>
                        <p className="panel-head-sub">
                            Busque al estudiante, seleccione el período académico vigente y el sistema
                            calculará automáticamente sus comprobaciones. No genera matrícula ni reserva cupos.
                        </p>
                    </div>
                </div>

                <div className="panel-body form-grid">
                    <div className="buscador-estudiante">
                        <Field id="txtEstudiantePreaprobacion" label="Estudiante" icon={Search}>
                            <input
                                id="txtEstudiantePreaprobacion"
                                className="sg-input"
                                type="search"
                                autoComplete="off"
                                placeholder="Nombre o número de identificación"
                                value={textoBusquedaEstudiante}
                                onChange={(event) => cambiarTextoBusquedaEstudiante(event.target.value)}
                                disabled={creandoPreaprobacion}
                            />
                        </Field>

                        {textoBusquedaEstudiante.trim().length >= 2 && !estudianteSeleccionado && (
                            <div className="search-results" role="listbox" aria-label="Resultados de la búsqueda">
                                {buscandoEstudiantes && (
                                    <div className="search-results__empty">Buscando...</div>
                                )}
                                {!buscandoEstudiantes && resultadosEstudiantes.length === 0 && (
                                    <div className="search-results__empty">Sin coincidencias.</div>
                                )}
                                {!buscandoEstudiantes && resultadosEstudiantes.map((estudiante) => (
                                    <button
                                        type="button"
                                        key={estudiante.id}
                                        role="option"
                                        aria-selected="false"
                                        className="search-results__item"
                                        onClick={() => seleccionarEstudiante(estudiante)}
                                    >
                                        <span className="who">
                                            <span className="avatar avatar--sm" aria-hidden="true">{iniciales(estudiante.nombreCompleto)}</span>
                                            <b>{estudiante.nombreCompleto}</b>
                                        </span>
                                        <span className="search-results__cedula">{estudiante.numeroIdentificacion}</span>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    <Field id="cmbPeriodoPreaprobacion" label="Período académico" select>
                        <select
                            id="cmbPeriodoPreaprobacion"
                            className="sg-input sg-select"
                            value={idPeriodoSeleccionado}
                            onChange={(event) => setIdPeriodoSeleccionado(event.target.value)}
                            disabled={creandoPreaprobacion}
                        >
                            <option value="">Seleccione un período</option>
                            {periodos.map((periodo) => (
                                <option key={periodo.id} value={periodo.id}>
                                    {periodo.nombre} ({periodo.codigo})
                                </option>
                            ))}
                        </select>
                    </Field>
                </div>

                <div className="panel-foot">
                    <button
                        type="button"
                        className={`sg-btn sg-btn--primary${creandoPreaprobacion ? " is-loading" : ""}`}
                        onClick={crearPreaprobacion}
                        disabled={creandoPreaprobacion}
                        aria-busy={creandoPreaprobacion || undefined}
                    >
                        {creandoPreaprobacion ? <span className="sg-spinner" aria-hidden="true" /> : <ClipboardCheck size={18} strokeWidth={1.75} aria-hidden="true" />}
                        {creandoPreaprobacion ? "Calculando…" : "Iniciar preaprobación"}
                    </button>
                </div>
            </section>
        );
    }

    function renderPendientes() {
        if (solicitudes.length === 0) {
            return (
                <div className="panel">
                    <EmptyState
                        icon={ClipboardCheck}
                        title="Sin preaprobaciones pendientes"
                        description="No existen preaprobaciones de matrícula pendientes de revisión en este momento. Puede consultar las decisiones anteriores desde la pestaña Historial."
                    />
                </div>
            );
        }

        return (
            <div className="stack">
                {solicitudes.map((solicitud) => {
                    const puedeAprobar = requisitosValidos(solicitud);
                    const procesando = procesandoId === solicitud.idAutorizacion;
                    const texto = observaciones[solicitud.idAutorizacion] || "";

                    return (
                        <section className="panel" key={solicitud.idAutorizacion}>
                            {encabezadoSolicitud(solicitud)}

                            <div className="panel-body stack">
                                {renderRequisitos(solicitud, true)}

                                {!puedeAprobar && (
                                    <Alert tone="warning" title="Aún no se puede habilitar">
                                        Esta preaprobación no puede habilitarse hasta que todos los requisitos estén validados.
                                    </Alert>
                                )}

                                <Field
                                    id={`observaciones-${solicitud.idAutorizacion}`}
                                    label="Observaciones administrativas"
                                    optional
                                    hint={`${texto.length}/1500 caracteres`}
                                >
                                    <textarea
                                        id={`observaciones-${solicitud.idAutorizacion}`}
                                        className="sg-input"
                                        value={texto}
                                        onChange={(event) => cambiarObservacion(solicitud.idAutorizacion, event.target.value)}
                                        maxLength={1500}
                                        placeholder="Ingrese observaciones sobre la revisión..."
                                        disabled={procesando}
                                    />
                                </Field>
                            </div>

                            <div className="panel-foot">
                                <button
                                    type="button"
                                    className="sg-btn sg-btn--secondary btn-rechazar"
                                    onClick={() => confirmarSolicitud(solicitud, "RECHAZAR")}
                                    disabled={procesando}
                                >
                                    <XCircle size={18} strokeWidth={1.75} aria-hidden="true" />
                                    {procesando ? "Procesando…" : "Rechazar"}
                                </button>

                                <button
                                    type="button"
                                    className="sg-btn sg-btn--primary"
                                    onClick={() => confirmarSolicitud(solicitud, "APROBAR")}
                                    disabled={procesando || !puedeAprobar}
                                >
                                    <CheckCircle2 size={18} strokeWidth={1.75} aria-hidden="true" />
                                    {procesando ? "Procesando…" : "Habilitar matrícula"}
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
                <div className="panel">
                    <EmptyState
                        icon={History}
                        title="Historial vacío"
                        description="Todavía no existen preaprobaciones de matrícula habilitadas o rechazadas."
                    />
                </div>
            );
        }

        return (
            <div className="stack">
                {historial.map((solicitud) => (
                    <section className="panel" key={solicitud.idAutorizacion}>
                        {encabezadoSolicitud(solicitud)}

                        <div className="panel-body stack">
                            <p className="muted revision-detalle">
                                Revisado por {solicitud.revisadoPor || "No registrado"} el {formatearFecha(solicitud.fechaRevision)}
                            </p>

                            {renderRequisitos(solicitud, false)}

                            <div className="review-block">
                                <h3>Observaciones administrativas</h3>
                                <p className="review-nota">
                                    {solicitud.observaciones || "Sin observaciones registradas."}
                                </p>
                            </div>
                        </div>
                    </section>
                ))}
            </div>
        );
    }

    return (
        <div className="matricula-admin">
            <PageHead
                title="Preaprobar requisitos de matrícula"
                sub="Revise el estado del estudiante, sus datos personales, mensualidades y pago de matrícula, habilite o rechace la preaprobación y consulte las decisiones registradas. Esta revisión no genera matrícula ni reserva cupos."
                actions={(
                    <button
                        type="button"
                        className="sg-btn sg-btn--secondary"
                        onClick={() => { void cargarDatos(); }}
                        disabled={cargando || procesandoId !== null}
                    >
                        <RefreshCw size={18} strokeWidth={1.75} aria-hidden="true" />
                        {cargando ? "Actualizando…" : "Actualizar"}
                    </button>
                )}
            />

            {renderFormularioNuevaPreaprobacion()}

            <div className="tabs pestanas-matricula" role="tablist" aria-label="Gestión de preaprobaciones de matrícula">
                <button
                    type="button"
                    role="tab"
                    aria-selected={pestanaActiva === "pendientes"}
                    onClick={() => setPestanaActiva("pendientes")}
                    disabled={procesandoId !== null}
                >
                    <Clock size={18} strokeWidth={1.75} aria-hidden="true" />
                    Pendientes ({solicitudes.length})
                </button>

                <button
                    type="button"
                    role="tab"
                    aria-selected={pestanaActiva === "historial"}
                    onClick={() => setPestanaActiva("historial")}
                    disabled={procesandoId !== null}
                >
                    <History size={18} strokeWidth={1.75} aria-hidden="true" />
                    Historial ({historial.length})
                </button>
            </div>

            <div role="tabpanel">
                {cargando ? (
                    <p className="muted">Cargando información de preaprobaciones...</p>
                ) : pestanaActiva === "pendientes" ? (
                    renderPendientes()
                ) : (
                    renderHistorial()
                )}
            </div>
        </div>
    );
}

export default Matricula;
