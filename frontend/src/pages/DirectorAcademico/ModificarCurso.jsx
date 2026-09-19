import { useEffect, useMemo, useRef, useState } from "react";
import {
    ArrowLeft,
    CheckCircle2,
    Edit3,
    GraduationCap,
    Music2,
    Search,
    XCircle,
} from "lucide-react";

import { API_URL } from "../../config/api";
import "./modificarCurso.css";

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
    };
}

function ModificarCurso() {

    const token = localStorage.getItem("token");

    const editorRef = useRef(null);

    const [cursos, setCursos] = useState([]);
    const [programas, setProgramas] = useState([]);

    const [busqueda, setBusqueda] = useState("");

    const [cursoSeleccionado, setCursoSeleccionado] =
        useState(null);

    const [form, setForm] = useState({
        codigo: "",
        nombre: "",
        descripcion: "",
        horasTotales: "",
        idPrograma: "",
        nivel: "",
    });

    const [formInicial, setFormInicial] =
        useState(null);

    const [errores, setErrores] = useState({});

    const [cargandoCatalogos, setCargandoCatalogos] =
        useState(true);

    const [cargandoCurso, setCargandoCurso] =
        useState(false);

    const [guardando, setGuardando] =
        useState(false);

    const [error, setError] = useState("");
    const [mensajeExito, setMensajeExito] =
        useState("");

    /*
     * Puede ser:
     * - null
     * - "guardar"
     * - "cancelar"
     */
    const [modalAccion, setModalAccion] =
        useState(null);

    useEffect(() => {
        cargarCatalogos();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

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

    async function cargarCatalogos() {

        setCargandoCatalogos(true);
        setError("");

        try {

            const headers = {
                Authorization: `Bearer ${token}`,
            };

            const [
                cursosResponse,
                programasResponse,
            ] = await Promise.all([
                fetch(`${API_URL}/cursos`, {
                    headers,
                }),
                fetch(`${API_URL}/programas`, {
                    headers,
                }),
            ]);

            if (
                cursosResponse.status === 401 ||
                programasResponse.status === 401
            ) {
                throw new Error(
                    "La sesión ha expirado. Inicie sesión nuevamente."
                );
            }

            if (
                cursosResponse.status === 403 ||
                programasResponse.status === 403
            ) {
                throw new Error(
                    "No tiene permisos para consultar la información académica."
                );
            }

            if (!cursosResponse.ok) {
                const data =
                    await leerJsonSeguro(cursosResponse);

                throw new Error(
                    mensajeDeRespuesta(
                        data,
                        "No fue posible cargar el listado de cursos."
                    )
                );
            }

            if (!programasResponse.ok) {
                const data =
                    await leerJsonSeguro(programasResponse);

                throw new Error(
                    mensajeDeRespuesta(
                        data,
                        "No fue posible cargar los programas académicos."
                    )
                );
            }

            const cursosData =
                await cursosResponse.json();

            const programasData =
                await programasResponse.json();

            setCursos(cursosData);
            setProgramas(programasData);

        } catch (e) {

            setError(
                e.message ||
                "No fue posible cargar la información necesaria."
            );

        } finally {

            setCargandoCatalogos(false);
        }
    }

    const cursosFiltrados = useMemo(() => {

        const texto =
            busqueda.trim().toLowerCase();

        if (!texto) {
            return cursos;
        }

        return cursos.filter((curso) => {
            const nombre =
                curso.nombre?.toLowerCase() || "";

            const codigo =
                curso.codigo?.toLowerCase() || "";

            return (
                nombre.includes(texto) ||
                codigo.includes(texto)
            );
        });

    }, [cursos, busqueda]);

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
            actual.nivel !== inicial.nivel
        );

    }, [form, formInicial]);

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

        return cambios;

    }, [form, formInicial]);

    async function seleccionarCurso(curso) {

        setCursoSeleccionado(curso);
        setCargandoCurso(true);
        setError("");
        setMensajeExito("");
        setErrores({});

        try {

            const response = await fetch(
                `${API_URL}/cursos/${curso.idCurso}`,
                {
                    headers: {
                        Authorization:
                            `Bearer ${token}`,
                    },
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
                throw new Error(
                    mensajeDeRespuesta(
                        data,
                        "No fue posible cargar la información del curso."
                    )
                );
            }

            const siguienteForm =
                construirFormDesdeCurso(data);

            setForm(siguienteForm);
            setFormInicial(siguienteForm);

            /*
             * Se conserva la información más completa
             * devuelta por el endpoint de edición.
             */
            setCursoSeleccionado({
                ...curso,
                ...data,
            });

            window.setTimeout(() => {
                editorRef.current?.scrollIntoView({
                    behavior: "smooth",
                    block: "start",
                });
            }, 50);

        } catch (e) {

            setCursoSeleccionado(null);
            setFormInicial(null);

            setError(
                e.message ||
                "Ocurrió un error al cargar el curso."
            );

        } finally {

            setCargandoCurso(false);
        }
    }

    function handleChange(e) {

        const { name, value } =
            e.target;

        setForm((prev) => ({
            ...prev,
            [name]: value,
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

        setError("");
        setMensajeExito("");
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
        }

        return siguientesErrores;
    }

    function solicitarGuardado(e) {

        e.preventDefault();

        setError("");
        setMensajeExito("");

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

            return;
        }

        if (!hayCambios) {
            setError(
                "No hay cambios pendientes por guardar."
            );
            return;
        }

        setErrores({});
        setModalAccion("guardar");
    }

    async function confirmarGuardado() {

        if (!cursoSeleccionado) {
            return;
        }

        setGuardando(true);
        setError("");
        setMensajeExito("");

        const payload =
            normalizarForm(form);

        try {

            const response = await fetch(
                `${API_URL}/cursos/${cursoSeleccionado.idCurso}`,
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

            const siguienteForm =
                construirFormDesdeCurso(data);

            setForm(siguienteForm);
            setFormInicial(siguienteForm);

            setCursoSeleccionado(
                (prev) => ({
                    ...prev,
                    ...data,
                })
            );

            /*
             * Actualiza la lista local para que el usuario
             * vea inmediatamente el nuevo nombre/descripción
             * sin recargar toda la página.
             */
            setCursos((prev) =>
                prev.map((curso) =>
                    curso.idCurso ===
                    data.idCurso
                        ? {
                            ...curso,
                            nombre:
                            data.nombre,
                            categoria:
                            data.descripcion,
                        }
                        : curso
                )
            );

            setModalAccion(null);

            setMensajeExito(
                `El curso ${data.codigo || form.codigo} fue modificado correctamente. Los cambios quedaron registrados sin alterar la información histórica de matrícula.`
            );

        } catch (e) {

            setModalAccion(null);

            setError(
                e.message ||
                "Ocurrió un error al modificar el curso."
            );

        } finally {

            setGuardando(false);
        }
    }

    function solicitarCancelar() {

        if (hayCambios) {
            setModalAccion("cancelar");
            return;
        }

        cerrarEdicion();
    }

    function cerrarEdicion() {

        setCursoSeleccionado(null);

        setForm({
            codigo: "",
            nombre: "",
            descripcion: "",
            horasTotales: "",
            idPrograma: "",
            nivel: "",
        });

        setFormInicial(null);
        setErrores({});
        setModalAccion(null);
        setError("");
    }

    function descartarCambios() {
        cerrarEdicion();
    }

    function restaurarValores() {

        if (!formInicial) {
            return;
        }

        setForm({
            ...formInicial,
        });

        setErrores({});
        setError("");
        setMensajeExito("");
    }

    return (
        <div className="container">

            <div className="modificar-curso">

                <header className="modificar-curso__header">
                    <h1>
                        Modificar curso
                    </h1>

                    <p>
                        Seleccione un curso activo y actualice únicamente
                        los datos permitidos de la malla curricular.
                    </p>
                </header>

                {error && (
                    <div
                        className="modificar-curso__message modificar-curso__message--error"
                        role="alert"
                    >
                        <XCircle
                            size={18}
                            aria-hidden="true"
                        />
                        <span>{error}</span>
                    </div>
                )}

                {mensajeExito && (
                    <div
                        className="modificar-curso__message modificar-curso__message--success"
                        role="status"
                        aria-live="polite"
                    >
                        <CheckCircle2
                            size={18}
                            aria-hidden="true"
                        />
                        <span>
                            {mensajeExito}
                        </span>
                    </div>
                )}

                {!cursoSeleccionado && (
                    <section
                        className="modificar-curso__selector"
                        aria-labelledby="seleccionar-curso-titulo"
                    >
                        <div className="modificar-curso__section-heading">
                            <div>
                                <h2 id="seleccionar-curso-titulo">
                                    1. Seleccione el curso
                                </h2>
                                <p>
                                    Busque por nombre o código del curso.
                                </p>
                            </div>
                        </div>

                        <div className="modificar-curso__search">
                            <Search
                                size={18}
                                aria-hidden="true"
                            />

                            <label
                                htmlFor="buscarCurso"
                                className="sr-only"
                            >
                                Buscar curso por nombre o código
                            </label>

                            <input
                                id="buscarCurso"
                                type="search"
                                value={busqueda}
                                onChange={(e) =>
                                    setBusqueda(
                                        e.target.value
                                    )
                                }
                                placeholder="Buscar por nombre o código"
                                disabled={
                                    cargandoCatalogos
                                }
                            />
                        </div>

                        <div
                            className="modificar-curso__lista"
                            aria-live="polite"
                        >
                            {cargandoCatalogos && (
                                <div className="modificar-curso__empty">
                                    Cargando cursos...
                                </div>
                            )}

                            {!cargandoCatalogos &&
                                cursosFiltrados.length === 0 && (
                                    <div className="modificar-curso__empty">
                                        No hay cursos activos que coincidan
                                        con la búsqueda.
                                    </div>
                                )}

                            {!cargandoCatalogos &&
                                cursosFiltrados.map(
                                    (curso) => (
                                        <article
                                            className="modificar-curso-card"
                                            key={
                                                curso.idCurso
                                            }
                                        >
                                            <div className="modificar-curso-card__content">
                                                <div className="modificar-curso-card__title">
                                                    <Music2
                                                        size={19}
                                                        aria-hidden="true"
                                                    />

                                                    <div>
                                                        <h3>
                                                            {curso.nombre}
                                                        </h3>

                                                        <span>
                                                        {curso.codigo}
                                                    </span>
                                                    </div>
                                                </div>

                                                {curso.categoria && (
                                                    <p className="modificar-curso-card__description">
                                                        {curso.categoria}
                                                    </p>
                                                )}
                                            </div>

                                            <button
                                                type="button"
                                                className="btn btn--primary modificar-curso-card__button"
                                                onClick={() =>
                                                    seleccionarCurso(
                                                        curso
                                                    )
                                                }
                                                disabled={
                                                    cargandoCurso
                                                }
                                            >
                                                <Edit3
                                                    size={16}
                                                    aria-hidden="true"
                                                />
                                                Modificar
                                            </button>
                                        </article>
                                    )
                                )}
                        </div>
                    </section>
                )}

                {cursoSeleccionado && (
                    <section
                        className="modificar-curso__editor"
                        ref={editorRef}
                        aria-labelledby="editar-curso-titulo"
                    >
                        <div className="modificar-curso__section-heading modificar-curso__section-heading--editor">
                            <div>
                                <h2 id="editar-curso-titulo">
                                    2. Modifique los datos
                                </h2>

                                <p>
                                    Los campos muestran la información
                                    actualmente registrada.
                                </p>
                            </div>

                            <button
                                type="button"
                                className="modificar-curso__back"
                                onClick={
                                    solicitarCancelar
                                }
                                disabled={
                                    guardando
                                }
                            >
                                <ArrowLeft
                                    size={17}
                                    aria-hidden="true"
                                />
                                Volver a cursos
                            </button>
                        </div>

                        {cargandoCurso ? (
                            <div className="modificar-curso__empty">
                                Cargando información del curso...
                            </div>
                        ) : (
                            <form
                                className="modificar-curso__form"
                                onSubmit={
                                    solicitarGuardado
                                }
                                noValidate
                            >
                                <div className="modificar-curso__identity">
                                    <div>
                                        <span className="modificar-curso__identity-label">
                                            Curso seleccionado
                                        </span>

                                        <strong>
                                            {form.nombre ||
                                                cursoSeleccionado.nombre}
                                        </strong>
                                    </div>

                                    <span className="modificar-curso__code">
                                        {form.codigo}
                                    </span>
                                </div>

                                <div className="modificar-curso__grid">

                                    <div className="modificar-field">
                                        <label htmlFor="codigo">
                                            Código
                                        </label>

                                        <input
                                            id="codigo"
                                            name="codigo"
                                            type="text"
                                            value={form.codigo}
                                            readOnly
                                            aria-readonly="true"
                                        />

                                        <small>
                                            El código identifica al curso y
                                            no puede modificarse desde esta
                                            historia de usuario.
                                        </small>
                                    </div>

                                    <div
                                        className={`modificar-field${
                                            errores.nombre
                                                ? " modificar-field--error"
                                                : ""
                                        }`}
                                    >
                                        <label htmlFor="nombre">
                                            Nombre del curso *
                                        </label>

                                        <input
                                            id="nombre"
                                            name="nombre"
                                            type="text"
                                            maxLength="160"
                                            value={
                                                form.nombre
                                            }
                                            onChange={
                                                handleChange
                                            }
                                            disabled={
                                                guardando
                                            }
                                            aria-invalid={
                                                Boolean(
                                                    errores.nombre
                                                )
                                            }
                                            aria-describedby={
                                                errores.nombre
                                                    ? "nombre-modificar-error"
                                                    : undefined
                                            }
                                            required
                                        />

                                        {errores.nombre && (
                                            <span
                                                id="nombre-modificar-error"
                                                className="modificar-field__error"
                                                role="alert"
                                            >
                                                {errores.nombre}
                                            </span>
                                        )}
                                    </div>

                                    <div className="modificar-field modificar-field--full">
                                        <label htmlFor="descripcion">
                                            Descripción
                                        </label>

                                        <textarea
                                            id="descripcion"
                                            name="descripcion"
                                            value={
                                                form.descripcion
                                            }
                                            onChange={
                                                handleChange
                                            }
                                            disabled={
                                                guardando
                                            }
                                            rows="4"
                                            placeholder="Descripción del curso"
                                        />
                                    </div>

                                    <div
                                        className={`modificar-field${
                                            errores.horasTotales
                                                ? " modificar-field--error"
                                                : ""
                                        }`}
                                    >
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
                                            value={
                                                form.horasTotales
                                            }
                                            onChange={
                                                handleChange
                                            }
                                            disabled={
                                                guardando
                                            }
                                            aria-invalid={
                                                Boolean(
                                                    errores.horasTotales
                                                )
                                            }
                                            aria-describedby={
                                                errores.horasTotales
                                                    ? "horas-modificar-error"
                                                    : undefined
                                            }
                                            required
                                        />

                                        {errores.horasTotales && (
                                            <span
                                                id="horas-modificar-error"
                                                className="modificar-field__error"
                                                role="alert"
                                            >
                                                {errores.horasTotales}
                                            </span>
                                        )}
                                    </div>

                                    <div
                                        className={`modificar-field${
                                            errores.nivel
                                                ? " modificar-field--error"
                                                : ""
                                        }`}
                                    >
                                        <label htmlFor="nivel">
                                            Nivel académico *
                                        </label>

                                        <input
                                            id="nivel"
                                            name="nivel"
                                            type="number"
                                            min="1"
                                            step="1"
                                            value={
                                                form.nivel
                                            }
                                            onChange={
                                                handleChange
                                            }
                                            disabled={
                                                guardando
                                            }
                                            aria-invalid={
                                                Boolean(
                                                    errores.nivel
                                                )
                                            }
                                            aria-describedby={
                                                errores.nivel
                                                    ? "nivel-modificar-error"
                                                    : undefined
                                            }
                                            required
                                        />

                                        {errores.nivel && (
                                            <span
                                                id="nivel-modificar-error"
                                                className="modificar-field__error"
                                                role="alert"
                                            >
                                                {errores.nivel}
                                            </span>
                                        )}
                                    </div>

                                    <div
                                        className={`modificar-field modificar-field--full${
                                            errores.idPrograma
                                                ? " modificar-field--error"
                                                : ""
                                        }`}
                                    >
                                        <label htmlFor="idPrograma">
                                            Programa académico *
                                        </label>

                                        <div className="modificar-field__select-wrap">
                                            <GraduationCap
                                                size={17}
                                                aria-hidden="true"
                                            />

                                            <select
                                                id="idPrograma"
                                                name="idPrograma"
                                                value={
                                                    form.idPrograma
                                                }
                                                onChange={
                                                    handleChange
                                                }
                                                disabled={
                                                    guardando ||
                                                    cargandoCatalogos
                                                }
                                                aria-invalid={
                                                    Boolean(
                                                        errores.idPrograma
                                                    )
                                                }
                                                aria-describedby={
                                                    errores.idPrograma
                                                        ? "programa-modificar-error"
                                                        : undefined
                                                }
                                                required
                                            >
                                                <option value="">
                                                    Seleccione un programa
                                                </option>

                                                {programas.map(
                                                    (programa) => (
                                                        <option
                                                            key={
                                                                programa.id
                                                            }
                                                            value={
                                                                programa.id
                                                            }
                                                        >
                                                            {programa.nombre}
                                                            {programa.codigo
                                                                ? ` (${programa.codigo})`
                                                                : ""}
                                                        </option>
                                                    )
                                                )}
                                            </select>
                                        </div>

                                        {errores.idPrograma && (
                                            <span
                                                id="programa-modificar-error"
                                                className="modificar-field__error"
                                                role="alert"
                                            >
                                                {errores.idPrograma}
                                            </span>
                                        )}

                                        <small>
                                            Si cambia el programa, el sistema
                                            conservará la relación histórica
                                            anterior y asociará el curso al
                                            plan vigente del nuevo programa.
                                        </small>
                                    </div>

                                </div>

                                <p className="modificar-curso__required">
                                    * Campos obligatorios
                                </p>

                                <div
                                    className="modificar-curso__change-status"
                                    aria-live="polite"
                                >
                                    {hayCambios
                                        ? `${camposModificados.length} campo${
                                            camposModificados.length === 1
                                                ? ""
                                                : "s"
                                        } con cambios pendientes.`
                                        : "No hay cambios pendientes."}
                                </div>

                                <div className="modificar-curso__actions">

                                    <button
                                        type="button"
                                        className="btn modificar-curso__secondary"
                                        onClick={
                                            solicitarCancelar
                                        }
                                        disabled={
                                            guardando
                                        }
                                    >
                                        Cancelar
                                    </button>

                                    <button
                                        type="button"
                                        className="btn modificar-curso__secondary"
                                        onClick={
                                            restaurarValores
                                        }
                                        disabled={
                                            guardando ||
                                            !hayCambios
                                        }
                                    >
                                        Restaurar valores
                                    </button>

                                    <button
                                        type="submit"
                                        className="btn btn--primary"
                                        disabled={
                                            guardando ||
                                            !hayCambios
                                        }
                                    >
                                        {guardando
                                            ? "Guardando..."
                                            : "Guardar cambios"}
                                    </button>

                                </div>

                            </form>
                        )}
                    </section>
                )}

                {modalAccion === "guardar" && (
                    <div
                        className="modificar-modal-overlay"
                        role="presentation"
                    >
                        <div
                            className="modificar-modal"
                            role="dialog"
                            aria-modal="true"
                            aria-labelledby="confirmar-modificacion-titulo"
                        >
                            <Edit3
                                size={24}
                                className="modificar-modal__icon"
                                aria-hidden="true"
                            />

                            <h2 id="confirmar-modificacion-titulo">
                                Confirmar modificación
                            </h2>

                            <p>
                                Se actualizará el curso{" "}
                                <strong>
                                    {form.nombre}
                                </strong>{" "}
                                ({form.codigo}).
                            </p>

                            <div className="modificar-modal__changes">
                                <span>
                                    Campos que cambiarán:
                                </span>

                                <ul>
                                    {camposModificados.map(
                                        (campo) => (
                                            <li key={campo}>
                                                {campo}
                                            </li>
                                        )
                                    )}
                                </ul>
                            </div>

                            <p className="modificar-modal__history">
                                Esta operación no elimina ni modifica
                                matrículas históricas registradas para el
                                curso.
                            </p>

                            <div className="modificar-modal__actions">
                                <button
                                    type="button"
                                    className="btn modificar-curso__secondary"
                                    onClick={() =>
                                        setModalAccion(
                                            null
                                        )
                                    }
                                    disabled={
                                        guardando
                                    }
                                >
                                    Revisar
                                </button>

                                <button
                                    type="button"
                                    className="btn btn--primary"
                                    onClick={
                                        confirmarGuardado
                                    }
                                    disabled={
                                        guardando
                                    }
                                >
                                    {guardando
                                        ? "Guardando..."
                                        : "Confirmar cambios"}
                                </button>
                            </div>
                        </div>
                    </div>
                )}

                {modalAccion === "cancelar" && (
                    <div
                        className="modificar-modal-overlay"
                        role="presentation"
                    >
                        <div
                            className="modificar-modal"
                            role="dialog"
                            aria-modal="true"
                            aria-labelledby="descartar-cambios-titulo"
                        >
                            <XCircle
                                size={24}
                                className="modificar-modal__icon modificar-modal__icon--danger"
                                aria-hidden="true"
                            />

                            <h2 id="descartar-cambios-titulo">
                                ¿Descartar cambios?
                            </h2>

                            <p>
                                Hay cambios que todavía no se han guardado.
                                Si continúa, esos cambios se perderán.
                            </p>

                            <div className="modificar-modal__actions">
                                <button
                                    type="button"
                                    className="btn modificar-curso__secondary"
                                    onClick={() =>
                                        setModalAccion(
                                            null
                                        )
                                    }
                                >
                                    Continuar editando
                                </button>

                                <button
                                    type="button"
                                    className="btn modificar-curso__danger"
                                    onClick={
                                        descartarCambios
                                    }
                                >
                                    Descartar cambios
                                </button>
                            </div>
                        </div>
                    </div>
                )}

            </div>

        </div>
    );
}

export default ModificarCurso;
