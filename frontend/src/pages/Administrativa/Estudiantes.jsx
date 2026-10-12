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
import { iniciales, listaErrores } from "../../components/ui/utilidades";

import { useDialog } from "../../components/dialog/useDialog";
import { lanzarError, mensajeDeExcepcion } from "../../utils/api";

// Mismo límite que valida el backend (PagoService).
const MAX_COMPROBANTE = 15 * 1024 * 1024;

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

// Genera la lista de meses (año + mes) comprendidos dentro de un período académico.
function mesesDelPeriodo(periodo) {
    if (!periodo?.fechaInicio || !periodo?.fechaFin) return [];
    const inicio = new Date(`${periodo.fechaInicio}T00:00:00`);
    const fin = new Date(`${periodo.fechaFin}T00:00:00`);
    const meses = [];
    const cursor = new Date(inicio.getFullYear(), inicio.getMonth(), 1);
    const limite = new Date(fin.getFullYear(), fin.getMonth(), 1);
    while (cursor <= limite) {
        meses.push({ anio: cursor.getFullYear(), mes: cursor.getMonth() + 1 });
        cursor.setMonth(cursor.getMonth() + 1);
    }
    return meses;
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

    // --- Mes seleccionado y estado de pago ---
    const [mesSeleccionado, setMesSeleccionado] = useState("");
    const [estadoMes, setEstadoMes] = useState(null);
    const [cargandoEstadoMes, setCargandoEstadoMes] = useState(false);

    // --- Formulario "Registrar pago" ---
    const [fechaPago, setFechaPago] = useState(todayISO());
    const [comprobante, setComprobante] = useState(null);
    const [erroresPago, setErroresPago] = useState({});
    const [guardandoPago, setGuardandoPago] = useState(false);
    const [intentoPago, setIntentoPago] = useState(0);

    // --- Ver / eliminar el pago ya registrado ---
    const [confirmarEliminarPago, setConfirmarEliminarPago] = useState(false);
    const [eliminandoPago, setEliminandoPago] = useState(false);
    const [abriendoComprobante, setAbriendoComprobante] = useState(false);

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

    function limpiarSeleccion() {
        setIdPeriodo("");
        setMesSeleccionado("");
        setEstadoMes(null);
        setFechaPago(todayISO());
        setComprobante(null);
        setErroresPago({});
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

    async function consultarEstadoMes() {
        if (!estudiante || !idPeriodo || !mesSeleccionado) return;
        const [anio, mes] = mesSeleccionado.split("-");

        setCargandoEstadoMes(true);
        setEstadoMes(null);
        setErroresPago({});
        setComprobante(null);
        setFechaPago(todayISO());
        setConfirmarEliminarPago(false);

        try {
            const params = new URLSearchParams({ idPeriodo, anio, mes });
            const response = await fetch(`${API_URL}/pagos/estudiante/${estudiante.id}/mes?${params}`, {
                headers: authHeaders,
            });
            const body = await response.json().catch(() => ({}));

            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para consultar pagos.");
            if (!response.ok) throw new Error(body.message || "No fue posible consultar el estado de pago.");

            setEstadoMes(body);
        } catch (e) {
            setEstadoMes(null);
            dialog.error(e.message || "Ocurrió un error al consultar el estado de pago.");
        } finally {
            setCargandoEstadoMes(false);
        }
    }

    useEffect(() => {
        if (mesSeleccionado) consultarEstadoMes();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mesSeleccionado]);

    async function registrarPago(evento) {
        evento.preventDefault();

        const nuevosErrores = {};
        if (!fechaPago) nuevosErrores.fechaPago = "Ingrese la fecha de pago.";
        else if (fechaPago > todayISO()) nuevosErrores.fechaPago = "La fecha de pago no puede ser futura.";
        if (!comprobante) nuevosErrores.comprobante = "Debe adjuntar el comprobante de pago.";
        else if (comprobante.size > MAX_COMPROBANTE) nuevosErrores.comprobante = "El comprobante no puede superar 15 MB.";

        setErroresPago(nuevosErrores);
        if (Object.keys(nuevosErrores).length > 0) {
            setIntentoPago((n) => n + 1);
            return;
        }

        const [anio, mes] = mesSeleccionado.split("-");
        const payload = {
            idEstudiante: estudiante.id,
            idPeriodo: Number(idPeriodo),
            anio: Number(anio),
            mes: Number(mes),
            fechaPago,
        };

        const data = new FormData();
        data.append("pago", new Blob([JSON.stringify(payload)], { type: "application/json" }));
        data.append("comprobante", comprobante);

        try {
            setGuardandoPago(true);
            const response = await fetch(`${API_URL}/pagos`, {
                method: "POST",
                headers: authHeaders,
                body: data,
            });
            const body = await response.json().catch(() => ({}));

            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para registrar pagos.");
            if (!response.ok) throw new Error(body.message || "No fue posible registrar el pago.");

            // Refleja el nuevo estado de inmediato, sin recargar nada.
            setEstadoMes((prev) => ({
                ...prev,
                pagada: true,
                idMensualidad: body.idMensualidad,
                monto: body.monto,
                fechaPago: body.fechaPago,
            }));
            setComprobante(null);
        } catch (e) {
            dialog.error(e.message || "Ocurrió un error al registrar el pago.");
        } finally {
            setGuardandoPago(false);
        }
    }

    async function verComprobante() {
        setAbriendoComprobante(true);
        try {
            const response = await fetch(`${API_URL}/pagos/${estadoMes.idMensualidad}/comprobante`, {
                headers: authHeaders,
            });
            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para ver el comprobante.");
            if (!response.ok) {
                await lanzarError(response, response.status === 404
                    ? "No se encontró el comprobante de este pago."
                    : "No fue posible abrir el comprobante.");
            }
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            window.open(url, "_blank", "noopener,noreferrer");
        } catch (e) {
            dialog.error(e.message || "No fue posible abrir el comprobante.");
        } finally {
            setAbriendoComprobante(false);
        }
    }

    async function eliminarPagoAhora() {
        try {
            setEliminandoPago(true);
            const response = await fetch(`${API_URL}/pagos/${estadoMes.idMensualidad}`, {
                method: "DELETE",
                headers: authHeaders,
            });
            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para eliminar pagos.");
            if (!response.ok) {
                await lanzarError(response, response.status === 404
                    ? "No se encontró el pago. Es posible que ya haya sido eliminado."
                    : "No fue posible eliminar el pago.");
            }
            setConfirmarEliminarPago(false);
            await consultarEstadoMes();
        } catch (e) {
            dialog.error(e.message || "No fue posible eliminar el pago.");
        } finally {
            setEliminandoPago(false);
        }
    }

    const resumenPago = listaErrores(erroresPago, "pago-");
    const pagadoOSinPendientes = Boolean(estadoMes?.sinPagosPendientes || estadoMes?.pagada);

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
                                <p className="panel-head-sub">Seleccione el período académico y el mes a consultar.</p>
                            </div>
                        </div>

                        <div className="panel-body stack">
                            <div className="form-grid">
                                <Field id="cmbPeriodoPago" label="Período académico" select error={errorPeriodos}>
                                    <select
                                        id="cmbPeriodoPago"
                                        className="sg-input sg-select"
                                        value={idPeriodo}
                                        onChange={(e) => { setIdPeriodo(e.target.value); setMesSeleccionado(""); setEstadoMes(null); }}
                                    >
                                        <option value="">Seleccione un período</option>
                                        {periodos.map((p) => (
                                            <option key={p.id} value={p.id}>{p.nombre}</option>
                                        ))}
                                    </select>
                                </Field>

                                <Field id="cmbMesPago" label="Mes" select hint={!idPeriodo ? "Seleccione primero un período." : undefined}>
                                    <select
                                        id="cmbMesPago"
                                        className="sg-input sg-select"
                                        value={mesSeleccionado}
                                        onChange={(e) => setMesSeleccionado(e.target.value)}
                                        disabled={!idPeriodo}
                                    >
                                        <option value="">Seleccione un mes</option>
                                        {mesesDelPeriodo(periodoSeleccionado).map(({ anio, mes }) => (
                                            <option key={`${anio}-${mes}`} value={`${anio}-${mes}`}>
                                                {MESES[mes]} {anio}
                                            </option>
                                        ))}
                                    </select>
                                </Field>
                            </div>

                            {cargandoEstadoMes && <p className="muted">Consultando estado de pago...</p>}

                            {!cargandoEstadoMes && estadoMes && (
                                <>
                                    <Alert
                                        tone={pagadoOSinPendientes ? "success" : "warning"}
                                        title={`Estado de pago: ${estadoMes.sinPagosPendientes ? "Sin pagos pendientes" : estadoMes.pagada ? "Al día" : "Pendiente"}`}
                                    >
                                        {estadoMes.sinPagosPendientes && "Este estudiante está becado al 100% para este período; no tiene pagos pendientes."}
                                    </Alert>

                                    {estadoMes.pagada && !estadoMes.sinPagosPendientes && (
                                        <div className="review-block">
                                            <h3>Pago registrado <span className="monto tnum">{formatoColones(estadoMes.monto)}</span></h3>
                                            <dl>
                                                <dt>Fecha de pago</dt>
                                                <dd className="tnum">{estadoMes.fechaPago}</dd>
                                            </dl>
                                            <div className="review-acciones">
                                                {confirmarEliminarPago ? (
                                                    <Alert tone="warning" title="¿Confirma eliminar este pago?">
                                                        <p>El mes quedará pendiente de nuevo.</p>
                                                        <div className="inline-actions confirmar-acciones">
                                                            <button type="button" className="sg-btn sg-btn--secondary sg-btn--sm" onClick={() => setConfirmarEliminarPago(false)} disabled={eliminandoPago}>
                                                                Cancelar
                                                            </button>
                                                            <button type="button" className="sg-btn sg-btn--danger sg-btn--sm" onClick={eliminarPagoAhora} disabled={eliminandoPago}>
                                                                {eliminandoPago ? "Eliminando…" : "Sí, eliminar"}
                                                            </button>
                                                        </div>
                                                    </Alert>
                                                ) : (
                                                    <div className="inline-actions">
                                                        <button type="button" className="sg-btn sg-btn--secondary sg-btn--sm" onClick={verComprobante} disabled={abriendoComprobante}>
                                                            <Eye size={16} strokeWidth={1.75} aria-hidden="true" /> {abriendoComprobante ? "Abriendo…" : "Ver comprobante"}
                                                        </button>
                                                        <button type="button" className="sg-btn sg-btn--ghost sg-btn--sm btn-peligro" onClick={() => setConfirmarEliminarPago(true)}>
                                                            <Trash2 size={16} strokeWidth={1.75} aria-hidden="true" /> Eliminar pago
                                                        </button>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    )}

                                    {!estadoMes.pagada && !estadoMes.sinPagosPendientes && (
                                        <>
                                            <div className="review-block">
                                                <h3>Monto a pagar <span className="monto tnum">{formatoColones(estadoMes.montoEsperado)}</span></h3>
                                                {estadoMes.porcentajeBeca > 0 && (
                                                    <p className="muted review-nota">Ya incluye el descuento de beca del {Number(estadoMes.porcentajeBeca)}%.</p>
                                                )}
                                            </div>

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

                                                <div className="form-grid">
                                                    <Field id="pago-fechaPago" errorKey="fechaPago" label="Fecha de pago" error={erroresPago.fechaPago}>
                                                        <input
                                                            id="pago-fechaPago"
                                                            className="sg-input"
                                                            type="date"
                                                            max={todayISO()}
                                                            value={fechaPago}
                                                            onChange={(e) => { setFechaPago(e.target.value); setErroresPago((er) => ({ ...er, fechaPago: "" })); }}
                                                            aria-invalid={erroresPago.fechaPago ? "true" : undefined}
                                                        />
                                                    </Field>

                                                    <Field id="pago-comprobante" errorKey="comprobante" label="Comprobante de pago" hint="Es la evidencia de que el estudiante pagó · máximo 15 MB." error={erroresPago.comprobante} className="span-all">
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

                                                <div className="pago-acciones">
                                                    <button className={`sg-btn sg-btn--primary${guardandoPago ? " is-loading" : ""}`} type="submit" disabled={guardandoPago} aria-busy={guardandoPago || undefined}>
                                                        {guardandoPago ? <span className="sg-spinner" aria-hidden="true" /> : <CheckCircle2 size={18} strokeWidth={1.75} aria-hidden="true" />}
                                                        {guardandoPago ? "Registrando…" : "Registrar pago"}
                                                    </button>
                                                </div>
                                            </form>
                                        </>
                                    )}
                                </>
                            )}

                            {!cargandoEstadoMes && !estadoMes && (
                                <EmptyState compact icon={Clock} title="Consulte un mes" description="Seleccione el período académico y el mes para ver el estado de pago." />
                            )}
                        </div>
                    </section>
                </>
            )}
        </div>
    );
}

export default Estudiantes;
