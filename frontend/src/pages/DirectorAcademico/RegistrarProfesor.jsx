import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, Check } from "lucide-react";
import { API_URL } from "../../config/api";
import "../AdminSis/styles/CreateStudent.css";
import "./Profesores.css";
import { useDialog } from "../../components/dialog/useDialog";
import UbicacionSelects from "../../components/UbicacionSelects";
import { UBICACION_VACIA } from "../../components/ubicacion";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Stepper from "../../components/ui/Stepper";
import { listaErrores } from "../../components/ui/utilidades";

const PASOS = ["Profesor", "Información académica y laboral", "Revisar y confirmar"];

// Prefijo de los ids de los controles (para los enlaces del resumen de errores)
const P = "prof-";

// Por ahora la clase de puesto es un dato fijo (no hay catálogo con varias opciones).
const CLASE_PUESTO_DEFAULT =
  "Profesor de Enseñanza Técnica Profesional(III y IV Ciclos, Enseñanza Especial y Escuela Laboratorio) (G. de E)";

// En qué paso está cada campo (para mostrar solo los errores del paso actual)
function pasoDeCampo(campo) {
  if (campo === "confirmado") return 2;
  return 0;
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
  telefono: "",
  correo: "",
  ubicacion: UBICACION_VACIA,
  direccion: "",
  gradoAcademico: "",
  especialidades: [],
  empleador: "",
  fechaIngreso: "",
};

export default function RegistrarProfesor() {
  const dialog = useDialog();
  const [tipos, setTipos] = useState([]);
  const [grados, setGrados] = useState([]);
  const [instrumentos, setInstrumentos] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [confirmado, setConfirmado] = useState(false);
  const [paso, setPaso] = useState(0);
  const [intento, setIntento] = useState(0);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [registrado, setRegistrado] = useState(null);

  useEffect(() => {
    const token = localStorage.getItem("token");
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const cargar = (ruta, mensaje) =>
      fetch(`${API_URL}${ruta}`, { headers }).then((r) => (r.ok ? r.json() : Promise.reject(new Error(mensaje))));

    Promise.all([
      cargar("/tipos-identificacion", "No se pudo cargar tipos de identificación."),
      cargar("/grados-academicos", "No se pudo cargar grados académicos."),
      cargar("/instrumentos", "No se pudo cargar las especialidades."),
    ])
      .then(([t, g, i]) => {
        setTipos(t);
        setGrados(g);
        setInstrumentos(i);
      })
      .catch(() => {
        if (dialog?.error) dialog.error("No fue posible cargar los catálogos. Verifique que el backend esté disponible.");
      })
      .finally(() => setCatalogLoading(false));
  }, [dialog]);

  // Al cambiar de paso, el foco va al título del paso
  useEffect(() => {
    if (paso === 0) return;
    document.getElementById("prof-step-title")?.focus();
  }, [paso]);

  const setField = (name, value) => {
    setForm((f) => ({ ...f, [name]: value }));
    setErrors((e) => ({ ...e, [name]: "" }));
  };

  const toggleEspecialidad = (nombre) => {
    setForm((f) => ({
      ...f,
      especialidades: f.especialidades.includes(nombre)
        ? f.especialidades.filter((e) => e !== nombre)
        : [...f.especialidades, nombre],
    }));
  };

  const reset = () => {
    setForm(initialForm);
    setErrors({});
    setConfirmado(false);
    setPaso(0);
    setRegistrado(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  // Mismas reglas que tenía el formulario de profesores, más la confirmación final
  const validar = () => {
    const nextErrors = {};
    if (!form.idTipoIdentificacion) nextErrors.idTipoIdentificacion = "Seleccione el tipo de identificación.";
    if (!form.numeroIdentificacion.trim()) nextErrors.numeroIdentificacion = "Ingrese el número de identificación.";
    if (!form.nombres.trim()) nextErrors.nombres = "Ingrese el nombre.";
    if (!form.primerApellido.trim()) nextErrors.primerApellido = "Ingrese el primer apellido.";
    if (!form.correo.trim()) {
      nextErrors.correo = "El correo es obligatorio para crear la cuenta de usuario.";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correo)) {
      nextErrors.correo = "Ingrese un correo electrónico válido.";
    }
    // La ubicación es opcional, pero si se empieza hay que llegar hasta el distrito
    if (form.ubicacion.idProvincia && !form.ubicacion.codigoDistrito) {
      nextErrors.codigoDistrito = "Complete cantón y distrito, o deje la provincia sin seleccionar.";
    }
    if (!confirmado) nextErrors.confirmado = "Confirme que verificó los datos con el profesor.";
    return nextErrors;
  };

  const irA = (n) => {
    setErrors({});
    setPaso(n);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const registrar = async () => {
    const nombreCompleto = [form.nombres, form.primerApellido, form.segundoApellido].map((s) => s.trim()).filter(Boolean).join(" ");
    const profesor = {
      idTipoIdentificacion: Number(form.idTipoIdentificacion),
      numeroIdentificacion: form.numeroIdentificacion.trim(),
      nombres: form.nombres.trim(),
      primerApellido: form.primerApellido.trim(),
      segundoApellido: form.segundoApellido.trim() || null,
      telefono: form.telefono.trim() || null,
      correo: form.correo.trim(),
      codigoDistrito: form.ubicacion.codigoDistrito ? Number(form.ubicacion.codigoDistrito) : null,
      direccion: form.direccion.trim() || null,
      gradoAcademico: form.gradoAcademico || null,
      especialidad: form.especialidades.join(", ") || null,
      empleador: form.empleador.trim() || null,
      clasePuesto: CLASE_PUESTO_DEFAULT,
      fechaIngreso: form.fechaIngreso || null,
    };
    const payload = {
      profesor,
      usuario: {
        cedula: profesor.numeroIdentificacion,
        nombre: nombreCompleto,
        correo: profesor.correo,
        // El backend siempre fija el rol PROFESOR y el estado ACTIVO
        // (ver ProfesorService.registrar), pero UsuarioCreateRequest
        // los exige con @NotNull, así que mandamos un valor cualquiera.
        idRol: 1,
        idEstadoUsuario: 1,
      },
    };

    try {
      setSaving(true);
      const token = localStorage.getItem("token");
      const response = await fetch(`${API_URL}/profesores`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify(payload),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        const mensaje = body.detail || body.message || "No fue posible registrar el profesor.";
        // Identificación repetida: se lleva al usuario al campo del primer paso
        if (response.status === 409) {
          setPaso(0);
          setErrors({ numeroIdentificacion: mensaje });
          setIntento((n) => n + 1);
          return;
        }
        throw new Error(mensaje);
      }

      setRegistrado({ id: body.id });
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
      if (Object.keys(delPaso).length === 0) irA(paso + 1);
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
            <h2 id="page-title" tabIndex={-1}>Profesor registrado</h2>
            <p className="muted">
              {`${form.nombres} ${form.primerApellido}`.trim()} quedó registrado con estado activo y ya tiene su cuenta de usuario con rol Profesor. Se envió una contraseña temporal a {form.correo}.
            </p>
            {registrado.id && <p className="receipt">ID asignado <b>{registrado.id}</b></p>}
            <div className="page-actions success-actions">
              <button type="button" className="sg-btn sg-btn--secondary" onClick={reset}>Registrar otro profesor</button>
              <Link to="/director-academico/profesores" className="sg-btn sg-btn--primary">Ir a profesores</Link>
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
      <h2 className="form-section-title" id="prof-step-title" tabIndex={-1}>Datos del profesor</h2>
      <p className="form-section-sub">Escriba los datos como aparecen en la cédula o el pasaporte.</p>
      <div className="form-grid">
        <Field id={`${P}idTipoIdentificacion`} errorKey="idTipoIdentificacion" label="Tipo de identificación" error={errors.idTipoIdentificacion} select>
          <select id={`${P}idTipoIdentificacion`} className="sg-input sg-select" value={form.idTipoIdentificacion} onChange={(e) => setField("idTipoIdentificacion", e.target.value)} {...aria("idTipoIdentificacion", errors)}>
            <option value="">Seleccione</option>
            {tipos.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
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
        <Field id={`${P}telefono`} label="Teléfono" optional>
          <input id={`${P}telefono`} className="sg-input" type="tel" inputMode="tel" maxLength={30} autoComplete="tel" value={form.telefono} onChange={(e) => setField("telefono", e.target.value)} placeholder="8888-4321" />
        </Field>
        <Field id={`${P}correo`} errorKey="correo" label="Correo electrónico" hint="Se usa para crear la cuenta de usuario." error={errors.correo} className="span-all">
          <input id={`${P}correo`} className="sg-input" type="email" maxLength={254} autoComplete="email" value={form.correo} onChange={(e) => setField("correo", e.target.value)} placeholder="nombre@correo.com" {...aria("correo", errors)} />
        </Field>
        <UbicacionSelects
          idPrefix={P}
          value={form.ubicacion}
          errors={errors}
          onChange={(ubicacion) => {
            setForm((f) => ({ ...f, ubicacion }));
            setErrors((e) => ({ ...e, codigoDistrito: "" }));
          }}
        />
        <Field id={`${P}direccion`} label="Dirección exacta (señas)" optional className="span-all">
          <textarea id={`${P}direccion`} className="sg-input" maxLength={500} value={form.direccion} onChange={(e) => setField("direccion", e.target.value)} placeholder="Ej.: 200 m norte de la iglesia, casa verde a mano derecha" />
        </Field>
      </div>
    </>
  );

  if (paso === 1) cuerpo = (
    <>
      <h2 className="form-section-title" id="prof-step-title" tabIndex={-1}>Información académica y laboral</h2>
      <p className="form-section-sub">Grado, puesto y los instrumentos que puede impartir.</p>
      <div className="form-grid">
        <Field id={`${P}gradoAcademico`} label="Grado académico" optional select>
          <select id={`${P}gradoAcademico`} className="sg-input sg-select" value={form.gradoAcademico} onChange={(e) => setField("gradoAcademico", e.target.value)}>
            <option value="">Seleccione</option>
            {grados.map((g) => <option key={g.id} value={g.nombre}>{g.nombre}</option>)}
          </select>
        </Field>
        <Field id={`${P}empleador`} label="Grupo profesional / Empleador" optional>
          <input id={`${P}empleador`} className="sg-input" maxLength={180} value={form.empleador} onChange={(e) => setField("empleador", e.target.value)} placeholder="VT, VAU u otro colegio profesional / institución" />
        </Field>
        <Field id={`${P}clasePuesto`} label="Clase de puesto" hint="Se asigna automáticamente y solo puede modificarse al editar el profesor." className="span-all">
          <input id={`${P}clasePuesto`} className="sg-input" value={CLASE_PUESTO_DEFAULT} disabled />
        </Field>
        <Field id={`${P}fechaIngreso`} label="Fecha de ingreso" optional hint="Si la deja vacía se usa la fecha de hoy.">
          <input id={`${P}fechaIngreso`} className="sg-input" type="date" value={form.fechaIngreso} onChange={(e) => setField("fechaIngreso", e.target.value)} />
        </Field>
        <fieldset className="fieldset span-all">
          <legend>Especialidad(es) <span className="sg-field__optional">(opcional)</span></legend>
          <p className="sg-field__hint plan-hint">Instrumentos que el profesor puede impartir. Puede seleccionar más de uno.</p>
          <div className="especialidades-checklist">
            {instrumentos.map((i) => {
              const marcada = form.especialidades.includes(i.nombre);
              return (
                <label key={i.id} className={`choice choice--compacta${marcada ? " is-checked" : ""}`}>
                  <input type="checkbox" checked={marcada} onChange={() => toggleEspecialidad(i.nombre)} />
                  <span className="choice-main"><b>{i.nombre}</b></span>
                </label>
              );
            })}
          </div>
        </fieldset>
      </div>
    </>
  );

  if (paso === 2) cuerpo = (
    <>
      <h2 className="form-section-title" id="prof-step-title" tabIndex={-1}>Revisar y confirmar</h2>
      <p className="form-section-sub">Verifique los datos con el profesor antes de confirmar.</p>

      <div className="review">
        <Alert tone="info" title="Cuenta de usuario">
          Se creará con rol Profesor y estado Activo. El sistema enviará una contraseña temporal a <b>{form.correo || "su correo"}</b>, que el profesor deberá cambiar la primera vez que ingrese.
        </Alert>

        <section className="review-block">
          <h3>Profesor <button type="button" className="link-btn" onClick={() => irA(0)}>Editar<span className="sr-only"> datos del profesor</span></button></h3>
          <dl>
            <dt>Nombre</dt><dd>{[form.nombres, form.primerApellido, form.segundoApellido].filter(Boolean).join(" ") || "—"}</dd>
            <dt>Identificación</dt><dd className="tnum">{nombreDe(tipos, form.idTipoIdentificacion)} · {form.numeroIdentificacion || "—"}</dd>
            <dt>Correo</dt><dd>{form.correo || "—"}</dd>
            <dt>Teléfono</dt><dd className="tnum">{form.telefono || "No indicado"}</dd>
            <dt>Dirección exacta</dt><dd>{form.direccion || "No indicada"}</dd>
          </dl>
        </section>

        <section className="review-block">
          <h3>Información académica y laboral <button type="button" className="link-btn" onClick={() => irA(1)}>Editar<span className="sr-only"> información académica y laboral</span></button></h3>
          <dl>
            <dt>Grado académico</dt><dd>{form.gradoAcademico || "No indicado"}</dd>
            <dt>Empleador</dt><dd>{form.empleador || "No indicado"}</dd>
            <dt>Fecha de ingreso</dt><dd className="tnum">{form.fechaIngreso ? fechaLegible(form.fechaIngreso) : "Hoy"}</dd>
            <dt>Especialidades</dt><dd>{form.especialidades.join(", ") || "Ninguna"}</dd>
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
            Verifiqué los datos con el profesor.
          </label>
          {errors.confirmado && <p className="sg-field__error" id={`${P}confirmado-error`}>{errors.confirmado}</p>}
        </div>
      </div>
    </>
  );

  const ultimo = paso === PASOS.length - 1;

  return (
    <div className="wizard">
      <PageHead title="Registrar profesor" sub="Datos personales, académicos y la cuenta de usuario del profesor." />

      <Stepper pasos={PASOS} actual={paso} etiqueta="Pasos del registro" />

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
            <button type="button" className="sg-btn sg-btn--secondary" onClick={() => irA(paso - 1)}>
              <ArrowLeft size={18} strokeWidth={1.75} aria-hidden="true" />
              Anterior
            </button>
          ) : (
            <Link to="/director-academico/profesores" className="sg-btn sg-btn--ghost">Cancelar</Link>
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
