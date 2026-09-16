import { useEffect, useMemo, useState } from "react";
import { API_URL } from "../../config/api";
import "../../index.css";
import "./CreateStudent.css";

const MAX_PDF = 50 * 1024 * 1024;
const GENEROS = ["MASCULINO", "FEMENINO", "OTRO"];
const PARENTESCOS = ["MADRE", "PADRE", "TUTOR", "FAMILIAR", "OTRO"];
const NIVELES = ["Preescolar", "Primaria", "Secundaria", "Universitaria", "Ninguno"];
const PORCENTAJES_BECA = [25, 50, 75, 100];

const emptyGuardian = () => ({
  idTipoIdentificacion: "", numeroIdentificacion: "", nombres: "", primerApellido: "",
  segundoApellido: "", telefonoPrincipal: "", telefonoSecundario: "", correo: "",
  ocupacion: "", parentesco: "MADRE", responsablePrincipal: false, contactoEmergencia: false,
});

function ageFromDate(value) {
  if (!value) return null;
  const birth = new Date(`${value}T00:00:00`);
  const today = new Date();
  if (Number.isNaN(birth.getTime()) || birth > today) return null;
  let age = today.getFullYear() - birth.getFullYear();
  const month = today.getMonth() - birth.getMonth();
  if (month < 0 || (month === 0 && today.getDate() < birth.getDate())) age--;
  return age;
}

function todayISO() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function formatoColones(valor) {
  if (valor === null || valor === undefined) return "—";
  return `₡${Number(valor).toLocaleString("es-CR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

const initialForm = {
  idTipoIdentificacion: "", numeroIdentificacion: "", nombres: "", primerApellido: "", segundoApellido: "",
  fechaNacimiento: "", genero: "", telefono: "", correo: "", direccion: "", nivelEscolar: "",
  idCentroEducativo: "", condicionesRelevantes: "", idInstrumento: "", encargados: [],
};

export default function CreateStudent() {
  const [types, setTypes] = useState([]);
  const [centers, setCenters] = useState([]);
  const [instruments, setInstruments] = useState([]);
  const [periodos, setPeriodos] = useState([]);
  const [tiposBeca, setTiposBeca] = useState([]);
  const [policy, setPolicy] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [tieneBeca, setTieneBeca] = useState(false);
  const [becaForm, setBecaForm] = useState({ idPeriodo: "", idTipoBeca: "", porcentaje: "" });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");
  const [resultado, setResultado] = useState(null);
  const [saving, setSaving] = useState(false);
  const [catalogLoading, setCatalogLoading] = useState(true);

  const edad = useMemo(() => ageFromDate(form.fechaNacimiento), [form.fechaNacimiento]);
  const menor = edad !== null && edad < 18;
  const requiereCentro = edad !== null && edad >= 6 && edad < 18;

  useEffect(() => {
    const token = localStorage.getItem("token");
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    setCatalogLoading(true);
    Promise.all([
      fetch(`/api/registro/catalogos/tipos-identificacion`, { headers }).then(async r => r.ok ? r.json() : Promise.reject()),
      fetch(`/api/registro/catalogos/centros-educativos`, { headers }).then(async r => r.ok ? r.json() : Promise.reject()),
      fetch(`/api/registro/catalogos/instrumentos`, { headers }).then(async r => r.ok ? r.json() : Promise.reject()),
      fetch(`${API_URL}/periodos-academicos`, { headers }).then(async r => r.ok ? r.json() : Promise.reject()),
      fetch(`/api/becas/tipos-becas`, { headers }).then(async r => r.ok ? r.json() : Promise.reject()),
    ]).then(([t, c, i, p, tb]) => {
      setTypes(t); setCenters(c); setInstruments(i); setPeriodos(p); setTiposBeca(tb);
    }).catch(() => setError("No fue posible cargar los catálogos. Verifique que el backend esté disponible."))
      .finally(() => setCatalogLoading(false));
  }, []);

  useEffect(() => {
    if (!menor) setForm(f => ({ ...f, encargados: [], idCentroEducativo: "" }));
  }, [menor]);

  const setField = (name, value) => {
    setForm(f => ({ ...f, [name]: value }));
    setErrors(e => ({ ...e, [name]: "" }));
    setError("");
  };

  const addGuardian = () => {
    if (form.encargados.length < 2) setForm(f => ({ ...f, encargados: [...f.encargados, emptyGuardian()] }));
  };

  const updateGuardian = (index, name, value) => {
    setForm(f => ({
      ...f, encargados: f.encargados.map((g, i) => i === index ? { ...g, [name]: value } : g),
    }));
    setErrors(e => ({ ...e, [`guardian-${index}-${name}`]: "" }));
    setError("");
  };

  const removeGuardian = index => {
    setForm(f => ({ ...f, encargados: f.encargados.filter((_, i) => i !== index) }));
    setErrors({});
  };

  const reset = () => {
    setForm(initialForm); setPolicy(null); setErrors({}); setError(""); setResultado(null);
    setTieneBeca(false); setBecaForm({ idPeriodo: "", idTipoBeca: "", porcentaje: "" });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const submit = async event => {
    event.preventDefault();
    setError("");
    const nextErrors = {};
    const required = [
      ["idTipoIdentificacion", "Seleccione el tipo de identificación."],
      ["numeroIdentificacion", "Ingrese el número de identificación."],
      ["nombres", "Ingrese los nombres."],
      ["primerApellido", "Ingrese el primer apellido."],
      ["genero", "Seleccione el género."],
      ["fechaNacimiento", "Ingrese la fecha de nacimiento."],
      ["direccion", "Ingrese la dirección exacta de residencia."],
      ["nivelEscolar", "Seleccione el nivel escolar."],
      ["idInstrumento", "Seleccione el instrumento musical."],
    ];

    required.forEach(([name, message]) => {
      if (!String(form[name] ?? "").trim()) nextErrors[name] = message;
    });

    if (edad === null && form.fechaNacimiento) nextErrors.fechaNacimiento = "La fecha de nacimiento no es válida.";
    if (requiereCentro && !form.idCentroEducativo) nextErrors.idCentroEducativo = "Seleccione el centro educativo.";
    if (form.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correo)) nextErrors.correo = "Ingrese un correo electrónico válido.";

    if (!policy) nextErrors.policy = "Debe adjuntar la póliza en PDF.";
    else if (policy.size > MAX_PDF) nextErrors.policy = "La póliza no puede superar 50 MB.";
    else if (policy.type !== "application/pdf" || !policy.name.toLowerCase().endsWith(".pdf")) nextErrors.policy = "La póliza debe ser un archivo PDF.";

    if (menor && (form.encargados.length < 1 || form.encargados.length > 2)) {
      nextErrors.guardians = "Un menor debe tener uno o dos encargados.";
    }

    if (tieneBeca) {
      if (!becaForm.idPeriodo) nextErrors.becaIdPeriodo = "Seleccione el período académico de la beca.";
      if (!becaForm.idTipoBeca) nextErrors.becaIdTipoBeca = "Seleccione el tipo de beca.";
      if (!becaForm.porcentaje) nextErrors.becaPorcentaje = "Seleccione el porcentaje de la beca.";
    }

    if (menor) {
      form.encargados.forEach((g, index) => {
        if (!g.nombres.trim()) nextErrors[`guardian-${index}-nombres`] = "Ingrese los nombres.";
        if (!g.primerApellido.trim()) nextErrors[`guardian-${index}-primerApellido`] = "Ingrese el primer apellido.";
        if (!g.parentesco) nextErrors[`guardian-${index}-parentesco`] = "Seleccione el parentesco.";
        if (!g.telefonoPrincipal.trim()) nextErrors[`guardian-${index}-telefonoPrincipal`] = "Ingrese el teléfono principal.";
        if (g.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(g.correo)) nextErrors[`guardian-${index}-correo`] = "Ingrese un correo válido.";
      });
      if (form.encargados.filter(g => g.responsablePrincipal).length !== 1) {
        nextErrors.guardians = "Debe existir exactamente un responsable principal.";
      }
    }

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      const firstError = Object.keys(nextErrors)[0];
      requestAnimationFrame(() => document.querySelector(`[data-error-field="${firstError}"]`)?.scrollIntoView({ behavior: "smooth", block: "center" }));
      return;
    }

    const payload = {
      ...form,
      idTipoIdentificacion: Number(form.idTipoIdentificacion),
      idInstrumento: Number(form.idInstrumento),
      idCentroEducativo: form.idCentroEducativo ? Number(form.idCentroEducativo) : null,
      encargados: menor ? form.encargados.map(g => ({
        ...g,
        idTipoIdentificacion: g.idTipoIdentificacion ? Number(g.idTipoIdentificacion) : null,
      })) : [],
      beca: tieneBeca ? {
        idPeriodo: Number(becaForm.idPeriodo),
        idTipoBeca: Number(becaForm.idTipoBeca),
        porcentaje: Number(becaForm.porcentaje),
      } : null,
    };

    const data = new FormData();
    data.append("estudiante", new Blob([JSON.stringify(payload)], { type: "application/json" }));
    data.append("politica", policy);

    try {
      setSaving(true);
      const token = localStorage.getItem("token");
      const response = await fetch(`/api/estudiantes`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: data,
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || "No fue posible registrar el estudiante.");
      setResultado(body);
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  };

  if (resultado) {
    return (
      <main className="confirm-wrap">
        <section className="confirm-card">
          <div className="confirm-icon">✓</div>
          <h1>Estudiante registrado</h1>
          <p>El estudiante quedó registrado con estado activo y ya está disponible para matrícula y gestión académica.</p>
          <div className="confirm-id"><small>ID asignado</small><strong>{resultado.id}</strong></div>
          {resultado.beca && (
            <div className="confirm-id">
              <small>Beca asignada</small>
              <strong>{resultado.beca.nombreTipoBeca} — {Number(resultado.beca.porcentaje)}%</strong>
              <p style={{ margin: "8px 0 0", fontSize: 13 }}>
                Descuento: {formatoColones(resultado.beca.montoDescuento)} · Monto a pagar: {formatoColones(resultado.beca.montoAPagar)}
              </p>
            </div>
          )}
          <button className="btn btn-primary full" type="button" onClick={reset}>Registrar otro estudiante</button>
        </section>
      </main>
    );
  }

  return (
    <main className="content">
      <div className="page-title">
        <h1>Registrar estudiante</h1>
        <p>Datos personales, académicos, de contacto y documentación requerida.</p>
      </div>

      {error && <div className="banner banner-warning" role="alert">⚠ {error}</div>}
      {catalogLoading && <div className="banner banner-info">Cargando catálogos...</div>}

      <form onSubmit={submit} noValidate>
        <section className="card">
          <h2 className="card-title">Datos personales</h2>
          <div className="grid-2">
            <Field label="Tipo de identificación" required error={errors.idTipoIdentificacion} errorKey="idTipoIdentificacion">
              <select className={`input ${errors.idTipoIdentificacion ? "input-error" : ""}`} value={form.idTipoIdentificacion} onChange={e => setField("idTipoIdentificacion", e.target.value)}>
                <option value="">Seleccione una opción</option>
                {types.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}
              </select>
            </Field>
            <Field label="Número de identificación" required hint="Debe ser única en el sistema." error={errors.numeroIdentificacion} errorKey="numeroIdentificacion">
              <input className={`input ${errors.numeroIdentificacion ? "input-error" : ""}`} maxLength={40} value={form.numeroIdentificacion} onChange={e => setField("numeroIdentificacion", e.target.value)} />
            </Field>
          </div>
          <div className="grid-2">
            <Field label="Nombres" required error={errors.nombres} errorKey="nombres"><input className={`input ${errors.nombres ? "input-error" : ""}`} maxLength={100} value={form.nombres} onChange={e => setField("nombres", e.target.value)} /></Field>
            <Field label="Primer apellido" required error={errors.primerApellido} errorKey="primerApellido"><input className={`input ${errors.primerApellido ? "input-error" : ""}`} maxLength={80} value={form.primerApellido} onChange={e => setField("primerApellido", e.target.value)} /></Field>
          </div>
          <div className="grid-2">
            <Field label="Segundo apellido"><input className="input" maxLength={80} value={form.segundoApellido} onChange={e => setField("segundoApellido", e.target.value)} /></Field>
            <Field label="Género" required error={errors.genero} errorKey="genero">
              <select className={`input ${errors.genero ? "input-error" : ""}`} value={form.genero} onChange={e => setField("genero", e.target.value)}>
                <option value="">Seleccione una opción</option>{GENEROS.map(g => <option key={g}>{g}</option>)}
              </select>
            </Field>
          </div>
          <div className="grid-2">
            <Field label="Fecha de nacimiento" required error={errors.fechaNacimiento} errorKey="fechaNacimiento">
              <input className={`input ${errors.fechaNacimiento ? "input-error" : ""}`} type="date" max={todayISO()} value={form.fechaNacimiento} onChange={e => setField("fechaNacimiento", e.target.value)} />
            </Field>
            <div className="age-box">
              <small>Edad calculada automáticamente</small>
              <strong>{edad === null ? "—" : `${edad} años`}</strong>
              {edad !== null && <span>{menor ? "Menor de edad" : "Mayor de edad"}</span>}
            </div>
          </div>
          <div className="grid-2">
            <Field label="Dirección exacta de residencia" required error={errors.direccion} errorKey="direccion"><textarea className={`input textarea ${errors.direccion ? "input-error" : ""}`} maxLength={500} value={form.direccion} onChange={e => setField("direccion", e.target.value)} placeholder="Provincia, cantón, distrito y señas exactas" /></Field>
            <Field label="Condiciones relevantes" hint="Opcional."><textarea className="input textarea" value={form.condicionesRelevantes} onChange={e => setField("condicionesRelevantes", e.target.value)} placeholder="Padecimientos médicos, alergias u otras condiciones" /></Field>
          </div>
          <div className="grid-2">
            <Field label="Correo electrónico" hint="Opcional." error={errors.correo} errorKey="correo"><input className={`input ${errors.correo ? "input-error" : ""}`} type="email" maxLength={254} value={form.correo} onChange={e => setField("correo", e.target.value)} placeholder="nombre@correo.com" /></Field>
            <Field label="Teléfono" hint="Opcional."><input className="input" maxLength={30} value={form.telefono} onChange={e => setField("telefono", e.target.value)} placeholder="8888-4321" /></Field>
          </div>
        </section>

        <section className="card">
          <h2 className="card-title">Información académica</h2>
          <div className="grid-2">
            <Field label="Nivel escolar" required error={errors.nivelEscolar} errorKey="nivelEscolar">
              <select className={`input ${errors.nivelEscolar ? "input-error" : ""}`} value={form.nivelEscolar} onChange={e => setField("nivelEscolar", e.target.value)}>
                <option value="">Seleccione una opción</option>{NIVELES.map(n => <option key={n}>{n}</option>)}
              </select>
            </Field>
            <Field label="Instrumento musical" required error={errors.idInstrumento} errorKey="idInstrumento">
              <select className={`input ${errors.idInstrumento ? "input-error" : ""}`} value={form.idInstrumento} onChange={e => setField("idInstrumento", e.target.value)}>
                <option value="">Seleccione un instrumento</option>{instruments.map(i => <option key={i.id} value={i.id}>{i.nombre}</option>)}
              </select>
            </Field>
          </div>
          {requiereCentro ? (
            <Field label="Centro educativo" required hint="Se solicita para estudiantes de 6 a 17 años." error={errors.idCentroEducativo} errorKey="idCentroEducativo">
              <select className={`input ${errors.idCentroEducativo ? "input-error" : ""}`} value={form.idCentroEducativo} onChange={e => setField("idCentroEducativo", e.target.value)}>
                <option value="">Seleccione un centro educativo</option>{centers.map(c => <option key={c.id} value={c.id}>{c.nombre}{c.tipo ? ` — ${c.tipo}` : ""}</option>)}
              </select>
            </Field>
          ) : edad !== null ? <div className="banner banner-info">Por la edad indicada no se solicita centro educativo.</div> : null}
        </section>

        <section className="card">
          <h2 className="card-title">Beca</h2>
          <p className="card-subtitle">Opcional.</p>
          <div className="check-row">
            <label>
              <input
                type="checkbox"
                checked={tieneBeca}
                onChange={e => {
                  setTieneBeca(e.target.checked);
                  setErrors(err => ({ ...err, becaIdPeriodo: "", becaIdTipoBeca: "", becaPorcentaje: "" }));
                }}
              /> El estudiante posee beca
            </label>
          </div>

          {!tieneBeca ? (
            <p className="hint">Beca: 0% — no se asignará ninguna beca en este registro.</p>
          ) : (
            <div className="grid-2">
              <Field label="Período académico" required error={errors.becaIdPeriodo} errorKey="becaIdPeriodo">
                <select className={`input ${errors.becaIdPeriodo ? "input-error" : ""}`} value={becaForm.idPeriodo} onChange={e => { setBecaForm(f => ({ ...f, idPeriodo: e.target.value })); setErrors(err => ({ ...err, becaIdPeriodo: "" })); }}>
                  <option value="">Seleccione un período</option>
                  {periodos.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                </select>
              </Field>
              <Field label="Tipo de beca" required error={errors.becaIdTipoBeca} errorKey="becaIdTipoBeca">
                <select className={`input ${errors.becaIdTipoBeca ? "input-error" : ""}`} value={becaForm.idTipoBeca} onChange={e => { setBecaForm(f => ({ ...f, idTipoBeca: e.target.value })); setErrors(err => ({ ...err, becaIdTipoBeca: "" })); }}>
                  <option value="">Seleccione</option>
                  {tiposBeca.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}
                </select>
              </Field>
              <Field label="Porcentaje" required error={errors.becaPorcentaje} errorKey="becaPorcentaje">
                <select className={`input ${errors.becaPorcentaje ? "input-error" : ""}`} value={becaForm.porcentaje} onChange={e => { setBecaForm(f => ({ ...f, porcentaje: e.target.value })); setErrors(err => ({ ...err, becaPorcentaje: "" })); }}>
                  <option value="">Seleccione</option>
                  {PORCENTAJES_BECA.map(p => <option key={p} value={p}>{p}%</option>)}
                </select>
              </Field>
            </div>
          )}
        </section>

        {menor && (
          <section className="card card-accent-warning">
            <div className="card-header"><div><h2 className="card-title">Encargados</h2><p className="card-subtitle">Requerido para menores de edad · máximo 2</p></div><span className="pill">{form.encargados.length}/2</span></div>
            {errors.guardians && <p className="field-error" data-error-field="guardians">{errors.guardians}</p>}
            {form.encargados.map((g, index) => (
              <div className="subcard" key={index}>
                <div className="subcard-header"><strong>Encargado {index + 1}</strong><button className="icon-btn danger" type="button" onClick={() => removeGuardian(index)}>×</button></div>
                <div className="grid-2">
                  <Field label="Tipo de identificación"><select className="input" value={g.idTipoIdentificacion} onChange={e => updateGuardian(index, "idTipoIdentificacion", e.target.value)}><option value="">Seleccione</option>{types.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}</select></Field>
                  <Field label="Número de identificación"><input className="input" maxLength={40} value={g.numeroIdentificacion} onChange={e => updateGuardian(index, "numeroIdentificacion", e.target.value)} /></Field>
                </div>
                <div className="grid-2">
                  <Field label="Nombres" required error={errors[`guardian-${index}-nombres`]} errorKey={`guardian-${index}-nombres`}><input className={`input ${errors[`guardian-${index}-nombres`] ? "input-error" : ""}`} maxLength={100} value={g.nombres} onChange={e => updateGuardian(index, "nombres", e.target.value)} /></Field>
                  <Field label="Primer apellido" required error={errors[`guardian-${index}-primerApellido`]} errorKey={`guardian-${index}-primerApellido`}><input className={`input ${errors[`guardian-${index}-primerApellido`] ? "input-error" : ""}`} maxLength={80} value={g.primerApellido} onChange={e => updateGuardian(index, "primerApellido", e.target.value)} /></Field>
                </div>
                <div className="grid-2">
                  <Field label="Segundo apellido"><input className="input" maxLength={80} value={g.segundoApellido} onChange={e => updateGuardian(index, "segundoApellido", e.target.value)} /></Field>
                  <Field label="Parentesco" required error={errors[`guardian-${index}-parentesco`]} errorKey={`guardian-${index}-parentesco`}><select className={`input ${errors[`guardian-${index}-parentesco`] ? "input-error" : ""}`} value={g.parentesco} onChange={e => updateGuardian(index, "parentesco", e.target.value)}>{PARENTESCOS.map(p => <option key={p}>{p}</option>)}</select></Field>
                </div>
                <div className="grid-2">
                  <Field label="Teléfono principal" required error={errors[`guardian-${index}-telefonoPrincipal`]} errorKey={`guardian-${index}-telefonoPrincipal`}><input className={`input ${errors[`guardian-${index}-telefonoPrincipal`] ? "input-error" : ""}`} maxLength={30} value={g.telefonoPrincipal} onChange={e => updateGuardian(index, "telefonoPrincipal", e.target.value)} /></Field>
                  <Field label="Teléfono secundario"><input className="input" maxLength={30} value={g.telefonoSecundario} onChange={e => updateGuardian(index, "telefonoSecundario", e.target.value)} /></Field>
                </div>
                <div className="grid-2">
                  <Field label="Correo" error={errors[`guardian-${index}-correo`]} errorKey={`guardian-${index}-correo`}><input className={`input ${errors[`guardian-${index}-correo`] ? "input-error" : ""}`} type="email" maxLength={254} value={g.correo} onChange={e => updateGuardian(index, "correo", e.target.value)} /></Field>
                  <Field label="Ocupación"><input className="input" maxLength={120} value={g.ocupacion} onChange={e => updateGuardian(index, "ocupacion", e.target.value)} /></Field>
                </div>
                <div className="check-row">
                  <label><input type="checkbox" checked={g.responsablePrincipal} onChange={e => updateGuardian(index, "responsablePrincipal", e.target.checked)} /> Responsable principal</label>
                  <label><input type="checkbox" checked={g.contactoEmergencia} onChange={e => updateGuardian(index, "contactoEmergencia", e.target.checked)} /> Contacto de emergencia</label>
                </div>
              </div>
            ))}
            <button className="btn btn-secondary" type="button" onClick={addGuardian} disabled={form.encargados.length >= 2}>+ Agregar encargado</button>
            <p className="hint">Debe existir exactamente un responsable principal. Puede marcar hasta dos contactos de emergencia.</p>
          </section>
        )}

        <section className="card">
          <h2 className="card-title">Documentación</h2>
          <div className="field">
            <label className="field-label">Póliza del estudiante <span className="required">*</span></label>
            <label className={`file-input ${policy ? "has-file" : ""} ${errors.policy ? "file-input-error" : ""}`} data-error-field="policy">
              <span>{policy ? `${policy.name} — ${(policy.size / 1024 / 1024).toFixed(2)} MB` : "Seleccionar archivo PDF"}</span>
              <input type="file" accept="application/pdf,.pdf" hidden onChange={e => { setPolicy(e.target.files?.[0] || null); setErrors(err => ({ ...err, policy: "" })); }} />
            </label>
            {errors.policy ? <p className="field-error">{errors.policy}</p> : <p className="hint">PDF obligatorio · tamaño máximo 50 MB.</p>}
          </div>
        </section>

        <div className="actions-row">
          <button className="btn btn-secondary" type="button" onClick={reset}>Cancelar</button>
          <button className="btn btn-primary" type="submit" disabled={saving || catalogLoading}>{saving ? "Guardando..." : "Guardar estudiante"}</button>
        </div>
      </form>
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
