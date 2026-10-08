import { useEffect, useState } from "react";
import { API_URL } from "../../config/api";
import { Plus, Trash2, Users } from "lucide-react";
import "./crearCurso.css";
import "./crearGrupo.css";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import { listaErrores } from "../../components/ui/utilidades";

import { useDialog } from "../../components/dialog/useDialog";

const MODALIDADES = [
    { value: "PRESENCIAL", label: "Presencial" },
    { value: "VIRTUAL", label: "Virtual" },
    { value: "HIBRIDA", label: "Híbrida" },
];

const ESTADOS = [
    { value: "PLANIFICADO", label: "Planificado" },
    { value: "PUBLICADO", label: "Publicado" },
];

// 1 = lunes ... 7 = domingo (igual que la tabla horarios_grupos).
const DIAS = [
    { value: "1", label: "Lunes" },
    { value: "2", label: "Martes" },
    { value: "3", label: "Miércoles" },
    { value: "4", label: "Jueves" },
    { value: "5", label: "Viernes" },
    { value: "6", label: "Sábado" },
    { value: "7", label: "Domingo" },
];

const HORARIO_VACIO = { diaSemana: "", horaInicio: "", horaFin: "", aula: "" };

const FORM_INICIAL = {
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

function nombreProfesor(p) {
    return [p.nombres, p.primerApellido, p.segundoApellido]
        .filter(Boolean)
        .join(" ");
}

function CrearGrupo() {
    const dialog = useDialog();

    const token = localStorage.getItem("token");

    const [form, setForm] = useState(FORM_INICIAL);
    const [horarios, setHorarios] = useState([{ ...HORARIO_VACIO }]);

    const [cursos, setCursos] = useState([]);
    const [periodos, setPeriodos] = useState([]);
    const [profesores, setProfesores] = useState([]);

    const [cargandoCatalogos, setCargandoCatalogos] = useState(true);
    const [guardando, setGuardando] = useState(false);

    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);

    useEffect(() => {

        async function cargarCatalogos() {

            setCargandoCatalogos(true);

            try {

                const headers = {
                    Authorization: `Bearer ${token}`,
                };

                const respuestas = await Promise.all([
                    fetch(`${API_URL}/cursos`, { headers }),
                    fetch(`${API_URL}/periodos-academicos`, { headers }),
                    fetch(`${API_URL}/profesores?activo=true`, { headers }),
                ]);

                if (respuestas.some((r) => r.status === 401)) {
                    throw new Error(
                        "La sesión ha expirado. Inicie sesión nuevamente."
                    );
                }

                if (respuestas.some((r) => r.status === 403)) {
                    throw new Error(
                        "No tiene permisos para consultar la información académica."
                    );
                }

                const [cursosResponse, periodosResponse, profesoresResponse] = respuestas;

                if (!cursosResponse.ok) {
                    throw new Error("No se pudieron cargar los cursos.");
                }

                if (!periodosResponse.ok) {
                    throw new Error("No se pudieron cargar los períodos académicos.");
                }

                if (!profesoresResponse.ok) {
                    throw new Error("No se pudieron cargar los profesores.");
                }

                setCursos(await cursosResponse.json());
                setPeriodos(await periodosResponse.json());
                setProfesores(await profesoresResponse.json());

            } catch (e) {

                dialog.error(
                    e.message ||
                    "No fue posible cargar la información necesaria."
                );

            } finally {

                setCargandoCatalogos(false);
            }
        }

        cargarCatalogos();

    }, [token, dialog]);

    function limpiarError(campo) {
        setErrores((prev) => {
            if (!prev[campo]) {
                return prev;
            }

            const siguiente = { ...prev };
            delete siguiente[campo];
            return siguiente;
        });
    }

    function handleChange(e) {

        const { name, value } = e.target;

        setForm((prev) => ({
            ...prev,
            [name]: value,
        }));

        // Limpiar el error del campo que el usuario está corrigiendo.
        limpiarError(name);
    }

    function handleHorarioChange(indice, campo, valor) {

        setHorarios((prev) =>
            prev.map((h, i) => (i === indice ? { ...h, [campo]: valor } : h))
        );

        limpiarError(`horario-${indice}-${campo}`);
        limpiarError("horarios");
    }

    function agregarHorario() {
        setHorarios((prev) => [...prev, { ...HORARIO_VACIO }]);
    }

    function quitarHorario(indice) {
        setHorarios((prev) => prev.filter((_, i) => i !== indice));

        // Los índices cambian: los errores de horario ya no aplican.
        setErrores((prev) =>
            Object.fromEntries(
                Object.entries(prev).filter(([campo]) => !campo.startsWith("horario"))
            )
        );
    }

    function validarFormulario() {
        const siguientesErrores = {};

        if (!form.idCurso) {
            siguientesErrores.idCurso = "Seleccione el curso.";
        }

        if (!form.idPeriodo) {
            siguientesErrores.idPeriodo = "Seleccione el período académico.";
        }

        if (!form.codigo.trim()) {
            siguientesErrores.codigo = "Ingrese el código del grupo.";
        } else if (form.codigo.trim().length > 40) {
            siguientesErrores.codigo = "El código no puede superar los 40 caracteres.";
        }

        const cupo = Number(form.cupoMaximo);

        if (!form.cupoMaximo) {
            siguientesErrores.cupoMaximo = "Ingrese el cupo máximo.";
        } else if (!Number.isInteger(cupo) || cupo <= 0) {
            siguientesErrores.cupoMaximo = "El cupo debe ser un número entero mayor que cero.";
        } else if (cupo > 32767) {
            siguientesErrores.cupoMaximo = "El cupo supera el máximo permitido.";
        }

        if (form.fechaInicio && form.fechaFin && form.fechaFin < form.fechaInicio) {
            siguientesErrores.fechaFin = "La fecha de fin no puede ser anterior a la de inicio.";
        }

        const periodo = periodos.find((p) => String(p.id) === form.idPeriodo);

        if (periodo) {
            if (form.fechaInicio && (form.fechaInicio < periodo.fechaInicio || form.fechaInicio > periodo.fechaFin)) {
                siguientesErrores.fechaInicio = "La fecha de inicio debe estar dentro del período seleccionado.";
            }

            if (!siguientesErrores.fechaFin && form.fechaFin && (form.fechaFin < periodo.fechaInicio || form.fechaFin > periodo.fechaFin)) {
                siguientesErrores.fechaFin = "La fecha de fin debe estar dentro del período seleccionado.";
            }
        }

        if (horarios.length === 0) {
            siguientesErrores.horarios = "Agregue al menos un horario para el grupo.";
        }

        horarios.forEach((h, i) => {
            const numero = i + 1;

            if (!h.diaSemana) {
                siguientesErrores[`horario-${i}-diaSemana`] = `Horario ${numero}: seleccione el día.`;
            }

            if (!h.horaInicio) {
                siguientesErrores[`horario-${i}-horaInicio`] = `Horario ${numero}: ingrese la hora de inicio.`;
            }

            if (!h.horaFin) {
                siguientesErrores[`horario-${i}-horaFin`] = `Horario ${numero}: ingrese la hora de fin.`;
            } else if (h.horaInicio && h.horaFin <= h.horaInicio) {
                siguientesErrores[`horario-${i}-horaFin`] = `Horario ${numero}: la hora de fin debe ser posterior a la de inicio.`;
            }
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
                siguientesErrores[`horario-${actual.i}-horaInicio`] =
                    `Horario ${actual.i + 1}: se traslapa con el horario ${anterior.i + 1}.`;
            }
        }

        return siguientesErrores;
    }

    async function handleSubmit(e) {

        e.preventDefault();

        const erroresValidacion = validarFormulario();

        if (Object.keys(erroresValidacion).length > 0) {
            setErrores(erroresValidacion);
            setIntento(n => n + 1);
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
            horarios: horarios.map((h) => ({
                diaSemana: Number(h.diaSemana),
                horaInicio: h.horaInicio,
                horaFin: h.horaFin,
                aula: h.aula.trim() || null,
            })),
        };

        setGuardando(true);

        try {

            const response = await fetch(`${API_URL}/grupos`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify(payload),
            });

            let data = {};

            try {
                data = await response.json();
            } catch {
                // La respuesta puede venir sin cuerpo.
            }

            if (response.status === 401) {
                throw new Error(
                    "La sesión ha expirado. Inicie sesión nuevamente."
                );
            }

            if (response.status === 403) {
                throw new Error(
                    "No tiene permisos para crear grupos."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.message ||
                    "No fue posible crear el grupo."
                );
            }

            dialog.exito(
                `El grupo "${data.codigo || payload.codigo}" fue creado correctamente.`
            );

            limpiarFormulario();

        } catch (e) {

            dialog.error(
                e.message ||
                "Ocurrió un error al crear el grupo."
            );

        } finally {

            setGuardando(false);
        }
    }

    function limpiarFormulario() {
        setForm(FORM_INICIAL);
        setHorarios([{ ...HORARIO_VACIO }]);
        setErrores({});
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores);
    const deshabilitado = guardando || cargandoCatalogos;

    return (
        <div className="curso-page">
            <PageHead
                title="Crear grupo"
                sub="Abra un grupo de un curso en un período académico: cupo, profesor, modalidad y horario semanal."
            />

            <form onSubmit={handleSubmit} noValidate>
                {resumen.length > 0 && (
                    <Alert
                        key={intento}
                        tone="danger"
                        autoFocus
                        className="form-alert"
                        title={resumen.length === 1 ? "Revise 1 dato antes de crear el grupo" : `Revise ${resumen.length} datos antes de crear el grupo`}
                        errors={resumen}
                    />
                )}

                <section className="panel">
                    <div className="panel-head"><h2>Curso y período</h2></div>
                    <div className="panel-body form-grid">
                        <Field id="idCurso" label="Curso" error={errores.idCurso} select>
                            <select id="idCurso" name="idCurso" className="sg-input sg-select" value={form.idCurso} onChange={handleChange} disabled={cargandoCatalogos} {...aria("idCurso")}>
                                <option value="">{cargandoCatalogos ? "Cargando…" : "Seleccione un curso"}</option>
                                {cursos.map((c) => (
                                    <option key={c.idCurso} value={c.idCurso}>
                                        {c.codigo} · {c.nombre}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <Field id="idPeriodo" label="Período académico" error={errores.idPeriodo} select>
                            <select id="idPeriodo" name="idPeriodo" className="sg-input sg-select" value={form.idPeriodo} onChange={handleChange} disabled={cargandoCatalogos} {...aria("idPeriodo")}>
                                <option value="">{cargandoCatalogos ? "Cargando…" : "Seleccione un período"}</option>
                                {periodos.map((p) => (
                                    <option key={p.id} value={p.id}>
                                        {p.nombre}
                                    </option>
                                ))}
                            </select>
                        </Field>
                    </div>
                </section>

                <section className="panel">
                    <div className="panel-head"><h2>Información del grupo</h2></div>
                    <div className="panel-body form-grid">
                        <Field id="codigo" label="Código del grupo" hint="Único dentro del período." error={errores.codigo}>
                            <input id="codigo" name="codigo" type="text" className="sg-input" maxLength="40" value={form.codigo} onChange={handleChange} placeholder="Ej. PIANO1-B" autoComplete="off" {...aria("codigo")} />
                        </Field>
                        <Field id="cupoMaximo" label="Cupo máximo" error={errores.cupoMaximo}>
                            <input id="cupoMaximo" name="cupoMaximo" type="number" min="1" step="1" className="sg-input" value={form.cupoMaximo} onChange={handleChange} placeholder="Ej. 15" {...aria("cupoMaximo")} />
                        </Field>
                        <Field id="idProfesor" label="Profesor" optional error={errores.idProfesor} select>
                            <select id="idProfesor" name="idProfesor" className="sg-input sg-select" value={form.idProfesor} onChange={handleChange} disabled={cargandoCatalogos} {...aria("idProfesor")}>
                                <option value="">Sin asignar</option>
                                {profesores.map((p) => (
                                    <option key={p.id} value={p.id}>
                                        {nombreProfesor(p)}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <Field id="modalidad" label="Modalidad" error={errores.modalidad} select>
                            <select id="modalidad" name="modalidad" className="sg-input sg-select" value={form.modalidad} onChange={handleChange} {...aria("modalidad")}>
                                {MODALIDADES.map((opcion) => (
                                    <option key={opcion.value} value={opcion.value}>
                                        {opcion.label}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <Field id="aula" label="Aula" optional hint="Se usa en los horarios que no indiquen otra." error={errores.aula}>
                            <input id="aula" name="aula" type="text" className="sg-input" maxLength="80" value={form.aula} onChange={handleChange} placeholder="Ej. Aula 3" autoComplete="off" {...aria("aula")} />
                        </Field>
                        <Field id="estado" label="Estado" hint="Publicado: visible para la matrícula." error={errores.estado} select>
                            <select id="estado" name="estado" className="sg-input sg-select" value={form.estado} onChange={handleChange} {...aria("estado")}>
                                {ESTADOS.map((opcion) => (
                                    <option key={opcion.value} value={opcion.value}>
                                        {opcion.label}
                                    </option>
                                ))}
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
                            <p className="panel-head-sub">Días y horas en que se reúne el grupo.</p>
                        </div>
                    </div>
                    <div className="panel-body">
                        {errores.horarios && (
                            <p className="sg-field__error" id="horarios">{errores.horarios}</p>
                        )}

                        <div className="horario-lista">
                            {horarios.map((h, i) => {
                                const id = (campo) => `horario-${i}-${campo}`;

                                return (
                                    <div className="horario-fila" key={i}>
                                        <Field id={id("diaSemana")} label="Día" error={errores[id("diaSemana")]} select>
                                            <select id={id("diaSemana")} className="sg-input sg-select" value={h.diaSemana} onChange={(e) => handleHorarioChange(i, "diaSemana", e.target.value)} {...aria(id("diaSemana"))}>
                                                <option value="">Seleccione</option>
                                                {DIAS.map((d) => (
                                                    <option key={d.value} value={d.value}>{d.label}</option>
                                                ))}
                                            </select>
                                        </Field>
                                        <Field id={id("horaInicio")} label="Hora de inicio" error={errores[id("horaInicio")]}>
                                            <input id={id("horaInicio")} type="time" className="sg-input" value={h.horaInicio} onChange={(e) => handleHorarioChange(i, "horaInicio", e.target.value)} {...aria(id("horaInicio"))} />
                                        </Field>
                                        <Field id={id("horaFin")} label="Hora de fin" error={errores[id("horaFin")]}>
                                            <input id={id("horaFin")} type="time" className="sg-input" value={h.horaFin} onChange={(e) => handleHorarioChange(i, "horaFin", e.target.value)} {...aria(id("horaFin"))} />
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
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={limpiarFormulario} disabled={guardando}>
                            Limpiar
                        </button>
                        <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={deshabilitado} aria-busy={guardando || undefined}>
                            {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <Users size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {guardando ? "Creando grupo…" : "Crear grupo"}
                        </button>
                    </div>
                </section>
            </form>
        </div>
    );
}

export default CrearGrupo;
