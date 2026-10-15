import RequisitosCursoOpcionales from "./RequisitosCursoOpcionales";
import { useEffect, useMemo, useState } from "react";
import { RotateCcw, Save } from "lucide-react";

import { API_URL } from "../../config/api";
import "./editarCurso.css";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import StatusBadge from "../../components/ui/StatusBadge";
import { listaErrores } from "../../components/ui/utilidades";

import { useDialog } from "../../components/dialog/useDialog";
import { useAvisoCambiosSinGuardar } from "../../hooks/useAvisoCambiosSinGuardar";

function construirFormDesdeCurso(curso) {
    return {
        codigo: curso?.codigo ?? "",
        nombre: curso?.nombre ?? "",
        descripcion: curso?.descripcion ?? "",
        horasTotales:
            curso?.horasTotales != null
                ? String(curso.horasTotales)
                : "",
        idPrograma:
            curso?.idPrograma != null
                ? String(curso.idPrograma)
                : "",
        nivel:
            curso?.nivel != null
                ? String(curso.nivel)
                : "",
    };
}

function normalizarForm(form) {
    return {
        nombre: form.nombre.trim(),
        descripcion: form.descripcion.trim(),
        horasTotales: Number(form.horasTotales),
        idPrograma: Number(form.idPrograma),
        nivel: Number(form.nivel),
        requisitos: form.requisitos,
    };
}

async function leerJsonSeguro(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}

function mensajeDeRespuesta(data, mensajePredeterminado) {
    return (
        data?.message ||
        data?.mensaje ||
        mensajePredeterminado
    );
}

/*
 * HU-030 - Modificar un curso de la malla curricular. Se abre dentro de
 * la pantalla Cursos (botón "Modificar" de la lista).
 */
function EditarCurso({ idCurso, onCancel, onSuccess }) {
    const dialog = useDialog();

    const token = localStorage.getItem("token");
    const [revisionRequisitos, setRevisionRequisitos] = useState(0);

    const [programas, setProgramas] = useState([]);

    const [form, setForm] = useState(construirFormDesdeCurso(null));
    const [formInicial, setFormInicial] = useState(null);

    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);

    const [cargando, setCargando] = useState(true);
    const [guardando, setGuardando] = useState(false);

    // Carga el curso (con su información completa para editar) y los programas.
    useEffect(() => {
        let activo = true;
        const headers = { Authorization: `Bearer ${token}` };

        async function cargar() {
            const [cursoResponse, programasResponse] = await Promise.all([
                fetch(`${API_URL}/cursos/${idCurso}`, { headers }),
                fetch(`${API_URL}/programas`, { headers }),
            ]);

            if (cursoResponse.status === 401 || programasResponse.status === 401) {
                throw new Error("La sesión ha expirado. Inicie sesión nuevamente.");
            }

            if (cursoResponse.status === 403 || programasResponse.status === 403) {
                throw new Error("No tiene permisos para modificar cursos.");
            }

            const curso = await leerJsonSeguro(cursoResponse);
            if (!cursoResponse.ok) {
                throw new Error(mensajeDeRespuesta(curso, "No fue posible cargar la información del curso."));
            }

            const programasData = await leerJsonSeguro(programasResponse);
            if (!programasResponse.ok) {
                throw new Error(mensajeDeRespuesta(programasData, "No fue posible cargar los programas académicos."));
            }

            return { curso, programasData };
        }

        cargar()
            .then(({ curso, programasData }) => {
                if (!activo) return;
                const siguienteForm = construirFormDesdeCurso(curso);
                setProgramas(programasData);
                setForm(siguienteForm);
                setFormInicial(siguienteForm);
            })
            .catch((e) => {
                if (!activo) return;
                dialog.error(e.message || "Ocurrió un error al cargar el curso.");
                onCancel();
            })
            .finally(() => { if (activo) setCargando(false); });

        return () => { activo = false; };
        // Solo al abrir el curso; onCancel cambia en cada render de la lista.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [idCurso, token, dialog]);

    const hayCambios = useMemo(() => {

        if (!formInicial) {
            return false;
        }

        const actual =
            normalizarForm(form);

        const inicial =
            normalizarForm(formInicial);

        return (
            actual.nombre !== inicial.nombre ||
            actual.descripcion !== inicial.descripcion ||
            actual.horasTotales !== inicial.horasTotales ||
            actual.idPrograma !== inicial.idPrograma ||
            actual.nivel !== inicial.nivel ||
            JSON.stringify(actual.requisitos) !== JSON.stringify(inicial.requisitos)
        );

    }, [form, formInicial]);

    // Avisa al salir de la pantalla o cerrar la pestaña con cambios pendientes
    const { confirmarDescarte, permitirSalida } = useAvisoCambiosSinGuardar(hayCambios);

    const camposModificados = useMemo(() => {

        if (!formInicial) {
            return [];
        }

        const actual =
            normalizarForm(form);

        const inicial =
            normalizarForm(formInicial);

        const cambios = [];

        if (actual.nombre !== inicial.nombre) {
            cambios.push("Nombre");
        }

        if (
            actual.descripcion !==
            inicial.descripcion
        ) {
            cambios.push("Descripción");
        }

        if (
            actual.horasTotales !==
            inicial.horasTotales
        ) {
            cambios.push("Cantidad de horas");
        }

        if (
            actual.idPrograma !==
            inicial.idPrograma
        ) {
            cambios.push("Programa académico");
        }

        if (
            actual.nivel !==
            inicial.nivel
        ) {
            cambios.push("Nivel académico");
        }

        if (JSON.stringify(actual.requisitos) !== JSON.stringify(inicial.requisitos)) cambios.push("Requisitos del curso");
        return cambios;

    }, [form, formInicial]);

    function handleChange(e) {

        const { name, value } =
            e.target;

        setForm((prev) => ({
            ...prev,
            [name]: value,
            ...(name === "idPrograma" ? { requisitos: undefined } : {}),
        }));

        setErrores((prev) => {

            if (!prev[name]) {
                return prev;
            }

            const siguientes = {
                ...prev,
            };

            delete siguientes[name];

            return siguientes;
        });
    }

    function validarFormulario() {

        const siguientesErrores = {};

        if (!form.nombre.trim()) {
            siguientesErrores.nombre =
                "Ingrese el nombre del curso.";
        } else if (
            form.nombre.trim().length > 160
        ) {
            siguientesErrores.nombre =
                "El nombre del curso no puede superar los 160 caracteres.";
        }

        if (
            !form.horasTotales ||
            Number(form.horasTotales) <= 0
        ) {
            siguientesErrores.horasTotales =
                "La cantidad de horas debe ser mayor que cero.";
        } else if (
            Number(form.horasTotales) > 65535
        ) {
            siguientesErrores.horasTotales =
                "La cantidad de horas supera el máximo permitido.";
        }

        if (!form.idPrograma) {
            siguientesErrores.idPrograma =
                "Seleccione un programa académico.";
        }

        if (
            !form.nivel ||
            Number(form.nivel) <= 0
        ) {
            siguientesErrores.nivel =
                "El nivel académico debe ser mayor que cero.";
        } else if (Number(form.nivel) > 999) {
            siguientesErrores.nivel =
                "El nivel académico no puede ser mayor que 999.";
        }

        if (form.descripcion.length > 2000) {
            siguientesErrores.descripcion =
                "La descripción no puede superar los 2000 caracteres.";
        }

        return siguientesErrores;
    }

    async function solicitarGuardado(e) {

        e.preventDefault();

        const erroresValidacion =
            validarFormulario();

        if (
            Object.keys(
                erroresValidacion
            ).length > 0
        ) {
            setErrores(
                erroresValidacion
            );

            setIntento(n => n + 1);

            return;
        }

        if (!hayCambios) {
            dialog.error(
                "No hay cambios pendientes por guardar."
            );
            return;
        }

        setErrores({});

        const confirmado = await dialog.confirmar(
            `Se actualizará el curso ${form.nombre} (${form.codigo}).\n\nEsta operación no elimina ni modifica matrículas históricas registradas para el curso.\n\nCampos que cambiarán:`,
            {
                titulo: "Confirmar modificación",
                textoAceptar: "Confirmar cambios",
                textoCancelar: "Revisar",
                lista: camposModificados,
            }
        );

        if (confirmado) {
            await confirmarGuardado();
        }
    }

    async function confirmarGuardado() {

        setGuardando(true);

        const payload =
            normalizarForm(form);

        try {

            const response = await fetch(
                `${API_URL}/cursos/${idCurso}`,
                {
                    method: "PUT",
                    headers: {
                        "Content-Type":
                            "application/json",
                        Authorization:
                            `Bearer ${token}`,
                    },
                    body:
                        JSON.stringify(
                            payload
                        ),
                }
            );

            const data =
                await leerJsonSeguro(response);

            if (response.status === 401) {
                throw new Error(
                    "La sesión ha expirado. Inicie sesión nuevamente."
                );
            }

            if (response.status === 403) {
                throw new Error(
                    "No tiene permisos para modificar cursos."
                );
            }

            if (!response.ok) {

                if (
                    data.fields &&
                    typeof data.fields === "object"
                ) {
                    setErrores(
                        data.fields
                    );
                }

                throw new Error(
                    mensajeDeRespuesta(
                        data,
                        "No fue posible modificar el curso."
                    )
                );
            }

            dialog.exito(
                `El curso ${data.codigo || form.codigo} fue modificado correctamente. Los cambios quedaron registrados sin alterar la información histórica de matrícula.`
            );

            permitirSalida();
            onSuccess();

        } catch (e) {

            dialog.error(
                e.message ||
                "Ocurrió un error al modificar el curso."
            );

        } finally {

            setGuardando(false);
        }
    }

    async function cancelar() {
        if (hayCambios && !(await confirmarDescarte())) return;
        permitirSalida();
        onCancel();
    }

    function restaurarValores() {
        setRevisionRequisitos(n => n + 1);

        if (!formInicial) {
            return;
        }

        setForm({
            ...formInicial,
        });

        setErrores({});
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores);

    return (
        <div className="modificar-curso">
            <h2 className="form-section-title form-titulo">Modificar curso</h2>

            {cargando ? (
                <Alert tone="info" title="Cargando información del curso..." />
            ) : (
                <form onSubmit={solicitarGuardado} noValidate>
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

                    <section className="panel" aria-labelledby="editar-curso-titulo">
                        <div className="panel-head">
                            <div>
                                <h2 id="editar-curso-titulo">Datos del curso</h2>
                                <p className="panel-head-sub">Los campos muestran la información actualmente registrada.</p>
                            </div>
                            <StatusBadge tone="primary" icon={null}>{form.codigo}</StatusBadge>
                        </div>

                        <div className="panel-body form-grid">
                            <Field id="codigo" label="Código" hint="El código identifica al curso y no puede modificarse.">
                                <input id="codigo" name="codigo" type="text" className="sg-input" value={form.codigo} readOnly aria-readonly="true" aria-describedby="codigo-hint" />
                            </Field>
                            <Field id="nombre" label="Nombre del curso" error={errores.nombre}>
                                <input id="nombre" name="nombre" type="text" className="sg-input" maxLength="160" value={form.nombre} onChange={handleChange} disabled={guardando} {...aria("nombre")} />
                            </Field>
                            <Field id="descripcion" label="Descripción" optional className="span-all" hint="Máximo 2000 caracteres." error={errores.descripcion}>
                                <textarea id="descripcion" name="descripcion" className="sg-input" rows="4" maxLength={2000} value={form.descripcion} onChange={handleChange} disabled={guardando} placeholder="Descripción del curso" />
                            </Field>
                            <Field id="horasTotales" label="Cantidad de horas" error={errores.horasTotales}>
                                <input id="horasTotales" name="horasTotales" type="number" className="sg-input" min="1" max="65535" step="1" inputMode="numeric" value={form.horasTotales} onChange={handleChange} disabled={guardando} {...aria("horasTotales")} />
                            </Field>
                            <Field id="nivel" label="Nivel académico" error={errores.nivel}>
                                <input id="nivel" name="nivel" type="number" className="sg-input" min="1" max="999" step="1" inputMode="numeric" value={form.nivel} onChange={handleChange} disabled={guardando} {...aria("nivel")} />
                            </Field>
                            <Field
                                id="idPrograma"
                                label="Programa académico"
                                hint="Si cambia el programa, el sistema conservará la relación histórica anterior y asociará el curso al plan vigente del nuevo programa."
                                error={errores.idPrograma}
                                className="span-all"
                                select
                            >
                                <select id="idPrograma" name="idPrograma" className="sg-input sg-select" value={form.idPrograma} onChange={handleChange} disabled={guardando} {...aria("idPrograma")}>
                                    <option value="">Seleccione un programa</option>
                                    {programas.map((programa) => (
                                        <option key={programa.id} value={programa.id}>
                                            {programa.nombre}
                                            {programa.codigo ? ` (${programa.codigo})` : ""}
                                        </option>
                                    ))}
                                </select>
                            </Field>
                        </div>
                    </section>

                    <RequisitosCursoOpcionales
                        key={idCurso + ':' + form.idPrograma + ':' + revisionRequisitos}
                        idCurso={idCurso} idPrograma={form.idPrograma}
                        value={form.requisitos}
                        onChange={requisitos => setForm(prev => ({ ...prev, requisitos }))}
                        disabled={guardando}
                    />

                    <div className="panel sticky-save">
                        <span className={hayCambios ? "dirty" : "muted guardado-estado"} aria-live="polite">
                            {hayCambios && <i aria-hidden="true" />}
                            {hayCambios
                                ? `${camposModificados.length} campo${camposModificados.length === 1 ? "" : "s"} con cambios pendientes`
                                : "No hay cambios pendientes."}
                        </span>
                        <div className="inline-actions">
                            <button type="button" className="sg-btn sg-btn--ghost" onClick={cancelar} disabled={guardando}>
                                Cancelar
                            </button>
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={restaurarValores} disabled={guardando || !hayCambios}>
                                <RotateCcw size={18} strokeWidth={1.75} aria-hidden="true" />
                                Restaurar valores
                            </button>
                            <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando || !hayCambios} aria-busy={guardando || undefined}>
                                {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <Save size={18} strokeWidth={1.75} aria-hidden="true" />}
                                {guardando ? "Guardando…" : "Guardar cambios"}
                            </button>
                        </div>
                    </div>
                </form>
            )}
        </div>
    );
}

export default EditarCurso;
