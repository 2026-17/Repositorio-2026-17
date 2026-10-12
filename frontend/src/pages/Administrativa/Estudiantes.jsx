import { useEffect, useMemo, useState } from "react";
import {
    Search,
    Wallet,
    Receipt,
    CheckCircle2,
    Clock,
    X,
    Eye,
    Trash2,
} from "lucide-react";
import { API_URL } from "../../config/api";
import "../AdminSis/styles/admin_Sis.css";
import "./becasPagos.css";
import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";
import { iniciales, listaErrores } from "../../components/ui/utilidades";

import { useDialog } from "../../components/dialog/useDialog";
import { lanzarError, mensajeDeExcepcion } from "../../utils/api";

// Mismo límite que valida el backend (PagoService).
const MAX_COMPROBANTE = 15 * 1024 * 1024;
const MAX_OBSERVACIONES = 1000;

// Clave de la casilla de matrícula (los meses usan "anio-mes").
const MATRICULA = "matricula";

const MESES = [
    "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
];

function todayISO() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function formatoColones(valor) {
    if (valor === null || valor === undefined) return "—";
    return `₡${Number(valor).toLocaleString("es-CR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function claveMes(m) {
    return `${m.anio}-${m.mes}`;
}

function Estudiantes() {
    const dialog = useDialog();

    const token = localStorage.getItem("token");
    const authHeaders = token ? { Authorization: `Bearer ${token}` } : {};

    // --- Búsqueda de estudiante ---
    const [texto, setTexto] = useState("");
    const [resultados, setResultados] = useState([]);
    const [buscando, setBuscando] = useState(false);

    // --- Estudiante seleccionado ---
    const [estudiante, setEstudiante] = useState(null);
    const [cargandoPerfil, setCargandoPerfil] = useState(false);

    // --- Períodos académicos ---
    const [periodos, setPeriodos] = useState([]);
    const [errorPeriodos, setErrorPeriodos] = useState("");
    const [idPeriodo, setIdPeriodo] = useState("");

    // --- Estado de la matrícula y de cada mes del período ---
    const [estadoPeriodo, setEstadoPeriodo] = useState(null);
    const [cargandoEstado, setCargandoEstado] = useState(false);
    // Casillas marcadas: "matricula" y/o "anio-mes"
    const [seleccion, setSeleccion] = useState([]);

    // --- Formulario "Registrar pago" ---
    const [fechaPago, setFechaPago] = useState(todayISO());
    const [observaciones, setObservaciones] = useState("");
    const [comprobante, setComprobante] = useState(null);
    const [erroresPago, setErroresPago] = useState({});
    const [guardandoPago, setGuardandoPago] = useState(false);
    const [intentoPago, setIntentoPago] = useState(0);

    // --- Ver / eliminar un pago ya registrado ---
    // { tipo: "mes" | "matricula", id, etiqueta }
    const [pagoAEliminar, setPagoAEliminar] = useState(null);
    const [eliminandoPago, setEliminandoPago] = useState(false);
    const [abriendoComprobante, setAbriendoComprobante] = useState("");

    // Catálogo de períodos académicos, se carga una sola vez.
    useEffect(() => {
        fetch(`${API_URL}/periodos-academicos`, { headers: authHeaders })
            .then((r) => (r.ok ? r.json() : lanzarError(r, "No se pudieron cargar los períodos académicos.")))
            .then(setPeriodos)
            .catch((e) => {
                setPeriodos([]);
                setErrorPeriodos(mensajeDeExcepcion(e, "No se pudieron cargar los períodos académicos. Intente de nuevo en unos momentos."));
            });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Búsqueda con un pequeño debounce para no golpear el backend en cada tecla.
    useEffect(() => {
        const consulta = texto.trim();
        if (consulta.length < 2) {
            setResultados([]);
            return;
        }

        setBuscando(true);
        const temporizador = setTimeout(async () => {
            try {
                const response = await fetch(`${API_URL}/estudiantes?texto=${encodeURIComponent(consulta)}`, {
                    headers: authHeaders,
                });
                if (!response.ok) await lanzarError(response, "No fue posible buscar estudiantes.");
                const data = await response.json();
                setResultados(data);
            } catch (e) {
                setResultados([]);
                dialog.error(mensajeDeExcepcion(e, "No fue posible buscar estudiantes."));
            } finally {
                setBuscando(false);
            }
        }, 300);

        return () => clearTimeout(temporizador);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [texto]);

    function limpiarFormulario() {
        setSeleccion([]);
        setFechaPago(todayISO());
        setObservaciones("");
        setComprobante(null);
        setErroresPago({});
        setPagoAEliminar(null);
    }

    function limpiarSeleccion() {
        setIdPeriodo("");
        setEstadoPeriodo(null);
        limpiarFormulario();
    }

    async function seleccionarEstudiante(idEstudiante) {
        setTexto("");
        setResultados([]);
        setEstudiante(null);
        setCargandoPerfil(true);
        limpiarSeleccion();

        try {
            const response = await fetch(`${API_URL}/estudiantes/${idEstudiante}`, { headers: authHeaders });
            if (!response.ok) await lanzarError(response, "No fue posible cargar la información del estudiante.");
            const data = await response.json();
            setEstudiante(data);
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No fue posible cargar la información del estudiante."));
        } finally {
            setCargandoPerfil(false);
        }
    }

    const nombreCompleto = useMemo(() => {
        if (!estudiante) return "";
        return [estudiante.nombres, estudiante.primerApellido, estudiante.segundoApellido]
            .filter(Boolean)
            .join(" ");
    }, [estudiante]);

    const periodoSeleccionado = useMemo(
        () => periodos.find((p) => String(p.id) === String(idPeriodo)) || null,
        [periodos, idPeriodo]
    );

    // La fecha de pago no puede ser anterior al inicio de la matrícula del
    // período (o al inicio del período si no tiene). Misma regla que el backend.
    const fechaMinimaPago = periodoSeleccionado?.fechaInicioMatricula || periodoSeleccionado?.fechaInicio || "";

    async function consultarEstadoPeriodo(periodo = idPeriodo) {
        if (!estudiante || !periodo) return;

        setCargandoEstado(true);
        setEstadoPeriodo(null);
        limpiarFormulario();

        try {
            const response = await fetch(`${API_URL}/pagos/estudiante/${estudiante.id}/periodo/${periodo}`, {
                headers: authHeaders,
            });
            const body = await response.json().catch(() => ({}));

            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para consultar pagos.");
            if (!response.ok) throw new Error(body.message || "No fue posible consultar el estado de pago.");

            setEstadoPeriodo(body);
        } catch (e) {
            setEstadoPeriodo(null);
            dialog.error(e.message || "Ocurrió un error al consultar el estado de pago.");
        } finally {
            setCargandoEstado(false);
        }
    }

    function alternarSeleccion(clave) {
        setSeleccion((prev) => (prev.includes(clave) ? prev.filter((c) => c !== clave) : [...prev, clave]));
        setErroresPago((er) => ({ ...er, seleccion: "" }));
    }

    const mesesSeleccionados = (estadoPeriodo?.meses || []).filter((m) => seleccion.includes(claveMes(m)));
    const incluirMatricula = seleccion.includes(MATRICULA);
    const totalMeses = mesesSeleccionados.length * Number(estadoPeriodo?.montoEsperado || 0);

    async function registrarPago(evento) {
        evento.preventDefault();

        const nuevosErrores = {};
        if (seleccion.length === 0) nuevosErrores.seleccion = "Marque al menos un mes o la matrícula.";
        if (!fechaPago) nuevosErrores.fechaPago = "Ingrese la fecha de pago.";
        else if (fechaPago > todayISO()) nuevosErrores.fechaPago = "La fecha de pago no puede ser futura.";
        else if (fechaMinimaPago && fechaPago < fechaMinimaPago) {
            nuevosErrores.fechaPago = periodoSeleccionado?.fechaInicioMatricula
                ? "La fecha de pago no puede ser anterior al inicio de la matrícula del período."
                : "La fecha de pago no puede ser anterior al inicio del período.";
        }
        if (observaciones.length > MAX_OBSERVACIONES) {
            nuevosErrores.observaciones = `Las observaciones no pueden superar los ${MAX_OBSERVACIONES} caracteres.`;
        }
        if (!comprobante) nuevosErrores.comprobante = "Debe adjuntar el comprobante de pago.";
        else if (comprobante.size > MAX_COMPROBANTE) nuevosErrores.comprobante = "El comprobante no puede superar 15 MB.";

        setErroresPago(nuevosErrores);
        if (Object.keys(nuevosErrores).length > 0) {
            setIntentoPago((n) => n + 1);
            return;
        }

        const payload = {
            idEstudiante: estudiante.id,
            idPeriodo: Number(idPeriodo),
            meses: mesesSeleccionados.map(({ anio, mes }) => ({ anio, mes })),
            incluirMatricula,
            fechaPago,
            observaciones: observaciones.trim() || null,
        };

        const data = new FormData();
        data.append("pago", new Blob([JSON.stringify(payload)], { type: "application/json" }));
        data.append("comprobante", comprobante);

        try {
            setGuardandoPago(true);
            const response = await fetch(`${API_URL}/pagos/lote`, {
                method: "POST",
                headers: authHeaders,
                body: data,
            });
            const body = await response.json().catch(() => ({}));

            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para registrar pagos.");
            if (!response.ok) throw new Error(body.message || "No fue posible registrar el pago.");

            // La respuesta ya trae el estado actualizado del período.
            setEstadoPeriodo(body);
            limpiarFormulario();
            dialog.exito(seleccion.length === 1 ? "Pago registrado correctamente." : "Pagos registrados correctamente.");
        } catch (e) {
            dialog.error(e.message || "Ocurrió un error al registrar el pago.");
        } finally {
            setGuardandoPago(false);
        }
    }

    async function verComprobante(url, clave) {
        setAbriendoComprobante(clave);
        try {
            const response = await fetch(url, { headers: authHeaders });
            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para ver el comprobante.");
            if (!response.ok) {
                await lanzarError(response, response.status === 404
                    ? "No se encontró el comprobante de este pago."
                    : "No fue posible abrir el comprobante.");
            }
            const blob = await response.blob();
            const enlace = URL.createObjectURL(blob);
            window.open(enlace, "_blank", "noopener,noreferrer");
        } catch (e) {
            dialog.error(e.message || "No fue posible abrir el comprobante.");
        } finally {
            setAbriendoComprobante("");
        }
    }

    async function eliminarPagoAhora() {
        const url = pagoAEliminar.tipo === "matricula"
            ? `${API_URL}/pagos/matricula/${pagoAEliminar.id}`
            : `${API_URL}/pagos/${pagoAEliminar.id}`;
        try {
            setEliminandoPago(true);
            const response = await fetch(url, { method: "DELETE", headers: authHeaders });
            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para eliminar pagos.");
            if (!response.ok) {
                await lanzarError(response, response.status === 404
                    ? "No se encontró el pago. Es posible que ya haya sido eliminado."
                    : "No fue posible eliminar el pago.");
            }
            setPagoAEliminar(null);
            await consultarEstadoPeriodo();
        } catch (e) {
            dialog.error(e.message || "No fue posible eliminar el pago.");
        } finally {
            setEliminandoPago(false);
        }
    }

    // Acciones de un pago ya registrado: ver comprobante y eliminar (con confirmación)
    function accionesPago({ tipo, id, etiqueta, urlComprobante, eliminable = true }) {
        const clave = `${tipo}-${id}`;
        if (pagoAEliminar && pagoAEliminar.tipo === tipo && pagoAEliminar.id === id) {
            return (
                <Alert tone="warning" title={`¿Confirma eliminar el pago de ${etiqueta}?`} className="pago-item__confirmar">
                    <p>{tipo === "matricula" ? "La matrícula" : "El mes"} quedará pendiente de nuevo.</p>
                    <div className="inline-actions confirmar-acciones">
                        <button type="button" className="sg-btn sg-btn--secondary sg-btn--sm" onClick={() => setPagoAEliminar(null)} disabled={eliminandoPago}>
                            Cancelar
                        </button>
                        <button type="button" className="sg-btn sg-btn--danger sg-btn--sm" onClick={eliminarPagoAhora} disabled={eliminandoPago}>
                            {eliminandoPago ? "Eliminando…" : "Sí, eliminar"}
                        </button>
                    </div>
                </Alert>
            );
        }
        return (
            <div className="inline-actions pago-item__acciones">
                <button type="button" className="sg-btn sg-btn--secondary sg-btn--sm" onClick={() => verComprobante(urlComprobante, clave)} disabled={abriendoComprobante === clave}>
                    <Eye size={16} strokeWidth={1.75} aria-hidden="true" /> {abriendoComprobante === clave ? "Abriendo…" : "Ver comprobante"}
                </button>
                {eliminable && (
                    <button type="button" className="sg-btn sg-btn--ghost sg-btn--sm btn-peligro" onClick={() => setPagoAEliminar({ tipo, id, etiqueta })}>
                        <Trash2 size={16} strokeWidth={1.75} aria-hidden="true" /> Eliminar pago
                    </button>
                )}
            </div>
        );
    }

    const resumenPago = listaErrores(erroresPago, "pago-");
    const matricula = estadoPeriodo?.matricula;

    return (
        <div className="becas-pagos">
            <PageHead title="Becas y pagos" sub="Busque un estudiante para registrar sus pagos." />

            {/* ---------- Buscador ---------- */}
            <section className="panel">
                <div className="panel-head">
                    <div>
                        <h2>Buscar estudiante</h2>
                        <p className="panel-head-sub">Por nombre, apellidos o número de identificación.</p>
                    </div>
                </div>
                <div className="panel-body">
                    <div className="buscador-estudiante">
                        <Field id="txtBuscarEstudiantePago" label="Estudiante" icon={Search}>
                            <input
                                id="txtBuscarEstudiantePago"
                                className="sg-input"
                                type="search"
                                autoComplete="off"
                                placeholder="Ej. María Solano o 205950123"
                                value={texto}
                                onChange={(e) => setTexto(e.target.value)}
                                aria-controls="resultados-estudiante"
                            />
                        </Field>

                        {texto.trim().length >= 2 && (
                            <div className="search-results" id="resultados-estudiante" role="listbox" aria-label="Resultados de la búsqueda">
                                {buscando && <div className="search-results__empty">Buscando...</div>}
                                {!buscando && resultados.length === 0 && (
                                    <div className="search-results__empty">Sin coincidencias.</div>
                                )}
                                {!buscando && resultados.map((r) => (
                                    <button
                                        type="button"
                                        key={r.id}
                                        role="option"
                                        aria-selected="false"
                                        className="search-results__item"
                                        onClick={() => seleccionarEstudiante(r.id)}
                                    >
                                        <span className="who">
                                            <span className="avatar avatar--sm" aria-hidden="true">{iniciales(r.nombreCompleto)}</span>
                                            <b>{r.nombreCompleto}</b>
                                        </span>
                                        <span className="search-results__cedula">{r.numeroIdentificacion}</span>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </section>

            {cargandoPerfil && <Alert tone="info" title="Cargando estudiante..." className="bloque-separado" />}

            {estudiante && !cargandoPerfil && (
                <>
                    {/* ---------- Perfil del estudiante ---------- */}
                    <section className="panel">
                        <div className="panel-body">
                            <div className="profile-head">
                                <span className="avatar avatar--lg" aria-hidden="true">{iniciales(nombreCompleto)}</span>
                                <div>
                                    <h3>{nombreCompleto}</h3>
                                    <p className="muted tnum">Identificación {estudiante.numeroIdentificacion}</p>
                                </div>
                            </div>
                        </div>
                    </section>

                    {/* ---------- Pagos ---------- */}
                    <section className="panel">
                        <div className="panel-head">
                            <div>
                                <h2><Wallet size={18} strokeWidth={1.75} className="inline-icon" aria-hidden="true" /> Pagos</h2>
                                <p className="panel-head-sub">Seleccione el período académico y marque lo que el estudiante pagó.</p>
                            </div>
                        </div>

                        <div className="panel-body stack">
                            <div className="form-grid">
                                <Field id="cmbPeriodoPago" label="Período académico" select error={errorPeriodos}>
                                    <select
                                        id="cmbPeriodoPago"
                                        className="sg-input sg-select"
                                        value={idPeriodo}
                                        onChange={(e) => { setIdPeriodo(e.target.value); setEstadoPeriodo(null); limpiarFormulario(); consultarEstadoPeriodo(e.target.value); }}
                                    >
                                        <option value="">Seleccione un período</option>
                                        {periodos.map((p) => (
                                            <option key={p.id} value={p.id}>{p.nombre}</option>
                                        ))}
                                    </select>
                                </Field>
                            </div>

                            {cargandoEstado && <p className="muted">Consultando estado de pago...</p>}

                            {!cargandoEstado && estadoPeriodo && (
                                <form onSubmit={registrarPago} noValidate className="stack">
                                    {resumenPago.length > 0 && (
                                        <Alert
                                            key={intentoPago}
                                            tone="danger"
                                            autoFocus
                                            title={resumenPago.length === 1 ? "Revise 1 dato antes de registrar el pago" : `Revise ${resumenPago.length} datos antes de registrar el pago`}
                                            errors={resumenPago}
                                        />
                                    )}

                                    {estadoPeriodo.sinPagosPendientes && (
                                        <Alert tone="success" title="Becado al 100%">
                                            Este estudiante no paga mensualidades en este período; solo puede registrarse el pago de matrícula.
                                        </Alert>
                                    )}

                                    <fieldset className="pagos-lista" id="pago-seleccion" aria-describedby={erroresPago.seleccion ? "pago-seleccion-error" : undefined}>
                                        <legend className="pagos-lista__titulo">¿Qué pagó el estudiante?</legend>
                                        {!estadoPeriodo.sinPagosPendientes && (
                                            <p className="muted pagos-lista__nota">
                                                Cada mensualidad: <b className="tnum">{formatoColones(estadoPeriodo.montoEsperado)}</b>
                                                {estadoPeriodo.porcentajeBeca > 0 && ` (ya incluye el descuento de beca del ${Number(estadoPeriodo.porcentajeBeca)}%)`}
                                            </p>
                                        )}
                                        {erroresPago.seleccion && (
                                            <p className="sg-field__error" id="pago-seleccion-error">{erroresPago.seleccion}</p>
                                        )}

                                        <ul className="pagos-lista__items">
                                            {/* Matrícula del período */}
                                            <li className={`pago-item${matricula?.pagada ? " is-pagado" : ""}`}>
                                                <label className="pago-item__check">
                                                    <input
                                                        type="checkbox"
                                                        checked={matricula?.pagada || incluirMatricula}
                                                        disabled={matricula?.pagada}
                                                        onChange={() => alternarSeleccion(MATRICULA)}
                                                    />
                                                    <span className="pago-item__nombre">Matrícula del período</span>
                                                </label>
                                                {matricula?.pagada ? (
                                                    <>
                                                        <StatusBadge tone="success">Pagada{matricula.fechaPago ? ` · ${matricula.fechaPago}` : ""}</StatusBadge>
                                                        {accionesPago({
                                                            tipo: "matricula",
                                                            id: matricula.idAutorizacion,
                                                            etiqueta: "la matrícula",
                                                            urlComprobante: `${API_URL}/pagos/matricula/${matricula.idAutorizacion}/comprobante`,
                                                            eliminable: matricula.eliminable,
                                                        })}
                                                        {!matricula.eliminable && (
                                                            <p className="muted pago-item__detalle">La solicitud de matrícula ya fue revisada; este pago no se puede eliminar.</p>
                                                        )}
                                                    </>
                                                ) : (
                                                    <StatusBadge tone="warning">Pendiente</StatusBadge>
                                                )}
                                            </li>

                                            {/* Mensualidades del período */}
                                            {estadoPeriodo.meses.map((m) => {
                                                const clave = claveMes(m);
                                                const etiqueta = `${MESES[m.mes]} ${m.anio}`;
                                                return (
                                                    <li key={clave} className={`pago-item${m.pagada ? " is-pagado" : ""}`}>
                                                        <label className="pago-item__check">
                                                            <input
                                                                type="checkbox"
                                                                checked={m.pagada || seleccion.includes(clave)}
                                                                disabled={m.pagada || estadoPeriodo.sinPagosPendientes}
                                                                onChange={() => alternarSeleccion(clave)}
                                                            />
                                                            <span className="pago-item__nombre">{etiqueta}</span>
                                                        </label>
                                                        {m.pagada ? (
                                                            <>
                                                                <StatusBadge tone="success">
                                                                    Pagado {formatoColones(m.monto)}{m.fechaPago ? ` · ${m.fechaPago}` : ""}
                                                                </StatusBadge>
                                                                {accionesPago({
                                                                    tipo: "mes",
                                                                    id: m.idMensualidad,
                                                                    etiqueta,
                                                                    urlComprobante: `${API_URL}/pagos/${m.idMensualidad}/comprobante`,
                                                                })}
                                                                {m.observaciones && (
                                                                    <p className="muted pago-item__detalle">Observaciones: {m.observaciones}</p>
                                                                )}
                                                            </>
                                                        ) : (
                                                            <StatusBadge tone={estadoPeriodo.sinPagosPendientes ? "neutral" : "warning"}>
                                                                {estadoPeriodo.sinPagosPendientes ? "Sin cobro" : "Pendiente"}
                                                            </StatusBadge>
                                                        )}
                                                    </li>
                                                );
                                            })}
                                        </ul>
                                    </fieldset>

                                    {seleccion.length > 0 && (
                                        <>
                                            <div className="review-block">
                                                <h3>
                                                    Registrar {seleccion.length === 1 ? "1 pago" : `${seleccion.length} pagos`}
                                                    {mesesSeleccionados.length > 0 && <span className="monto tnum">{formatoColones(totalMeses)}</span>}
                                                </h3>
                                                <p className="muted review-nota">
                                                    {[
                                                        incluirMatricula && "Matrícula",
                                                        ...mesesSeleccionados.map((m) => `${MESES[m.mes]} ${m.anio}`),
                                                    ].filter(Boolean).join(", ")}
                                                    {incluirMatricula && mesesSeleccionados.length > 0 && " · el total corresponde solo a las mensualidades"}
                                                </p>
                                            </div>

                                            <div className="form-grid">
                                                <Field id="pago-fechaPago" errorKey="fechaPago" label="Fecha de pago" error={erroresPago.fechaPago}>
                                                    <input
                                                        id="pago-fechaPago"
                                                        className="sg-input"
                                                        type="date"
                                                        min={fechaMinimaPago || undefined}
                                                        max={todayISO()}
                                                        value={fechaPago}
                                                        onChange={(e) => { setFechaPago(e.target.value); setErroresPago((er) => ({ ...er, fechaPago: "" })); }}
                                                        aria-invalid={erroresPago.fechaPago ? "true" : undefined}
                                                    />
                                                </Field>

                                                <Field
                                                    id="pago-observaciones"
                                                    errorKey="observaciones"
                                                    label="Observaciones (opcional)"
                                                    hint={`Ej. "Pagó dos meses en un solo depósito" · ${observaciones.length}/${MAX_OBSERVACIONES}`}
                                                    error={erroresPago.observaciones}
                                                    className="span-all"
                                                >
                                                    <textarea
                                                        id="pago-observaciones"
                                                        className="sg-input"
                                                        rows={3}
                                                        maxLength={MAX_OBSERVACIONES}
                                                        value={observaciones}
                                                        onChange={(e) => { setObservaciones(e.target.value); setErroresPago((er) => ({ ...er, observaciones: "" })); }}
                                                        aria-invalid={erroresPago.observaciones ? "true" : undefined}
                                                    />
                                                </Field>

                                                <Field id="pago-comprobante" errorKey="comprobante" label="Comprobante de pago" hint="Es la evidencia de que el estudiante pagó; sirve para todos los pagos marcados · máximo 15 MB." error={erroresPago.comprobante} className="span-all">
                                                    {comprobante ? (
                                                        <div className="file-drop has-file">
                                                            <Receipt size={20} strokeWidth={1.75} aria-hidden="true" />
                                                            <span className="file-drop__nombre">
                                                                {comprobante.name} — {(comprobante.size / 1024 / 1024).toFixed(2)} MB
                                                            </span>
                                                            <button
                                                                type="button"
                                                                className="sg-icon-btn"
                                                                aria-label="Quitar comprobante adjunto"
                                                                onClick={() => setComprobante(null)}
                                                            >
                                                                <X size={16} strokeWidth={1.75} aria-hidden="true" />
                                                            </button>
                                                        </div>
                                                    ) : (
                                                        <label className="file-drop">
                                                            <Receipt size={20} strokeWidth={1.75} aria-hidden="true" />
                                                            <span>Adjuntar comprobante (PDF, JPG o PNG)</span>
                                                            <input
                                                                id="pago-comprobante"
                                                                type="file"
                                                                accept="application/pdf,image/jpeg,image/png,image/webp"
                                                                onChange={(e) => {
                                                                    setComprobante(e.target.files?.[0] || null);
                                                                    setErroresPago((er) => ({ ...er, comprobante: "" }));
                                                                }}
                                                            />
                                                        </label>
                                                    )}
                                                </Field>
                                            </div>
                                        </>
                                    )}

                                    <div className="pago-acciones">
                                        <button className={`sg-btn sg-btn--primary${guardandoPago ? " is-loading" : ""}`} type="submit" disabled={guardandoPago || seleccion.length === 0} aria-busy={guardandoPago || undefined}>
                                            {guardandoPago ? <span className="sg-spinner" aria-hidden="true" /> : <CheckCircle2 size={18} strokeWidth={1.75} aria-hidden="true" />}
                                            {guardandoPago ? "Registrando…" : seleccion.length > 1 ? "Registrar pagos" : "Registrar pago"}
                                        </button>
                                    </div>
                                </form>
                            )}

                            {!cargandoEstado && !estadoPeriodo && (
                                <EmptyState compact icon={Clock} title="Consulte un período" description="Seleccione el período académico para ver la matrícula y los meses." />
                            )}
                        </div>
                    </section>
                </>
            )}
        </div>
    );
}

export default Estudiantes;
