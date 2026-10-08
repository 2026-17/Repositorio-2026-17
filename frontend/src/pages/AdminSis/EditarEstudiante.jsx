import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Save } from "lucide-react";
import "./styles/admin_Sis.css";
import "./styles/Estudiantes.css";

import { useDialog } from "../../components/dialog/useDialog";
import UbicacionSelects from "../../components/UbicacionSelects";
import { UBICACION_VACIA, ubicacionDesdeRespuesta } from "../../components/ubicacion";

const GENEROS = ["MASCULINO", "FEMENINO", "OTRO"];
const NIVELES = ["Preescolar", "Primaria", "Secundaria", "Universitaria", "Ninguno"];

function ageFromDate(value) {
    if (!value) return null;
    const birth = new Date(`${value}T00:00:00`);
    const today = new Date();
    if (Number.isNaN(birth.getTime()) || birth > today) return null;
    let edad = today.getFullYear() - birth.getFullYear();
    const month = today.getMonth() - birth.getMonth();
    if (month < 0 || (month === 0 && today.getDate() < birth.getDate())) edad--;
    return edad;
}

function todayISO() {
    const date = new Date();
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

const initialForm = {
    idTipoIdentificacion: "", numeroIdentificacion: "", nombres: "", primerApellido: "", segundoApellido: "",
    fechaNacimiento: "", genero: "", ubicacion: UBICACION_VACIA, direccion: "", telefono: "", correo: "", nivelEscolar: "",
    idCentroEducativo: "", condicionesRelevantes: "", idInstrumento: "", idsPlanEstudio: [],
};

function EditarEstudiante({ studentId, onCancel, onSuccess } = {}) {
    const dialog = useDialog();
    const { id: routeId } = useParams();
    const id = studentId ?? routeId;
    const navigate = useNavigate();
    const [types, setTypes] = useState([]);
    const [centers, setCenters] = useState([]);
    const [instruments, setInstruments] = useState([]);
    const [planes, setPlanes] = useState([]);
    const [form, setForm] = useState(initialForm);
    const [errors, setErrors] = useState({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    const edad = useMemo(() => ageFromDate(form.fechaNacimiento), [form.fechaNacimiento]);
    const requiereCentro = edad !== null && edad >= 6 && edad < 18;

    useEffect(() => {
        async function cargar() {
            try {
                const token = localStorage.getItem("token");
                const headers = token ? { Authorization: `Bearer ${token}` } : {};
                const [studentResponse, typesResponse, centersResponse, instrumentsResponse, plansResponse] = await Promise.all([
                    fetch(`/api/estudiantes/${id}`, { headers }),
                    fetch(`/api/registro/catalogos/tipos-identificacion`, { headers }),
                    fetch(`/api/registro/catalogos/centros-educativos`, { headers }),
                    fetch(`/api/registro/catalogos/instrumentos`, { headers }),
                    fetch(`/api/planes-estudio`, { headers }),
                ]);
                if (!studentResponse.ok || !typesResponse.ok || !centersResponse.ok || !instrumentsResponse.ok || !plansResponse.ok) throw new Error("No fue posible cargar la información del estudiante.");
                const [student, typeData, centerData, instrumentData, planData] = await Promise.all([studentResponse.json(), typesResponse.json(), centersResponse.json(), instrumentsResponse.json(), plansResponse.json()]);
                setForm({
                    idTipoIdentificacion: String(student.idTipoIdentificacion ?? ""), numeroIdentificacion: student.numeroIdentificacion ?? "",
                    nombres: student.nombres ?? "", primerApellido: student.primerApellido ?? "", segundoApellido: student.segundoApellido ?? "",
                    fechaNacimiento: student.fechaNacimiento ?? "", genero: student.genero ?? "", ubicacion: ubicacionDesdeRespuesta(student.ubicacion), direccion: student.direccion ?? "",
                    telefono: student.telefono ?? "", correo: student.correo ?? "", nivelEscolar: student.nivelEscolar ?? "",
                    idCentroEducativo: student.idCentroEducativo == null ? "" : String(student.idCentroEducativo),
                    condicionesRelevantes: student.condicionesRelevantes ?? "", idInstrumento: student.idInstrumento == null ? "" : String(student.idInstrumento),
                    idsPlanEstudio: Array.isArray(student.idsPlanEstudio) ? student.idsPlanEstudio : [],
                });
                setTypes(typeData);
                setCenters(centerData);
                setInstruments(instrumentData);
                setPlanes(planData);
            } catch (e) {
                dialog.error(e.message);
            } finally {
                setLoading(false);
            }
        }
        cargar();
    }, [id, dialog]);

    useEffect(() => {
        if (edad !== null && edad >= 18) setForm(current => ({ ...current, idCentroEducativo: "" }));
    }, [edad]);

    function setField(name, value) {
        setForm(current => ({ ...current, [name]: value }));
        setErrors(current => ({ ...current, [name]: "" }));
    }

    function togglePlan(idPlan) {
        setForm(current => ({
            ...current,
            idsPlanEstudio: current.idsPlanEstudio.includes(idPlan)
                ? current.idsPlanEstudio.filter(idActual => idActual !== idPlan)
                : [...current.idsPlanEstudio, idPlan],
        }));
        setErrors(current => ({ ...current, idsPlanEstudio: "" }));
    }

    async function submit(event) {
        event.preventDefault();
        const nextErrors = {};
        const required = [
            ["idTipoIdentificacion", "Seleccione el tipo de identificación."], ["numeroIdentificacion", "Ingrese el número de identificación."],
            ["nombres", "Ingrese los nombres."], ["primerApellido", "Ingrese el primer apellido."], ["fechaNacimiento", "Ingrese la fecha de nacimiento."],
            ["genero", "Seleccione el género."], ["direccion", "Ingrese las señas exactas de residencia."], ["nivelEscolar", "Seleccione el nivel escolar."], ["idInstrumento", "Seleccione el instrumento musical."],
        ];
        required.forEach(([name, message]) => { if (!String(form[name] ?? "").trim()) nextErrors[name] = message; });
        if (edad === null && form.fechaNacimiento) nextErrors.fechaNacimiento = "La fecha de nacimiento no es válida.";
        if (!form.ubicacion.idProvincia) nextErrors.idProvincia = "Seleccione la provincia.";
        if (!form.ubicacion.codigoCanton) nextErrors.codigoCanton = "Seleccione el cantón.";
        if (!form.ubicacion.codigoDistrito) nextErrors.codigoDistrito = "Seleccione el distrito.";
        if (form.idsPlanEstudio.length === 0) nextErrors.idsPlanEstudio = "Seleccione al menos un plan de estudio.";
        if (requiereCentro && !form.idCentroEducativo) nextErrors.idCentroEducativo = "Seleccione el centro educativo.";
        if (form.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correo)) nextErrors.correo = "Ingrese un correo electrónico válido.";
        setErrors(nextErrors);
        if (Object.keys(nextErrors).length > 0) {
            dialog.validacion(nextErrors);
            const firstError = Object.keys(nextErrors)[0];
            requestAnimationFrame(() => document.querySelector(`[data-error-field="${firstError}"]`)?.scrollIntoView({ behavior: "smooth", block: "center" }));
            return;
        }

        const { ubicacion, ...datos } = form;
        const payload = { ...datos, codigoDistrito: Number(ubicacion.codigoDistrito), idTipoIdentificacion: Number(form.idTipoIdentificacion), idInstrumento: Number(form.idInstrumento), idCentroEducativo: form.idCentroEducativo ? Number(form.idCentroEducativo) : null };
        try {
            setSaving(true);
            const token = localStorage.getItem("token");
            const response = await fetch(`/api/estudiantes/${id}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
                body: JSON.stringify(payload),
            });
            const body = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(body.message || "No fue posible actualizar el estudiante.");
            if (onSuccess) onSuccess();
            else navigate("/admin/administracion/estudiantes");
        } catch (e) {
            dialog.error(e.message);
            window.scrollTo({ top: 0, behavior: "smooth" });
        } finally {
            setSaving(false);
        }
    }

    if (loading) return <main className="content"><div className="banner banner-info">Cargando estudiante...</div></main>;
    if (!form.numeroIdentificacion) return <main className="content"><Link className="btn btn-secondary" to="/admin/administracion/estudiantes"><ArrowLeft size={16} /> Volver a estudiantes</Link></main>;

    return (
        <main className="content">
            <div className="page-title"><h1>Editar estudiante</h1><p>Actualiza la información personal y académica del estudiante.</p></div>
            <form onSubmit={submit} noValidate>
                <section className="card">
                    <h2 className="card-title">Datos personales</h2>
                    <div className="grid-2">
                        <Field label="Tipo de identificación" required error={errors.idTipoIdentificacion} errorKey="idTipoIdentificacion"><select id="cmbTipoIdentificacion" className={`input ${errors.idTipoIdentificacion ? "input-error" : ""}`} value={form.idTipoIdentificacion} onChange={e => setField("idTipoIdentificacion", e.target.value)}><option value="">Seleccione una opción</option>{types.map(type => <option key={type.id} value={type.id}>{type.nombre}</option>)}</select></Field>
                        <Field label="Número de identificación" required error={errors.numeroIdentificacion} errorKey="numeroIdentificacion"><input id="txtNumeroIdentificacion" className={`input ${errors.numeroIdentificacion ? "input-error" : ""}`} maxLength={40} value={form.numeroIdentificacion} onChange={e => setField("numeroIdentificacion", e.target.value)} /></Field>
                    </div>
                    <div className="grid-2">
                        <Field label="Nombres" required error={errors.nombres} errorKey="nombres"><input id="txtNombres" className={`input ${errors.nombres ? "input-error" : ""}`} maxLength={100} value={form.nombres} onChange={e => setField("nombres", e.target.value)} /></Field>
                        <Field label="Primer apellido" required error={errors.primerApellido} errorKey="primerApellido"><input id="txtPrimerApellido" className={`input ${errors.primerApellido ? "input-error" : ""}`} maxLength={80} value={form.primerApellido} onChange={e => setField("primerApellido", e.target.value)} /></Field>
                    </div>
                    <div className="grid-2">
                        <Field label="Segundo apellido"><input id="txtSegundoApellido" className="input" maxLength={80} value={form.segundoApellido} onChange={e => setField("segundoApellido", e.target.value)} /></Field>
                        <Field label="Género" required error={errors.genero} errorKey="genero"><select id="cmbGenero" className={`input ${errors.genero ? "input-error" : ""}`} value={form.genero} onChange={e => setField("genero", e.target.value)}><option value="">Seleccione una opción</option>{GENEROS.map(g => <option key={g}>{g}</option>)}</select></Field>
                    </div>
                    <div className="grid-2">
                        <Field label="Fecha de nacimiento" required error={errors.fechaNacimiento} errorKey="fechaNacimiento"><input id="txtFechaNacimiento" className={`input ${errors.fechaNacimiento ? "input-error" : ""}`} type="date" max={todayISO()} value={form.fechaNacimiento} onChange={e => setField("fechaNacimiento", e.target.value)} /></Field>
                        <div className="age-box"><small>Edad calculada automáticamente</small><strong>{edad === null ? "—" : `${edad} años`}</strong>{edad !== null && <span>{edad < 18 ? "Menor de edad" : "Mayor de edad"}</span>}</div>
                    </div>
                    <UbicacionSelects required value={form.ubicacion} errors={errors} onChange={ubicacion => {
                        setForm(current => ({ ...current, ubicacion }));
                        setErrors(current => ({ ...current, idProvincia: "", codigoCanton: "", codigoDistrito: "" }));
                    }} />
                    <div className="grid-2">
                        <Field label="Dirección exacta (señas)" required error={errors.direccion} errorKey="direccion"><textarea id="txtDireccion" className={`input textarea ${errors.direccion ? "input-error" : ""}`} maxLength={500} value={form.direccion} onChange={e => setField("direccion", e.target.value)} placeholder="Ej.: 200 m norte de la iglesia, casa verde a mano derecha" /></Field>
                        <Field label="Condiciones relevantes" hint="Opcional."><textarea id="txtCondicionesRelevantes" className="input textarea" value={form.condicionesRelevantes} onChange={e => setField("condicionesRelevantes", e.target.value)} /></Field>
                    </div>
                    <div className="grid-2">
                        <Field label="Correo electrónico" hint="Opcional." error={errors.correo} errorKey="correo"><input id="txtCorreo" className={`input ${errors.correo ? "input-error" : ""}`} type="email" maxLength={254} value={form.correo} onChange={e => setField("correo", e.target.value)} /></Field>
                        <Field label="Teléfono" hint="Opcional."><input id="txtTelefono" className="input" maxLength={30} value={form.telefono} onChange={e => setField("telefono", e.target.value)} /></Field>
                    </div>
                </section>
                <section className="card">
                    <h2 className="card-title">Información académica</h2>
                    <div className="grid-2">
                        <Field label="Nivel escolar" required error={errors.nivelEscolar} errorKey="nivelEscolar"><select id="cmbNivelEscolar" className={`input ${errors.nivelEscolar ? "input-error" : ""}`} value={form.nivelEscolar} onChange={e => setField("nivelEscolar", e.target.value)}><option value="">Seleccione una opción</option>{NIVELES.map(level => <option key={level}>{level}</option>)}</select></Field>
                        <Field label="Instrumento musical" required error={errors.idInstrumento} errorKey="idInstrumento"><select id="cmbInstrumento" className={`input ${errors.idInstrumento ? "input-error" : ""}`} value={form.idInstrumento} onChange={e => setField("idInstrumento", e.target.value)}><option value="">Seleccione una opción</option>{instruments.map(instrument => <option key={instrument.id} value={instrument.id}>{instrument.nombre}</option>)}</select></Field>
                    </div>
                    <Field label="Planes de estudio" required error={errors.idsPlanEstudio} errorKey="idsPlanEstudio" hint={planes.length === 0 ? "No hay planes de estudio vigentes." : "Determinan los cursos que el estudiante podrá matricular. Puede seleccionar más de uno."}>
                        <div className="check-row">
                            {planes.map(plan => <label key={plan.id}><input type="checkbox" checked={form.idsPlanEstudio.includes(plan.id)} onChange={() => togglePlan(plan.id)} /> {plan.nombre}</label>)}
                        </div>
                    </Field>
                    <Field label="Centro educativo" required={requiereCentro} error={errors.idCentroEducativo} errorKey="idCentroEducativo"><select id="cmbCentroEducativo" className={`input ${errors.idCentroEducativo ? "input-error" : ""}`} value={form.idCentroEducativo} onChange={e => setField("idCentroEducativo", e.target.value)} disabled={!requiereCentro}><option value="">{requiereCentro ? "Seleccione una opción" : "No aplica para esta edad"}</option>{centers.map(center => <option key={center.id} value={center.id}>{center.nombre}</option>)}</select></Field>
                </section>
                <div className="actions-row">
                    {onCancel ? <button type="button" className="btn btn-secondary" onClick={onCancel}><ArrowLeft size={16} /> Cancelar</button> : <Link className="btn btn-secondary" to="/admin/administracion/estudiantes"><ArrowLeft size={16} /> Cancelar</Link>}
                    <button id="btnGuardarCambios" className="btn btn-primary" type="submit" disabled={saving}><Save size={16} /> {saving ? "Guardando..." : "Guardar cambios"}</button>
                </div>
            </form>
        </main>
    );
}

function Field({ label, required, hint, error, errorKey, children }) {
    return <div className="field" data-error-field={errorKey}><label className="field-label">{label} {required && <span className="required">*</span>}</label>{children}{error ? <p className="error">{error}</p> : hint ? <p className="hint">{hint}</p> : null}</div>;
}

export default EditarEstudiante;
