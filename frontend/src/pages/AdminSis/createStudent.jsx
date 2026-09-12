import { useEffect, useMemo, useState } from "react";
import "../../index.css";

const API = import.meta.env.VITE_API_URL || "http://localhost:8080";
const MAX_PDF = 50 * 1024 * 1024;
const GENEROS = ["MASCULINO", "FEMENINO", "OTRO"];
const PARENTESCOS = ["MADRE", "PADRE", "TUTOR", "FAMILIAR", "OTRO"];
const NIVELES = ["Preescolar", "Primaria", "Secundaria", "Universitaria", "Ninguno"];

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

const initialForm = {
  idTipoIdentificacion: "", numeroIdentificacion: "", nombres: "", primerApellido: "", segundoApellido: "",
  fechaNacimiento: "", genero: "", telefono: "", correo: "", direccion: "", nivelEscolar: "",
  idCentroEducativo: "", condicionesRelevantes: "", idInstrumento: "", encargados: [],
};

export default function CreateStudent() {
  const [types, setTypes] = useState([]);
  const [centers, setCenters] = useState([]);
  const [instruments, setInstruments] = useState([]);
  const [policy, setPolicy] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState("");
  const [successId, setSuccessId] = useState("");
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
      fetch(`${API}/api/registro/catalogos/tipos-identificacion`, { headers }).then(async r => r.ok ? r.json() : Promise.reject()),
      fetch(`${API}/api/registro/catalogos/centros-educativos`, { headers }).then(async r => r.ok ? r.json() : Promise.reject()),
      fetch(`${API}/api/registro/catalogos/instrumentos`, { headers }).then(async r => r.ok ? r.json() : Promise.reject()),
    ]).then(([t, c, i]) => {
      setTypes(t); setCenters(c); setInstruments(i);
    }).catch(() => setError("No fue posible cargar los catálogos. Verifique que el backend esté disponible."))
      .finally(() => setCatalogLoading(false));
  }, []);

  useEffect(() => {
    if (!menor) setForm(f => ({ ...f, encargados: [], idCentroEducativo: "" }));
  }, [menor]);

  const setField = (name, value) => setForm(f => ({ ...f, [name]: value }));
  const addGuardian = () => {
    if (form.encargados.length < 2) setForm(f => ({ ...f, encargados: [...f.encargados, emptyGuardian()] }));
  };
  const updateGuardian = (index, name, value) => setForm(f => ({
    ...f, encargados: f.encargados.map((g, i) => i === index ? { ...g, [name]: value } : g),
  }));
  const removeGuardian = index => setForm(f => ({ ...f, encargados: f.encargados.filter((_, i) => i !== index) }));

  const reset = () => {
    setForm(initialForm); setPolicy(null); setError(""); setSuccessId("");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const submit = async event => {
    event.preventDefault();
    setError("");

    if (!policy) return setError("Debe adjuntar la póliza en PDF.");
    if (policy.size > MAX_PDF) return setError("La póliza no puede superar 50 MB.");
    if (policy.type !== "application/pdf" || !policy.name.toLowerCase().endsWith(".pdf")) return setError("La póliza debe ser un archivo PDF.");
    if (edad === null) return setError("La fecha de nacimiento no es válida.");
    if (menor && (form.encargados.length < 1 || form.encargados.length > 2)) return setError("Un menor debe tener uno o dos encargados.");
    if (menor && form.encargados.filter(g => g.responsablePrincipal).length !== 1) return setError("Debe existir exactamente un responsable principal.");
    if (requiereCentro && !form.idCentroEducativo) return setError("Debe seleccionar el centro educativo.");

    const payload = {
      ...form,
      idTipoIdentificacion: Number(form.idTipoIdentificacion),
      idInstrumento: Number(form.idInstrumento),
      idCentroEducativo: form.idCentroEducativo ? Number(form.idCentroEducativo) : null,
      encargados: menor ? form.encargados.map(g => ({
        ...g,
        idTipoIdentificacion: g.idTipoIdentificacion ? Number(g.idTipoIdentificacion) : null,
      })) : [],
    };

    const data = new FormData();
    data.append("estudiante", new Blob([JSON.stringify(payload)], { type: "application/json" }));
    data.append("politica", policy);

    try {
      setSaving(true);
      const token = localStorage.getItem("token");
      const response = await fetch(`${API}/api/estudiantes`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: data,
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || "No fue posible registrar el estudiante.");
      setSuccessId(body.id);
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  };

  if (successId) {
    return (
      <div className="sigac-app">
        <header className="sigac-header">
          <div className="brand"><span className="brand-mark">♪</span><strong>SIGAC</strong><span>Matrícula académica</span></div>
          <div className="user-badge"><span>AD</span> Administrador</div>
        </header>
        <main className="confirm-wrap">
          <section className="confirm-card">
            <div className="confirm-icon">✓</div>
            <h1>Estudiante registrado</h1>
            <p>El estudiante quedó registrado con estado activo y ya está disponible para matrícula y gestión académica.</p>
            <div className="confirm-id"><small>ID asignado</small><strong>{successId}</strong></div>
            <button className="btn btn-primary full" type="button" onClick={reset}>Registrar otro estudiante</button>
          </section>
        </main>
      </div>
    );
  }

  return (
    <div className="sigac-app">
      <header className="sigac-header">
        <div className="brand"><span className="brand-mark">♪</span><strong>SIGAC</strong><span>Matrícula académica</span></div>
        <div className="user-badge"><span>AD</span> Administrador</div>
      </header>

      <main className="content">
        <div className="page-title">
          <h1>Registrar estudiante</h1>
          <p>Datos personales, académicos, de contacto y documentación requerida.</p>
        </div>

        {error && <div className="banner banner-warning" role="alert">⚠ {error}</div>}
        {catalogLoading && <div className="banner banner-info">Cargando catálogos...</div>}

        <form onSubmit={submit}>
          <section className="card">
            <h2 className="card-title">Datos personales</h2>
            <div className="grid-2">
              <Field label="Tipo de identificación" required>
                <select className="input" required value={form.idTipoIdentificacion} onChange={e => setField("idTipoIdentificacion", e.target.value)}>
                  <option value="">Seleccione una opción</option>
                  {types.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}
                </select>
              </Field>
              <Field label="Número de identificación" required hint="Debe ser única en el sistema.">
                <input className="input" required maxLength={40} value={form.numeroIdentificacion} onChange={e => setField("numeroIdentificacion", e.target.value)} />
              </Field>
            </div>
            <div className="grid-2">
              <Field label="Nombres" required><input className="input" required maxLength={100} value={form.nombres} onChange={e => setField("nombres", e.target.value)} /></Field>
              <Field label="Primer apellido" required><input className="input" required maxLength={80} value={form.primerApellido} onChange={e => setField("primerApellido", e.target.value)} /></Field>
            </div>
            <div className="grid-2">
              <Field label="Segundo apellido"><input className="input" maxLength={80} value={form.segundoApellido} onChange={e => setField("segundoApellido", e.target.value)} /></Field>
              <Field label="Género" required>
                <select className="input" required value={form.genero} onChange={e => setField("genero", e.target.value)}>
                  <option value="">Seleccione una opción</option>{GENEROS.map(g => <option key={g}>{g}</option>)}
                </select>
              </Field>
            </div>
            <div className="grid-2">
              <Field label="Fecha de nacimiento" required>
                <input className="input" required type="date" max={todayISO()} value={form.fechaNacimiento} onChange={e => setField("fechaNacimiento", e.target.value)} />
              </Field>
              <div className="age-box">
                <small>Edad calculada automáticamente</small>
                <strong>{edad === null ? "—" : `${edad} años`}</strong>
                {edad !== null && <span>{menor ? "Menor de edad" : "Mayor de edad"}</span>}
              </div>
            </div>
            <div className="grid-2">
              <Field label="Dirección exacta de residencia" required><textarea className="input textarea" required maxLength={500} value={form.direccion} onChange={e => setField("direccion", e.target.value)} placeholder="Provincia, cantón, distrito y señas exactas" /></Field>
              <Field label="Condiciones relevantes" hint="Opcional."><textarea className="input textarea" value={form.condicionesRelevantes} onChange={e => setField("condicionesRelevantes", e.target.value)} placeholder="Padecimientos médicos, alergias u otras condiciones" /></Field>
            </div>
            <div className="grid-2">
              <Field label="Correo electrónico" hint="Opcional."><input className="input" type="email" maxLength={254} value={form.correo} onChange={e => setField("correo", e.target.value)} placeholder="nombre@correo.com" /></Field>
              <Field label="Teléfono" hint="Opcional."><input className="input" maxLength={30} value={form.telefono} onChange={e => setField("telefono", e.target.value)} placeholder="8888-4321" /></Field>
            </div>
          </section>

          <section className="card">
            <h2 className="card-title">Información académica</h2>
            <div className="grid-2">
              <Field label="Nivel escolar" required>
                <select className="input" required value={form.nivelEscolar} onChange={e => setField("nivelEscolar", e.target.value)}>
                  <option value="">Seleccione una opción</option>{NIVELES.map(n => <option key={n}>{n}</option>)}
                </select>
              </Field>
              <Field label="Instrumento musical" required>
                <select className="input" required value={form.idInstrumento} onChange={e => setField("idInstrumento", e.target.value)}>
                  <option value="">Seleccione un instrumento</option>{instruments.map(i => <option key={i.id} value={i.id}>{i.nombre}</option>)}
                </select>
              </Field>
            </div>
            {requiereCentro ? (
              <Field label="Centro educativo" required hint="Se solicita para estudiantes de 6 a 17 años.">
                <select className="input" required value={form.idCentroEducativo} onChange={e => setField("idCentroEducativo", e.target.value)}>
                  <option value="">Seleccione un centro educativo</option>{centers.map(c => <option key={c.id} value={c.id}>{c.nombre}{c.tipo ? ` — ${c.tipo}` : ""}</option>)}
                </select>
              </Field>
            ) : edad !== null ? <div className="banner banner-info">Por la edad indicada no se solicita centro educativo.</div> : null}
          </section>

          {menor && (
            <section className="card card-accent-warning">
              <div className="card-header"><div><h2 className="card-title">Encargados</h2><p className="card-subtitle">Requerido para menores de edad · máximo 2</p></div><span className="pill">{form.encargados.length}/2</span></div>
              {form.encargados.map((g, index) => (
                <div className="subcard" key={index}>
                  <div className="subcard-header"><strong>Encargado {index + 1}</strong><button className="icon-btn danger" type="button" onClick={() => removeGuardian(index)}>×</button></div>
                  <div className="grid-2">
                    <Field label="Tipo de identificación"><select className="input" value={g.idTipoIdentificacion} onChange={e => updateGuardian(index, "idTipoIdentificacion", e.target.value)}><option value="">Seleccione</option>{types.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}</select></Field>
                    <Field label="Número de identificación"><input className="input" maxLength={40} value={g.numeroIdentificacion} onChange={e => updateGuardian(index, "numeroIdentificacion", e.target.value)} /></Field>
                  </div>
                  <div className="grid-2">
                    <Field label="Nombres" required><input className="input" required maxLength={100} value={g.nombres} onChange={e => updateGuardian(index, "nombres", e.target.value)} /></Field>
                    <Field label="Primer apellido" required><input className="input" required maxLength={80} value={g.primerApellido} onChange={e => updateGuardian(index, "primerApellido", e.target.value)} /></Field>
                  </div>
                  <div className="grid-2">
                    <Field label="Segundo apellido"><input className="input" maxLength={80} value={g.segundoApellido} onChange={e => updateGuardian(index, "segundoApellido", e.target.value)} /></Field>
                    <Field label="Parentesco" required><select className="input" required value={g.parentesco} onChange={e => updateGuardian(index, "parentesco", e.target.value)}>{PARENTESCOS.map(p => <option key={p}>{p}</option>)}</select></Field>
                  </div>
                  <div className="grid-2">
                    <Field label="Teléfono principal" required><input className="input" required maxLength={30} value={g.telefonoPrincipal} onChange={e => updateGuardian(index, "telefonoPrincipal", e.target.value)} /></Field>
                    <Field label="Teléfono secundario"><input className="input" maxLength={30} value={g.telefonoSecundario} onChange={e => updateGuardian(index, "telefonoSecundario", e.target.value)} /></Field>
                  </div>
                  <div className="grid-2">
                    <Field label="Correo"><input className="input" type="email" maxLength={254} value={g.correo} onChange={e => updateGuardian(index, "correo", e.target.value)} /></Field>
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
              <label className={`file-input ${policy ? "has-file" : ""}`}>
                <span>{policy ? `${policy.name} — ${(policy.size / 1024 / 1024).toFixed(2)} MB` : "Seleccionar archivo PDF"}</span>
                <input type="file" accept="application/pdf,.pdf" hidden onChange={e => setPolicy(e.target.files?.[0] || null)} />
              </label>
              <p className="hint">PDF obligatorio · tamaño máximo 50 MB.</p>
            </div>
          </section>

          <div className="actions-row">
            <button className="btn btn-secondary" type="button" onClick={reset}>Cancelar</button>
            <button className="btn btn-primary" type="submit" disabled={saving || catalogLoading}>{saving ? "Guardando..." : "Guardar estudiante"}</button>
          </div>
        </form>
      </main>
    </div>
  );
}

function Field({ label, required = false, hint, children }) {
  return <div className="field"><label className="field-label">{label} {required && <span className="required">*</span>}</label>{children}{hint && <p className="hint">{hint}</p>}</div>;
}
