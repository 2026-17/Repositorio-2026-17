import { useCallback, useEffect, useState } from "react";
import "../AdminSis/styles/admin_Sis.css";
import "./AsignarInstrumento.css";
import { History, Music, Search } from "lucide-react";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";
import { iniciales } from "../../components/ui/utilidades";
import { API_URL } from "../../config/api.js";

import { useDialog } from "../../components/dialog/useDialog";

const initialForm = { idInstrumento: "", observaciones: "" };

function formatDate(iso) {
    if (!iso) return "—";
    const [y, m, d] = iso.split("-");
    return `${d}/${m}/${y}`;
}

export default function AsignarInstrumento() {
    const dialog = useDialog();
    const [numeroIdentificacion, setNumeroIdentificacion] = useState("");
    const [idEstudiante, setIdEstudiante] = useState(null);
    const [estudianteActivo, setEstudianteActivo] = useState(null);
    const [instrumentos, setInstrumentos] = useState([]);
    const [vigentes, setVigentes] = useState([]);
    const [historial, setHistorial] = useState([]);
    const [form, setForm] = useState(initialForm);
    const [errors, setErrors] = useState({});
    const [catalogLoading, setCatalogLoading] = useState(true);
    const [buscando, setBuscando] = useState(false);
    const [saving, setSaving] = useState(false);
    const [deshabilitandoId, setDeshabilitandoId] = useState(null);

    const authHeaders = useCallback((json = false) => {
        const token = localStorage.getItem("token");
        const headers = token ? { Authorization: `Bearer ${token}` } : {};
        if (json) headers["Content-Type"] = "application/json";
        return headers;
    }, []);

    useEffect(() => {
        setCatalogLoading(true);
        fetch(`/api/registro/catalogos/instrumentos`, { headers: authHeaders() })
            .then(r => (r.ok ? r.json() : Promise.reject()))
            .then(setInstrumentos)
            .catch(() => dialog.error("No fue posible cargar el catálogo de instrumentos."))
            .finally(() => setCatalogLoading(false));
    }, [authHeaders, dialog]);

    const cargarInstrumentosEstudiante = useCallback(async id => {
        const vigentesRes = await fetch(`${API_URL}/estudiantes/${id}/instrumentos/actual`, { headers: authHeaders() });
        if (!vigentesRes.ok) {
            const body = await vigentesRes.json().catch(() => ({}));
            throw new Error(body.message || "No fue posible consultar los instrumentos del estudiante.");
        }
        const historialRes = await fetch(`${API_URL}/estudiantes/${id}/instrumentos`, { headers: authHeaders() });
        if (!historialRes.ok) {
            const body = await historialRes.json().catch(() => ({}));
            throw new Error(body.message || "No fue posible consultar el historial de instrumentos.");
        }
        setVigentes(await vigentesRes.json());
        setHistorial(await historialRes.json());
    }, [authHeaders]);

    const buscarEstudiante = async event => {
        event.preventDefault();
        const texto = numeroIdentificacion.trim();
        if (!texto) {
            const errores = { numeroIdentificacion: "Ingrese el número de identificación del estudiante." };
            setErrors(errores);
            return;
        }
        setErrors({});
        setBuscando(true);
        try {
            const busquedaRes = await fetch(`${API_URL}/estudiantes?texto=${encodeURIComponent(texto)}`, {
                headers: authHeaders(),
            });
            if (!busquedaRes.ok) {
                const body = await busquedaRes.json().catch(() => ({}));
                throw new Error(body.message || "No fue posible buscar al estudiante.");
            }
            const resultados = await busquedaRes.json();
            const encontrado = resultados.find(r => r.numeroIdentificacion === texto) || resultados[0];
            if (!encontrado) {
                throw new Error("No se encontró ningún estudiante con ese número de identificación.");
            }

            await cargarInstrumentosEstudiante(encontrado.id);
            setIdEstudiante(encontrado.id);
            setEstudianteActivo(encontrado);
            setForm(initialForm);
        } catch (e) {
            setIdEstudiante(null);
            setEstudianteActivo(null);
            setVigentes([]);
            setHistorial([]);
            dialog.error(e.message);
        } finally {
            setBuscando(false);
        }
    };

    const setField = (name, value) => {
        setForm(f => ({ ...f, [name]: value }));
        setErrors(e => ({ ...e, [name]: "" }));
    };

    const asignar = async event => {
        event.preventDefault();
        if (!form.idInstrumento) {
            setErrors(e => ({ ...e, idInstrumento: "Seleccione un instrumento." }));
            return;
        }

        const payload = {
            idInstrumento: Number(form.idInstrumento),
            observaciones: form.observaciones.trim() || null,
        };

        try {
            setSaving(true);
            const response = await fetch(`${API_URL}/estudiantes/${idEstudiante}/instrumentos`, {
                method: "POST",
                headers: authHeaders(true),
                body: JSON.stringify(payload),
            });
            const body = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(body.message || "No fue posible asignar el instrumento.");
            await cargarInstrumentosEstudiante(idEstudiante);
            setForm(initialForm);
            dialog.exito(`Instrumento "${body.nombreInstrumento}" asignado correctamente el ${formatDate(body.fechaInicio)}.`);
        } catch (e) {
            dialog.error(e.message);
        } finally {
            setSaving(false);
        }
    };

    const deshabilitar = async idEstudianteInstrumento => {
        setDeshabilitandoId(idEstudianteInstrumento);
        try {
            const response = await fetch(
                `${API_URL}/estudiantes/${idEstudiante}/instrumentos/${idEstudianteInstrumento}/deshabilitar`,
                { method: "PUT", headers: authHeaders(true) }
            );
            const body = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(body.message || "No fue posible deshabilitar el instrumento.");
            await cargarInstrumentosEstudiante(idEstudiante);
            dialog.exito(`Instrumento "${body.nombreInstrumento}" finalizado el ${formatDate(body.fechaFin)}.`);
        } catch (e) {
            dialog.error(e.message);
        } finally {
            setDeshabilitandoId(null);
        }
    };

    return (
        <div className="asignar-instrumento">
            <PageHead
                title="Asignar instrumento"
                sub="Registre o cambie el instrumento musical asociado al expediente académico del estudiante."
            />

            <section className="panel">
                <div className="panel-head"><h2>Estudiante</h2></div>
                <form onSubmit={buscarEstudiante} className="panel-body busqueda-estudiante" noValidate>
                    <Field id="txtIdentificacionEstudiante" label="Número de identificación" icon={Search} error={errors.numeroIdentificacion} errorKey="numeroIdentificacion">
                        <input
                            id="txtIdentificacionEstudiante"
                            className="sg-input"
                            value={numeroIdentificacion}
                            onChange={e => { setNumeroIdentificacion(e.target.value); setErrors(err => ({ ...err, numeroIdentificacion: "" })); }}
                            placeholder="Ej: 118760542"
                            inputMode="numeric"
                            aria-invalid={errors.numeroIdentificacion ? "true" : undefined}
                        />
                    </Field>
                    <button className={`sg-btn sg-btn--primary${buscando ? " is-loading" : ""}`} type="submit" disabled={buscando} aria-busy={buscando || undefined}>
                        {buscando ? <span className="sg-spinner" aria-hidden="true" /> : <Search size={18} strokeWidth={1.75} aria-hidden="true" />}
                        {buscando ? "Buscando…" : "Buscar estudiante"}
                    </button>
                </form>
                {estudianteActivo && (
                    <div className="panel-foot ficha-estudiante">
                        <div className="profile-head">
                            <span className="avatar avatar--lg" aria-hidden="true">{iniciales(estudianteActivo.nombreCompleto)}</span>
                            <div>
                                <h3>{estudianteActivo.nombreCompleto}</h3>
                                <p className="muted tnum">Identificación {estudianteActivo.numeroIdentificacion}</p>
                            </div>
                        </div>
                    </div>
                )}
            </section>

            {estudianteActivo && (
                <>
                    <section className="panel">
                        <div className="panel-head"><h2>Instrumento(s) actual(es)</h2></div>
                        {vigentes.length === 0 ? (
                            <EmptyState compact icon={Music} title="Sin instrumentos vigentes" description="El estudiante no tiene instrumentos vigentes actualmente." />
                        ) : (
                            <div className="table-wrap">
                                <table className="dtable dtable--cards">
                                    <caption className="sr-only">Instrumentos vigentes del estudiante</caption>
                                    <thead>
                                        <tr>
                                            <th scope="col">Instrumento</th>
                                            <th scope="col">Fecha de asignación</th>
                                            <th scope="col" className="actions"><span className="sr-only">Acciones</span></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {vigentes.map(v => (
                                            <tr key={v.idEstudianteInstrumento}>
                                                <td className="c-main"><span className="inst">{v.nombreInstrumento}</span><span className="sub">{v.codigoInstrumento}</span></td>
                                                <td data-label="Desde" className="tnum">{formatDate(v.fechaInicio)}</td>
                                                <td className="actions">
                                                    <button
                                                        type="button"
                                                        className="sg-btn sg-btn--ghost sg-btn--sm btn-peligro"
                                                        disabled={deshabilitandoId === v.idEstudianteInstrumento}
                                                        onClick={() => deshabilitar(v.idEstudianteInstrumento)}
                                                    >
                                                        {deshabilitandoId === v.idEstudianteInstrumento ? "Deshabilitando…" : "Deshabilitar"}
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </section>

                    <section className="panel">
                        <div className="panel-head"><h2>Asignar instrumento</h2></div>
                        <form onSubmit={asignar} noValidate>
                            <div className="panel-body form-grid">
                                <Field id="cmbInstrumentoAsignar" label="Instrumento" error={errors.idInstrumento} errorKey="idInstrumento" select>
                                    <select
                                        id="cmbInstrumentoAsignar"
                                        className="sg-input sg-select"
                                        value={form.idInstrumento}
                                        onChange={e => setField("idInstrumento", e.target.value)}
                                        disabled={catalogLoading}
                                        aria-invalid={errors.idInstrumento ? "true" : undefined}
                                    >
                                        <option value="">Seleccione un instrumento</option>
                                        {instrumentos.map(i => <option key={i.id} value={i.id}>{i.nombre}</option>)}
                                    </select>
                                </Field>
                                <Field id="txtObservacionesInstrumento" label="Observaciones" optional hint="Máximo 500 caracteres." className="span-all">
                                    <textarea
                                        id="txtObservacionesInstrumento"
                                        className="sg-input"
                                        maxLength={500}
                                        value={form.observaciones}
                                        onChange={e => setField("observaciones", e.target.value)}
                                    />
                                </Field>
                            </div>
                            <div className="panel-foot">
                                <button className={`sg-btn sg-btn--primary${saving ? " is-loading" : ""}`} type="submit" disabled={saving || catalogLoading} aria-busy={saving || undefined}>
                                    {saving ? <span className="sg-spinner" aria-hidden="true" /> : <Music size={18} strokeWidth={1.75} aria-hidden="true" />}
                                    {saving ? "Guardando…" : "Asignar instrumento"}
                                </button>
                            </div>
                        </form>
                    </section>

                    <section className="panel">
                        <div className="panel-head"><h2>Historial de instrumentos</h2></div>
                        {historial.length === 0 ? (
                            <EmptyState compact icon={History} title="Sin historial" description="Aún no hay historial registrado." />
                        ) : (
                            <div className="table-wrap">
                                <table className="dtable dtable--cards">
                                    <caption className="sr-only">Historial de instrumentos del estudiante</caption>
                                    <thead>
                                        <tr>
                                            <th scope="col">Instrumento</th>
                                            <th scope="col">Inicio</th>
                                            <th scope="col">Fin</th>
                                            <th scope="col">Estado</th>
                                            <th scope="col">Observaciones</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {historial.map(h => (
                                            <tr key={h.idEstudianteInstrumento}>
                                                <td className="c-main"><span className="inst">{h.nombreInstrumento}</span></td>
                                                <td data-label="Inicio" className="tnum">{formatDate(h.fechaInicio)}</td>
                                                <td data-label="Fin" className="tnum">{formatDate(h.fechaFin)}</td>
                                                <td>{h.vigente ? <StatusBadge tone="success">Vigente</StatusBadge> : <StatusBadge tone="neutral">Finalizado</StatusBadge>}</td>
                                                <td data-label="Observaciones">{h.observaciones || "—"}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </section>
                </>
            )}
        </div>
    );
}
