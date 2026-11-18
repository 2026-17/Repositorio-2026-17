import { useState } from "react";
import { GraduationCap, Lock, Save } from "lucide-react";
import "./crearCurso.css";
import "./programas.css";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import StatusBadge from "../../components/ui/StatusBadge";
import { listaErrores } from "../../components/ui/utilidades";
import { useDialog } from "../../components/dialog/useDialog";
import { formularioModificado, useAvisoCambiosSinGuardar } from "../../hooks/useAvisoCambiosSinGuardar";
import CamposPlanEstudio from "./CamposPlanEstudio";
import {
    PLAN_VACIO,
    actualizarPrograma,
    agregarPlanPrograma,
    fechaLegible,
    hoyISO,
    planAPayload,
    validarPlan,
} from "./programasApi";

const MAX_DESCRIPCION = 2000;

/*
 * Modificar un programa académico: nombre y descripción (el código no
 * cambia). Si el programa no tiene plan vigente, aquí mismo se le puede
 * crear uno. Se abre dentro de la pantalla Programas académicos.
 */
function EditarProgramaAcademico({ programa, onCancel, onSuccess, onPlanCreado }) {
    const dialog = useDialog();

    const [inicial] = useState(() => ({
        nombre: programa.nombre || "",
        descripcion: programa.descripcion || "",
    }));

    const [form, setForm] = useState(inicial);
    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);
    const [guardando, setGuardando] = useState(false);

    const [plan, setPlan] = useState({ ...PLAN_VACIO, fechaVigenciaDesde: hoyISO() });
    const [erroresPlan, setErroresPlan] = useState({});
    const [creandoPlan, setCreandoPlan] = useState(false);

    const hayCambios = formularioModificado(form, inicial);
    const { confirmarDescarte, permitirSalida } = useAvisoCambiosSinGuardar(hayCambios);

    const planVigente = programa.planVigente;

    function cambiar(e) {
        const { name, value } = e.target;
        setForm((prev) => ({ ...prev, [name]: value }));
        setErrores((prev) => {
            if (!prev[name]) return prev;
            const siguiente = { ...prev };
            delete siguiente[name];
            return siguiente;
        });
    }

    function cambiarPlan(campo, valor) {
        setPlan((prev) => ({ ...prev, [campo]: valor }));
        setErroresPlan((prev) => {
            if (!prev[campo]) return prev;
            const siguiente = { ...prev };
            delete siguiente[campo];
            return siguiente;
        });
    }

    async function cancelar() {
        if (hayCambios && !(await confirmarDescarte())) return;
        permitirSalida();
        onCancel();
    }

    async function guardar(e) {
        e.preventDefault();

        const encontrados = {};
        const nombre = form.nombre.trim();
        if (!nombre) encontrados.nombre = "Ingrese el nombre del programa.";
        else if (nombre.length > 150) encontrados.nombre = "El nombre no puede superar los 150 caracteres.";
        if (form.descripcion.length > MAX_DESCRIPCION) encontrados.descripcion = `La descripción no puede superar los ${MAX_DESCRIPCION} caracteres.`;

        if (Object.keys(encontrados).length > 0) {
            setErrores(encontrados);
            setIntento((n) => n + 1);
            return;
        }

        if (!hayCambios) {
            dialog.aviso("No hay cambios por guardar.");
            return;
        }

        setGuardando(true);
        try {
            const actualizado = await actualizarPrograma(programa.id, {
                nombre,
                descripcion: form.descripcion.trim() || null,
            });
            dialog.exito(`El programa "${actualizado.nombre}" fue modificado correctamente.`);
            permitirSalida();
            onSuccess();
        } catch (error) {
            if (error.status === 409 || error.fields?.nombre) {
                setErrores({ nombre: error.fields?.nombre || error.message });
                setIntento((n) => n + 1);
            } else {
                dialog.error(error.message || "Ocurrió un error al modificar el programa académico.");
            }
        } finally {
            setGuardando(false);
        }
    }

    async function crearPlan() {
        const encontrados = validarPlan(plan);
        if (Object.keys(encontrados).length > 0) {
            setErroresPlan(encontrados);
            return;
        }

        setCreandoPlan(true);
        try {
            const actualizado = await agregarPlanPrograma(programa.id, planAPayload(plan));
            dialog.exito(`Se creó el plan ${actualizado.planVigente?.codigo || ""}. Ya puede agregar cursos a este programa.`.replace("  ", " "));
            onPlanCreado(actualizado);
        } catch (error) {
            if (error.status === 409) setErroresPlan({ version: error.message });
            else dialog.error(error.message || "Ocurrió un error al crear el plan de estudio.");
        } finally {
            setCreandoPlan(false);
        }
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `editar-${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores, "editar-");

    return (
        <div className="curso-page">
            <h2 className="form-section-title form-titulo">Modificar programa académico</h2>

            <form onSubmit={guardar} noValidate>
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
                    <div className="panel-head">
                        <div>
                            <h2>Información del programa</h2>
                            <p className="panel-head-sub">
                                <Lock size={14} strokeWidth={1.75} aria-hidden="true" /> Código <b className="tnum">{programa.codigo}</b> (no se puede cambiar)
                            </p>
                        </div>
                        <StatusBadge tone={programa.activo ? "success" : "neutral"}>
                            {programa.activo ? "Activo" : "Inactivo"}
                        </StatusBadge>
                    </div>
                    <div className="panel-body form-grid">
                        <Field id="editar-nombre" label="Nombre del programa" hint="Debe ser único." error={errores.nombre} className="span-all">
                            <input id="editar-nombre" name="nombre" type="text" className="sg-input" maxLength={150} value={form.nombre} onChange={cambiar} autoComplete="off" {...aria("nombre")} />
                        </Field>
                        <Field id="editar-descripcion" label="Descripción" optional error={errores.descripcion} className="span-all">
                            <textarea id="editar-descripcion" name="descripcion" className="sg-input" rows={4} maxLength={MAX_DESCRIPCION} value={form.descripcion} onChange={cambiar} {...aria("descripcion")} />
                        </Field>
                        <p className="programa-contador span-all">{form.descripcion.length} / {MAX_DESCRIPCION}</p>
                    </div>
                    <div className="panel-foot">
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={cancelar} disabled={guardando}>
                            Cancelar
                        </button>
                        <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando} aria-busy={guardando || undefined}>
                            {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <Save size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {guardando ? "Guardando…" : "Guardar cambios"}
                        </button>
                    </div>
                </section>
            </form>

            <section className="panel">
                <div className="panel-head">
                    <div>
                        <h2>Plan de estudio vigente</h2>
                        <p className="panel-head-sub">Los cursos del programa se agregan a este plan.</p>
                    </div>
                </div>

                {planVigente ? (
                    <div className="panel-body">
                        <div className="programa-plan-preview">
                            <GraduationCap size={20} strokeWidth={1.75} aria-hidden="true" />
                            <div>
                                <b>{planVigente.nombre}</b>
                                <span className="tnum">
                                    {planVigente.codigo} · versión {planVigente.version} · vigente desde {fechaLegible(planVigente.fechaVigenciaDesde)}
                                    {planVigente.notaMinimaAprobacion != null && ` · nota mínima ${Number(planVigente.notaMinimaAprobacion)}`}
                                </span>
                                <span className="muted">
                                    {programa.cantidadCursos === 1 ? "1 curso activo" : `${programa.cantidadCursos} cursos activos`} en la malla
                                    {programa.cantidadPlanes > 1 && ` · ${programa.cantidadPlanes} versiones del plan en total`}
                                </span>
                            </div>
                        </div>
                    </div>
                ) : (
                    <>
                        <div className="panel-body">
                            <Alert tone="warning" title="Este programa no tiene plan vigente" className="form-alert">
                                Mientras no tenga uno, no se le pueden crear cursos. Créelo aquí:
                            </Alert>
                            <CamposPlanEstudio plan={plan} errores={erroresPlan} onChange={cambiarPlan} prefijo="editPlan-" />
                        </div>
                        <div className="panel-foot">
                            <button
                                type="button"
                                className={`sg-btn sg-btn--primary${creandoPlan ? " is-loading" : ""}`}
                                onClick={crearPlan}
                                disabled={creandoPlan}
                                aria-busy={creandoPlan || undefined}
                            >
                                {creandoPlan ? <span className="sg-spinner" aria-hidden="true" /> : <GraduationCap size={18} strokeWidth={1.75} aria-hidden="true" />}
                                {creandoPlan ? "Creando plan…" : "Crear plan de estudio"}
                            </button>
                        </div>
                    </>
                )}
            </section>
        </div>
    );
}

export default EditarProgramaAcademico;
