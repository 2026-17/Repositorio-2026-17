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
    GraduationCap,
    Pencil,
    Plus,
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
import CedulaInput from "../../components/ui/CedulaInput";
import { lanzarError, mensajeDeExcepcion } from "../../utils/api";
import { mostrarIdentificacion, pareceCedula } from "../../utils/cedula";
import { limpiarIdentificacion } from "../../utils/validaciones";

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

/* =====================================================================
 * Becas del estudiante: listar, asignar, modificar y eliminar
 * ===================================================================== */

// Mismos porcentajes que al registrar el estudiante (createStudent.jsx).
const PORCENTAJES_BECA = [25, 50, 75, 100];
const MAX_MOTIVO_BECA = 1000;
const TEXTO_PERMANENTE = "Permanente";

function formularioBecaVacio() {
    return {
        idBeca: null,
        permanente: false,
        idTipoBeca: "",
        idPeriodo: "",
        porcentaje: "",
        fechaInicio: todayISO(),
        fechaFin: "",
        motivo: "",
    };
}

function formularioBecaDesde(beca) {
    return {
        idBeca: beca.idBeca,
        permanente: beca.idPeriodo == null,
        idTipoBeca: beca.idTipoBeca ? String(beca.idTipoBeca) : "",
        idPeriodo: beca.idPeriodo ? String(beca.idPeriodo) : "",
        porcentaje: beca.porcentaje != null ? String(Number(beca.porcentaje)) : "",
        fechaInicio: beca.fechaInicio || "",
        fechaFin: beca.fechaFin || "",
        motivo: beca.motivo || "",
    };
}

function SeccionBecas({ estudiante, periodos, authHeaders, dialog, onCambio }) {
    const [becas, setBecas] = useState({ idEstudiante: null, lista: [] });
    const [tiposBeca, setTiposBeca] = useState([]);
    const [formulario, setFormulario] = useState(null);
    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);
    const [guardando, setGuardando] = useState(false);
    const [eliminando, setEliminando] = useState(null);
    const [recarga, setRecarga] = useState(0);

    const cargando = becas.idEstudiante !== estudiante.id;
    const editando = formulario?.idBeca != null;

    // Catálogo de tipos de beca, una sola vez.
    useEffect(() => {
        fetch(`${API_URL}/becas/tipos-becas`, { headers: authHeaders })
            .then((r) => (r.ok ? r.json() : lanzarError(r, "No se pudieron cargar los tipos de beca.")))
            .then(setTiposBeca)
            .catch((e) => dialog.error(mensajeDeExcepcion(e, "No se pudieron cargar los tipos de beca.")));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Becas del estudiante (y al asignar, modificar o eliminar).
    useEffect(() => {
        let activo = true;
        fetch(`${API_URL}/becas/estudiante/${estudiante.id}`, { headers: authHeaders })
            .then((r) => (r.ok ? r.json() : lanzarError(r, "No se pudieron cargar las becas del estudiante.")))
            .then((lista) => { if (activo) setBecas({ idEstudiante: estudiante.id, lista: lista ?? [] }); })
            .catch((e) => {
                if (!activo) return;
                setBecas({ idEstudiante: estudiante.id, lista: [] });
                dialog.error(mensajeDeExcepcion(e, "No se pudieron cargar las becas del estudiante."));
            });
        return () => { activo = false; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [estudiante.id, recarga]);

    function nombrePeriodo(beca) {
        if (beca.idPeriodo == null) return TEXTO_PERMANENTE;
        return beca.nombrePeriodo
            || periodos.find((p) => p.id === beca.idPeriodo)?.nombre
            || beca.codigoPeriodo
            || "—";
    }

    function abrirFormulario(beca = null) {
        setErrores({});
        setFormulario(beca ? formularioBecaDesde(beca) : formularioBecaVacio());
    }

    function cambiar(campo, valor) {
        setFormulario((f) => ({ ...f, [campo]: valor }));
        setErrores((e) => ({ ...e, [campo]: "" }));
    }

    function validar() {
        const e = {};
        if (!formulario.idTipoBeca) e.idTipoBeca = "Seleccione el tipo de beca.";
        if (!formulario.permanente && !formulario.idPeriodo) e.idPeriodo = "Seleccione el período académico de la beca.";
        if (!formulario.porcentaje) e.porcentaje = "Seleccione el porcentaje de la beca.";
        if (formulario.motivo.length > MAX_MOTIVO_BECA) e.motivo = `El motivo no puede superar los ${MAX_MOTIVO_BECA} caracteres.`;
        if (editando && formulario.fechaFin && formulario.fechaInicio && formulario.fechaFin < formulario.fechaInicio) {
            e.fechaFin = "La fecha de fin no puede ser anterior a la fecha de inicio de la beca.";
        }
        return e;
    }

    async function guardar(evento) {
        evento.preventDefault();
        const e = validar();
        setErrores(e);
        if (Object.keys(e).length > 0) {
            setIntento((n) => n + 1);
            return;
        }

        const comun = {
            idTipoBeca: Number(formulario.idTipoBeca),
            idPeriodo: formulario.permanente ? null : Number(formulario.idPeriodo),
            porcentaje: Number(formulario.porcentaje),
            motivo: formulario.motivo.trim() || null,
        };
        const payload = editando
            ? { ...comun, fechaFin: formulario.fechaFin || null }
            : { ...comun, idEstudiante: estudiante.id, fechaInicio: formulario.fechaInicio || null };

        setGuardando(true);
        try {
            const response = await fetch(`${API_URL}/becas${editando ? `/${formulario.idBeca}` : ""}`, {
                method: editando ? "PUT" : "POST",
                headers: { ...authHeaders, "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });
            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para gestionar becas.");
            if (!response.ok) await lanzarError(response, editando ? "No fue posible modificar la beca." : "No fue posible asignar la beca.");

            setFormulario(null);
            setRecarga((n) => n + 1);
            onCambio();
            dialog.exito(editando ? "La beca se modificó correctamente." : "La beca se asignó correctamente.");
        } catch (error) {
            dialog.error(mensajeDeExcepcion(error, editando ? "No fue posible modificar la beca." : "No fue posible asignar la beca."));
        } finally {
            setGuardando(false);
        }
    }

    async function eliminar(beca) {
        const acepta = await dialog.confirmar("¿Eliminar esta beca? Esta acción no se puede deshacer.", {
            titulo: "Eliminar beca",
            textoAceptar: "Sí, eliminar",
            peligro: true,
        });
        if (!acepta) return;

        setEliminando(beca.idBeca);
        try {
            const response = await fetch(`${API_URL}/becas/${beca.idBeca}`, { method: "DELETE", headers: authHeaders });
            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error("No tiene permisos para eliminar becas.");
            if (!response.ok) await lanzarError(response, "No fue posible eliminar la beca.");

            if (formulario?.idBeca === beca.idBeca) setFormulario(null);
            setRecarga((n) => n + 1);
            onCambio();
            dialog.exito("La beca se eliminó correctamente.");
        } catch (error) {
            dialog.error(mensajeDeExcepcion(error, "No fue posible eliminar la beca."));
        } finally {
            setEliminando(null);
        }
    }

    const resumen = listaErrores(errores, "beca-");
    const ocupado = guardando || eliminando !== null;

    return (
        <section className="panel">
            <div className="panel-head">
                <div>
                    <h2><GraduationCap size={18} strokeWidth={1.75} className="inline-icon" aria-hidden="true" /> Becas</h2>
                    <p className="panel-head-sub">El descuento de la beca se aplica a las mensualidades del período.</p>
                </div>
                {!formulario && (
                    <button type="button" className="sg-btn sg-btn--primary sg-btn--sm" onClick={() => abrirFormulario()} disabled={ocupado}>
                        <Plus size={16} strokeWidth={1.75} aria-hidden="true" /> Asignar beca
                    </button>
                )}
            </div>

            <div className="panel-body stack">
                {formulario && (
                    <form onSubmit={guardar} noValidate className="stack beca-formulario">
                        <h3 className="beca-formulario__titulo">{editando ? "Modificar beca" : "Asignar beca"}</h3>

                        {resumen.length > 0 && (
                            <Alert
                                key={intento}
                                tone="danger"
                                autoFocus
                                title={resumen.length === 1 ? "Revise 1 dato antes de guardar la beca" : `Revise ${resumen.length} datos antes de guardar la beca`}
                                errors={resumen}
                            />
                        )}

                        <div className="form-grid">
                            <Field id="beca-idTipoBeca" errorKey="idTipoBeca" label="Tipo de beca" error={errores.idTipoBeca} select>
                                <select
                                    id="beca-idTipoBeca"
                                    className="sg-input sg-select"
                                    value={formulario.idTipoBeca}
                                    onChange={(e) => cambiar("idTipoBeca", e.target.value)}
                                    aria-invalid={errores.idTipoBeca ? "true" : undefined}
                                >
                                    <option value="">Seleccione un tipo</option>
                                    {tiposBeca.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
                                </select>
                            </Field>

                            <Field
                                id="beca-idPeriodo"
                                errorKey="idPeriodo"
                                label="Período académico"
                                error={errores.idPeriodo}
                                hint={formulario.permanente ? "Una beca permanente aplica a todos los períodos y no se puede cambiar de período." : undefined}
                                select
                            >
                                {formulario.permanente ? (
                                    <select id="beca-idPeriodo" className="sg-input sg-select" value="" disabled>
                                        <option value="">Permanente (todos los períodos)</option>
                                    </select>
                                ) : (
                                    <select
                                        id="beca-idPeriodo"
                                        className="sg-input sg-select"
                                        value={formulario.idPeriodo}
                                        onChange={(e) => cambiar("idPeriodo", e.target.value)}
                                        aria-invalid={errores.idPeriodo ? "true" : undefined}
                                    >
                                        <option value="">Seleccione un período</option>
                                        {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                                    </select>
                                )}
                            </Field>

                            <Field id="beca-porcentaje" errorKey="porcentaje" label="Porcentaje" error={errores.porcentaje} select>
                                <select
                                    id="beca-porcentaje"
                                    className="sg-input sg-select"
                                    value={formulario.porcentaje}
                                    onChange={(e) => cambiar("porcentaje", e.target.value)}
                                    aria-invalid={errores.porcentaje ? "true" : undefined}
                                >
                                    <option value="">Seleccione</option>
                                    {PORCENTAJES_BECA.map((p) => <option key={p} value={p}>{p}%</option>)}
                                </select>
                            </Field>

                            {editando ? (
                                <Field id="beca-fechaFin" errorKey="fechaFin" label="Fecha de fin (opcional)" error={errores.fechaFin}>
                                    <input
                                        id="beca-fechaFin"
                                        className="sg-input"
                                        type="date"
                                        min={formulario.fechaInicio || undefined}
                                        value={formulario.fechaFin}
                                        onChange={(e) => cambiar("fechaFin", e.target.value)}
                                        aria-invalid={errores.fechaFin ? "true" : undefined}
                                    />
                                </Field>
                            ) : (
                                <Field id="beca-fechaInicio" errorKey="fechaInicio" label="Fecha de inicio (opcional)" hint="Si la deja vacía, se usa la fecha de hoy.">
                                    <input
                                        id="beca-fechaInicio"
                                        className="sg-input"
                                        type="date"
                                        value={formulario.fechaInicio}
                                        onChange={(e) => cambiar("fechaInicio", e.target.value)}
                                    />
                                </Field>
                            )}

                            <Field
                                id="beca-motivo"
                                errorKey="motivo"
                                label="Motivo (opcional)"
                                hint={`${formulario.motivo.length}/${MAX_MOTIVO_BECA}`}
                                error={errores.motivo}
                                className="span-all"
                            >
                                <textarea
                                    id="beca-motivo"
                                    className="sg-input"
                                    rows={2}
                                    maxLength={MAX_MOTIVO_BECA}
                                    value={formulario.motivo}
                                    onChange={(e) => cambiar("motivo", e.target.value)}
                                    aria-invalid={errores.motivo ? "true" : undefined}
                                />
                            </Field>
                        </div>

                        <div className="inline-actions beca-formulario__acciones">
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={() => setFormulario(null)} disabled={guardando}>
                                Cancelar
                            </button>
                            <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando} aria-busy={guardando || undefined}>
                                {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <CheckCircle2 size={18} strokeWidth={1.75} aria-hidden="true" />}
                                {guardando ? "Guardando…" : editando ? "Guardar cambios" : "Asignar beca"}
                            </button>
                        </div>
                    </form>
                )}

                {cargando ? (
                    <p className="muted">Cargando becas...</p>
                ) : becas.lista.length === 0 ? (
                    <EmptyState compact icon={GraduationCap} title="Sin becas" description="Este estudiante no tiene becas asignadas." />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards becas-tabla">
                            <caption className="sr-only">Becas de {estudiante.nombres}</caption>
                            <thead>
                                <tr>
                                    <th scope="col">Tipo</th>
                                    <th scope="col">Período</th>
                                    <th scope="col" className="num">Porcentaje</th>
                                    <th scope="col">Desde</th>
                                    <th scope="col">Hasta</th>
                                    <th scope="col">Motivo</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="actions"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {becas.lista.map((b) => (
                                    <tr key={b.idBeca}>
                                        <td className="c-main"><b>{b.nombreTipoBeca}</b></td>
                                        <td data-label="Período">{nombrePeriodo(b)}</td>
                                        <td className="num tnum" data-label="Porcentaje">{Number(b.porcentaje)}%</td>
                                        <td className="tnum" data-label="Desde">{b.fechaInicio || "—"}</td>
                                        <td className="tnum" data-label="Hasta">{b.fechaFin || "—"}</td>
                                        <td data-label="Motivo" className="becas-tabla__motivo">{b.motivo || <span className="muted">—</span>}</td>
                                        <td data-label="Estado">
                                            <StatusBadge tone={b.activa ? "success" : "neutral"}>{b.activa ? "Activa" : "Inactiva"}</StatusBadge>
                                        </td>
                                        <td className="actions">
                                            <div className="row-actions">
                                                <button
                                                    type="button"
                                                    className="sg-btn sg-btn--ghost sg-btn--sm"
                                                    onClick={() => abrirFormulario(b)}
                                                    disabled={ocupado}
                                                    aria-label={`Modificar la beca ${b.nombreTipoBeca} (${nombrePeriodo(b)})`}
                                                >
                                                    <Pencil size={16} strokeWidth={1.75} aria-hidden="true" /> Modificar
                                                </button>
                                                <button
                                                    type="button"
                                                    className="sg-btn sg-btn--ghost sg-btn--sm btn-peligro"
                                                    onClick={() => eliminar(b)}
                                                    disabled={ocupado}
                                                    aria-label={`Eliminar la beca ${b.nombreTipoBeca} (${nombrePeriodo(b)})`}
                                                >
                                                    <Trash2 size={16} strokeWidth={1.75} aria-hidden="true" />
                                                    {eliminando === b.idBeca ? "Eliminando…" : "Eliminar"}
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </section>
    );
}

function Estudiantes() {
    const dialog = useDialog();

    const token = localStorage.getItem("token");
    const authHeaders = token ? { Authorization: `Bearer ${token}` } : {};

    // --- Búsqueda de estudiante ---
    const [texto, setTexto] = useState("");
    // Resultados de la última búsqueda hecha (y para qué texto)
    const [busqueda, setBusqueda] = useState({ consulta: "", lista: [] });
    // Una cédula escrita con la máscara se busca sin guiones
    const consulta = pareceCedula(texto.trim()) ? limpiarIdentificacion(texto.trim()) : texto.trim();
    const resultados = busqueda.lista;
    const buscando = consulta.length >= 2 && busqueda.consulta !== consulta;

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
        if (consulta.length < 2) return undefined;

        const temporizador = setTimeout(async () => {
            try {
                const response = await fetch(`${API_URL}/estudiantes?texto=${encodeURIComponent(consulta)}`, {
                    headers: authHeaders,
                });
                if (!response.ok) await lanzarError(response, "No fue posible buscar estudiantes.");
                const data = await response.json();
                setBusqueda({ consulta, lista: data });
            } catch (e) {
                setBusqueda({ consulta, lista: [] });
                dialog.error(mensajeDeExcepcion(e, "No fue posible buscar estudiantes."));
            }
        }, 300);

        return () => clearTimeout(temporizador);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [consulta]);

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
            <PageHead title="Becas y pagos" sub="Busque un estudiante para gestionar sus becas y registrar sus pagos." />

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
                            <CedulaInput
                                id="txtBuscarEstudiantePago"
                                className="sg-input"
                                type="search"
                                mascara="auto"
                                autoComplete="off"
                                placeholder="Ej. María Solano o 2-0595-0123"
                                value={texto}
                                onChange={setTexto}
                                aria-controls="resultados-estudiante"
                            />
                        </Field>

                        {consulta.length >= 2 && (
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
                                        <span className="search-results__cedula">{mostrarIdentificacion(r.numeroIdentificacion)}</span>
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
                                    <p className="muted tnum">Identificación {mostrarIdentificacion(estudiante.numeroIdentificacion)}</p>
                                </div>
                            </div>
                        </div>
                    </section>

                    {/* ---------- Becas ---------- */}
                    <SeccionBecas
                        estudiante={estudiante}
                        periodos={periodos}
                        authHeaders={authHeaders}
                        dialog={dialog}
                        // El monto con descuento del período cambia con la beca.
                        onCambio={() => { if (idPeriodo) consultarEstadoPeriodo(); }}
                    />

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
