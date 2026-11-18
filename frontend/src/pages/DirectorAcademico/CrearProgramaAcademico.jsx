import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, BookPlus, CircleCheckBig, FilePlus2, GraduationCap } from "lucide-react";
import "./programas.css";
import Alert from "../../components/ui/Alert";
import { AyudaCodigoSugerido, EstadoDisponibilidad } from "../../components/ui/CodigoSugerido";
import Field from "../../components/ui/Field";
import Stepper from "../../components/ui/Stepper";
import { listaErrores } from "../../components/ui/utilidades";
import { useDialog } from "../../components/dialog/useDialog";
import { formularioModificado, useAvisoCambiosSinGuardar } from "../../hooks/useAvisoCambiosSinGuardar";
import { PATRON_CODIGO, normalizarCodigoEscrito, sugerirCodigo } from "../../utils/codigos";
import CamposPlanEstudio from "./CamposPlanEstudio";
import {
    PLAN_VACIO,
    consultarDisponibilidad,
    crearPrograma,
    fechaLegible,
    hoyISO,
    planAPayload,
    validarPlan,
} from "./programasApi";

const PASOS = ["Datos del programa", "Plan de estudio", "Revisar y crear"];

const MAX_DESCRIPCION = 2000;

function formInicial() {
    return {
        nombre: "",
        codigo: "",
        descripcion: "",
        conPlan: true,
        plan: { ...PLAN_VACIO, fechaVigenciaDesde: hoyISO() },
    };
}

/*
 * Asistente "Nuevo programa académico". Se abre dentro de la pantalla
 * Programas académicos. Un programa no se liga a ningún período.
 *
 *   1. Datos del programa (con código sugerido y validación en vivo).
 *   2. Plan de estudio inicial (recomendado: sin plan no se le pueden
 *      crear cursos).
 *   3. Revisar y crear.
 */
function CrearProgramaAcademico({ onCancel, onSuccess }) {
    const dialog = useDialog();
    const navigate = useNavigate();

    const [inicial] = useState(formInicial);
    const [form, setForm] = useState(inicial);
    const [codigoEditado, setCodigoEditado] = useState(false);
    const [paso, setPaso] = useState(0);
    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);
    const [guardando, setGuardando] = useState(false);
    const [creado, setCreado] = useState(null);

    // Última respuesta de la validación en vivo, con los valores consultados:
    // { codigo, nombre, codigoDisponible, nombreDisponible, fallo }
    const [respuestaDisponibilidad, setRespuestaDisponibilidad] = useState(null);

    // Estado de cada campo: "consultando" mientras la respuesta no sea del
    // valor escrito ahora; luego "libre" u "ocupado" (nada si falló la red:
    // lo valida el backend al guardar).
    function estadoDisponibilidad(valor, campo, campoDisponible) {
        if (!valor) return undefined;
        const r = respuestaDisponibilidad;
        if (!r || r[campo] !== valor) return "consultando";
        if (r.fallo) return undefined;
        return r[campoDisponible] ? "libre" : "ocupado";
    }

    const disponibilidad = {
        codigo: estadoDisponibilidad(form.codigo.trim(), "codigo", "codigoDisponible"),
        nombre: estadoDisponibilidad(form.nombre.trim(), "nombre", "nombreDisponible"),
    };

    const hayCambios = !creado && formularioModificado(form, inicial);
    const { confirmarDescarte, permitirSalida } = useAvisoCambiosSinGuardar(hayCambios);

    const tituloPaso = useRef(null);
    const primerRender = useRef(true);

    // Al cambiar de paso, el foco va al título del paso.
    useEffect(() => {
        if (primerRender.current) {
            primerRender.current = false;
            return;
        }
        tituloPaso.current?.focus();
    }, [paso, creado]);

    // Consulta (con pausa de 400 ms) si el código y el nombre ya existen.
    useEffect(() => {
        const codigo = form.codigo.trim();
        const nombre = form.nombre.trim();
        if (!codigo && !nombre) return undefined;

        const control = new AbortController();
        const espera = setTimeout(() => {
            consultarDisponibilidad({ codigo, nombre }, control.signal)
                .then((r) => setRespuestaDisponibilidad(r ? { ...r, codigo, nombre } : { codigo, nombre, fallo: true }))
                .catch((error) => {
                    if (error.name !== "AbortError") setRespuestaDisponibilidad({ codigo, nombre, fallo: true });
                });
        }, 400);

        return () => {
            clearTimeout(espera);
            control.abort();
        };
    }, [form.codigo, form.nombre]);

    function limpiarError(campo) {
        setErrores((prev) => {
            if (!prev[campo]) return prev;
            const siguiente = { ...prev };
            delete siguiente[campo];
            return siguiente;
        });
    }

    function cambiarCampo(e) {
        const { name, value } = e.target;

        setForm((prev) => {
            const siguiente = { ...prev, [name]: name === "codigo" ? normalizarCodigoEscrito(value) : value };
            // Mientras el director no escriba su propio código, se sugiere uno.
            if (name === "nombre" && !codigoEditado) siguiente.codigo = sugerirCodigo(value);
            return siguiente;
        });

        if (name === "codigo") setCodigoEditado(value.trim() !== "");
        limpiarError(name);
        if (name === "nombre" && !codigoEditado) limpiarError("codigo");
    }

    function restaurarSugerencia() {
        setCodigoEditado(false);
        setForm((prev) => ({ ...prev, codigo: sugerirCodigo(prev.nombre) }));
        limpiarError("codigo");
    }

    function cambiarPlan(campo, valor) {
        setForm((prev) => ({ ...prev, plan: { ...prev.plan, [campo]: valor } }));
        limpiarError(campo);
    }

    // ---------- Validación ----------

    function validarPaso0() {
        const e = {};
        const nombre = form.nombre.trim();
        const codigo = form.codigo.trim();

        if (!nombre) e.nombre = "Ingrese el nombre del programa.";
        else if (nombre.length > 150) e.nombre = "El nombre no puede superar los 150 caracteres.";
        else if (disponibilidad.nombre === "ocupado") e.nombre = "Ya existe un programa con este nombre.";

        if (!codigo) e.codigo = "Ingrese el código del programa.";
        else if (codigo.length > 30) e.codigo = "El código no puede superar los 30 caracteres.";
        else if (!PATRON_CODIGO.test(codigo)) e.codigo = "Use solo letras, números y guiones, sin guiones al inicio o al final (ej. FMI-2027).";
        else if (disponibilidad.codigo === "ocupado") e.codigo = "Ya existe un programa con este código.";

        if (form.descripcion.length > MAX_DESCRIPCION) e.descripcion = `La descripción no puede superar los ${MAX_DESCRIPCION} caracteres.`;

        return e;
    }

    function validarPaso1() {
        return form.conPlan ? validarPlan(form.plan) : {};
    }

    function irA(n) {
        setErrores({});
        setPaso(n);
    }

    async function continuar(e) {
        e?.preventDefault();

        if (paso === 0 || paso === 1) {
            const encontrados = paso === 0 ? validarPaso0() : validarPaso1();
            if (Object.keys(encontrados).length > 0) {
                setErrores(encontrados);
                setIntento((n) => n + 1);
                return;
            }
            irA(paso + 1);
            return;
        }

        // Último paso: validar todo otra vez y crear.
        const e0 = validarPaso0();
        if (Object.keys(e0).length > 0) {
            setPaso(0);
            setErrores(e0);
            setIntento((n) => n + 1);
            return;
        }
        const e1 = validarPaso1();
        if (Object.keys(e1).length > 0) {
            setPaso(1);
            setErrores(e1);
            setIntento((n) => n + 1);
            return;
        }

        await guardar();
    }

    async function guardar() {
        const payload = {
            codigo: form.codigo.trim(),
            nombre: form.nombre.trim(),
            descripcion: form.descripcion.trim() || null,
            planInicial: form.conPlan ? planAPayload(form.plan) : null,
        };

        setGuardando(true);
        try {
            const programa = await crearPrograma(payload);
            permitirSalida();
            setCreado(programa);
        } catch (error) {
            // Un dato repetido (409) o inválido se marca en su paso.
            const mensaje = error.message || "Ocurrió un error al crear el programa académico.";
            const campos = error.fields || {};
            const duplicado = error.status === 409;
            const campoCodigo = campos.codigo || (duplicado && /código/i.test(mensaje));
            const campoNombre = campos.nombre || (duplicado && /nombre/i.test(mensaje));

            if (duplicado && /versión/i.test(mensaje)) {
                setPaso(1);
                setErrores({ version: mensaje });
                setIntento((n) => n + 1);
            } else if (campoCodigo || campoNombre) {
                setPaso(0);
                setErrores({
                    ...(campoCodigo ? { codigo: campos.codigo || mensaje } : {}),
                    ...(campoNombre ? { nombre: campos.nombre || mensaje } : {}),
                });
                setIntento((n) => n + 1);
            } else if (Object.keys(campos).some((c) => c.startsWith("planInicial"))) {
                setPaso(1);
                setErrores(Object.fromEntries(Object.entries(campos).map(([c, m]) => [c.replace("planInicial.", ""), m])));
                setIntento((n) => n + 1);
            } else {
                dialog.error(mensaje);
            }
        } finally {
            setGuardando(false);
        }
    }

    async function cancelar() {
        if (hayCambios && !(await confirmarDescarte())) return;
        permitirSalida();
        onCancel();
    }

    function crearOtro() {
        const nuevo = formInicial();
        setForm(nuevo);
        setCodigoEditado(false);
        setErrores({});
        setCreado(null);
        setPaso(0);
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${campo}-error` : `${campo}-estado`,
    });
    const resumen = listaErrores(errores);

    // ---------- Pantalla de éxito ----------

    if (creado) {
        const plan = creado.planVigente;
        return (
            <div className="wizard programa-wizard">
                <div className="panel">
                    <div className="success-screen" role="status">
                        <span className="success-mark"><CircleCheckBig size={32} strokeWidth={1.75} aria-hidden="true" /></span>
                        <h2 ref={tituloPaso} tabIndex={-1}>Programa creado</h2>
                        <p className="muted">
                            <b>{creado.nombre}</b> ya está activo{plan ? " y listo para recibir cursos." : "."}
                        </p>
                        <dl className="receipt programa-recibo">
                            <div><dt>Código</dt><dd>{creado.codigo}</dd></div>
                            <div><dt>Plan vigente</dt><dd>{plan ? `${plan.codigo} · versión ${plan.version}` : "Sin plan todavía"}</dd></div>
                        </dl>
                        {!plan && (
                            <Alert tone="warning" title="Falta el plan de estudio">
                                Para crear cursos en este programa, ábralo con «Modificar» y agregue su plan de estudio.
                            </Alert>
                        )}
                        <div className="programa-exito-acciones">
                            {plan && (
                                <button
                                    type="button"
                                    className="sg-btn sg-btn--primary"
                                    onClick={() => navigate("/director-academico/cursos", { state: { nuevoCursoIdPrograma: creado.id } })}
                                >
                                    <BookPlus size={18} strokeWidth={1.75} aria-hidden="true" />
                                    Crear un curso en este programa
                                </button>
                            )}
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={crearOtro}>
                                <FilePlus2 size={18} strokeWidth={1.75} aria-hidden="true" />
                                Crear otro programa
                            </button>
                            <button type="button" className="sg-btn sg-btn--ghost" onClick={onSuccess}>
                                Volver al listado
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    // ---------- Contenido de cada paso ----------

    let cuerpo;

    if (paso === 0) {
        cuerpo = (
            <>
                <h2 className="form-section-title" ref={tituloPaso} tabIndex={-1}>Datos del programa</h2>
                <p className="form-section-sub">
                    Un programa es la formación completa que sigue un estudiante (por ejemplo, «Formación Musical Infantil»). No depende de ningún período académico.
                </p>

                <div className="form-grid">
                    <Field id="nombre" label="Nombre del programa" hint="Debe ser único." error={errores.nombre} className="span-all">
                        <input
                            id="nombre"
                            name="nombre"
                            type="text"
                            className="sg-input"
                            maxLength={150}
                            value={form.nombre}
                            onChange={cambiarCampo}
                            placeholder="Ej. Formación Musical Infantil"
                            autoComplete="off"
                            autoFocus
                            {...aria("nombre")}
                        />
                    </Field>
                    {!errores.nombre && <EstadoDisponibilidad id="nombre-estado" estado={disponibilidad.nombre} que="nombre" mensajeOcupado="Ya existe un programa con este nombre." className="span-all programa-nombre-estado" />}

                    <Field
                        id="codigo"
                        label="Código"
                        hint={codigoEditado ? "Letras, números y guiones. Se guarda en mayúsculas y no se puede cambiar después." : "Se sugiere a partir del nombre; puede cambiarlo. No se puede modificar después."}
                        error={errores.codigo}
                    >
                        <input
                            id="codigo"
                            name="codigo"
                            type="text"
                            className="sg-input tnum programa-codigo-input"
                            maxLength={30}
                            value={form.codigo}
                            onChange={cambiarCampo}
                            placeholder="Ej. FMI"
                            autoComplete="off"
                            spellCheck={false}
                            {...aria("codigo")}
                        />
                    </Field>
                    <div className="programa-codigo-extra">
                        <AyudaCodigoSugerido
                            editado={codigoEditado}
                            codigo={form.codigo}
                            sugerencia={sugerirCodigo(form.nombre)}
                            onUsarSugerencia={restaurarSugerencia}
                        >
                            {!errores.codigo && (
                                <EstadoDisponibilidad id="codigo-estado" estado={disponibilidad.codigo} que="código" mensajeOcupado="Ya existe un programa con este código." />
                            )}
                        </AyudaCodigoSugerido>
                    </div>

                    <Field id="descripcion" label="Descripción" optional error={errores.descripcion} className="span-all">
                        <textarea
                            id="descripcion"
                            name="descripcion"
                            className="sg-input"
                            rows={4}
                            maxLength={MAX_DESCRIPCION}
                            value={form.descripcion}
                            onChange={cambiarCampo}
                            placeholder="A quién va dirigido, qué instrumentos abarca, duración aproximada…"
                            aria-describedby="descripcion-contador"
                        />
                    </Field>
                    <p id="descripcion-contador" className="programa-contador span-all" aria-live="polite">
                        {form.descripcion.length} / {MAX_DESCRIPCION}
                    </p>
                </div>
            </>
        );
    }

    if (paso === 1) {
        const version = form.plan.version.trim() || "…";
        const codigoPlan = `${form.codigo.trim() || "…"}-V${version.toUpperCase()}`;

        cuerpo = (
            <>
                <h2 className="form-section-title" ref={tituloPaso} tabIndex={-1}>Plan de estudio</h2>
                <p className="form-section-sub">
                    El plan de estudio es la versión de la malla curricular del programa: los cursos se agregan a él. Sin un plan vigente no se pueden crear cursos en el programa.
                </p>

                <fieldset className="fieldset">
                    <legend>¿Desea crear el plan de estudio ahora?</legend>
                    <div className="choice-list programa-opciones">
                        <label className={`choice${form.conPlan ? " is-checked" : ""}`}>
                            <input type="radio" name="conPlan" checked={form.conPlan} onChange={() => setForm((p) => ({ ...p, conPlan: true }))} />
                            <span className="choice-main">
                                <b>Sí, crear el plan ahora <span className="sg-badge sg-badge--success">Recomendado</span></b>
                                <span>El programa queda listo para recibir cursos.</span>
                            </span>
                        </label>
                        <label className={`choice${!form.conPlan ? " is-checked" : ""}`}>
                            <input
                                type="radio"
                                name="conPlan"
                                checked={!form.conPlan}
                                onChange={() => { setForm((p) => ({ ...p, conPlan: false })); setErrores({}); }}
                            />
                            <span className="choice-main">
                                <b>No, lo crearé después</b>
                                <span>Podrá agregarlo luego con «Modificar» en el listado.</span>
                            </span>
                        </label>
                    </div>
                </fieldset>

                {form.conPlan ? (
                    <div className="programa-plan">
                        <CamposPlanEstudio plan={form.plan} errores={errores} onChange={cambiarPlan} />
                        <div className="programa-plan-preview" aria-live="polite">
                            <GraduationCap size={20} strokeWidth={1.75} aria-hidden="true" />
                            <div>
                                <span className="muted">El sistema lo registrará como</span>
                                <b>Plan de estudios {form.nombre.trim()} v{version}</b>
                                <span className="tnum">Código {codigoPlan}</span>
                            </div>
                        </div>
                    </div>
                ) : (
                    <Alert tone="warning" title="El programa quedará sin plan de estudio" className="programa-plan">
                        No aparecerá como opción útil en «Nuevo curso» hasta que le agregue un plan.
                    </Alert>
                )}
            </>
        );
    }

    if (paso === 2) {
        cuerpo = (
            <>
                <h2 className="form-section-title" ref={tituloPaso} tabIndex={-1}>Revisar y crear</h2>
                <p className="form-section-sub">Verifique los datos. El código no se podrá cambiar después.</p>

                <div className="review">
                    <section className="review-block">
                        <h3>Programa <button type="button" className="link-btn" onClick={() => irA(0)}>Editar<span className="sr-only"> datos del programa</span></button></h3>
                        <dl>
                            <dt>Nombre</dt><dd>{form.nombre.trim()}</dd>
                            <dt>Código</dt><dd className="tnum">{form.codigo.trim()}</dd>
                            <dt>Descripción</dt><dd className="programa-descripcion">{form.descripcion.trim() || "Sin descripción"}</dd>
                            <dt>Estado</dt><dd>Activo</dd>
                        </dl>
                    </section>

                    <section className="review-block">
                        <h3>Plan de estudio <button type="button" className="link-btn" onClick={() => irA(1)}>Editar<span className="sr-only"> plan de estudio</span></button></h3>
                        {form.conPlan ? (
                            <dl>
                                <dt>Versión</dt><dd className="tnum">{form.plan.version.trim()}</dd>
                                <dt>Vigente desde</dt><dd>{fechaLegible(form.plan.fechaVigenciaDesde)}</dd>
                                <dt>Nota mínima</dt><dd className="tnum">{form.plan.notaMinimaAprobacion}</dd>
                            </dl>
                        ) : (
                            <dl><dt>Plan</dt><dd>Se creará después</dd></dl>
                        )}
                    </section>

                    <Alert tone="info" title="Qué pasará al crear">
                        {form.conPlan
                            ? "Se creará el programa activo con su plan de estudio vigente. Después podrá agregarle cursos desde «Cursos → Nuevo curso»."
                            : "Se creará el programa activo, sin plan de estudio. Agréguele uno antes de crear cursos."}
                    </Alert>
                </div>
            </>
        );
    }

    const ultimo = paso === PASOS.length - 1;
    const consultando = paso === 0 && (disponibilidad.codigo === "consultando" || disponibilidad.nombre === "consultando");

    return (
        <div className="wizard programa-wizard">
            <h2 className="form-section-title form-titulo">Nuevo programa académico</h2>

            <Stepper pasos={PASOS} actual={paso} etiqueta="Pasos para crear el programa" />

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
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={() => irA(paso - 1)} disabled={guardando}>
                            <ArrowLeft size={18} strokeWidth={1.75} aria-hidden="true" />
                            Anterior
                        </button>
                    ) : (
                        <button type="button" className="sg-btn sg-btn--ghost" onClick={cancelar}>Cancelar</button>
                    )}
                    <div className="right">
                        {paso > 0 && (
                            <button type="button" className="sg-btn sg-btn--ghost" onClick={cancelar} disabled={guardando}>Cancelar</button>
                        )}
                        <button
                            type="button"
                            className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`}
                            onClick={continuar}
                            disabled={guardando || consultando}
                            aria-busy={guardando || undefined}
                        >
                            {guardando && <span className="sg-spinner" aria-hidden="true" />}
                            {ultimo ? (guardando ? "Creando programa…" : "Crear programa") : "Continuar"}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default CrearProgramaAcademico;
