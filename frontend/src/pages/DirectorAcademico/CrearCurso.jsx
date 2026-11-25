import RequisitosCursoOpcionales from "./RequisitosCursoOpcionales";
import CasillaCursoInstrumento from "./CasillaCursoInstrumento";
import CasillaCursoACoordinar from "./CasillaCursoACoordinar";
import CamposEdadCurso from "./CamposEdadCurso";
import { edadParaEnviar, erroresEdadCurso } from "./quienesPueden";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { API_URL } from "../../config/api";
import { BookPlus, CalendarRange } from "lucide-react";
import "./crearCurso.css";
import Alert from "../../components/ui/Alert";
import { AyudaCodigoSugerido, EstadoDisponibilidad } from "../../components/ui/CodigoSugerido";
import Field from "../../components/ui/Field";
import { listaErrores } from "../../components/ui/utilidades";
import { useDisponibilidad } from "../../hooks/useDisponibilidad";
import { normalizarCodigoEscrito, sugerirCodigo } from "../../utils/codigos";

import { useDialog } from "../../components/dialog/useDialog";
import { formularioModificado, useAvisoCambiosSinGuardar } from "../../hooks/useAvisoCambiosSinGuardar";

const FORM_VACIO = {
    codigo: "",
    nombre: "",
    descripcion: "",
    horasTotales: "",
    idPrograma: "",
    nivel: "",
    esCursoInstrumento: false,
    // Música de Cámara: horario y profesor se coordinan en Dirección.
    aCoordinar: false,
    // D7: opcionales (vacío = sin límite).
    edadMinima: "",
    edadMaxima: "",
};

/*
 * HU-028 - Crear curso. Se abre dentro de la pantalla Cursos
 * (botón "Nuevo curso").
 */
function CrearCurso({ onCancel, onSuccess, idProgramaInicial }) {
    const dialog = useDialog();

    const token = localStorage.getItem("token");

    // idProgramaInicial: viene de «Crear un curso en este programa» (Programas académicos).
    const [formInicial] = useState(() => ({
        ...FORM_VACIO,
        idPrograma: idProgramaInicial ? String(idProgramaInicial) : "",
    }));

    const [form, setForm] = useState(formInicial);

    const navigate = useNavigate();

    const [programas, setProgramas] = useState([]);

    const [cargandoCatalogos, setCargandoCatalogos] = useState(true);
    const [guardando, setGuardando] = useState(false);

    // Avisa al salir con datos escritos que no se han guardado
    const hayCambios = formularioModificado(form, formInicial);
    // Código sugerido a partir del nombre y verificado en vivo.
    const [codigoEditado, setCodigoEditado] = useState(false);
    const disponibilidadCodigo = useDisponibilidad(form.codigo, (codigo, signal) =>
        fetch(`${API_URL}/cursos/disponibilidad?${new URLSearchParams({ codigo })}`, {
            headers: { Authorization: `Bearer ${token}` },
            signal,
        }).then((r) => (r.ok ? r.json() : null)).then((d) => d?.codigoDisponible)
    );

    function limpiarErrorCodigo() {
        setErrores((prev) => {
            if (!prev.codigo) return prev;
            const siguiente = { ...prev };
            delete siguiente.codigo;
            return siguiente;
        });
    }

    function usarSugerencia() {
        setCodigoEditado(false);
        setForm((prev) => ({ ...prev, codigo: sugerirCodigo(prev.nombre) }));
        limpiarErrorCodigo();
    }
    const { confirmarDescarte, permitirSalida } = useAvisoCambiosSinGuardar(hayCambios);

    async function cancelar() {
        if (hayCambios && !(await confirmarDescarte())) return;
        permitirSalida();
        onCancel();
    }

    const [errores, setErrores] = useState({});
    const [intento, setIntento] = useState(0);


    useEffect(() => {

        async function cargarCatalogos() {

            setCargandoCatalogos(true);

            try {

                const headers = {
                    Authorization: `Bearer ${token}`,
                };

                const programasResponse = await fetch(`${API_URL}/programas`, {
                    headers,
                });

                if (programasResponse.status === 401) {
                    throw new Error(
                        "La sesión ha expirado. Inicie sesión nuevamente."
                    );
                }

                if (programasResponse.status === 403) {
                    throw new Error(
                        "No tiene permisos para consultar la información académica."
                    );
                }

                if (!programasResponse.ok) {
                    throw new Error(
                        "No se pudieron cargar los programas académicos."
                    );
                }

                setProgramas(await programasResponse.json());

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

    function handleChange(e) {

        const { name, value } = e.target;

        setForm((prev) => ({
            ...prev,
            [name]: name === "codigo" ? normalizarCodigoEscrito(value) : value,
            ...(name === "idPrograma" ? { requisitos: undefined } : {}),
            // Mientras no se escriba un código propio, se sugiere desde el nombre.
            ...(name === "nombre" && !codigoEditado ? { codigo: sugerirCodigo(value) } : {}),
        }));

        if (name === "codigo") setCodigoEditado(value.trim() !== "");
        if (name === "nombre" && !codigoEditado) limpiarErrorCodigo();

        // Limpiar el error del campo que el usuario está corrigiendo.
        setErrores((prev) => {
            if (!prev[name]) {
                return prev;
            }

            const siguiente = { ...prev };
            delete siguiente[name];
            return siguiente;
        });
    }

    function validarFormulario() {
        const siguientesErrores = {};

        if (!form.codigo.trim()) {
            siguientesErrores.codigo = "Ingrese el código del curso.";
        } else if (form.codigo.trim().length > 30) {
            siguientesErrores.codigo = "El código del curso no puede superar los 30 caracteres.";
        } else if (disponibilidadCodigo === "ocupado") {
            siguientesErrores.codigo = "Ya existe un curso con este código.";
        }

        if (!form.nombre.trim()) {
            siguientesErrores.nombre = "Ingrese el nombre del curso.";
        } else if (form.nombre.trim().length > 160) {
            siguientesErrores.nombre = "El nombre del curso no puede superar los 160 caracteres.";
        }

        if (!form.horasTotales || Number(form.horasTotales) <= 0) {
            siguientesErrores.horasTotales = "La cantidad de horas debe ser mayor que cero.";
        } else if (Number(form.horasTotales) > 65535) {
            siguientesErrores.horasTotales = "La cantidad de horas no puede ser mayor que 65535.";
        }

        if (form.descripcion.length > 2000) {
            siguientesErrores.descripcion = "La descripción no puede superar los 2000 caracteres.";
        }

        if (!form.idPrograma) {
            siguientesErrores.idPrograma = "Seleccione un programa académico.";
        }

        if (!form.nivel || Number(form.nivel) <= 0) {
            siguientesErrores.nivel = "El nivel académico debe ser mayor que cero.";
        } else if (Number(form.nivel) > 999) {
            siguientesErrores.nivel = "El nivel académico no puede ser mayor que 999.";
        }

        Object.assign(siguientesErrores, erroresEdadCurso(form));

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
            codigo: form.codigo.trim(),
            nombre: form.nombre.trim(),
            descripcion: form.descripcion.trim(),
            horasTotales: Number(form.horasTotales),
            idPrograma: Number(form.idPrograma),
            nivel: Number(form.nivel),
            esCursoInstrumento: form.esCursoInstrumento,
            aCoordinar: form.aCoordinar,
            edadMinima: edadParaEnviar(form.edadMinima),
            edadMaxima: edadParaEnviar(form.edadMaxima),
            requisitos: form.requisitos,
        };

        setGuardando(true);

        try {

            const response = await fetch(`${API_URL}/cursos`, {
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
                    "No tiene permisos para crear cursos."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.message ||
                    "No fue posible crear el curso."
                );
            }

            permitirSalida();

            // El curso no crea grupos: se ofrece abrir el primero ahora.
            const abrirGrupo = await dialog.confirmar(
                `El curso ${data.codigo || payload.codigo} fue creado y agregado a la malla curricular. ` +
                "Para ofrecerlo en un período, ábrale un grupo con su cupo, profesor y horario.",
                {
                    titulo: "Curso creado",
                    textoAceptar: "Abrir un grupo ahora",
                    textoCancelar: "Más tarde",
                }
            );

            if (abrirGrupo && data.idCurso) {
                navigate("/director-academico/grupos", { state: { nuevoGrupoIdCurso: data.idCurso } });
                return;
            }

            onSuccess();

        } catch (e) {

            dialog.error(
                e.message ||
                "Ocurrió un error al crear el curso."
            );

        } finally {

            setGuardando(false);
        }
    }

    const aria = (campo) => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `${campo}-error` : undefined,
    });
    const resumen = listaErrores(errores);

    return (
        <div className="curso-page">
            <h2 className="form-section-title form-titulo">Nuevo curso</h2>

            {cargandoCatalogos && (
                <Alert tone="info" title="Cargando información académica..." className="form-alert" />
            )}

            <form onSubmit={handleSubmit} noValidate>
                {resumen.length > 0 && (
                    <Alert
                        key={intento}
                        tone="danger"
                        autoFocus
                        className="form-alert"
                        title={resumen.length === 1 ? "Revise 1 dato antes de crear el curso" : `Revise ${resumen.length} datos antes de crear el curso`}
                        errors={resumen}
                    />
                )}

                <section className="panel">
                    <div className="panel-head"><h2>Información del curso</h2></div>
                    <div className="panel-body form-grid">
                        <Field id="nombre" label="Nombre del curso" error={errores.nombre}>
                            <input id="nombre" name="nombre" type="text" className="sg-input" maxLength="160" value={form.nombre} onChange={handleChange} placeholder="Ej. Introducción a la música" autoComplete="off" {...aria("nombre")} />
                        </Field>
                        <div>
                            <Field
                                id="codigo"
                                label="Código del curso"
                                hint={codigoEditado ? "Debe ser un código único." : "Se sugiere a partir del nombre; puede cambiarlo."}
                                error={errores.codigo}
                            >
                                <input id="codigo" name="codigo" type="text" className="sg-input tnum" maxLength="30" value={form.codigo} onChange={handleChange} placeholder="Ej. IM" autoComplete="off" spellCheck={false} {...aria("codigo")} aria-describedby={errores.codigo ? "codigo-error" : "codigo-estado"} />
                            </Field>
                            <AyudaCodigoSugerido
                                editado={codigoEditado}
                                codigo={form.codigo}
                                sugerencia={sugerirCodigo(form.nombre)}
                                onUsarSugerencia={usarSugerencia}
                            >
                                {!errores.codigo && (
                                    <EstadoDisponibilidad id="codigo-estado" estado={disponibilidadCodigo} mensajeOcupado="Ya existe un curso con este código." />
                                )}
                            </AyudaCodigoSugerido>
                        </div>
                        <Field id="descripcion" label="Descripción" optional className="span-all" hint="Máximo 2000 caracteres." error={errores.descripcion}>
                            <textarea id="descripcion" name="descripcion" className="sg-input" rows="4" maxLength={2000} value={form.descripcion} onChange={handleChange} placeholder="Describa brevemente el contenido del curso." />
                        </Field>
                        <Field id="horasTotales" label="Cantidad de horas" error={errores.horasTotales}>
                            <input id="horasTotales" name="horasTotales" type="number" className="sg-input" min="1" max="65535" step="1" inputMode="numeric" value={form.horasTotales} onChange={handleChange} placeholder="Ej. 40" {...aria("horasTotales")} />
                        </Field>
                        <Field id="nivel" label="Nivel académico" error={errores.nivel}>
                            <input id="nivel" name="nivel" type="number" className="sg-input" min="1" max="999" step="1" inputMode="numeric" value={form.nivel} onChange={handleChange} placeholder="Ej. 1" {...aria("nivel")} />
                        </Field>
                        <CamposEdadCurso form={form} errores={errores} onChange={handleChange} aria={aria} disabled={guardando} />
                        <CasillaCursoACoordinar
                            checked={form.aCoordinar}
                            onChange={(valor) => setForm((prev) => ({ ...prev, aCoordinar: valor }))}
                            disabled={guardando}
                        />
                        <CasillaCursoInstrumento
                            checked={form.esCursoInstrumento}
                            onChange={(valor) => setForm((prev) => ({ ...prev, esCursoInstrumento: valor }))}
                            disabled={guardando}
                        />
                    </div>
                </section>

                <section className="panel">
                    <div className="panel-head"><h2>Ubicación en la malla curricular</h2></div>
                    <div className="panel-body form-grid">
                        <Field id="idPrograma" label="Programa académico" error={errores.idPrograma} select>
                            <select id="idPrograma" name="idPrograma" className="sg-input sg-select" value={form.idPrograma} onChange={handleChange} disabled={cargandoCatalogos} {...aria("idPrograma")}>
                                <option value="">Seleccione un programa</option>
                                {programas.map((programa) => (
                                    <option key={programa.id} value={programa.id}>
                                        {programa.nombre}
                                        {programa.codigo ? ` (${programa.codigo})` : ""}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <p className="curso-nota-oferta">
                            <CalendarRange size={16} strokeWidth={1.75} aria-hidden="true" />
                            <span>
                                El curso es parte del catálogo y se repite cada año, por eso no lleva período ni cupos.
                                La oferta de cada período (cupo, profesor y horario) se abre en <b>Grupos</b>.
                            </span>
                        </p>
                    </div>
                </section>

                <RequisitosCursoOpcionales
                    key={form.idPrograma}
                    idPrograma={form.idPrograma} value={form.requisitos}
                    onChange={requisitos => setForm(prev => ({ ...prev, requisitos }))}
                    disabled={guardando || cargandoCatalogos}
                />

                <div className="panel acciones-panel">
                    <div className="panel-foot">
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={cancelar} disabled={guardando}>
                            Cancelar
                        </button>
                        <button type="submit" className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`} disabled={guardando || cargandoCatalogos} aria-busy={guardando || undefined}>
                            {guardando ? <span className="sg-spinner" aria-hidden="true" /> : <BookPlus size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {guardando ? "Creando curso…" : "Crear curso"}
                        </button>
                    </div>
                </div>
            </form>
        </div>
    );
}

export default CrearCurso;
