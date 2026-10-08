import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, Check, FileUp, Plus, X } from "lucide-react";
import { API_URL } from "../../config/api";
import "./styles/CreateStudent.css";
import { useDialog } from "../../components/dialog/useDialog";
import UbicacionSelects from "../../components/UbicacionSelects";
import { UBICACION_VACIA } from "../../components/ubicacion";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Stepper from "../../components/ui/Stepper";
import { listaErrores } from "../../components/ui/utilidades";

const MAX_PDF = 50 * 1024 * 1024;
const GENEROS = ["MASCULINO", "FEMENINO", "OTRO"];
const PARENTESCOS = ["MADRE", "PADRE", "TUTOR", "FAMILIAR", "OTRO"];
const NIVELES = ["Preescolar", "Primaria", "Secundaria", "Universitaria", "Ninguno"];
const PORCENTAJES_BECA = [25, 50, 75, 100];

const PASOS = ["Estudiante", "Encargados", "Información académica", "Revisar y confirmar"];

// Prefijo de los ids de los controles (para los enlaces del resumen de errores)
const P = "est-";

// En qué paso está cada campo (para mostrar solo los errores del paso actual)
function pasoDeCampo(campo) {
  if (campo === "guardians" || campo.startsWith("guardian-")) return 1;
  if (["nivelEscolar", "idInstrumento", "idProfesor", "idsPlanEstudio", "centroEducativo", "becaIdPeriodo", "becaIdTipoBeca", "becaPorcentaje"].includes(campo)) return 2;
  if (campo === "policy" || campo === "confirmado") return 3;
  return 0;
}

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
  if (Number.isNaN(birth.getTime()) || birth > today) return null;
  let age = today.getFullYear() - birth.getFullYear();
  const month = today.getMonth() - birth.getMonth();
  if (month < 0 || (month === 0 && today.getDate() < birth.getDate())) age -= 1;
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

function fechaLegible(iso) {
  return iso ? iso.split("-").reverse().join("/") : "—";
}

// Atributos aria de un control según su error
function aria(campo, errors) {
  return {
    "aria-invalid": errors[campo] ? "true" : undefined,
    "aria-describedby": errors[campo] ? `${P}${campo}-error` : undefined,
  };
}

const initialForm = {
  idTipoIdentificacion: "",
  numeroIdentificacion: "",
  nombres: "",
  primerApellido: "",
  segundoApellido: "",
  fechaNacimiento: "",
  genero: "",
  telefono: "",
  correo: "",
  ubicacion: UBICACION_VACIA,
  direccion: "",
  nivelEscolar: "",
  centroEducativo: "",
  condicionesRelevantes: "",
  idInstrumento: "",
  idProfesor: "",
  idsPlanEstudio: [],
  encargados: [],
};

export default function CreateStudent({ onCancel, onSuccess } = {}) {
  const dialog = useDialog();
  const [types, setTypes] = useState([]);
  const [instruments, setInstruments] = useState([]);
  const [planes, setPlanes] = useState([]);
  const [profesores, setProfesores] = useState([]);
  const [profesoresLoading, setProfesoresLoading] = useState(false);
  const [periodos, setPeriodos] = useState([]);
  const [tiposBeca, setTiposBeca] = useState([]);
  const [policy, setPolicy] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [tieneBeca, setTieneBeca] = useState(false);
  const [becaForm, setBecaForm] = useState({ idPeriodo: "", idTipoBeca: "", porcentaje: "" });
  const [confirmado, setConfirmado] = useState(false);
  const [paso, setPaso] = useState(0);
  const [intento, setIntento] = useState(0);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [registrado, setRegistrado] = useState(null);

  const edad = useMemo(() => ageFromDate(form.fechaNacimiento), [form.fechaNacimiento]);
  const menor = edad !== null && edad < 18;
  const requiereCentro = edad !== null && edad >= 6 && edad < 18;
  const incrustado = Boolean(onCancel);
  // Los encargados solo aplican a menores de edad: para un mayor, ese paso no existe.
  const pasosVisibles = menor ? [0, 1, 2, 3] : [0, 2, 3];
  const pasoSiguiente = pasosVisibles[pasosVisibles.indexOf(paso) + 1];
  const pasoAnterior = pasosVisibles[pasosVisibles.indexOf(paso) - 1];

  useEffect(() => {
    const token = localStorage.getItem("token");
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    setCatalogLoading(true);
    Promise.all([
      fetch(`${API_URL}/registro/catalogos/tipos-identificacion`, { headers }).then((r) => (r.ok ? r.json() : Promise.reject(new Error("No se pudo cargar tipos de identificación.")))),
      fetch(`${API_URL}/registro/catalogos/centros-educativos`, { headers }).then((r) => (r.ok ? r.json() : Promise.reject(new Error("No se pudo cargar centros educativos.")))),
      fetch(`${API_URL}/registro/catalogos/instrumentos`, { headers }).then((r) => (r.ok ? r.json() : Promise.reject(new Error("No se pudo cargar instrumentos.")))),
      fetch(`${API_URL}/periodos-academicos`, { headers }).then((r) => (r.ok ? r.json() : Promise.reject(new Error("No se pudo cargar períodos académicos.")))),
      fetch(`${API_URL}/becas/tipos-becas`, { headers }).then((r) => (r.ok ? r.json() : Promise.reject(new Error("No se pudo cargar tipos de beca.")))),
      fetch(`${API_URL}/planes-estudio`, { headers }).then((r) => (r.ok ? r.json() : Promise.reject(new Error("No se pudo cargar los planes de estudio.")))),
    ])
      .then(([t, , i, p, tb, pl]) => {
        setTypes(t);
        setInstruments(i);
        setPeriodos(p);
        setTiposBeca(tb);
        setPlanes(pl);
      })
      .catch(() => {
        if (dialog?.error) dialog.error("No fue posible cargar los catálogos. Verifique que el backend esté disponible.");
      })
      .finally(() => setCatalogLoading(false));
  }, [dialog]);

  useEffect(() => {
    if (!menor) setForm((f) => ({ ...f, encargados: [], centroEducativo: "" }));
  }, [menor]);

  useEffect(() => {
    if (!form.idInstrumento) {
      setProfesores([]);
      return undefined;
    }

    const token = localStorage.getItem("token");
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    let cancelado = false;
    setProfesoresLoading(true);

    fetch(`${API_URL}/profesores/por-instrumento/${form.idInstrumento}`, { headers })
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error("No se pudo cargar los profesores."))))
      .then((data) => {
        if (!cancelado) setProfesores(data);
      })
      .catch(() => {
        if (!cancelado) setErrors((prev) => ({ ...prev, idProfesor: "No fue posible cargar los profesores de ese instrumento." }));
      })
      .finally(() => {
        if (!cancelado) setProfesoresLoading(false);
      });

    return () => {
      cancelado = true;
    };
  }, [form.idInstrumento]);

  // Al cambiar de paso, el foco va al título del paso
  useEffect(() => {
    if (paso === 0) return;
    document.getElementById("est-step-title")?.focus();
  }, [paso]);

  const setField = (name, value) => {
    setForm((f) => {
      const next = {
        ...f,
        [name]: value,
        ...(name === "idInstrumento" ? { idProfesor: "" } : {}),
      };

      if (name === "fechaNacimiento") {
        const age = ageFromDate(value);
        if (age === null || age >= 18) next.encargados = [];
        if (age === null || age < 6 || age >= 18) next.centroEducativo = "";
      }

      return next;
    });

    setErrors((e) => ({ ...e, [name]: "" }));
  };

  const addGuardian = () => {
    if (form.encargados.length < 2) {
      setForm((f) => ({ ...f, encargados: [...f.encargados, emptyGuardian()] }));
      setErrors((e) => ({ ...e, guardians: "" }));
    }
  };

  const updateGuardian = (index, name, value) => {
    setForm((f) => ({
      ...f,
      encargados: f.encargados.map((g, i) => (i === index ? { ...g, [name]: value } : g)),
    }));
    setErrors((e) => ({ ...e, [`guardian-${index}-${name}`]: "", guardians: "" }));
  };

  const removeGuardian = (index) => {
    setForm((f) => ({ ...f, encargados: f.encargados.filter((_, i) => i !== index) }));
    setErrors({});
  };

  const togglePlan = (idPlan) => {
    setForm((f) => ({
      ...f,
      idsPlanEstudio: f.idsPlanEstudio.includes(idPlan)
        ? f.idsPlanEstudio.filter((id) => id !== idPlan)
        : [...f.idsPlanEstudio, idPlan],
    }));
    setErrors((err) => ({ ...err, idsPlanEstudio: "" }));
  };

  const reset = () => {
    setForm(initialForm);
    setPolicy(null);
    setErrors({});
    setTieneBeca(false);
    setBecaForm({ idPeriodo: "", idTipoBeca: "", porcentaje: "" });
    setConfirmado(false);
    setPaso(0);
    setRegistrado(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  // Mismas reglas de validación de siempre, más la confirmación final
  const validar = () => {
    const nextErrors = {};
    const required = [
      ["idTipoIdentificacion", "Seleccione el tipo de identificación."],
      ["numeroIdentificacion", "Ingrese el número de identificación."],
      ["nombres", "Ingrese los nombres."],
      ["primerApellido", "Ingrese el primer apellido."],
      ["genero", "Seleccione el género."],
      ["fechaNacimiento", "Ingrese la fecha de nacimiento."],
      ["direccion", "Ingrese las señas exactas de residencia."],
      ["correo", "Ingrese el correo electrónico."],
      ["nivelEscolar", "Seleccione el nivel escolar."],
      ["idInstrumento", "Seleccione el instrumento musical."],
    ];

    required.forEach(([name, message]) => {
      if (!String(form[name] ?? "").trim()) nextErrors[name] = message;
    });

    if (!form.ubicacion.idProvincia) nextErrors.idProvincia = "Seleccione la provincia.";
    if (!form.ubicacion.codigoCanton) nextErrors.codigoCanton = "Seleccione el cantón.";
    if (!form.ubicacion.codigoDistrito) nextErrors.codigoDistrito = "Seleccione el distrito.";
    if (form.idsPlanEstudio.length === 0) nextErrors.idsPlanEstudio = "Seleccione al menos un plan de estudio.";
    if (edad === null && form.fechaNacimiento) nextErrors.fechaNacimiento = "La fecha de nacimiento no es válida.";
    if (requiereCentro && !form.centroEducativo.trim()) nextErrors.centroEducativo = "Ingrese el centro educativo.";
    if (form.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correo)) nextErrors.correo = "Ingrese un correo electrónico válido.";

    if (!policy) {
      nextErrors.policy = "Debe adjuntar la póliza en PDF.";
    } else if (policy.size > MAX_PDF) {
      nextErrors.policy = "La póliza no puede superar 50 MB.";
    } else if (policy.type !== "application/pdf" && !policy.name.toLowerCase().endsWith(".pdf")) {
      nextErrors.policy = "La póliza debe ser un archivo PDF.";
    }

    if (menor && (form.encargados.length < 1 || form.encargados.length > 2)) nextErrors.guardians = "Un menor debe tener uno o dos encargados.";

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
      if (form.encargados.length > 0 && form.encargados.filter((g) => g.responsablePrincipal).length !== 1) nextErrors.guardians = "Debe existir exactamente un responsable principal.";
    }

    if (!confirmado) nextErrors.confirmado = "Confirme que verificó los datos con el estudiante o su encargado.";

    return nextErrors;
  };

  const irA = (n) => {
    setErrors({});
    setPaso(n);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const registrar = async () => {
    const { ubicacion, ...datos } = form;
    const payload = {
      ...datos,
      codigoDistrito: Number(ubicacion.codigoDistrito),
      idTipoIdentificacion: Number(form.idTipoIdentificacion),
      idInstrumento: Number(form.idInstrumento),
      idProfesor: form.idProfesor ? Number(form.idProfesor) : null,
      centroEducativo: form.centroEducativo.trim() || null,
      encargados: menor ? form.encargados.map((g) => ({ ...g, idTipoIdentificacion: g.idTipoIdentificacion ? Number(g.idTipoIdentificacion) : null })) : [],
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
      const response = await fetch(`${API_URL}/estudiantes`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: data,
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || "No fue posible registrar el estudiante.");

      if (onSuccess) {
        onSuccess(body.id);
        return;
      }

      setRegistrado({ id: body.id, beca: body.beca || null });
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (e) {
      if (dialog?.error) dialog.error(e.message);
    } finally {
      setSaving(false);
    }
  };

  // Continuar: valida solo el paso actual. En el último paso, valida todo y registra.
  const continuar = (event) => {
    event?.preventDefault();
    const todos = validar();
    setIntento((n) => n + 1);

    if (paso < PASOS.length - 1) {
      const delPaso = Object.fromEntries(Object.entries(todos).filter(([campo]) => pasoDeCampo(campo) === paso));
      setErrors(delPaso);
      if (Object.keys(delPaso).length === 0) irA(pasoSiguiente);
      return;
    }

    if (Object.keys(todos).length > 0) {
      const primerPaso = Math.min(...Object.keys(todos).map(pasoDeCampo));
      const delPaso = Object.fromEntries(Object.entries(todos).filter(([campo]) => pasoDeCampo(campo) === primerPaso));
      setPaso(primerPaso);
      setErrors(delPaso);
      return;
    }

    registrar();
  };

  const nombreDe = (lista, id) => lista.find((x) => String(x.id) === String(id))?.nombre || "—";
  const resumen = listaErrores(errors, P);

  // ---------- Pantalla de éxito ----------
  if (registrado) {
    return (
      <div className="wizard">
        <div className="panel">
          <div className="success-screen">
            <span className="success-mark"><Check size={32} strokeWidth={1.75} aria-hidden="true" /></span>
            <h2 id="page-title" tabIndex={-1}>Estudiante registrado</h2>
            <p className="muted">
              {`${form.nombres} ${form.primerApellido}`.trim()} quedó registrado con estado activo y ya está disponible para matrícula y gestión académica.
            </p>
            <p className="receipt">ID asignado <b>{registrado.id}</b></p>
            {registrado.beca && (
              <p className="muted">
                Beca: {registrado.beca.nombreTipoBeca} — {Number(registrado.beca.porcentaje)}% · Descuento: {formatoColones(registrado.beca.montoDescuento)} · Monto a pagar: {formatoColones(registrado.beca.montoAPagar)}
              </p>
            )}
            <div className="page-actions success-actions">
              <button type="button" className="sg-btn sg-btn--secondary" onClick={reset}>Registrar otro estudiante</button>
              <Link to="/admin/administracion/estudiantes" className="sg-btn sg-btn--primary">Ir a estudiantes</Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ---------- Contenido de cada paso ----------
  let cuerpo;

  if (paso === 0) cuerpo = (
    <>
      <h2 className="form-section-title" id="est-step-title" tabIndex={-1}>Datos del estudiante</h2>
      <p className="form-section-sub">Escriba los datos como aparecen en la cédula o el pasaporte.</p>
      <div className="form-grid">
        <Field id={`${P}idTipoIdentificacion`} errorKey="idTipoIdentificacion" label="Tipo de identificación" error={errors.idTipoIdentificacion} select>
          <select id={`${P}idTipoIdentificacion`} className="sg-input sg-select" value={form.idTipoIdentificacion} onChange={(e) => setField("idTipoIdentificacion", e.target.value)} {...aria("idTipoIdentificacion", errors)}>
            <option value="">Seleccione</option>
            {types.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>
        </Field>
        <Field id={`${P}numeroIdentificacion`} errorKey="numeroIdentificacion" label="Número de identificación" hint="Debe ser única en el sistema." error={errors.numeroIdentificacion}>
          <input id={`${P}numeroIdentificacion`} className="sg-input" maxLength={40} autoComplete="off" value={form.numeroIdentificacion} onChange={(e) => setField("numeroIdentificacion", e.target.value)} {...aria("numeroIdentificacion", errors)} />
        </Field>
        <Field id={`${P}nombres`} errorKey="nombres" label="Nombres" error={errors.nombres}>
          <input id={`${P}nombres`} className="sg-input" maxLength={100} autoComplete="given-name" value={form.nombres} onChange={(e) => setField("nombres", e.target.value)} {...aria("nombres", errors)} />
        </Field>
        <Field id={`${P}primerApellido`} errorKey="primerApellido" label="Primer apellido" error={errors.primerApellido}>
          <input id={`${P}primerApellido`} className="sg-input" maxLength={80} value={form.primerApellido} onChange={(e) => setField("primerApellido", e.target.value)} {...aria("primerApellido", errors)} />
        </Field>
        <Field id={`${P}segundoApellido`} label="Segundo apellido" optional>
          <input id={`${P}segundoApellido`} className="sg-input" maxLength={80} value={form.segundoApellido} onChange={(e) => setField("segundoApellido", e.target.value)} />
        </Field>
        <Field id={`${P}genero`} errorKey="genero" label="Género" error={errors.genero} select>
          <select id={`${P}genero`} className="sg-input sg-select" value={form.genero} onChange={(e) => setField("genero", e.target.value)} {...aria("genero", errors)}>
            <option value="">Seleccione</option>
            {GENEROS.map((g) => <option key={g}>{g}</option>)}
          </select>
        </Field>
        <Field
          id={`${P}fechaNacimiento`}
          errorKey="fechaNacimiento"
          label="Fecha de nacimiento"
          hint={edad !== null ? `${edad} años · ${menor ? "menor de edad, requiere encargado" : "mayor de edad, sin encargados"}` : "Solo los menores de edad registran encargados."}
          error={errors.fechaNacimiento}
        >
          <input id={`${P}fechaNacimiento`} className="sg-input" type="date" max={todayISO()} value={form.fechaNacimiento} onChange={(e) => setField("fechaNacimiento", e.target.value)} {...aria("fechaNacimiento", errors)} />
        </Field>
        <Field id={`${P}correo`} errorKey="correo" label="Correo electrónico" error={errors.correo}>
          <input id={`${P}correo`} className="sg-input" type="email" maxLength={254} autoComplete="email" value={form.correo} onChange={(e) => setField("correo", e.target.value)} placeholder="nombre@correo.com" {...aria("correo", errors)} />
        </Field>
        <Field id={`${P}telefono`} label="Teléfono" optional>
          <input id={`${P}telefono`} className="sg-input" type="tel" inputMode="tel" maxLength={30} autoComplete="tel" value={form.telefono} onChange={(e) => setField("telefono", e.target.value)} placeholder="8888-4321" />
        </Field>
        <UbicacionSelects
          required
          idPrefix={P}
          value={form.ubicacion}
          errors={errors}
          onChange={(ubicacion) => {
            setForm((f) => ({ ...f, ubicacion }));
            setErrors((e) => ({ ...e, idProvincia: "", codigoCanton: "", codigoDistrito: "" }));
          }}
        />
        <Field id={`${P}direccion`} errorKey="direccion" label="Dirección exacta (señas)" className="span-all" error={errors.direccion}>
          <textarea id={`${P}direccion`} className="sg-input" maxLength={500} value={form.direccion} onChange={(e) => setField("direccion", e.target.value)} placeholder="Ej.: 200 m norte de la iglesia, casa verde a mano derecha" {...aria("direccion", errors)} />
        </Field>
        <Field id={`${P}condicionesRelevantes`} label="Condiciones relevantes" optional className="span-all">
          <textarea id={`${P}condicionesRelevantes`} className="sg-input" value={form.condicionesRelevantes} onChange={(e) => setField("condicionesRelevantes", e.target.value)} placeholder="Padecimientos médicos, alergias u otras condiciones" />
        </Field>
      </div>
    </>
  );

  if (paso === 1) cuerpo = (
    <>
      <h2 className="form-section-title" id="est-step-title" tabIndex={-1}>Encargados</h2>
      <p className="form-section-sub">
        El estudiante es menor de edad: registre uno o dos encargados. Debe existir exactamente un responsable principal.
      </p>

      {menor && (
        <div id={`${P}guardians`} tabIndex={-1}>
          {errors.guardians && (
            <Alert tone="danger" title={errors.guardians} className="form-alert" />
          )}

          {form.encargados.map((g, index) => {
            const k = (campo) => `guardian-${index}-${campo}`;
            return (
              <div className="subpanel" key={index}>
                <div className="subpanel-head">
                  <span>Encargado {index + 1}</span>
                  <button className="sg-icon-btn" type="button" aria-label={`Quitar encargado ${index + 1}`} onClick={() => removeGuardian(index)}>
                    <X size={18} strokeWidth={1.75} aria-hidden="true" />
                  </button>
                </div>
                <div className="subpanel-body">
                  <div className="form-grid">
                    <Field id={`${P}${k("nombres")}`} errorKey={k("nombres")} label="Nombres" error={errors[k("nombres")]}>
                      <input id={`${P}${k("nombres")}`} className="sg-input" maxLength={100} value={g.nombres} onChange={(e) => updateGuardian(index, "nombres", e.target.value)} {...aria(k("nombres"), errors)} />
                    </Field>
                    <Field id={`${P}${k("primerApellido")}`} errorKey={k("primerApellido")} label="Primer apellido" error={errors[k("primerApellido")]}>
                      <input id={`${P}${k("primerApellido")}`} className="sg-input" maxLength={80} value={g.primerApellido} onChange={(e) => updateGuardian(index, "primerApellido", e.target.value)} {...aria(k("primerApellido"), errors)} />
                    </Field>
                    <Field id={`${P}${k("segundoApellido")}`} label="Segundo apellido" optional>
                      <input id={`${P}${k("segundoApellido")}`} className="sg-input" maxLength={80} value={g.segundoApellido} onChange={(e) => updateGuardian(index, "segundoApellido", e.target.value)} />
                    </Field>
                    <Field id={`${P}${k("parentesco")}`} errorKey={k("parentesco")} label="Parentesco" error={errors[k("parentesco")]} select>
                      <select id={`${P}${k("parentesco")}`} className="sg-input sg-select" value={g.parentesco} onChange={(e) => updateGuardian(index, "parentesco", e.target.value)} {...aria(k("parentesco"), errors)}>
                        {PARENTESCOS.map((p) => <option key={p}>{p}</option>)}
                      </select>
                    </Field>
                    <Field id={`${P}${k("idTipoIdentificacion")}`} label="Tipo de identificación" optional select>
                      <select id={`${P}${k("idTipoIdentificacion")}`} className="sg-input sg-select" value={g.idTipoIdentificacion} onChange={(e) => updateGuardian(index, "idTipoIdentificacion", e.target.value)}>
                        <option value="">Seleccione</option>
                        {types.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
                      </select>
                    </Field>
                    <Field id={`${P}${k("numeroIdentificacion")}`} label="Número de identificación" optional>
                      <input id={`${P}${k("numeroIdentificacion")}`} className="sg-input" maxLength={40} value={g.numeroIdentificacion} onChange={(e) => updateGuardian(index, "numeroIdentificacion", e.target.value)} />
                    </Field>
                    <Field id={`${P}${k("telefonoPrincipal")}`} errorKey={k("telefonoPrincipal")} label="Teléfono principal" error={errors[k("telefonoPrincipal")]}>
                      <input id={`${P}${k("telefonoPrincipal")}`} className="sg-input" type="tel" inputMode="tel" maxLength={30} value={g.telefonoPrincipal} onChange={(e) => updateGuardian(index, "telefonoPrincipal", e.target.value)} {...aria(k("telefonoPrincipal"), errors)} />
                    </Field>
                    <Field id={`${P}${k("telefonoSecundario")}`} label="Teléfono secundario" optional>
                      <input id={`${P}${k("telefonoSecundario")}`} className="sg-input" type="tel" inputMode="tel" maxLength={30} value={g.telefonoSecundario} onChange={(e) => updateGuardian(index, "telefonoSecundario", e.target.value)} />
                    </Field>
                    <Field id={`${P}${k("correo")}`} errorKey={k("correo")} label="Correo electrónico" optional error={errors[k("correo")]}>
                      <input id={`${P}${k("correo")}`} className="sg-input" type="email" maxLength={254} value={g.correo} onChange={(e) => updateGuardian(index, "correo", e.target.value)} {...aria(k("correo"), errors)} />
                    </Field>
                    <Field id={`${P}${k("ocupacion")}`} label="Ocupación" optional>
                      <input id={`${P}${k("ocupacion")}`} className="sg-input" maxLength={120} value={g.ocupacion} onChange={(e) => updateGuardian(index, "ocupacion", e.target.value)} />
                    </Field>
                    <div className="span-all inline-actions">
                      <label className="check-line">
                        <input type="checkbox" checked={g.responsablePrincipal} onChange={(e) => updateGuardian(index, "responsablePrincipal", e.target.checked)} />
                        Responsable principal
                      </label>
                      <label className="check-line">
                        <input type="checkbox" checked={g.contactoEmergencia} onChange={(e) => updateGuardian(index, "contactoEmergencia", e.target.checked)} />
                        Contacto de emergencia
                      </label>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}

          <div className="inline-actions guardian-add">
            <button className="sg-btn sg-btn--secondary" type="button" onClick={addGuardian} disabled={form.encargados.length >= 2}>
              <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
              Agregar encargado
            </button>
            <span className="muted">{form.encargados.length} de 2</span>
          </div>
        </div>
      )}
    </>
  );

  if (paso === 2) cuerpo = (
    <>
      <h2 className="form-section-title" id="est-step-title" tabIndex={-1}>Información académica</h2>
      <p className="form-section-sub">Instrumento, planes de estudio y beca del estudiante.</p>
      <div className="form-grid">
        <Field id={`${P}nivelEscolar`} errorKey="nivelEscolar" label="Nivel escolar" error={errors.nivelEscolar} select>
          <select id={`${P}nivelEscolar`} className="sg-input sg-select" value={form.nivelEscolar} onChange={(e) => setField("nivelEscolar", e.target.value)} {...aria("nivelEscolar", errors)}>
            <option value="">Seleccione</option>
            {NIVELES.map((n) => <option key={n}>{n}</option>)}
          </select>
        </Field>
        {requiereCentro ? (
          <Field id={`${P}centroEducativo`} errorKey="centroEducativo" label="Centro educativo" hint="Se solicita para estudiantes de 6 a 17 años." error={errors.centroEducativo}>
            <input id={`${P}centroEducativo`} className="sg-input" maxLength={180} value={form.centroEducativo} onChange={(e) => setField("centroEducativo", e.target.value)} placeholder="Nombre del centro educativo" {...aria("centroEducativo", errors)} />
          </Field>
        ) : <div />}
        <Field id={`${P}idInstrumento`} errorKey="idInstrumento" label="Instrumento musical" error={errors.idInstrumento} select>
          <select id={`${P}idInstrumento`} className="sg-input sg-select" value={form.idInstrumento} onChange={(e) => setField("idInstrumento", e.target.value)} {...aria("idInstrumento", errors)}>
            <option value="">Seleccione un instrumento</option>
            {instruments.map((i) => <option key={i.id} value={i.id}>{i.nombre}</option>)}
          </select>
        </Field>
        <Field
          id={`${P}idProfesor`}
          errorKey="idProfesor"
          label="Profesor"
          optional
          select
          error={errors.idProfesor}
          hint={!form.idInstrumento ? "Seleccione primero un instrumento." : !profesoresLoading && profesores.length === 0 ? "No hay profesores activos con esa especialidad." : undefined}
        >
          <select id={`${P}idProfesor`} className="sg-input sg-select" value={form.idProfesor} onChange={(e) => setField("idProfesor", e.target.value)} disabled={!form.idInstrumento || profesoresLoading}>
            <option value="">{profesoresLoading ? "Cargando profesores..." : "Sin asignar"}</option>
            {profesores.map((p) => <option key={p.id} value={p.id}>{[p.nombres, p.primerApellido, p.segundoApellido].filter(Boolean).join(" ")}</option>)}
          </select>
        </Field>

        <fieldset className="fieldset span-all" id={`${P}idsPlanEstudio`} tabIndex={-1} data-error-field="idsPlanEstudio" aria-describedby={errors.idsPlanEstudio ? `${P}idsPlanEstudio-error` : undefined}>
          <legend>Planes de estudio</legend>
          <p className="sg-field__hint plan-hint">
            {!catalogLoading && planes.length === 0
              ? "No hay planes de estudio vigentes. El director académico debe crear uno."
              : "Determinan los cursos que el estudiante podrá matricular. Puede seleccionar más de uno."}
          </p>
          {errors.idsPlanEstudio && <p className="sg-field__error plan-error" id={`${P}idsPlanEstudio-error`}>{errors.idsPlanEstudio}</p>}
          <div className="choice-list">
            {planes.map((p) => {
              const marcado = form.idsPlanEstudio.includes(p.id);
              return (
                <label key={p.id} className={`choice${marcado ? " is-checked" : ""}`}>
                  <input type="checkbox" checked={marcado} onChange={() => togglePlan(p.id)} />
                  <span className="choice-main"><b>{p.nombre}</b></span>
                </label>
              );
            })}
          </div>
        </fieldset>

        <div className="span-all beca-bloque">
          <label className="check-line">
            <input type="checkbox" checked={tieneBeca} onChange={(e) => {
              setTieneBeca(e.target.checked);
              setErrors((err) => ({ ...err, becaIdPeriodo: "", becaIdTipoBeca: "", becaPorcentaje: "" }));
            }} />
            El estudiante posee beca
          </label>
          {!tieneBeca && <p className="sg-field__hint">Beca: 0% — no se asignará ninguna beca en este registro.</p>}
        </div>

        {tieneBeca && (
          <div className="form-grid form-grid--3 span-all">
            <Field id={`${P}becaIdPeriodo`} errorKey="becaIdPeriodo" label="Período académico" error={errors.becaIdPeriodo} select>
              <select id={`${P}becaIdPeriodo`} className="sg-input sg-select" value={becaForm.idPeriodo} onChange={(e) => {
                setBecaForm((f) => ({ ...f, idPeriodo: e.target.value }));
                setErrors((err) => ({ ...err, becaIdPeriodo: "" }));
              }} {...aria("becaIdPeriodo", errors)}>
                <option value="">Seleccione un período</option>
                {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
              </select>
            </Field>
            <Field id={`${P}becaIdTipoBeca`} errorKey="becaIdTipoBeca" label="Tipo de beca" error={errors.becaIdTipoBeca} select>
              <select id={`${P}becaIdTipoBeca`} className="sg-input sg-select" value={becaForm.idTipoBeca} onChange={(e) => {
                setBecaForm((f) => ({ ...f, idTipoBeca: e.target.value }));
                setErrors((err) => ({ ...err, becaIdTipoBeca: "" }));
              }} {...aria("becaIdTipoBeca", errors)}>
                <option value="">Seleccione</option>
                {tiposBeca.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
              </select>
            </Field>
            <Field id={`${P}becaPorcentaje`} errorKey="becaPorcentaje" label="Porcentaje" error={errors.becaPorcentaje} select>
              <select id={`${P}becaPorcentaje`} className="sg-input sg-select" value={becaForm.porcentaje} onChange={(e) => {
                setBecaForm((f) => ({ ...f, porcentaje: e.target.value }));
                setErrors((err) => ({ ...err, becaPorcentaje: "" }));
              }} {...aria("becaPorcentaje", errors)}>
                <option value="">Seleccione</option>
                {PORCENTAJES_BECA.map((p) => <option key={p} value={p}>{p}%</option>)}
              </select>
            </Field>
          </div>
        )}
      </div>
    </>
  );

  if (paso === 3) cuerpo = (
    <>
      <h2 className="form-section-title" id="est-step-title" tabIndex={-1}>Revisar y confirmar</h2>
      <p className="form-section-sub">Adjunte la póliza y verifique los datos con el estudiante o su encargado antes de confirmar.</p>

      <div className="review">
        <Field id={`${P}policy`} errorKey="policy" label="Póliza del estudiante" hint="PDF obligatorio · tamaño máximo 50 MB." error={errors.policy}>
          <label className={`file-drop${policy ? " has-file" : ""}`}>
            <FileUp size={20} strokeWidth={1.75} aria-hidden="true" />
            <span>{policy ? `${policy.name} — ${(policy.size / 1024 / 1024).toFixed(2)} MB` : "Seleccionar archivo PDF"}</span>
            <input
              id={`${P}policy`}
              type="file"
              accept="application/pdf,.pdf"
              onChange={(e) => { setPolicy(e.target.files?.[0] || null); setErrors((err) => ({ ...err, policy: "" })); }}
              {...aria("policy", errors)}
            />
          </label>
        </Field>

        <section className="review-block">
          <h3>Estudiante <button type="button" className="link-btn" onClick={() => irA(0)}>Editar<span className="sr-only"> datos del estudiante</span></button></h3>
          <dl>
            <dt>Nombre</dt><dd>{[form.nombres, form.primerApellido, form.segundoApellido].filter(Boolean).join(" ") || "—"}</dd>
            <dt>Identificación</dt><dd className="tnum">{nombreDe(types, form.idTipoIdentificacion)} · {form.numeroIdentificacion || "—"}</dd>
            <dt>Fecha de nacimiento</dt><dd className="tnum">{fechaLegible(form.fechaNacimiento)}{edad !== null ? ` (${edad} años)` : ""}</dd>
            <dt>Género</dt><dd>{form.genero || "—"}</dd>
            <dt>Correo</dt><dd>{form.correo || "—"}</dd>
            <dt>Teléfono</dt><dd className="tnum">{form.telefono || "No indicado"}</dd>
            <dt>Dirección exacta</dt><dd>{form.direccion || "—"}</dd>
          </dl>
        </section>

        {menor && (
          <section className="review-block">
            <h3>Encargados <button type="button" className="link-btn" onClick={() => irA(1)}>Editar<span className="sr-only"> encargados</span></button></h3>
            <dl>
              {form.encargados.map((g, i) => (
                <div className="review-pair" key={i}>
                  <dt>Encargado {i + 1}</dt>
                  <dd>
                    {[g.nombres, g.primerApellido].filter(Boolean).join(" ")} ({g.parentesco.toLowerCase()}) · {g.telefonoPrincipal}
                    {g.responsablePrincipal ? " · responsable principal" : ""}
                  </dd>
                </div>
              ))}
            </dl>
          </section>
        )}

        <section className="review-block">
          <h3>Información académica <button type="button" className="link-btn" onClick={() => irA(2)}>Editar<span className="sr-only"> información académica</span></button></h3>
          <dl>
            <dt>Nivel escolar</dt><dd>{form.nivelEscolar || "—"}</dd>
            <dt>Instrumento</dt><dd>{nombreDe(instruments, form.idInstrumento)}</dd>
            <dt>Profesor</dt><dd>{form.idProfesor ? [profesores.find((p) => String(p.id) === String(form.idProfesor))].filter(Boolean).map((p) => [p.nombres, p.primerApellido].join(" "))[0] : "Sin asignar"}</dd>
            <dt>Planes de estudio</dt><dd>{planes.filter((p) => form.idsPlanEstudio.includes(p.id)).map((p) => p.nombre).join(", ") || "—"}</dd>
            {requiereCentro && <><dt>Centro educativo</dt><dd>{form.centroEducativo || "—"}</dd></>}
            <dt>Beca</dt><dd>{tieneBeca ? `${nombreDe(tiposBeca, becaForm.idTipoBeca)} · ${becaForm.porcentaje || "—"}%` : "Sin beca"}</dd>
          </dl>
        </section>

        <div className={`sg-field${errors.confirmado ? " has-error" : ""}`} data-error-field="confirmado">
          <label className="check-line" htmlFor={`${P}confirmado`}>
            <input
              id={`${P}confirmado`}
              type="checkbox"
              checked={confirmado}
              onChange={(e) => { setConfirmado(e.target.checked); setErrors((err) => ({ ...err, confirmado: "" })); }}
              {...aria("confirmado", errors)}
            />
            Verifiqué los datos con el estudiante o su encargado.
          </label>
          {errors.confirmado && <p className="sg-field__error" id={`${P}confirmado-error`}>{errors.confirmado}</p>}
        </div>
      </div>
    </>
  );

  const ultimo = paso === PASOS.length - 1;

  return (
    <div className="wizard">
      {incrustado
        ? <h2 className="form-section-title wizard-titulo">Registrar estudiante</h2>
        : <PageHead title="Registrar estudiante" sub="Datos personales, académicos, de contacto y documentación requerida." />}

      <Stepper pasos={pasosVisibles.map((i) => PASOS[i])} actual={pasosVisibles.indexOf(paso)} etiqueta="Pasos del registro" />

      {catalogLoading && <Alert tone="info" title="Cargando catálogos..." className="form-alert" />}

      <div className="panel">
        <form className="panel-body step-panel" key={paso} onSubmit={continuar} noValidate>
          {resumen.length > 0 && (
            <Alert
              key={intento}
              tone="danger"
              autoFocus
              className="form-alert"
              title={resumen.length === 1 ? "Revise 1 dato antes de continuar" : `Revise ${resumen.length} datos antes de continuar`}
              errors={resumen}
            />
          )}
          {cuerpo}
          <button type="submit" hidden tabIndex={-1} />
        </form>

        <div className="wizard-foot">
          {paso > 0 ? (
            <button type="button" className="sg-btn sg-btn--secondary" onClick={() => irA(pasoAnterior)}>
              <ArrowLeft size={18} strokeWidth={1.75} aria-hidden="true" />
              Anterior
            </button>
          ) : (
            <button type="button" className="sg-btn sg-btn--ghost" onClick={onCancel || reset}>Cancelar</button>
          )}
          <div className="right">
            <button
              type="button"
              className={`sg-btn sg-btn--primary${saving ? " is-loading" : ""}`}
              onClick={continuar}
              disabled={saving || catalogLoading}
              aria-busy={saving || undefined}
            >
              {saving && <span className="sg-spinner" aria-hidden="true" />}
              {ultimo ? (saving ? "Guardando…" : "Confirmar registro") : "Continuar"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
