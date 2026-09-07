import { useEffect, useMemo, useState } from "react";

const API = import.meta.env.VITE_API_URL || "http://localhost:8080";
const MAX_PDF = 50 * 1024 * 1024;
const GENEROS = ["MASCULINO", "FEMENINO", "OTRO"];

const emptyGuardian = () => ({
  idTipoIdentificacion: "",
  numeroIdentificacion: "",
  nombres: "",
  primerApellido: "",
  segundoApellido: "",
  telefonoPrincipal: "",
  telefonoSecundario: "",
  correo: "",
  ocupacion: "",
  parentesco: "MADRE",
  responsablePrincipal: false,
  contactoEmergencia: false,
});

function ageFromDate(value) {
  if (!value) return null;
  const birth = new Date(`${value}T00:00:00`);
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const month = today.getMonth() - birth.getMonth();
  if (month < 0 || (month === 0 && today.getDate() < birth.getDate())) age--;
  return age;
}

export default function CreateStudent() {
  const [types, setTypes] = useState([]);
  const [centers, setCenters] = useState([]);
  const [instruments, setInstruments] = useState([]);
  const [policy, setPolicy] = useState(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    idTipoIdentificacion: "",
    numeroIdentificacion: "",
    nombres: "",
    primerApellido: "",
    segundoApellido: "",
    fechaNacimiento: "",
    genero: "",
    telefono: "",
    correo: "",
    direccion: "",
    nivelEscolar: "",
    idCentroEducativo: "",
    condicionesRelevantes: "",
    idInstrumento: "",
    encargados: [],
  });

  const edad = useMemo(() => ageFromDate(form.fechaNacimiento), [form.fechaNacimiento]);
  const menor = edad !== null && edad < 18;
  const requiereCentro = edad !== null && edad >= 6 && edad < 18;

  useEffect(() => {
    const token = localStorage.getItem("token");
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    Promise.all([
      fetch(`${API}/api/registro/catalogos/tipos-identificacion`, { headers }).then(r => r.json()),
      fetch(`${API}/api/registro/catalogos/centros-educativos`, { headers }).then(r => r.json()),
      fetch(`${API}/api/registro/catalogos/instrumentos`, { headers }).then(r => r.json()),
    ]).then(([t, c, i]) => {
      setTypes(t); setCenters(c); setInstruments(i);
    }).catch(() => setError("No fue posible cargar los catálogos."));
  }, []);

  useEffect(() => {
    if (!menor) setForm(f => ({ ...f, encargados: [], idCentroEducativo: "" }));
  }, [menor]);

  const setField = (name, value) => setForm(f => ({ ...f, [name]: value }));

  const addGuardian = () => {
    if (form.encargados.length < 2) setForm(f => ({ ...f, encargados: [...f.encargados, emptyGuardian()] }));
  };

  const updateGuardian = (index, name, value) => {
    setForm(f => ({
      ...f,
      encargados: f.encargados.map((g, i) => i === index ? { ...g, [name]: value } : g),
    }));
  };

  const removeGuardian = (index) => {
    setForm(f => ({ ...f, encargados: f.encargados.filter((_, i) => i !== index) }));
  };

  const submit = async (event) => {
    event.preventDefault();
    setError(""); setSuccess("");
    if (!policy) return setError("Debe adjuntar la póliza en PDF.");
    if (policy.size > MAX_PDF || policy.type !== "application/pdf" || !policy.name.toLowerCase().endsWith(".pdf")) {
      return setError("La póliza debe ser un PDF válido de máximo 10 MB.");
    }
    if (menor && (form.encargados.length < 1 || form.encargados.length > 2)) {
      return setError("Un menor debe tener uno o dos encargados.");
    }
    if (menor && form.encargados.filter(g => g.responsablePrincipal).length !== 1) {
      return setError("Debe existir exactamente un responsable principal.");
    }
    if (requiereCentro && !form.idCentroEducativo) {
      return setError("Debe seleccionar el centro educativo.");
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
      setSuccess(`Estudiante registrado correctamente. ID: ${body.id}`);
      setPolicy(null);
      event.target.reset();
      setForm({ idTipoIdentificacion: "", numeroIdentificacion: "", nombres: "", primerApellido: "", segundoApellido: "", fechaNacimiento: "", genero: "", telefono: "", correo: "", direccion: "", nivelEscolar: "", idCentroEducativo: "", condicionesRelevantes: "", idInstrumento: "", encargados: [] });
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <main style={{ maxWidth: 1000, margin: "30px auto", padding: 20 }}>
      <h1>Registrar estudiante</h1>
      <p>Complete la información del estudiante. La edad se calcula automáticamente.</p>
      {error && <div role="alert" style={{ marginBottom: 15 }}>{error}</div>}
      {success && <div role="status" style={{ marginBottom: 15 }}>{success}</div>}

      <form onSubmit={submit}>
        <fieldset><legend>Identificación y datos personales</legend>
          <label>Tipo de identificación *<br/><select required value={form.idTipoIdentificacion} onChange={e => setField("idTipoIdentificacion", e.target.value)}><option value="">Seleccione</option>{types.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}</select></label><br/>
          <label>Identificación *<br/><input required maxLength={40} value={form.numeroIdentificacion} onChange={e => setField("numeroIdentificacion", e.target.value)} /></label><br/>
          <label>Nombres *<br/><input required maxLength={100} value={form.nombres} onChange={e => setField("nombres", e.target.value)} /></label><br/>
          <label>Primer apellido *<br/><input required maxLength={80} value={form.primerApellido} onChange={e => setField("primerApellido", e.target.value)} /></label><br/>
          <label>Segundo apellido<br/><input maxLength={80} value={form.segundoApellido} onChange={e => setField("segundoApellido", e.target.value)} /></label><br/>
          <label>Fecha de nacimiento *<br/><input required type="date" value={form.fechaNacimiento} onChange={e => setField("fechaNacimiento", e.target.value)} /></label>{edad !== null && <strong> — Edad: {edad} años ({menor ? "menor de edad" : "mayor de edad"})</strong>}<br/>
          <label>Género *<br/><select required value={form.genero} onChange={e => setField("genero", e.target.value)}><option value="">Seleccione</option>{GENEROS.map(g => <option key={g} value={g}>{g.replaceAll("_", " ")}</option>)}</select></label><br/>
          <label>Teléfono<br/><input maxLength={30} value={form.telefono} onChange={e => setField("telefono", e.target.value)} /></label><br/>
          <label>Correo<br/><input type="email" maxLength={254} value={form.correo} onChange={e => setField("correo", e.target.value)} /></label><br/>
          <label>Dirección<br/><textarea maxLength={500} value={form.direccion} onChange={e => setField("direccion", e.target.value)} /></label>
        </fieldset>

        <fieldset><legend>Información académica</legend>
          <label>Instrumento *<br/><select required value={form.idInstrumento} onChange={e => setField("idInstrumento", e.target.value)}><option value="">Seleccione</option>{instruments.map(i => <option key={i.id} value={i.id}>{i.nombre}</option>)}</select></label><br/>
          {requiereCentro && <><label>Centro educativo *<br/><select required value={form.idCentroEducativo} onChange={e => setField("idCentroEducativo", e.target.value)}><option value="">Seleccione</option>{centers.map(c => <option key={c.id} value={c.id}>{c.nombre}{c.tipo ? ` — ${c.tipo}` : ""}</option>)}</select></label><br/></>}
          {menor && !requiereCentro && <p>Por la edad indicada no se solicita centro educativo.</p>}
          <label>Nivel escolar<br/><input maxLength={80} value={form.nivelEscolar} onChange={e => setField("nivelEscolar", e.target.value)} /></label><br/>
          <label>Condiciones relevantes<br/><textarea value={form.condicionesRelevantes} onChange={e => setField("condicionesRelevantes", e.target.value)} /></label>
        </fieldset>

        {menor && <fieldset><legend>Encargados</legend>
          {form.encargados.map((g, index) => <div key={index} style={{ border: "1px solid #ccc", padding: 15, marginBottom: 10 }}>
            <h3>Encargado {index + 1}</h3>
            <label>Tipo identificación<br/><select value={g.idTipoIdentificacion} onChange={e => updateGuardian(index, "idTipoIdentificacion", e.target.value)}><option value="">Seleccione</option>{types.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}</select></label><br/>
            <label>Identificación<br/><input maxLength={40} value={g.numeroIdentificacion} onChange={e => updateGuardian(index, "numeroIdentificacion", e.target.value)} /></label><br/>
            <label>Nombres *<br/><input required maxLength={100} value={g.nombres} onChange={e => updateGuardian(index, "nombres", e.target.value)} /></label><br/>
            <label>Primer apellido *<br/><input required maxLength={80} value={g.primerApellido} onChange={e => updateGuardian(index, "primerApellido", e.target.value)} /></label><br/>
            <label>Segundo apellido<br/><input maxLength={80} value={g.segundoApellido} onChange={e => updateGuardian(index, "segundoApellido", e.target.value)} /></label><br/>
            <label>Teléfono principal *<br/><input required maxLength={30} value={g.telefonoPrincipal} onChange={e => updateGuardian(index, "telefonoPrincipal", e.target.value)} /></label><br/>
            <label>Teléfono secundario<br/><input maxLength={30} value={g.telefonoSecundario} onChange={e => updateGuardian(index, "telefonoSecundario", e.target.value)} /></label><br/>
            <label>Correo<br/><input type="email" maxLength={254} value={g.correo} onChange={e => updateGuardian(index, "correo", e.target.value)} /></label><br/>
            <label>Ocupación<br/><input maxLength={120} value={g.ocupacion} onChange={e => updateGuardian(index, "ocupacion", e.target.value)} /></label><br/>
            <label>Parentesco *<br/><select required value={g.parentesco} onChange={e => updateGuardian(index, "parentesco", e.target.value)}>{["MADRE","PADRE","TUTOR","FAMILIAR","OTRO"].map(p => <option key={p}>{p}</option>)}</select></label><br/>
            <label><input type="checkbox" checked={g.responsablePrincipal} onChange={e => updateGuardian(index, "responsablePrincipal", e.target.checked)} /> Responsable principal</label><br/>
            <label><input type="checkbox" checked={g.contactoEmergencia} onChange={e => updateGuardian(index, "contactoEmergencia", e.target.checked)} /> Contacto de emergencia</label><br/>
            <button type="button" onClick={() => removeGuardian(index)}>Eliminar encargado</button>
          </div>)}
          {form.encargados.length < 2 && <button type="button" onClick={addGuardian}>Agregar encargado</button>}
          {form.encargados.length > 0 && <p>Responsable principal: {form.encargados.filter(g => g.responsablePrincipal).length}/1 · Contactos de emergencia: {form.encargados.filter(g => g.contactoEmergencia).length}/2</p>}
        </fieldset>}

        <fieldset><legend>Póliza</legend>
          <label>Póliza (PDF, máximo 10 MB) *<br/><input required type="file" accept="application/pdf,.pdf" onChange={e => setPolicy(e.target.files?.[0] || null)} /></label>
          {policy && <p>{policy.name} — {(policy.size / 1024 / 1024).toFixed(2)} MB</p>}
        </fieldset>

        <button type="submit" disabled={saving}>{saving ? "Registrando..." : "Registrar estudiante"}</button>
      </form>
    </main>
  );
}
