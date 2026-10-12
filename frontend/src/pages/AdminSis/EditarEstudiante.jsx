import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Save } from "lucide-react";
import "./styles/admin_Sis.css";
import "./styles/Estudiantes.css";
import "./styles/CreateStudent.css";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";

import { useDialog } from "../../components/dialog/useDialog";
import UbicacionSelects from "../../components/UbicacionSelects";
import { UBICACION_VACIA, ubicacionDesdeRespuesta } from "../../components/ubicacion";
import { API_URL } from "../../config/api";
import { lanzarError, mensajeDeExcepcion } from "../../utils/api";

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

// id del control de cada campo (para los enlaces del resumen de errores)
const IDS = {
    idTipoIdentificacion: "cmbTipoIdentificacion", numeroIdentificacion: "txtNumeroIdentificacion", nombres: "txtNombres",
    primerApellido: "txtPrimerApellido", genero: "cmbGenero", fechaNacimiento: "txtFechaNacimiento", correo: "txtCorreo",
    direccion: "txtDireccion", nivelEscolar: "cmbNivelEscolar", idInstrumento: "cmbInstrumento", idCentroEducativo: "cmbCentroEducativo",
    idsPlanEstudio: "edit-idsPlanEstudio", idProvincia: "edit-idProvincia", codigoCanton: "edit-codigoCanton", codigoDistrito: "edit-codigoDistrito",
};

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
    const [intento, setIntento] = useState(0);

    const edad = useMemo(() => ageFromDate(form.fechaNacimiento), [form.fechaNacimiento]);
    const requiereCentro = edad !== null && edad >= 6 && edad < 18;

    useEffect(() => {
        async function cargar() {
            try {
                const token = localStorage.getItem("token");
                const headers = token ? { Authorization: `Bearer ${token}` } : {};
                const [studentResponse, typesResponse, centersResponse, instrumentsResponse, plansResponse] = await Promise.all([
                    fetch(`${API_URL}/estudiantes/${id}`, { headers }),
                    fetch(`${API_URL}/registro/catalogos/tipos-identificacion`, { headers }),
                    fetch(`${API_URL}/registro/catalogos/centros-educativos`, { headers }),
                    fetch(`${API_URL}/registro/catalogos/instrumentos`, { headers }),
                    fetch(`${API_URL}/planes-estudio`, { headers }),
                ]);
                if (!studentResponse.ok) await lanzarError(studentResponse, "No fue posible cargar la información del estudiante.");
                // Si falla un catálogo se indica cuál, para no confundirlo con un problema del estudiante
                if (!typesResponse.ok) throw new Error("No se pudo cargar el catálogo de tipos de identificación.");
                if (!centersResponse.ok) throw new Error("No se pudo cargar el catálogo de centros educativos.");
                if (!instrumentsResponse.ok) throw new Error("No se pudo cargar el catálogo de instrumentos.");
                if (!plansResponse.ok) throw new Error("No se pudo cargar el catálogo de planes de estudio.");
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
                dialog.error(mensajeDeExcepcion(e, "No fue posible cargar la información del estudiante."));
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
            setIntento(n => n + 1);
            return;
        }

        const { ubicacion, ...datos } = form;
        const payload = { ...datos, codigoDistrito: Number(ubicacion.codigoDistrito), idTipoIdentificacion: Number(form.idTipoIdentificacion), idInstrumento: Number(form.idInstrumento), idCentroEducativo: form.idCentroEducativo ? Number(form.idCentroEducativo) : null };
        try {
            setSaving(true);
            const token = localStorage.getItem("token");
            const response = await fetch(`${API_URL}/estudiantes/${id}`, {
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

    const resumen = Object.entries(errors)
        .filter(([, texto]) => texto)
        .map(([campo, texto]) => ({ href: `#${IDS[campo] || `edit-${campo}`}`, text: texto }));
    const err = (campo) => ({
        "aria-invalid": errors[campo] ? "true" : undefined,
        "aria-describedby": errors[campo] ? `${IDS[campo]}-error` : undefined,
    });

    if (loading) return <Alert tone="info" title="Cargando estudiante..." />;
    if (!form.numeroIdentificacion) return <Link className="sg-btn sg-btn--secondary" to="/admin/administracion/estudiantes"><ArrowLeft size={18} strokeWidth={1.75} aria-hidden="true" /> Volver a estudiantes</Link>;

    return (
        <div className="editar-estudiante">
            {onCancel
                ? <h2 className="form-section-title editar-titulo">Editar estudiante</h2>
                : <PageHead title="Editar estudiante" sub="Actualiza la información personal y académica del estudiante." />}

            <form onSubmit={submit} noValidate>
                {resumen.length > 0 && (
                    <Alert
                        key={intento}
                        tone="danger"
                        autoFocus
                        className="form-alert"
                        title={resumen.length === 1 ? "Revise 1 dato antes de guardar" : `Revise ${resumen.length} datos antes de guardar`}
                        errors={resumen}
                    />
                )}

                <section className="panel">
                    <div className="panel-head"><h2>Datos personales</h2></div>
                    <div className="panel-body form-grid">
                        <Field id={IDS.idTipoIdentificacion} errorKey="idTipoIdentificacion" label="Tipo de identificación" error={errors.idTipoIdentificacion} select>
                            <select id={IDS.idTipoIdentificacion} className="sg-input sg-select" value={form.idTipoIdentificacion} onChange={e => setField("idTipoIdentificacion", e.target.value)} {...err("idTipoIdentificacion")}>
                                <option value="">Seleccione</option>
                                {types.map(type => <option key={type.id} value={type.id}>{type.nombre}</option>)}
                            </select>
                        </Field>
                        <Field id={IDS.numeroIdentificacion} errorKey="numeroIdentificacion" label="Número de identificación" error={errors.numeroIdentificacion}>
                            <input id={IDS.numeroIdentificacion} className="sg-input" maxLength={40} value={form.numeroIdentificacion} onChange={e => setField("numeroIdentificacion", e.target.value)} {...err("numeroIdentificacion")} />
                        </Field>
                        <Field id={IDS.nombres} errorKey="nombres" label="Nombres" error={errors.nombres}>
                            <input id={IDS.nombres} className="sg-input" maxLength={100} value={form.nombres} onChange={e => setField("nombres", e.target.value)} {...err("nombres")} />
                        </Field>
                        <Field id={IDS.primerApellido} errorKey="primerApellido" label="Primer apellido" error={errors.primerApellido}>
                            <input id={IDS.primerApellido} className="sg-input" maxLength={80} value={form.primerApellido} onChange={e => setField("primerApellido", e.target.value)} {...err("primerApellido")} />
                        </Field>
                        <Field id="txtSegundoApellido" label="Segundo apellido" optional>
                            <input id="txtSegundoApellido" className="sg-input" maxLength={80} value={form.segundoApellido} onChange={e => setField("segundoApellido", e.target.value)} />
                        </Field>
                        <Field id={IDS.genero} errorKey="genero" label="Género" error={errors.genero} select>
                            <select id={IDS.genero} className="sg-input sg-select" value={form.genero} onChange={e => setField("genero", e.target.value)} {...err("genero")}>
                                <option value="">Seleccione</option>
                                {GENEROS.map(g => <option key={g}>{g}</option>)}
                            </select>
                        </Field>
                        <Field
                            id={IDS.fechaNacimiento}
                            errorKey="fechaNacimiento"
                            label="Fecha de nacimiento"
                            hint={edad === null ? undefined : `${edad} años · ${edad < 18 ? "menor de edad" : "mayor de edad"}`}
                            error={errors.fechaNacimiento}
                        >
                            <input id={IDS.fechaNacimiento} className="sg-input" type="date" max={todayISO()} value={form.fechaNacimiento} onChange={e => setField("fechaNacimiento", e.target.value)} {...err("fechaNacimiento")} />
                        </Field>
                        <Field id={IDS.correo} errorKey="correo" label="Correo electrónico" optional error={errors.correo}>
                            <input id={IDS.correo} className="sg-input" type="email" maxLength={254} value={form.correo} onChange={e => setField("correo", e.target.value)} {...err("correo")} />
                        </Field>
                        <Field id="txtTelefono" label="Teléfono" optional>
                            <input id="txtTelefono" className="sg-input" type="tel" maxLength={30} value={form.telefono} onChange={e => setField("telefono", e.target.value)} />
                        </Field>
                        <div />
                        <UbicacionSelects required idPrefix="edit-" value={form.ubicacion} errors={errors} onChange={ubicacion => {
                            setForm(current => ({ ...current, ubicacion }));
                            setErrors(current => ({ ...current, idProvincia: "", codigoCanton: "", codigoDistrito: "" }));
                        }} />
                        <Field id={IDS.direccion} errorKey="direccion" label="Dirección exacta (señas)" className="span-all" error={errors.direccion}>
                            <textarea id={IDS.direccion} className="sg-input" maxLength={500} value={form.direccion} onChange={e => setField("direccion", e.target.value)} placeholder="Ej.: 200 m norte de la iglesia, casa verde a mano derecha" {...err("direccion")} />
                        </Field>
                        <Field id="txtCondicionesRelevantes" label="Condiciones relevantes" optional className="span-all">
                            <textarea id="txtCondicionesRelevantes" className="sg-input" value={form.condicionesRelevantes} onChange={e => setField("condicionesRelevantes", e.target.value)} />
                        </Field>
                    </div>
                </section>

                <section className="panel">
                    <div className="panel-head"><h2>Información académica</h2></div>
                    <div className="panel-body form-grid">
                        <Field id={IDS.nivelEscolar} errorKey="nivelEscolar" label="Nivel escolar" error={errors.nivelEscolar} select>
                            <select id={IDS.nivelEscolar} className="sg-input sg-select" value={form.nivelEscolar} onChange={e => setField("nivelEscolar", e.target.value)} {...err("nivelEscolar")}>
                                <option value="">Seleccione</option>
                                {NIVELES.map(level => <option key={level}>{level}</option>)}
                            </select>
                        </Field>
                        <Field id={IDS.idInstrumento} errorKey="idInstrumento" label="Instrumento musical" error={errors.idInstrumento} select>
                            <select id={IDS.idInstrumento} className="sg-input sg-select" value={form.idInstrumento} onChange={e => setField("idInstrumento", e.target.value)} {...err("idInstrumento")}>
                                <option value="">Seleccione</option>
                                {instruments.map(instrument => <option key={instrument.id} value={instrument.id}>{instrument.nombre}</option>)}
                            </select>
                        </Field>
                        <Field
                            id={IDS.idCentroEducativo}
                            errorKey="idCentroEducativo"
                            label="Centro educativo"
                            optional={!requiereCentro}
                            hint={requiereCentro ? undefined : "No aplica para esta edad."}
                            error={errors.idCentroEducativo}
                            select
                        >
                            <select id={IDS.idCentroEducativo} className="sg-input sg-select" value={form.idCentroEducativo} onChange={e => setField("idCentroEducativo", e.target.value)} disabled={!requiereCentro} {...err("idCentroEducativo")}>
                                <option value="">{requiereCentro ? "Seleccione" : "No aplica"}</option>
                                {centers.map(center => <option key={center.id} value={center.id}>{center.nombre}</option>)}
                            </select>
                        </Field>
                        <div />
                        <fieldset className="fieldset span-all" id={IDS.idsPlanEstudio} tabIndex={-1} data-error-field="idsPlanEstudio" aria-describedby={errors.idsPlanEstudio ? `${IDS.idsPlanEstudio}-error` : undefined}>
                            <legend>Planes de estudio</legend>
                            <p className="sg-field__hint plan-hint">
                                {planes.length === 0 ? "No hay planes de estudio vigentes." : "Determinan los cursos que el estudiante podrá matricular. Puede seleccionar más de uno."}
                            </p>
                            {errors.idsPlanEstudio && <p className="sg-field__error plan-error" id={`${IDS.idsPlanEstudio}-error`}>{errors.idsPlanEstudio}</p>}
                            <div className="choice-list">
                                {planes.map(plan => {
                                    const marcado = form.idsPlanEstudio.includes(plan.id);
                                    return (
                                        <label key={plan.id} className={`choice${marcado ? " is-checked" : ""}`}>
                                            <input type="checkbox" checked={marcado} onChange={() => togglePlan(plan.id)} />
                                            <span className="choice-main"><b>{plan.nombre}</b></span>
                                        </label>
                                    );
                                })}
                            </div>
                        </fieldset>
                    </div>
                    <div className="panel-foot">
                        {onCancel
                            ? <button type="button" className="sg-btn sg-btn--secondary" onClick={onCancel}>Cancelar</button>
                            : <Link className="sg-btn sg-btn--secondary" to="/admin/administracion/estudiantes">Cancelar</Link>}
                        <button id="btnGuardarCambios" className={`sg-btn sg-btn--primary${saving ? " is-loading" : ""}`} type="submit" disabled={saving} aria-busy={saving || undefined}>
                            {saving ? <span className="sg-spinner" aria-hidden="true" /> : <Save size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {saving ? "Guardando…" : "Guardar cambios"}
                        </button>
                    </div>
                </section>
            </form>
        </div>
    );
}

export default EditarEstudiante;
