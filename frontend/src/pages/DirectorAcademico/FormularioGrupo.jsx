import { useEffect, useState } from "react";
import { API_URL } from "../../config/api";
import { Lock, Plus, Save, Trash2, Users } from "lucide-react";
import "./crearCurso.css";
import "./crearGrupo.css";
import Alert from "../../components/ui/Alert";
import { AyudaCodigoSugerido } from "../../components/ui/CodigoSugerido";
import Field from "../../components/ui/Field";
import { listaErrores } from "../../components/ui/utilidades";
import { lanzarError, mensajeDeExcepcion } from "../../utils/api";
import { normalizarCodigoEscrito } from "../../utils/codigos";

import { useDialog } from "../../components/dialog/useDialog";
import { formularioModificado, useAvisoCambiosSinGuardar } from "../../hooks/useAvisoCambiosSinGuardar";
import { DIAS, ESTADOS_GRUPO, MODALIDADES, aHoraCorta } from "./gruposComun";

const HORARIO_VACIO = { diaSemana: "", horaInicio: "", horaFin: "", aula: "" };

// Franja permitida para las lecciones (igual que en GrupoService).
const HORA_MINIMA = "07:00";
const HORA_MAXIMA = "22:00";

const FORM_VACIO = {
    idCurso: "",
    idPeriodo: "",
    codigo: "",
    cupoMaximo: "",
    idProfesor: "",
    modalidad: "PRESENCIAL",
    aula: "",
    fechaInicio: "",
    fechaFin: "",
    estado: "PLANIFICADO",
};

// Un grupo nuevo solo nace planificado o publicado.
const ESTADOS_CREACION = ESTADOS_GRUPO.filter((e) => ["PLANIFICADO", "PUBLICADO"].includes(e.value));

function nombreProfesor(p) {
    return [p.nombres, p.primerApellido, p.segundoApellido].filter(Boolean).join(" ");
}

function formDesdeGrupo(g) {
    return {
        idCurso: String(g.idCurso),
        idPeriodo: String(g.idPeriodo),
        codigo: g.codigo || "",
        cupoMaximo: String(g.cupoMaximo ?? ""),
        idProfesor: g.idProfesor ? String(g.idProfesor) : "",
        modalidad: g.modalidad || "PRESENCIAL",
        aula: g.aula || "",
        fechaInicio: g.fechaInicio || "",
        fechaFin: g.fechaFin || "",
        estado: g.estado || "PLANIFICADO",
    };
}

// Fila de horario sin ningún dato (en un curso a coordinar se ignora).
function horarioVacio(h) {
    return !h.diaSemana && !h.horaInicio && !h.horaFin && !h.aula.trim();
}

function horariosDesdeGrupo(g) {
    const lista = (g.horarios || []).map((h) => ({
        diaSemana: String(h.diaSemana),
        horaInicio: aHoraCorta(h.horaInicio),
        horaFin: aHoraCorta(h.horaFin),
        // El aula igual a la del grupo se deja vacía («Igual al grupo»).
        aula: h.aula && h.aula !== g.aula ? h.aula : "",
    }));
    return lista.length > 0 ? lista : [{ ...HORARIO_VACIO }];
}

/*
 * Formulario de grupo, para crear (grupo = null) o modificar. Se abre
 * dentro de la pantalla Grupos.
 *
 * Al crear, el código se sugiere con el código del curso y la primera
 * letra libre en el período (MUS-101-A, MUS-101-B…).
 */
function FormularioGrupo({ grupo = null, idCursoInicial = null, onCancel, onSuccess }) {
    const dialog = useDialog();
    const token = localStorage.getItem("token");
    const editando = grupo !== null;

    // idCursoInicial: viene de «Abrir un grupo ahora» al crear un curso.
    const [inicial] = useState(() => {
        if (editando) return formDesdeGrupo(grupo);
        return idCursoInicial ? { ...FORM_VACIO, idCurso: String(idCursoInicial) } : FORM_VACIO;
    });
    const [horariosIniciales] = useState(() => (editando ? horariosDesdeGrupo(grupo) : [{ ...HORARIO_VACIO }]));

    const [form, setForm] = useState(inicial);
    const [horarios, setHorarios] = useState(horariosIniciales);

    const [cursos, setCursos] = useState([]);
    const [periodos, setPeriodos] = useState([]);
    const [profesores, setProfesores] = useState([]);

    const [cargandoCatalogos, setCargandoCatalogos] = useState(true);
    const [guardando, setGuardando] = useState(false);

    // Código sugerido (solo al crear): { clave: "idCurso-idPeriodo", codigo }
    const [codigoEditado, setCodigoEditado] = useState(editando);
    const [sugerencia, setSugerencia] = useState(null);

    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);

    const hayCambios = formularioModificado(form, inicial) || formularioModificado(horarios, horariosIniciales);
    const { confirmarDescarte, permitirSalida } = useAvisoCambiosSinGuardar(hayCambios);

    // Con matrículas, el grupo queda atado a su curso y período.
    const cursoYPeriodoFijos = editando && grupo.tieneMatriculas;

    useEffect(() => {
        async function cargarCatalogos() {
            setCargandoCatalogos(true);
            try {
                const headers = { Authorization: `Bearer ${token}` };

                const respuestas = await Promise.all([
                    fetch(`${API_URL}/cursos`, { headers }),
                    fetch(`${API_URL}/periodos-academicos`, { headers }),
                    fetch(`${API_URL}/profesores?activo=true`, { headers }),
                ]);

                if (respuestas.some((r) => r.status === 401)) {
                    throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
                }
                if (respuestas.some((r) => r.status === 403)) {
                    throw new Error("No tiene permisos para consultar la información académica.");
                }

                const [cursosResponse, periodosResponse, profesoresResponse] = respuestas;

                if (!cursosResponse.ok) await lanzarError(cursosResponse, "No se pudieron cargar los cursos.");
                if (!periodosResponse.ok) await lanzarError(periodosResponse, "No se pudieron cargar los períodos académicos.");
                if (!profesoresResponse.ok) await lanzarError(profesoresResponse, "No se pudieron cargar los profesores.");

                // Instrumento I a XIV no se abren como grupos: van en
                // «Espacios de instrumento». (Se conserva el curso actual de
                // un grupo que ya existía.)
                let listaCursos = (await cursosResponse.json())
                    .filter((c) => !c.esCursoInstrumento || (editando && c.idCurso === grupo.idCurso));
                let listaPeriodos = await periodosResponse.json();
                const listaProfesores = await profesoresResponse.json();

                // Al modificar, el curso o período actual puede no estar entre
                // los disponibles (curso inactivo, período cerrado): se agrega
                // para que el formulario lo muestre.
                if (editando) {
                    if (!listaCursos.some((c) => c.idCurso === grupo.idCurso)) {
                        listaCursos = [{ idCurso: grupo.idCurso, codigo: grupo.codigoCurso, nombre: `${grupo.nombreCurso} (inactivo)` }, ...listaCursos];
                    }
                    if (!listaPeriodos.some((p) => p.id === grupo.idPeriodo)) {
                        listaPeriodos = [{ id: grupo.idPeriodo, nombre: grupo.nombrePeriodo, fechaInicio: grupo.fechaInicioPeriodo, fechaFin: grupo.fechaFinPeriodo }, ...listaPeriodos];
                    }
                }

                setCursos(listaCursos);
                setPeriodos(listaPeriodos);
                setProfesores(listaProfesores);
            } catch (e) {
                dialog.error(mensajeDeExcepcion(e, "No fue posible cargar la información necesaria."));
            } finally {
                setCargandoCatalogos(false);
            }
        }

        cargarCatalogos();
        // grupo no cambia mientras el formulario está abierto (se monta con key).
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [token, dialog, editando]);

    // Sugerencia de código: al elegir curso (y período), se pide al backend
    // el siguiente código libre. Solo al crear.
    useEffect(() => {
        if (editando || !form.idCurso) return undefined;

        const control = new AbortController();
        const params = new URLSearchParams({ idCurso: form.idCurso });
        if (form.idPeriodo) params.set("idPeriodo", form.idPeriodo);
        const clave = `${form.idCurso}-${form.idPeriodo}`;

        fetch(`${API_URL}/grupos/sugerencia-codigo?${params}`, {
            headers: { Authorization: `Bearer ${token}` },
            signal: control.signal,
        })
            .then((r) => (r.ok ? r.json() : null))
            .then((d) => {
                if (!d?.codigo) return;
                setSugerencia({ clave, codigo: d.codigo });
                if (!codigoEditado) {
                    setForm((prev) => ({ ...prev, codigo: d.codigo }));
                    limpiarError("codigo");
                }
            })
            .catch(() => { /* Sin sugerencia: el director escribe el código. */ });

        return () => control.abort();
        // codigoEditado se lee al llegar la respuesta; no debe volver a consultar.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [editando, form.idCurso, form.idPeriodo, token]);

    const sugerenciaActual = sugerencia && sugerencia.clave === `${form.idCurso}-${form.idPeriodo}` ? sugerencia.codigo : null;

    /*
     * Curso a coordinar en Dirección (Música de Cámara): el grupo puede
     * publicarse sin horario y sin profesor. Para los demás cursos, todo
     * sigue igual.
     */
    const cursoElegido = cursos.find((c) => String(c.idCurso) === form.idCurso);
    const aCoordinar = Boolean(cursoElegido?.aCoordinar ?? (editando && form.idCurso === String(grupo.idCurso) && grupo.aCoordinar));

    function limpiarError(campo) {
        setErrores((prev) => {
            if (!prev[campo]) return prev;
            const siguiente = { ...prev };
            delete siguiente[campo];
            return siguiente;
        });
    }

    function handleChange(e) {
        const { name, value } = e.target;
        setForm((prev) => ({ ...prev, [name]: name === "codigo" ? normalizarCodigoEscrito(value) : value }));
        if (name === "codigo") setCodigoEditado(value.trim() !== "");
        limpiarError(name);
    }

    function usarSugerencia() {
        if (!sugerenciaActual) return;
        setCodigoEditado(false);
        setForm((prev) => ({ ...prev, codigo: sugerenciaActual }));
        limpiarError("codigo");
    }

    function handleHorarioChange(indice, campo, valor) {
        setHorarios((prev) => prev.map((h, i) => (i === indice ? { ...h, [campo]: valor } : h)));
        limpiarError(`horario-${indice}-${campo}`);
        limpiarError("horarios");
    }

    function agregarHorario() {
        setHorarios((prev) => [...prev, { ...HORARIO_VACIO }]);
    }

    function quitarHorario(indice) {
        setHorarios((prev) => prev.filter((_, i) => i !== indice));
        // Los índices cambian: los errores de horario ya no aplican.
        setErrores((prev) => Object.fromEntries(Object.entries(prev).filter(([campo]) => !campo.startsWith("horario"))));
    }

    function validarFormulario() {
        const e = {};

        if (!form.idCurso) e.idCurso = "Seleccione el curso.";
        if (!form.idPeriodo) e.idPeriodo = "Seleccione el período académico.";

        if (!form.codigo.trim()) e.codigo = "Ingrese el código del grupo.";
        else if (form.codigo.trim().length > 40) e.codigo = "El código no puede superar los 40 caracteres.";

        const cupo = Number(form.cupoMaximo);
        if (!form.cupoMaximo) e.cupoMaximo = "Ingrese el cupo máximo.";
        else if (!Number.isInteger(cupo) || cupo <= 0) e.cupoMaximo = "El cupo debe ser un número entero mayor que cero.";
        else if (cupo > 500) e.cupoMaximo = "El cupo máximo no puede ser mayor que 500.";
        else if (editando && cupo < grupo.inscritos) e.cupoMaximo = `El cupo no puede ser menor que los ${grupo.inscritos} estudiantes inscritos.`;

        if (["PUBLICADO", "EN_CURSO"].includes(form.estado) && !form.idProfesor && !aCoordinar) {
            e.idProfesor = "Un grupo publicado o en curso necesita un profesor asignado.";
        }

        if (form.fechaInicio && form.fechaFin && form.fechaFin < form.fechaInicio) {
            e.fechaFin = "La fecha de fin no puede ser anterior a la de inicio.";
        }

        const periodo = periodos.find((p) => String(p.id) === form.idPeriodo);
        if (periodo?.fechaInicio && periodo?.fechaFin) {
            if (form.fechaInicio && (form.fechaInicio < periodo.fechaInicio || form.fechaInicio > periodo.fechaFin)) {
                e.fechaInicio = "La fecha de inicio debe estar dentro del período seleccionado.";
            }
            if (!e.fechaFin && form.fechaFin && (form.fechaFin < periodo.fechaInicio || form.fechaFin > periodo.fechaFin)) {
                e.fechaFin = "La fecha de fin debe estar dentro del período seleccionado.";
            }
        }

        if (horarios.length === 0 && !aCoordinar) e.horarios = "Agregue al menos un horario para el grupo.";

        horarios.forEach((h, i) => {
            // En un curso a coordinar, una fila sin datos no cuenta.
            if (aCoordinar && horarioVacio(h)) return;
            const n = i + 1;
            if (!h.diaSemana) e[`horario-${i}-diaSemana`] = `Horario ${n}: seleccione el día.`;

            if (!h.horaInicio) e[`horario-${i}-horaInicio`] = `Horario ${n}: ingrese la hora de inicio.`;
            else if (h.horaInicio < HORA_MINIMA || h.horaInicio >= HORA_MAXIMA) e[`horario-${i}-horaInicio`] = `Horario ${n}: la hora de inicio debe estar entre las 7:00 a. m. y las 10:00 p. m.`;

            if (!h.horaFin) e[`horario-${i}-horaFin`] = `Horario ${n}: ingrese la hora de fin.`;
            else if (h.horaInicio && h.horaFin <= h.horaInicio) e[`horario-${i}-horaFin`] = `Horario ${n}: la hora de fin debe ser posterior a la de inicio.`;
            else if (h.horaFin > HORA_MAXIMA) e[`horario-${i}-horaFin`] = `Horario ${n}: la hora de fin no puede pasar de las 10:00 p. m.`;
        });

        // Dos bloques del mismo día no pueden traslaparse.
        const completos = horarios
            .map((h, i) => ({ ...h, i }))
            .filter((h) => h.diaSemana && h.horaInicio && h.horaFin && h.horaFin > h.horaInicio)
            .sort((a, b) => a.diaSemana.localeCompare(b.diaSemana) || a.horaInicio.localeCompare(b.horaInicio));

        for (let k = 1; k < completos.length; k++) {
            const anterior = completos[k - 1];
            const actual = completos[k];
            if (anterior.diaSemana === actual.diaSemana && actual.horaInicio < anterior.horaFin) {
                e[`horario-${actual.i}-horaInicio`] = `Horario ${actual.i + 1}: se traslapa con el horario ${anterior.i + 1}.`;
            }
        }

        return e;
    }

    async function cancelar() {
        if (hayCambios && !(await confirmarDescarte())) return;
        permitirSalida();
        onCancel();
    }

    async function handleSubmit(e) {
        e.preventDefault();

        const erroresValidacion = validarFormulario();
        if (Object.keys(erroresValidacion).length > 0) {
            setErrores(erroresValidacion);
            setIntento((n) => n + 1);
            return;
        }

        if (editando && !hayCambios) {
            dialog.aviso("No hay cambios por guardar.");
            return;
        }

        setErrores({});
        const payload = {
            idCurso: Number(form.idCurso),
            idPeriodo: Number(form.idPeriodo),
            codigo: form.codigo.trim().toUpperCase(),
            cupoMaximo: Number(form.cupoMaximo),
            idProfesor: form.idProfesor ? Number(form.idProfesor) : null,
            modalidad: form.modalidad,
            aula: form.aula.trim() || null,
            fechaInicio: form.fechaInicio || null,
            fechaFin: form.fechaFin || null,
            estado: form.estado,
            horarios: horarios.filter((h) => !(aCoordinar && horarioVacio(h))).map((h) => ({
                diaSemana: Number(h.diaSemana),
                horaInicio: h.horaInicio,
                horaFin: h.horaFin,
                aula: h.aula.trim() || null,
            })),
        };

        setGuardando(true);
        try {
            const response = await fetch(`${API_URL}/grupos${editando ? `/${grupo.id}` : ""}`, {
                method: editando ? "PUT" : "POST",
                headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
                body: JSON.stringify(payload),
            });

            let data = {};
            try {
                data = await response.json();
            } catch {
                // La respuesta puede venir sin cuerpo.
            }

            if (response.status === 401) throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            if (response.status === 403) throw new Error(editando ? "No tiene permisos para modificar grupos." : "No tiene permisos para crear grupos.");

            if (!response.ok) {
                const mensaje = data.message || (editando ? "No fue posible modificar el grupo." : "No fue posible crear el grupo.");
                // El código repetido se marca en su campo.
                if (/código/i.test(mensaje)) {
                    setErrores({ codigo: mensaje });
                    setIntento((n) => n + 1);
                    return;
                }
                throw new Error(mensaje);
            }

            dialog.exito(editando
                ? `El grupo "${data.codigo || payload.codigo}" fue modificado correctamente.`
                : `El grupo "${data.codigo || payload.codigo}" fue creado correctamente.`);

            permitirSalida();
            onSuccess();
        } catch (error) {
            dialog.error(error.message || "Ocurrió un error al guardar el grupo.");
        } finally {
            setGuardando(false);
        }
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores);
    const deshabilitado = guardando || cargandoCatalogos;
    const estados = editando ? ESTADOS_GRUPO : ESTADOS_CREACION;
    const accion = editando ? "guardar" : "crear el grupo";

    return (
        <div className="curso-page">
            <h2 className="form-section-title form-titulo">
                {editando ? `Modificar grupo ${grupo.codigo}` : "Nuevo grupo"}
            </h2>

            <form onSubmit={handleSubmit} noValidate>
                {resumen.length > 0 && (
                    <Alert
                        key={intento}
                        tone="danger"
                        autoFocus
                        className="form-alert"
                        title={resumen.length === 1 ? `Revise 1 dato antes de ${accion}` : `Revise ${resumen.length} datos antes de ${accion}`}
                        errors={resumen}
                    />
                )}

                {aCoordinar && (
                    <Alert tone="info" className="form-alert" title="Curso a coordinar en Dirección">
                        Este curso se coordina en Dirección: puede publicar el grupo sin horario y sin profesor.
                    </Alert>
                )}

                {editando && grupo.inscritos > 0 && (
                    <Alert tone="info" className="form-alert" title={`${grupo.inscritos} ${grupo.inscritos === 1 ? "estudiante inscrito" : "estudiantes inscritos"}`}>
                        Los cambios de horario, aula o profesor les afectan. El cupo no puede quedar por debajo de los inscritos.
                    </Alert>
                )}

                <section className="panel">
                    <div className="panel-head">
                        <div>
                            <h2>Curso y período</h2>
                            {cursoYPeriodoFijos && (
                                <p className="panel-head-sub">
                                    <Lock size={14} strokeWidth={1.75} aria-hidden="true" /> El grupo tiene matrículas: su curso y período no se pueden cambiar.
                                </p>
                            )}
                        </div>
                    </div>
                    <div className="panel-body form-grid">
                        <Field id="idCurso" label="Curso" error={errores.idCurso} select hint="Instrumento I a XIV no aparecen aquí: se ofrecen en «Espacios de instrumento».">
                            <select id="idCurso" name="idCurso" className="sg-input sg-select" value={form.idCurso} onChange={handleChange} disabled={cargandoCatalogos || cursoYPeriodoFijos} {...aria("idCurso")}>
                                <option value="">{cargandoCatalogos ? "Cargando…" : "Seleccione un curso"}</option>
                                {cursos.map((c) => (
                                    <option key={c.idCurso} value={c.idCurso}>
                                        {c.codigo} · {c.nombre}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <Field id="idPeriodo" label="Período académico" error={errores.idPeriodo} select>
                            <select id="idPeriodo" name="idPeriodo" className="sg-input sg-select" value={form.idPeriodo} onChange={handleChange} disabled={cargandoCatalogos || cursoYPeriodoFijos} {...aria("idPeriodo")}>
                                <option value="">{cargandoCatalogos ? "Cargando…" : "Seleccione un período"}</option>
                                {periodos.map((p) => (
                                    <option key={p.id} value={p.id}>{p.nombre}</option>
                                ))}
                            </select>
                        </Field>
                    </div>
                </section>

                <section className="panel">
                    <div className="panel-head"><h2>Información del grupo</h2></div>
                    <div className="panel-body form-grid">
                        <div>
                            <Field
                                id="codigo"
                                label="Código del grupo"
                                hint={editando || codigoEditado ? "Único dentro del período." : "Se sugiere con el código del curso y la siguiente letra libre del período."}
                                error={errores.codigo}
                            >
                                <input id="codigo" name="codigo" type="text" className="sg-input tnum" maxLength="40" value={form.codigo} onChange={handleChange} placeholder={form.idCurso ? "" : "Elija primero el curso"} autoComplete="off" spellCheck={false} {...aria("codigo")} />
                            </Field>
                            {!editando && (
                                <AyudaCodigoSugerido
                                    editado={codigoEditado}
                                    codigo={form.codigo}
                                    sugerencia={sugerenciaActual}
                                    onUsarSugerencia={usarSugerencia}
                                />
                            )}
                        </div>
                        <Field id="cupoMaximo" label="Cupo máximo" hint={editando && grupo.inscritos > 0 ? `Mínimo ${grupo.inscritos} (inscritos).` : undefined} error={errores.cupoMaximo}>
                            <input id="cupoMaximo" name="cupoMaximo" type="number" min={editando ? Math.max(1, grupo.inscritos) : 1} max="500" step="1" className="sg-input" value={form.cupoMaximo} onChange={handleChange} placeholder="Ej. 15" {...aria("cupoMaximo")} />
                        </Field>
                        <Field id="idProfesor" label="Profesor" optional error={errores.idProfesor} select hint={aCoordinar ? "Se asigna por estudiante en «Cursos a coordinar»." : undefined}>
                            <select id="idProfesor" name="idProfesor" className="sg-input sg-select" value={form.idProfesor} onChange={handleChange} disabled={cargandoCatalogos} {...aria("idProfesor")}>
                                <option value="">Sin asignar</option>
                                {editando && grupo.idProfesor && !cargandoCatalogos && !profesores.some((p) => p.id === grupo.idProfesor) && (
                                    <option value={grupo.idProfesor}>{grupo.nombreProfesor} (inactivo)</option>
                                )}
                                {profesores.map((p) => (
                                    <option key={p.id} value={p.id}>{nombreProfesor(p)}</option>
                                ))}
                            </select>
                        </Field>
                        <Field id="modalidad" label="Modalidad" error={errores.modalidad} select>
                            <select id="modalidad" name="modalidad" className="sg-input sg-select" value={form.modalidad} onChange={handleChange} {...aria("modalidad")}>
                                {MODALIDADES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                            </select>
                        </Field>
                        <Field id="aula" label="Aula" optional hint="Se usa en los horarios que no indiquen otra." error={errores.aula}>
                            <input id="aula" name="aula" type="text" className="sg-input" maxLength="80" value={form.aula} onChange={handleChange} placeholder="Ej. Aula 3" autoComplete="off" {...aria("aula")} />
                        </Field>
                        <Field id="estado" label="Estado" hint={editando ? "Cerrado y Cancelado son definitivos: después ya no se puede modificar." : "Publicado: visible para la matrícula."} error={errores.estado} select>
                            <select id="estado" name="estado" className="sg-input sg-select" value={form.estado} onChange={handleChange} {...aria("estado")}>
                                {estados.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                            </select>
                        </Field>
                        <Field id="fechaInicio" label="Fecha de inicio" optional error={errores.fechaInicio}>
                            <input id="fechaInicio" name="fechaInicio" type="date" className="sg-input" value={form.fechaInicio} onChange={handleChange} {...aria("fechaInicio")} />
                        </Field>
                        <Field id="fechaFin" label="Fecha de fin" optional error={errores.fechaFin}>
                            <input id="fechaFin" name="fechaFin" type="date" className="sg-input" value={form.fechaFin} onChange={handleChange} {...aria("fechaFin")} />
                        </Field>
                    </div>
                </section>

                <section className="panel">
                    <div className="panel-head">
                        <div>
                            <h2>Horario semanal</h2>
                            <p className="panel-head-sub">
                                {aCoordinar
                                    ? "Opcional en este curso: déjelo vacío si el horario se acuerda con cada estudiante."
                                    : "Días y horas en que se reúne el grupo, entre las 7:00 a. m. y las 10:00 p. m."}
                            </p>
                        </div>
                    </div>
                    <div className="panel-body">
                        {errores.horarios && <p className="sg-field__error" id="horarios">{errores.horarios}</p>}

                        <div className="horario-lista">
                            {horarios.map((h, i) => {
                                const id = (campo) => `horario-${i}-${campo}`;
                                return (
                                    <div className="horario-fila" key={i}>
                                        <Field id={id("diaSemana")} label="Día" error={errores[id("diaSemana")]} select>
                                            <select id={id("diaSemana")} className="sg-input sg-select" value={h.diaSemana} onChange={(e) => handleHorarioChange(i, "diaSemana", e.target.value)} {...aria(id("diaSemana"))}>
                                                <option value="">Seleccione</option>
                                                {DIAS.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
                                            </select>
                                        </Field>
                                        <Field id={id("horaInicio")} label="Hora de inicio" error={errores[id("horaInicio")]}>
                                            <input id={id("horaInicio")} type="time" min={HORA_MINIMA} max={HORA_MAXIMA} className="sg-input" value={h.horaInicio} onChange={(e) => handleHorarioChange(i, "horaInicio", e.target.value)} {...aria(id("horaInicio"))} />
                                        </Field>
                                        <Field id={id("horaFin")} label="Hora de fin" error={errores[id("horaFin")]}>
                                            <input id={id("horaFin")} type="time" min={HORA_MINIMA} max={HORA_MAXIMA} className="sg-input" value={h.horaFin} onChange={(e) => handleHorarioChange(i, "horaFin", e.target.value)} {...aria(id("horaFin"))} />
                                        </Field>
                                        <Field id={id("aula")} label="Aula" optional>
                                            <input id={id("aula")} type="text" className="sg-input" maxLength="80" value={h.aula} onChange={(e) => handleHorarioChange(i, "aula", e.target.value)} placeholder={form.aula || "Igual al grupo"} autoComplete="off" />
                                        </Field>
                                        <button type="button" className="sg-icon-btn" aria-label={`Quitar horario ${i + 1}`} onClick={() => quitarHorario(i)}>
                                            <Trash2 size={18} strokeWidth={1.75} aria-hidden="true" />
                                        </button>
                                    </div>
                                );
                            })}
                        </div>

                        <button type="button" className="sg-btn sg-btn--secondary sg-btn--sm horario-agregar" onClick={agregarHorario}>
                            <Plus size={16} strokeWidth={1.75} aria-hidden="true" />
                            Agregar horario
                        </button>
                    </div>
                    <div className="panel-foot">
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={cancelar} disabled={guardando}>
                            Cancelar
                        </button>
                        <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={deshabilitado} aria-busy={guardando || undefined}>
                            {guardando
                                ? <span className="sg-spinner" aria-hidden="true" />
                                : editando ? <Save size={18} strokeWidth={1.75} aria-hidden="true" /> : <Users size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {guardando ? "Guardando…" : editando ? "Guardar cambios" : "Crear grupo"}
                        </button>
                    </div>
                </section>
            </form>
        </div>
    );
}

export default FormularioGrupo;
