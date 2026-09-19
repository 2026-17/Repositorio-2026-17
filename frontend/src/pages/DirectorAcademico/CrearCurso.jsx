import RequisitosCursoOpcionales from "./RequisitosCursoOpcionales";
import { useEffect, useState } from "react";
import { API_URL } from "../../config/api";
import "./crearCurso.css";

function CrearCurso() {

    const token = localStorage.getItem("token");
    const [revisionRequisitos, setRevisionRequisitos] = useState(0);

    const [form, setForm] = useState({
        codigo: "",
        nombre: "",
        descripcion: "",
        horasTotales: "",
        idPrograma: "",
        nivel: "",
        cupos: "",
        idPeriodo: "",
    });

    const [programas, setProgramas] = useState([]);
    const [periodos, setPeriodos] = useState([]);

    const [cargandoCatalogos, setCargandoCatalogos] = useState(true);
    const [guardando, setGuardando] = useState(false);

    const [error, setError] = useState("");
    const [mensajeExito, setMensajeExito] = useState("");
    const [errores, setErrores] = useState({});


    useEffect(() => {

        async function cargarCatalogos() {

            setCargandoCatalogos(true);
            setError("");

            try {

                const headers = {
                    Authorization: `Bearer ${token}`,
                };

                const [programasResponse, periodosResponse] =
                    await Promise.all([
                        fetch(`${API_URL}/programas`, {
                            headers,
                        }),
                        fetch(`${API_URL}/periodos-academicos`, {
                            headers,
                        }),
                    ]);

                if (
                    programasResponse.status === 401 ||
                    periodosResponse.status === 401
                ) {
                    throw new Error(
                        "La sesión ha expirado. Inicie sesión nuevamente."
                    );
                }

                if (
                    programasResponse.status === 403 ||
                    periodosResponse.status === 403
                ) {
                    throw new Error(
                        "No tiene permisos para consultar la información académica."
                    );
                }

                if (!programasResponse.ok) {
                    throw new Error(
                        "No se pudieron cargar los programas académicos."
                    );
                }

                if (!periodosResponse.ok) {
                    throw new Error(
                        "No se pudieron cargar los períodos lectivos."
                    );
                }

                const programasData = await programasResponse.json();
                const periodosData = await periodosResponse.json();

                setProgramas(programasData);
                setPeriodos(periodosData);

            } catch (e) {

                setError(
                    e.message ||
                    "No fue posible cargar la información necesaria."
                );

            } finally {

                setCargandoCatalogos(false);
            }
        }

        cargarCatalogos();

    }, [token]);

    function handleChange(e) {

        const { name, value } = e.target;

        setForm((prev) => ({
            ...prev,
            [name]: value,
            ...(name === "idPrograma" ? { requisitos: undefined } : {}),
        }));

        // Limpiar el error del campo que el usuario está corrigiendo.
        setErrores((prev) => {
            if (!prev[name]) {
                return prev;
            }

            const siguiente = { ...prev };
            delete siguiente[name];
            return siguiente;
        });
        setError("");
        setMensajeExito("");
    }

    function validarFormulario() {
        const siguientesErrores = {};

        if (!form.codigo.trim()) {
            siguientesErrores.codigo = "Ingrese el código del curso.";
        } else if (form.codigo.trim().length > 30) {
            siguientesErrores.codigo = "El código del curso no puede superar los 30 caracteres.";
        }

        if (!form.nombre.trim()) {
            siguientesErrores.nombre = "Ingrese el nombre del curso.";
        } else if (form.nombre.trim().length > 160) {
            siguientesErrores.nombre = "El nombre del curso no puede superar los 160 caracteres.";
        }

        if (!form.horasTotales || Number(form.horasTotales) <= 0) {
            siguientesErrores.horasTotales = "La cantidad de horas debe ser mayor que cero.";
        }

        if (!form.idPrograma) {
            siguientesErrores.idPrograma = "Seleccione un programa académico.";
        }

        if (!form.nivel || Number(form.nivel) <= 0) {
            siguientesErrores.nivel = "El nivel académico debe ser mayor que cero.";
        }

        if (!form.cupos || Number(form.cupos) <= 0) {
            siguientesErrores.cupos = "La cantidad de cupos debe ser mayor que cero.";
        }

        if (!form.idPeriodo) {
            siguientesErrores.idPeriodo = "Seleccione un período lectivo.";
        }

        return siguientesErrores;
    }

    async function handleSubmit(e) {

        e.preventDefault();

        setError("");
        setMensajeExito("");
        const erroresValidacion = validarFormulario();

        if (Object.keys(erroresValidacion).length > 0) {
            setErrores(erroresValidacion);
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
            cupos: Number(form.cupos),
            idPeriodo: Number(form.idPeriodo),
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

            setMensajeExito(
                `El curso ${data.codigo || payload.codigo} fue creado correctamente y agregado a la malla curricular.`
            );

            setRevisionRequisitos(n => n + 1);
            setForm({
                codigo: "",
                nombre: "",
                descripcion: "",
                horasTotales: "",
                idPrograma: "",
                nivel: "",
                cupos: "",
                idPeriodo: "",
            });

        } catch (e) {

            setError(
                e.message ||
                "Ocurrió un error al crear el curso."
            );

        } finally {

            setGuardando(false);
        }
    }

    function limpiarFormulario() {
        setRevisionRequisitos(n => n + 1);

        setForm({
            codigo: "",
            nombre: "",
            descripcion: "",
            horasTotales: "",
            idPrograma: "",
            nivel: "",
            cupos: "",
            idPeriodo: "",
        });

        setError("");
        setMensajeExito("");
        setErrores({});
    }

    return (
        <div className="container">

            <div className="curso-page">

                <div className="curso-page__header">
                    <div>
                        <h1>Crear curso</h1>

                        <p>
                            Complete la información para incorporar un nuevo
                            curso a la malla curricular.
                        </p>
                    </div>
                </div>

                {cargandoCatalogos && (
                    <div
                        className="curso-message curso-message--info"
                        role="status"
                    >
                        Cargando información académica...
                    </div>
                )}

                {error && (
                    <div
                        className="curso-message curso-message--error"
                        role="alert"
                    >
                        {error}
                    </div>
                )}

                {mensajeExito && (
                    <div
                        className="curso-message curso-message--success"
                        role="status"
                    >
                        {mensajeExito}
                    </div>
                )}

                <form
                    className="curso-form"
                    onSubmit={handleSubmit}
                    noValidate
                >

                    <div className="curso-form__section">

                        <h2>Información del curso</h2>

                        <div className="curso-form__grid">
                            <div className={`curso-field${errores.codigo ? " curso-field--error" : ""}`}>
                                <label htmlFor="codigo">
                                    Código del curso *
                                </label>

                                <input
                                    id="codigo"
                                    name="codigo"
                                    type="text"
                                    maxLength="30"
                                    value={form.codigo}
                                    onChange={handleChange}
                                    placeholder="Ej. MUS-101"
                                    autoComplete="off"

                                    aria-invalid={Boolean(errores.codigo)}
                                    aria-describedby={errores.codigo ? "codigo-error" : undefined}
                                    required
                                />

                                {errores.codigo ? (
                                    <span className="curso-field__error" id="codigo-error" role="alert">
                                        {errores.codigo}
                                    </span>
                                ) : (
                                    <small>
                                        Debe ser un código único.
                                    </small>
                                )}
                            </div>

                            <div className={`curso-field${errores.nombre ? " curso-field--error" : ""}`}>

                                <label htmlFor="nombre">
                                    Nombre del curso *
                                </label>

                                <input
                                    id="nombre"
                                    name="nombre"
                                    type="text"
                                    maxLength="160"
                                    value={form.nombre}
                                    onChange={handleChange}
                                    placeholder="Ej. Introducción a la música"
                                    autoComplete="off"
                                    aria-invalid={Boolean(errores.nombre)}
                                    aria-describedby={errores.nombre ? "nombre-error" : undefined}
                                    required
                                />

                                {errores.nombre && (
                                    <span className="curso-field__error" id="nombre-error" role="alert">
                                        {errores.nombre}
                                    </span>
                                )}

                            </div>

                            <div className="curso-field curso-field--full">
                                <label htmlFor="descripcion">
                                    Descripción
                                </label>

                                <textarea
                                    id="descripcion"
                                    name="descripcion"
                                    rows="4"
                                    value={form.descripcion}
                                    onChange={handleChange}
                                    placeholder="Describa brevemente el contenido del curso."
                                />
                            </div>


                            <div className={`curso-field${errores.horasTotales ? " curso-field--error" : ""}`}>

                                <label htmlFor="horasTotales">
                                    Cantidad de horas *
                                </label>

                                <input
                                    id="horasTotales"
                                    name="horasTotales"
                                    type="number"
                                    min="1"
                                    max="65535"
                                    step="1"
                                    value={form.horasTotales}
                                    onChange={handleChange}
                                    placeholder="Ej. 40"

                                    aria-invalid={Boolean(errores.horasTotales)}
                                    aria-describedby={errores.horasTotales ? "horasTotales-error" : undefined}
                                    required
                                />

                                {errores.horasTotales && (
                                    <span className="curso-field__error" id="horasTotales-error" role="alert">
                                        {errores.horasTotales}
                                    </span>
                                )}
                            </div>

                            <div className={`curso-field${errores.nivel ? " curso-field--error" : ""}`}>

                                <label htmlFor="nivel">
                                    Nivel académico *
                                </label>

                                <input
                                    id="nivel"
                                    name="nivel"
                                    type="number"
                                    min="1"
                                    step="1"
                                    value={form.nivel}
                                    onChange={handleChange}
                                    placeholder="Ej. 1"
                                    aria-invalid={Boolean(errores.nivel)}
                                    aria-describedby={errores.nivel ? "nivel-error" : undefined}
                                    required
                                />

                                {errores.nivel && (
                                    <span className="curso-field__error" id="nivel-error" role="alert">
                                        {errores.nivel}
                                    </span>
                                )}

                            </div>

                        </div>
                    </div>

                    <div className="curso-form__section">

                        <h2>Ubicación en la malla curricular</h2>

                        <div className="curso-form__grid">


                            <div className={`curso-field${errores.idPrograma ? " curso-field--error" : ""}`}>

                                <label htmlFor="idPrograma">
                                    Programa académico *
                                </label>

                                <select
                                    id="idPrograma"
                                    name="idPrograma"
                                    value={form.idPrograma}
                                    onChange={handleChange}
                                    disabled={cargandoCatalogos}

                                    aria-invalid={Boolean(errores.idPrograma)}
                                    aria-describedby={errores.idPrograma ? "idPrograma-error" : undefined}

                                    required
                                >
                                    <option value="">
                                        Seleccione un programa
                                    </option>

                                    {programas.map((programa) => (
                                        <option
                                            key={programa.id}
                                            value={programa.id}
                                        >
                                            {programa.nombre}
                                            {programa.codigo
                                                ? ` (${programa.codigo})`
                                                : ""}
                                        </option>
                                    ))}
                                </select>

                                {errores.idPrograma && (
                                    <span className="curso-field__error" id="idPrograma-error" role="alert">
                                        {errores.idPrograma}
                                    </span>
                                )}
                            </div>

                            <div className={`curso-field${errores.idPeriodo ? " curso-field--error" : ""}`}>
                                <label htmlFor="idPeriodo">
                                    Período lectivo *
                                </label>

                                <select
                                    id="idPeriodo"
                                    name="idPeriodo"
                                    value={form.idPeriodo}
                                    onChange={handleChange}
                                    disabled={cargandoCatalogos}

                                    aria-invalid={Boolean(errores.idPeriodo)}
                                    aria-describedby={errores.idPeriodo ? "idPeriodo-error" : undefined}

                                    required
                                >
                                    <option value="">
                                        Seleccione un período
                                    </option>

                                    {periodos.map((periodo) => (
                                        <option
                                            key={periodo.id}
                                            value={periodo.id}
                                        >
                                            {periodo.nombre}
                                            {periodo.codigo
                                                ? ` (${periodo.codigo})`
                                                : ""}
                                        </option>
                                    ))}
                                </select>

                                {errores.idPeriodo && (
                                    <span className="curso-field__error" id="idPeriodo-error" role="alert">
                                        {errores.idPeriodo}
                                    </span>
                                )}
                            </div>

                            <div className={`curso-field${errores.cupos ? " curso-field--error" : ""}`}>
                                <label htmlFor="cupos">
                                    Cantidad de cupos *
                                </label>

                                <input
                                    id="cupos"
                                    name="cupos"
                                    type="number"
                                    min="1"
                                    step="1"
                                    value={form.cupos}
                                    onChange={handleChange}
                                    placeholder="Ej. 25"
                                    aria-invalid={Boolean(errores.cupos)}
                                    aria-describedby={errores.cupos ? "cupos-error" : undefined}
                                    required
                                />

                                {errores.cupos && (
                                    <span className="curso-field__error" id="cupos-error" role="alert">
                                        {errores.cupos}
                                    </span>
                                )}
                            </div>

                        </div>
                    </div>

                    <p className="curso-required">
                        * Campos obligatorios
                    </p>

                    <RequisitosCursoOpcionales
                        key={form.idPrograma + ':' + revisionRequisitos}
                        idPrograma={form.idPrograma} value={form.requisitos}
                        onChange={requisitos => setForm(prev => ({ ...prev, requisitos }))}
                        disabled={guardando || cargandoCatalogos}
                    />
                    <div className="curso-form__actions">

                        <button
                            type="button"
                            className="btn curso-btn--secondary"
                            onClick={limpiarFormulario}
                            disabled={guardando}
                        >
                            Limpiar
                        </button>

                        <button
                            type="submit"
                            className="btn btn--primary"
                            disabled={
                                guardando ||
                                cargandoCatalogos
                            }
                        >
                            {guardando
                                ? "Creando curso..."
                                : "Crear curso"}
                        </button>

                    </div>

                </form>

            </div>
        </div>
    );
}

export default CrearCurso;