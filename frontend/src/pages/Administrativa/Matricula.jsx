import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Search, CheckCircle2, Clock, AlertTriangle } from "lucide-react";
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

function badgeEstadoClase(estado) {
    if (estado === "AUTORIZADA") return "badge badge-autorizada";
    if (estado === "RECHAZADA") return "badge badge-rechazada";
    return "badge badge-pendiente";
}

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
                <CheckCircle2 size={16} />
            ) : (
                <Clock size={16} />
            )}
            <span className="requisitos-list__label">{etiqueta}</span>
            <span className="requisitos-list__value">
                {valor ?? (cumplido ? "Validado" : "Pendiente")}
            </span>
        </div>
    );
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

            setErrorGeneral(
                error.message ||
                "Ocurrió un error al consultar las preaprobaciones."
            );
        } finally {
            setCargando(false);
        }
    }, [token]);

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

        setErrorGeneral("");
        setMensajeExito("");
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
        setErrorGeneral("");
        setMensajeExito("");

        if (!estudianteSeleccionado) {
            setErrorGeneral(
                "Debe seleccionar un estudiante de la lista de resultados."
            );
            return;
        }

        if (!idPeriodoSeleccionado) {
            setErrorGeneral(
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

            setMensajeExito(
                `Se calcularon las comprobaciones automáticas para ${estudianteSeleccionado.nombreCompleto}. Revise el resultado en la pestaña Pendientes.`
            );

            setEstudianteSeleccionado(null);
            setTextoBusquedaEstudiante("");
            setResultadosEstudiantes([]);
            setPestanaActiva("pendientes");

            await cargarDatos();
        } catch (error) {
            setErrorGeneral(
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
        setErrorGeneral("");
        setMensajeExito("");

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

            setMensajeExito(
                "Se registró el resultado de la revisión de documentación."
            );

            await cargarDatos();
        } catch (error) {
            setErrorGeneral(
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
        setErrorGeneral("");
        setMensajeExito("");

        if (
            decision === "APROBAR" &&
            !requisitosValidos(solicitud)
        ) {
            setErrorGeneral(
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
            setErrorGeneral(
                "Debe indicar una observación administrativa para rechazar la solicitud."
            );
            return;
        }

        const textoDecision =
            decision === "APROBAR"
                ? "habilitar"
                : "rechazar";

        const confirmado = window.confirm(
            `¿Está seguro de que desea ${textoDecision} la matrícula de ${solicitud.estudiante}?`
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
            <div className="subcard">
                <div className="subcard-header">Requisitos</div>

                <div className="requisitos-list">
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
                        <div className="requisitos-list__item">
                            {documentacionActual ? (
                                <CheckCircle2 size={16} />
                            ) : (
                                <Clock size={16} />
                            )}
                            <label
                                className="requisitos-list__label"
                                htmlFor={`documentacion-${solicitud.idAutorizacion}`}
                            >
                                Documentación completa
                            </label>
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
                                disabled={
                                    guardandoDocumentacionId ===
                                    solicitud.idAutorizacion
                                }
                                style={{ marginLeft: "auto" }}
                            />
                        </div>
                    ) : (
                        <RequisitoItem
                            etiqueta="Documentación completa"
                            cumplido={solicitud.documentacionCompleta === true}
                        />
                    )}
                </div>

                {interactivo && (
                    <div className="actions-row" style={{ paddingTop: "14px" }}>
                        <button
                            type="button"
                            className="btn btn-secondary"
                            onClick={() =>
                                guardarDocumentacion(solicitud)
                            }
                            disabled={
                                guardandoDocumentacionId ===
                                solicitud.idAutorizacion
                            }
                        >
                            {guardandoDocumentacionId ===
                            solicitud.idAutorizacion
                                ? "Guardando..."
                                : "Guardar documentación"}
                        </button>
                    </div>
                )}
            </div>
        );
    }

    function renderFormularioNuevaPreaprobacion() {
        return (
            <section className="card">
                <div className="card-header-row">
                    <div>
                        <h2 className="card-title">Iniciar preaprobación</h2>
                        <p className="card-subtitle">
                            Busque al estudiante, seleccione el período
                            académico vigente y el sistema calculará
                            automáticamente sus comprobaciones. No genera
                            matrícula ni reserva cupos.
                        </p>
                    </div>
                </div>

                <div className="grid-2">
                    <div className="field" style={{ position: "relative" }}>
                        <label className="field-label">Estudiante</label>
                        <div className="input-suffix-wrap">
                            <input
                                className="input"
                                placeholder="Nombre o número de identificación..."
                                value={textoBusquedaEstudiante}
                                onChange={(event) =>
                                    cambiarTextoBusquedaEstudiante(
                                        event.target.value
                                    )
                                }
                                disabled={creandoPreaprobacion}
                            />
                            <span className="input-suffix">
                                <Search size={16} />
                            </span>
                        </div>

                        {textoBusquedaEstudiante.trim().length >= 2 &&
                            !estudianteSeleccionado && (
                                <div className="search-results">
                                    {buscandoEstudiantes && (
                                        <div className="search-results__empty">
                                            Buscando...
                                        </div>
                                    )}
                                    {!buscandoEstudiantes &&
                                        resultadosEstudiantes.length === 0 && (
                                            <div className="search-results__empty">
                                                Sin coincidencias.
                                            </div>
                                        )}
                                    {!buscandoEstudiantes &&
                                        resultadosEstudiantes.map(
                                            (estudiante) => (
                                                <button
                                                    type="button"
                                                    key={estudiante.id}
                                                    className="search-results__item"
                                                    onClick={() =>
                                                        seleccionarEstudiante(
                                                            estudiante
                                                        )
                                                    }
                                                >
                                                    <span>
                                                        {estudiante.nombreCompleto}
                                                    </span>
                                                    <span className="search-results__cedula">
                                                        {
                                                            estudiante.numeroIdentificacion
                                                        }
                                                    </span>
                                                </button>
                                            )
                                        )}
                                </div>
                            )}
                    </div>

                    <div className="field">
                        <label className="field-label">
                            Período académico
                        </label>
                        <select
                            className="input"
                            value={idPeriodoSeleccionado}
                            onChange={(event) =>
                                setIdPeriodoSeleccionado(
                                    event.target.value
                                )
                            }
                            disabled={creandoPreaprobacion}
                        >
                            <option value="">Seleccione un período</option>
                            {periodos.map((periodo) => (
                                <option
                                    key={periodo.id}
                                    value={periodo.id}
                                >
                                    {periodo.nombre} ({periodo.codigo})
                                </option>
                            ))}
                        </select>
                    </div>
                </div>

                <div className="actions-row">
                    <button
                        type="button"
                        className="btn btn-primary"
                        onClick={crearPreaprobacion}
                        disabled={creandoPreaprobacion}
                    >
                        {creandoPreaprobacion
                            ? "Calculando..."
                            : "Iniciar preaprobación"}
                    </button>
                </div>
            </section>
        );
    }

    function renderPendientes() {
        if (solicitudes.length === 0) {
            return (
                <section className="card">
                    <h2 className="card-title">
                        Sin preaprobaciones pendientes
                    </h2>
                    <p className="card-subtitle">
                        No existen preaprobaciones de matrícula
                        pendientes de revisión en este momento. Puede
                        consultar las decisiones anteriores desde la
                        pestaña Historial.
                    </p>
                </section>
            );
        }

        return (
            <>
                {solicitudes.map((solicitud) => {
                    const puedeAprobar =
                        requisitosValidos(solicitud);

                    const procesando =
                        procesandoId === solicitud.idAutorizacion;

                    return (
                        <section
                            className="card"
                            key={solicitud.idAutorizacion}
                        >
                            <div className="card-header-row">
                                <div>
                                    <h2 className="card-title">
                                        {solicitud.estudiante}
                                    </h2>
                                    <p className="card-subtitle">
                                        Preaprobación #
                                        {solicitud.idAutorizacion} ·{" "}
                                        {solicitud.periodo}
                                        {solicitud.codigoPeriodo
                                            ? ` (${solicitud.codigoPeriodo})`
                                            : ""}
                                    </p>
                                </div>
                                <span
                                    className={badgeEstadoClase(
                                        solicitud.estado
                                    )}
                                >
                                    {solicitud.estado}
                                </span>
                            </div>

                            {renderRequisitos(solicitud, true)}

                            {!puedeAprobar && (
                                <div className="banner banner-warning">
                                    <AlertTriangle size={16} />
                                    Esta preaprobación no puede
                                    habilitarse hasta que todos los
                                    requisitos estén validados.
                                </div>
                            )}

                            <div className="field">
                                <label
                                    className="field-label"
                                    htmlFor={`observaciones-${solicitud.idAutorizacion}`}
                                >
                                    Observaciones administrativas
                                </label>

                                <textarea
                                    id={`observaciones-${solicitud.idAutorizacion}`}
                                    className="input textarea"
                                    value={
                                        observaciones[
                                            solicitud.idAutorizacion
                                            ] || ""
                                    }
                                    onChange={(event) =>
                                        cambiarObservacion(
                                            solicitud.idAutorizacion,
                                            event.target.value
                                        )
                                    }
                                    maxLength={1500}
                                    placeholder="Ingrese observaciones sobre la revisión..."
                                    disabled={procesando}
                                />

                                <p className="hint">
                                    {
                                        (
                                            observaciones[
                                                solicitud.idAutorizacion
                                                ] || ""
                                        ).length
                                    }
                                    /1500
                                </p>
                            </div>

                            <div className="actions-row">
                                <button
                                    type="button"
                                    className="btn btn-secondary"
                                    onClick={() =>
                                        confirmarSolicitud(
                                            solicitud,
                                            "RECHAZAR"
                                        )
                                    }
                                    disabled={procesando}
                                >
                                    {procesando
                                        ? "Procesando..."
                                        : "Rechazar"}
                                </button>

                                <button
                                    type="button"
                                    className="btn btn-primary"
                                    onClick={() =>
                                        confirmarSolicitud(
                                            solicitud,
                                            "APROBAR"
                                        )
                                    }
                                    disabled={procesando || !puedeAprobar}
                                >
                                    {procesando
                                        ? "Procesando..."
                                        : "Habilitar matrícula"}
                                </button>
                            </div>
                        </section>
                    );
                })}
            </>
        );
    }

    function renderHistorial() {
        if (historial.length === 0) {
            return (
                <section className="card">
                    <h2 className="card-title">Historial vacío</h2>
                    <p className="card-subtitle">
                        Todavía no existen preaprobaciones de matrícula
                        habilitadas o rechazadas.
                    </p>
                </section>
            );
        }

        return (
            <>
                {historial.map((solicitud) => (
                    <section
                        className="card"
                        key={solicitud.idAutorizacion}
                    >
                        <div className="card-header-row">
                            <div>
                                <h2 className="card-title">
                                    {solicitud.estudiante}
                                </h2>
                                <p className="card-subtitle">
                                    Preaprobación #
                                    {solicitud.idAutorizacion} ·{" "}
                                    {solicitud.periodo}
                                    {solicitud.codigoPeriodo
                                        ? ` (${solicitud.codigoPeriodo})`
                                        : ""}
                                </p>
                            </div>
                            <span
                                className={badgeEstadoClase(
                                    solicitud.estado
                                )}
                            >
                                {solicitud.estado}
                            </span>
                        </div>

                        <p className="hint">
                            Revisado por{" "}
                            {solicitud.revisadoPor || "No registrado"}{" "}
                            el {formatearFecha(solicitud.fechaRevision)}
                        </p>

                        {renderRequisitos(solicitud, false)}

                        <div className="subcard">
                            <div className="subcard-header">
                                Observaciones administrativas
                            </div>
                            <p style={{ margin: 0 }}>
                                {solicitud.observaciones ||
                                    "Sin observaciones registradas."}
                            </p>
                        </div>
                    </section>
                ))}
            </>
        );
    }

    return (
        <main className="content">
            <div className="page-title">
                <h1>Preaprobar requisitos de matrícula</h1>
                <p>
                    Revise el estado del estudiante, sus datos
                    personales, mensualidades y pago de matrícula,
                    habilite o rechace la preaprobación y consulte las
                    decisiones registradas. Esta revisión no genera
                    matrícula ni reserva cupos.
                </p>
            </div>

            <div className="actions-row" style={{ borderTop: "none", paddingTop: 0 }}>
                <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() =>
                        navigate("/admin/administracion/estudiantes")
                    }
                    disabled={procesandoId !== null}
                >
                    ← Volver a administración
                </button>

                <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => {
                        void cargarDatos();
                    }}
                    disabled={cargando || procesandoId !== null}
                >
                    {cargando ? "Actualizando..." : "Actualizar"}
                </button>
            </div>

            <br></br>

            {errorGeneral && (
                <div className="banner banner-danger" role="alert">
                    <AlertTriangle size={16} />
                    {errorGeneral}
                </div>
            )}

            {mensajeExito && (
                <div className="banner banner-success" role="status">
                    <CheckCircle2 size={16} />
                    {mensajeExito}
                </div>
            )}

            {renderFormularioNuevaPreaprobacion()}

            <div
                className="tabs"
                role="tablist"
                aria-label="Gestión de preaprobaciones de matrícula"
            >
                <button
                    type="button"
                    role="tab"
                    aria-selected={pestanaActiva === "pendientes"}
                    className={`tab ${
                        pestanaActiva === "pendientes" ? "tab--active" : ""
                    }`}
                    onClick={() => setPestanaActiva("pendientes")}
                    disabled={procesandoId !== null}
                >
                    Pendientes ({solicitudes.length})
                </button>

                <button
                    type="button"
                    role="tab"
                    aria-selected={pestanaActiva === "historial"}
                    className={`tab ${
                        pestanaActiva === "historial" ? "tab--active" : ""
                    }`}
                    onClick={() => setPestanaActiva("historial")}
                    disabled={procesandoId !== null}
                >
                    Historial ({historial.length})
                </button>
            </div>

            {cargando ? (
                <p className="hint">
                    Cargando información de preaprobaciones...
                </p>
            ) : pestanaActiva === "pendientes" ? (
                renderPendientes()
            ) : (
                renderHistorial()
            )}
        </main>
    );
}

export default Matricula;
