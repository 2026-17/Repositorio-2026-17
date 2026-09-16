import { useEffect, useMemo, useState } from "react";
import {
    Search,
    GraduationCap,
    Wallet,
    Receipt,
    CheckCircle2,
    Clock,
    AlertTriangle,
    X,
} from "lucide-react";
import { API_URL } from "../../config/api";
import "../AdminSis/styles/admin_Sis.css";

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

    const token = localStorage.getItem("token");
    const authHeaders = token ? { Authorization: `Bearer ${token}` } : {};

    // --- Búsqueda de estudiante ---
    const [texto, setTexto] = useState("");
    const [resultados, setResultados] = useState([]);
    const [buscando, setBuscando] = useState(false);
    const [errorBusqueda, setErrorBusqueda] = useState("");

    // --- Estudiante seleccionado ---
    const [estudiante, setEstudiante] = useState(null);
    const [cargandoPerfil, setCargandoPerfil] = useState(false);
    const [errorPerfil, setErrorPerfil] = useState("");

    // --- Períodos académicos ---
    const [periodos, setPeriodos] = useState([]);
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
    const [errorPago, setErrorPago] = useState("");

    // Catálogo de períodos académicos, se carga una sola vez.
    useEffect(() => {
        fetch(`${API_URL}/periodos-academicos`, { headers: authHeaders })
            .then((r) => (r.ok ? r.json() : Promise.reject()))
            .then(setPeriodos)
            .catch(() => setPeriodos([]));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Búsqueda con un pequeño debounce para no golpear el backend en cada tecla.
    useEffect(() => {
        const consulta = texto.trim();
        if (consulta.length < 2) {
            setResultados([]);
            setErrorBusqueda("");
            return;
        }

        setBuscando(true);
        const temporizador = setTimeout(async () => {
            try {
                const response = await fetch(`/api/estudiantes?texto=${encodeURIComponent(consulta)}`, {
                    headers: authHeaders,
                });
                if (!response.ok) throw new Error();
                const data = await response.json();
                setResultados(data);
                setErrorBusqueda("");
            } catch {
                setResultados([]);
                setErrorBusqueda("No fue posible buscar estudiantes.");
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
        setErrorPago("");
    }

    async function seleccionarEstudiante(idEstudiante) {
        setTexto("");
        setResultados([]);
        setEstudiante(null);
        setErrorPerfil("");
        setCargandoPerfil(true);
        limpiarSeleccion();

        try {
            const response = await fetch(`/api/estudiantes/${idEstudiante}`, { headers: authHeaders });
            if (!response.ok) throw new Error();
            const data = await response.json();
            setEstudiante(data);
        } catch {
            setErrorPerfil("No fue posible cargar la información del estudiante.");
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
        setErrorPago("");
        setErroresPago({});
        setComprobante(null);
        setFechaPago(todayISO());

        try {
            const params = new URLSearchParams({ idPeriodo, anio, mes });
            const response = await fetch(`/api/pagos/estudiante/${estudiante.id}/mes?${params}`, {
                headers: authHeaders,
            });
            if (!response.ok) throw new Error();
            setEstadoMes(await response.json());
        } catch {
            setEstadoMes(null);
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
        setErrorPago("");

        const nuevosErrores = {};
        if (!fechaPago) nuevosErrores.fechaPago = "Ingrese la fecha de pago.";
        else if (fechaPago > todayISO()) nuevosErrores.fechaPago = "La fecha de pago no puede ser futura.";
        if (!comprobante) nuevosErrores.comprobante = "Debe adjuntar el comprobante de pago.";

        setErroresPago(nuevosErrores);
        if (Object.keys(nuevosErrores).length > 0) return;

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
            const response = await fetch(`/api/pagos`, {
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
            setErrorPago(e.message || "Ocurrió un error al registrar el pago.");
        } finally {
            setGuardandoPago(false);
        }
    }

    return (
        <main className="content">
            <div className="page-title">
                <h1>Estudiantes</h1>
                <p>Busque un estudiante para registrar sus pagos.</p>
            </div>

            {/* ---------- Buscador ---------- */}
            <section className="card">
                <h2 className="card-title">Buscar estudiante</h2>
                <p className="card-subtitle">Por nombre, apellidos o número de identificación.</p>

                <div className="field" style={{ position: "relative" }}>
                    <div className="input-suffix-wrap">
                        <input
                            className="input"
                            placeholder="Ej. María Solano o 205950123"
                            value={texto}
                            onChange={(e) => setTexto(e.target.value)}
                        />
                        <span className="input-suffix"><Search size={16} /></span>
                    </div>

                    {texto.trim().length >= 2 && (
                        <div className="search-results">
                            {buscando && <div className="search-results__empty">Buscando...</div>}
                            {!buscando && errorBusqueda && (
                                <div className="search-results__empty">{errorBusqueda}</div>
                            )}
                            {!buscando && !errorBusqueda && resultados.length === 0 && (
                                <div className="search-results__empty">Sin coincidencias.</div>
                            )}
                            {!buscando && resultados.map((r) => (
                                <button
                                    type="button"
                                    key={r.id}
                                    className="search-results__item"
                                    onClick={() => seleccionarEstudiante(r.id)}
                                >
                                    <span>{r.nombreCompleto}</span>
                                    <span className="search-results__cedula">{r.numeroIdentificacion}</span>
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            </section>

            {cargandoPerfil && <div className="banner banner-warning">Cargando estudiante...</div>}
            {errorPerfil && <div className="banner banner-danger" role="alert">{errorPerfil}</div>}

            {estudiante && !cargandoPerfil && (
                <>
                    {/* ---------- Perfil del estudiante ---------- */}
                    <section className="card">
                        <div className="student-header">
                            <div className="student-header__avatar"><GraduationCap size={22} /></div>
                            <div>
                                <h2 className="card-title">{nombreCompleto}</h2>
                                <p className="card-subtitle">Identificación: {estudiante.numeroIdentificacion}</p>
                            </div>
                        </div>
                        <p className="hint">El resto del expediente del estudiante se incorporará en otra historia de usuario.</p>
                    </section>

                    {/* ---------- Pagos ---------- */}
                    <section className="card">
                        <div className="card-header-row">
                            <div>
                                <h2 className="card-title"><Wallet size={18} className="inline-icon" /> Pagos</h2>
                                <p className="card-subtitle">Seleccione el período académico y el mes a consultar.</p>
                            </div>
                        </div>

                        <div className="grid-2">
                            <div className="field">
                                <label className="field-label">Período académico</label>
                                <select
                                    className="input"
                                    value={idPeriodo}
                                    onChange={(e) => { setIdPeriodo(e.target.value); setMesSeleccionado(""); setEstadoMes(null); }}
                                >
                                    <option value="">Seleccione un período</option>
                                    {periodos.map((p) => (
                                        <option key={p.id} value={p.id}>{p.nombre}</option>
                                    ))}
                                </select>
                            </div>

                            <div className="field">
                                <label className="field-label">Mes</label>
                                <select
                                    className="input"
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
                            </div>
                        </div>

                        {cargandoEstadoMes && <p className="hint">Consultando estado de pago...</p>}

                        {!cargandoEstadoMes && estadoMes && (
                            <>
                                <div className={`banner ${estadoMes.sinPagosPendientes || estadoMes.pagada ? "banner-success" : "banner-warning"}`}>
                                    {estadoMes.sinPagosPendientes || estadoMes.pagada
                                        ? <CheckCircle2 size={16} />
                                        : <Clock size={16} />}
                                    Estado de pago: <strong>&nbsp;{estadoMes.sinPagosPendientes ? "Sin pagos pendientes" : estadoMes.pagada ? "Al día" : "Pendiente"}</strong>
                                </div>

                                {estadoMes.sinPagosPendientes ? (
                                    <p className="hint">Este estudiante está becado al 100% para este período; no tiene pagos pendientes.</p>
                                ) : estadoMes.pagada ? (
                                    <div className="subcard">
                                        <div className="subcard-header">
                                            <span>Pago registrado</span>
                                            <strong>{formatoColones(estadoMes.monto)}</strong>
                                        </div>
                                        <p className="hint">Fecha de pago: {estadoMes.fechaPago}</p>
                                    </div>
                                ) : (
                                    <>
                                        <div className="subcard">
                                            <div className="subcard-header">
                                                <span>Monto a pagar</span>
                                                <strong>{formatoColones(estadoMes.montoEsperado)}</strong>
                                            </div>
                                            {estadoMes.porcentajeBeca > 0 && (
                                                <p className="hint">Ya incluye el descuento de beca del {Number(estadoMes.porcentajeBeca)}%.</p>
                                            )}
                                        </div>

                                        <form onSubmit={registrarPago} noValidate>
                                            {errorPago && <div className="banner banner-danger" role="alert"><AlertTriangle size={16} /> {errorPago}</div>}

                                            <div className="field">
                                                <label className="field-label">Fecha de pago <span className="required">*</span></label>
                                                <input
                                                    className={`input ${erroresPago.fechaPago ? "error" : ""}`}
                                                    type="date"
                                                    max={todayISO()}
                                                    value={fechaPago}
                                                    onChange={(e) => { setFechaPago(e.target.value); setErroresPago((er) => ({ ...er, fechaPago: "" })); }}
                                                />
                                                {erroresPago.fechaPago && <p className="error">{erroresPago.fechaPago}</p>}
                                            </div>

                                            <div className="field">
                                                <label className="field-label">Comprobante de pago <span className="required">*</span></label>
                                                {comprobante ? (
                                                    <div className="file-input has-file" style={{ justifyContent: "space-between" }}>
                                                        <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
                                                            <Receipt size={16} />
                                                            {comprobante.name} — {(comprobante.size / 1024 / 1024).toFixed(2)} MB
                                                        </span>
                                                        <button
                                                            type="button"
                                                            className="icon-btn danger"
                                                            aria-label="Quitar comprobante adjunto"
                                                            onClick={() => setComprobante(null)}
                                                        >
                                                            <X size={15} />
                                                        </button>
                                                    </div>
                                                ) : (
                                                    <label className="file-input">
                                                        <Receipt size={16} />
                                                        <span>Adjuntar comprobante (PDF, JPG o PNG)</span>
                                                        <input
                                                            type="file"
                                                            accept="application/pdf,image/jpeg,image/png,image/webp"
                                                            hidden
                                                            onChange={(e) => {
                                                                setComprobante(e.target.files?.[0] || null);
                                                                setErroresPago((er) => ({ ...er, comprobante: "" }));
                                                            }}
                                                        />
                                                    </label>
                                                )}
                                                {erroresPago.comprobante
                                                    ? <p className="error">{erroresPago.comprobante}</p>
                                                    : <p className="hint">Es la evidencia de que el estudiante pagó · máximo 15 MB.</p>}
                                            </div>

                                            <div className="actions-row">
                                                <button className="btn btn-primary" type="submit" disabled={guardandoPago}>
                                                    {guardandoPago ? "Registrando..." : "Registrar pago"}
                                                </button>
                                            </div>
                                        </form>
                                    </>
                                )}
                            </>
                        )}
                    </section>
                </>
            )}
        </main>
    );
}

export default Estudiantes;
