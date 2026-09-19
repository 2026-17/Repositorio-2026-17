import { useCallback, useEffect, useState } from "react";
import "../AdminSis/styles/admin_Sis.css";
import "./AsignarInstrumento.css";
import { API_URL } from "../../config/api.js";

const initialForm = { idInstrumento: "", observaciones: "" };

function formatDate(iso) {
    if (!iso) return "—";
    const [y, m, d] = iso.split("-");
    return `${d}/${m}/${y}`;
}

export default function AsignarInstrumento() {
    const [numeroIdentificacion, setNumeroIdentificacion] = useState("");
    const [idEstudiante, setIdEstudiante] = useState(null);
    const [estudianteActivo, setEstudianteActivo] = useState(null);
    const [instrumentos, setInstrumentos] = useState([]);
    const [vigentes, setVigentes] = useState([]);
    const [historial, setHistorial] = useState([]);
    const [form, setForm] = useState(initialForm);
    const [errors, setErrors] = useState({});
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
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
            .catch(() => setError("No fue posible cargar el catálogo de instrumentos."))
            .finally(() => setCatalogLoading(false));
    }, [authHeaders]);

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
        setError("");
        setSuccess("");
        const texto = numeroIdentificacion.trim();
        if (!texto) {
            setErrors({ numeroIdentificacion: "Ingrese el número de identificación del estudiante." });
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
            setError(e.message);
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
        setError("");
        setSuccess("");
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
            setSuccess(`Instrumento "${body.nombreInstrumento}" asignado correctamente el ${formatDate(body.fechaInicio)}.`);
        } catch (e) {
            setError(e.message);
        } finally {
            setSaving(false);
        }
    };

    const deshabilitar = async idEstudianteInstrumento => {
        setError("");
        setSuccess("");
        setDeshabilitandoId(idEstudianteInstrumento);
        try {
            const response = await fetch(
                `${API_URL}/estudiantes/${idEstudiante}/instrumentos/${idEstudianteInstrumento}/deshabilitar`,
                { method: "PUT", headers: authHeaders(true) }
            );
            const body = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(body.message || "No fue posible deshabilitar el instrumento.");
            await cargarInstrumentosEstudiante(idEstudiante);
            setSuccess(`Instrumento "${body.nombreInstrumento}" finalizado el ${formatDate(body.fechaFin)}.`);
        } catch (e) {
            setError(e.message);
        } finally {
            setDeshabilitandoId(null);
        }
    };

    return (
        <main className="content">
            <div className="page-title">
                <h1>Asignar instrumento</h1>
                <p>Registre o cambie el instrumento musical asociado al expediente académico del estudiante.</p>
            </div>

            {error && <div className="banner banner-danger" role="alert">⚠ {error}</div>}
            {success && <div className="banner banner-success" role="status">✓ {success}</div>}

            <section className="card">
                <h2 className="card-title">Estudiante</h2>
                <form onSubmit={buscarEstudiante} className="grid-2" noValidate>
                    <Field label="Número de identificación" required error={errors.numeroIdentificacion} errorKey="numeroIdentificacion">
                        <input
                            className={`input ${errors.numeroIdentificacion ? "input-error" : ""}`}
                            value={numeroIdentificacion}
                            onChange={e => { setNumeroIdentificacion(e.target.value); setErrors(err => ({ ...err, numeroIdentificacion: "" })); }}
                            placeholder="Ej: 118760542"
                            inputMode="numeric"
                        />
                    </Field>
                    <div className="field asignar-instrumento-buscar-btn">
                        <button className="btn btn-secondary" type="submit" disabled={buscando}>
                            {buscando ? "Buscando..." : "Buscar estudiante"}
                        </button>
                    </div>
                </form>
            </section>

            {estudianteActivo && (
                <>
                    <section className="card">
                        <h2 className="card-title">Instrumento(s) actual(es)</h2>
                        {vigentes.length === 0 ? (
                            <p className="hint">El estudiante no tiene instrumentos vigentes actualmente.</p>
                        ) : (
                            <table className="table">
                                <thead>
                                <tr>
                                    <th>Instrumento</th>
                                    <th>Código</th>
                                    <th>Fecha de asignación</th>
                                    <th></th>
                                </tr>
                                </thead>
                                <tbody>
                                {vigentes.map(v => (
                                    <tr key={v.idEstudianteInstrumento}>
                                        <td>{v.nombreInstrumento}</td>
                                        <td>{v.codigoInstrumento}</td>
                                        <td>{formatDate(v.fechaInicio)}</td>
                                        <td>
                                            <button
                                                type="button"
                                                className="btn btn-secondary btn-sm"
                                                disabled={deshabilitandoId === v.idEstudianteInstrumento}
                                                onClick={() => deshabilitar(v.idEstudianteInstrumento)}
                                            >
                                                {deshabilitandoId === v.idEstudianteInstrumento ? "Deshabilitando..." : "Deshabilitar"}
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        )}
                    </section>

                    <section className="card">
                        <h2 className="card-title">Asignar instrumento</h2>
                        <form onSubmit={asignar} noValidate>
                            <div className="grid-2">
                                <Field label="Instrumento" required error={errors.idInstrumento} errorKey="idInstrumento">
                                    <select
                                        className={`input ${errors.idInstrumento ? "input-error" : ""}`}
                                        value={form.idInstrumento}
                                        onChange={e => setField("idInstrumento", e.target.value)}
                                        disabled={catalogLoading}
                                    >
                                        <option value="">Seleccione un instrumento</option>
                                        {instrumentos.map(i => <option key={i.id} value={i.id}>{i.nombre}</option>)}
                                    </select>
                                </Field>
                            </div>
                            <Field label="Observaciones" hint="Opcional, máximo 500 caracteres.">
                <textarea
                    className="input textarea"
                    maxLength={500}
                    value={form.observaciones}
                    onChange={e => setField("observaciones", e.target.value)}
                />
                            </Field>
                            <div className="actions-row">
                                <button className="btn btn-primary" type="submit" disabled={saving || catalogLoading}>
                                    {saving ? "Guardando..." : "Asignar instrumento"}
                                </button>
                            </div>
                        </form>
                    </section>

                    <section className="card">
                        <h2 className="card-title">Historial de instrumentos</h2>
                        {historial.length === 0 ? (
                            <p className="hint">Aún no hay historial registrado.</p>
                        ) : (
                            <table className="table">
                                <thead>
                                <tr>
                                    <th>Instrumento</th>
                                    <th>Inicio</th>
                                    <th>Fin</th>
                                    <th>Estado</th>
                                    <th>Observaciones</th>
                                </tr>
                                </thead>
                                <tbody>
                                {historial.map(h => (
                                    <tr key={h.idEstudianteInstrumento}>
                                        <td>{h.nombreInstrumento}</td>
                                        <td>{formatDate(h.fechaInicio)}</td>
                                        <td>{formatDate(h.fechaFin)}</td>
                                        <td>{h.vigente ? <span className="pill">Vigente</span> : "Finalizado"}</td>
                                        <td>{h.observaciones || "—"}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        )}
                    </section>
                </>
            )}
        </main>
    );
}

function Field({ label, required = false, hint, error, errorKey, children }) {
    return (
        <div className="field" data-error-field={errorKey || undefined}>
            <label className="field-label">{label} {required && <span className="required">*</span>}</label>
            {children}
            {error ? <p className="field-error">{error}</p> : hint && <p className="hint">{hint}</p>}
        </div>
    );
}
