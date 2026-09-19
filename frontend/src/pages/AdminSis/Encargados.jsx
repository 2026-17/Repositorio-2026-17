import { useState } from "react";
import { API_URL } from "../../config/api";
import "./styles/admin_Sis.css";

const ID_TIPO_CEDULA = 1;
const MAX_CONTACTOS = 2;

function formularioInicial() {
    return {
        idEstudiante: "",
        numeroIdentificacion: "",
        nombres: "",
        primerApellido: "",
        segundoApellido: "",
        telefonoPrincipal: "",
        telefonoSecundario: "",
        correo: "",
        ocupacion: "",
        estadoCivil: "",
        direccion: "",
        parentesco: "",
        esResponsablePrincipal: true,
        autorizadoRetiro: false,
        observaciones: "",
        contactosEmergencia: [],
    };
}

function contactoInicial() {
    return {
        idContactoEmergencia: null,
        nombre: "",
        telefono: "",
        relacion: "",
    };
}

function Encargados() {
    const token = localStorage.getItem("token");

    const [form, setForm] = useState(formularioInicial());

    const [errores, setErrores] = useState({});
    const [guardando, setGuardando] = useState(false);

    const [errorGeneral, setErrorGeneral] = useState("");
    const [mensajeExito, setMensajeExito] = useState("");

    const [idBusqueda, setIdBusqueda] = useState("");
    const [consultando, setConsultando] = useState(false);
    const [encargadosEncontrados, setEncargadosEncontrados] = useState([]);

    const [modoEdicion, setModoEdicion] = useState(false);
    const [idEncargadoEdicion, setIdEncargadoEdicion] = useState(null);

    function handleChange(event) {
        const { name, value, type, checked } = event.target;

        setForm((prev) => ({
            ...prev,
            [name]: type === "checkbox" ? checked : value,
        }));

        setErrores((prev) => {
            if (!prev[name]) {
                return prev;
            }

            const siguientes = { ...prev };
            delete siguientes[name];
            return siguientes;
        });

        setErrorGeneral("");
        setMensajeExito("");
    }

    function agregarContacto() {
        if (form.contactosEmergencia.length >= MAX_CONTACTOS) {
            return;
        }

        setForm((prev) => ({
            ...prev,
            contactosEmergencia: [
                ...prev.contactosEmergencia,
                contactoInicial(),
            ],
        }));

        setErrorGeneral("");
        setMensajeExito("");
    }

    function eliminarContacto(indice) {
        setForm((prev) => ({
            ...prev,
            contactosEmergencia:
                prev.contactosEmergencia.filter(
                    (_, index) => index !== indice
                ),
        }));

        setErrores({});
        setErrorGeneral("");
        setMensajeExito("");
    }

    function cambiarContacto(indice, campo, valor) {
        setForm((prev) => ({
            ...prev,
            contactosEmergencia:
                prev.contactosEmergencia.map(
                    (contacto, index) =>
                        index === indice
                            ? {
                                ...contacto,
                                [campo]: valor,
                            }
                            : contacto
                ),
        }));

        setErrores((prev) => {
            const clave = `contacto-${indice}-${campo}`;

            if (!prev[clave]) {
                return prev;
            }

            const siguientes = { ...prev };
            delete siguientes[clave];
            return siguientes;
        });

        setErrorGeneral("");
        setMensajeExito("");
    }

    function validarFormulario() {
        const siguientesErrores = {};

        if (
            !form.idEstudiante ||
            Number(form.idEstudiante) <= 0
        ) {
            siguientesErrores.idEstudiante =
                "Ingrese un identificador de estudiante válido.";
        }

        const cedula =
            form.numeroIdentificacion.trim();

        if (!cedula) {
            siguientesErrores.numeroIdentificacion =
                "Ingrese la cédula del encargado.";
        } else if (!/^\d{9}$/.test(cedula)) {
            siguientesErrores.numeroIdentificacion =
                "La cédula debe contener exactamente 9 dígitos.";
        }

        if (!form.nombres.trim()) {
            siguientesErrores.nombres =
                "Ingrese el nombre del encargado.";
        }

        if (!form.primerApellido.trim()) {
            siguientesErrores.primerApellido =
                "Ingrese el primer apellido.";
        }

        if (!form.telefonoPrincipal.trim()) {
            siguientesErrores.telefonoPrincipal =
                "Ingrese el teléfono principal.";
        }

        if (!form.parentesco) {
            siguientesErrores.parentesco =
                "Seleccione el parentesco con el estudiante.";
        }

        if (
            form.correo.trim() &&
            !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(
                form.correo.trim()
            )
        ) {
            siguientesErrores.correo =
                "Ingrese un correo electrónico válido.";
        }

        form.contactosEmergencia.forEach(
            (contacto, indice) => {
                if (!contacto.nombre.trim()) {
                    siguientesErrores[
                        `contacto-${indice}-nombre`
                        ] =
                        "Ingrese el nombre del contacto.";
                }

                if (!contacto.telefono.trim()) {
                    siguientesErrores[
                        `contacto-${indice}-telefono`
                        ] =
                        "Ingrese el teléfono del contacto.";
                }

                if (!contacto.relacion.trim()) {
                    siguientesErrores[
                        `contacto-${indice}-relacion`
                        ] =
                        "Ingrese el parentesco o relación.";
                }
            }
        );

        return siguientesErrores;
    }

    async function leerRespuesta(response) {
        try {
            return await response.json();
        } catch {
            return {};
        }
    }

    function validarEstadoRespuesta(
        response,
        data,
        mensajePorDefecto
    ) {
        if (response.status === 401) {
            throw new Error(
                "La sesión ha expirado. Inicie sesión nuevamente."
            );
        }

        if (response.status === 403) {
            throw new Error(
                "No tiene permisos para gestionar encargados."
            );
        }

        if (!response.ok) {
            throw new Error(
                data.message || mensajePorDefecto
            );
        }
    }

    async function buscarEncargados(event) {
        event.preventDefault();

        setErrorGeneral("");
        setMensajeExito("");

        if (
            !idBusqueda ||
            Number(idBusqueda) <= 0
        ) {
            setErrorGeneral(
                "Ingrese un ID de estudiante válido para realizar la consulta."
            );
            return;
        }

        setConsultando(true);

        try {
            const response = await fetch(
                `${API_URL}/encargados/estudiante/${Number(idBusqueda)}`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }
            );

            const data =
                await leerRespuesta(response);

            validarEstadoRespuesta(
                response,
                data,
                "No fue posible consultar los encargados."
            );

            const lista = Array.isArray(data)
                ? data
                : [data];

            setEncargadosEncontrados(lista);

            if (lista.length === 0) {
                setMensajeExito(
                    "El estudiante no tiene encargados registrados."
                );
            }
        } catch (error) {
            setEncargadosEncontrados([]);

            setErrorGeneral(
                error.message ||
                "Ocurrió un error al consultar los encargados."
            );
        } finally {
            setConsultando(false);
        }
    }

    function seleccionarParaEditar(encargado) {
        setModoEdicion(true);
        setIdEncargadoEdicion(encargado.id);

        setForm({
            idEstudiante:
                String(encargado.idEstudiante ?? ""),
            numeroIdentificacion:
                encargado.numeroIdentificacion ?? "",
            nombres:
                encargado.nombres ?? "",
            primerApellido:
                encargado.primerApellido ?? "",
            segundoApellido:
                encargado.segundoApellido ?? "",
            telefonoPrincipal:
                encargado.telefonoPrincipal ?? "",
            telefonoSecundario:
                encargado.telefonoSecundario ?? "",
            correo:
                encargado.correo ?? "",
            ocupacion:
                encargado.ocupacion ?? "",
            estadoCivil:
                encargado.estadoCivil ?? "",
            direccion:
                encargado.direccion ?? "",
            parentesco:
                encargado.parentesco ?? "",
            esResponsablePrincipal:
                Boolean(
                    encargado.esResponsablePrincipal
                ),
            autorizadoRetiro:
                Boolean(
                    encargado.autorizadoRetiro
                ),
            observaciones:
                encargado.observaciones ?? "",
            contactosEmergencia:
                (
                    encargado.contactosEmergencia || []
                ).map((contacto) => ({
                    idContactoEmergencia:
                    contacto.id,
                    nombre:
                        contacto.nombre ?? "",
                    telefono:
                        contacto.telefono ?? "",
                    relacion:
                        contacto.relacion ?? "",
                })),
        });

        setErrores({});
        setErrorGeneral("");
        setMensajeExito("");

        window.scrollTo({
            top: 0,
            behavior: "smooth",
        });
    }

    function cancelarEdicion() {
        setModoEdicion(false);
        setIdEncargadoEdicion(null);
        setForm(formularioInicial());
        setErrores({});
        setErrorGeneral("");
        setMensajeExito("");
    }

    async function handleSubmit(event) {
        event.preventDefault();

        setErrorGeneral("");
        setMensajeExito("");

        const erroresValidacion =
            validarFormulario();

        if (
            Object.keys(erroresValidacion).length > 0
        ) {
            setErrores(erroresValidacion);
            return;
        }

        setErrores({});
        setGuardando(true);

        const contactos =
            form.contactosEmergencia.map(
                (contacto) => {
                    const datos = {
                        nombre:
                            contacto.nombre.trim(),
                        telefono:
                            contacto.telefono.trim(),
                        relacion:
                            contacto.relacion.trim(),
                    };

                    if (
                        modoEdicion &&
                        contacto.idContactoEmergencia
                    ) {
                        datos.idContactoEmergencia =
                            contacto.idContactoEmergencia;
                    }

                    return datos;
                }
            );

        const payload = {
            idTipoIdentificacion:
            ID_TIPO_CEDULA,
            numeroIdentificacion:
                form.numeroIdentificacion.trim(),
            nombres:
                form.nombres.trim(),
            primerApellido:
                form.primerApellido.trim(),
            segundoApellido:
                form.segundoApellido.trim() || null,
            telefonoPrincipal:
                form.telefonoPrincipal.trim(),
            telefonoSecundario:
                form.telefonoSecundario.trim() ||
                null,
            correo:
                form.correo.trim() || null,
            ocupacion:
                form.ocupacion.trim() || null,
            estadoCivil:
                form.estadoCivil.trim() || null,
            direccion:
                form.direccion.trim() || null,
            parentesco:
            form.parentesco,
            esResponsablePrincipal:
            form.esResponsablePrincipal,
            autorizadoRetiro:
            form.autorizadoRetiro,
            observaciones:
                form.observaciones.trim() || null,
            contactosEmergencia:
            contactos,
        };

        if (!modoEdicion) {
            payload.idEstudiante =
                Number(form.idEstudiante);
        }

        const url = modoEdicion
            ? `${API_URL}/encargados/estudiante/${Number(
                form.idEstudiante
            )}/${idEncargadoEdicion}`
            : `${API_URL}/encargados`;

        const method =
            modoEdicion ? "PUT" : "POST";

        try {
            const response = await fetch(url, {
                method,
                headers: {
                    "Content-Type":
                        "application/json",
                    Authorization:
                        `Bearer ${token}`,
                },
                body: JSON.stringify(payload),
            });

            const data =
                await leerRespuesta(response);

            validarEstadoRespuesta(
                response,
                data,
                modoEdicion
                    ? "No fue posible actualizar el encargado."
                    : "No fue posible registrar el encargado."
            );

            if (modoEdicion) {
                setMensajeExito(
                    `La información de ${data.nombres} ${data.primerApellido} fue actualizada correctamente.`
                );

                setEncargadosEncontrados(
                    (prev) =>
                        prev.map((encargado) =>
                            encargado.id === data.id
                                ? data
                                : encargado
                        )
                );

                setModoEdicion(false);
                setIdEncargadoEdicion(null);
            } else {
                setMensajeExito(
                    `El encargado ${data.nombres} ${data.primerApellido} fue registrado correctamente para el estudiante ${data.idEstudiante}.`
                );

                if (
                    String(data.idEstudiante) ===
                    String(idBusqueda)
                ) {
                    setEncargadosEncontrados(
                        (prev) => [
                            ...prev,
                            data,
                        ]
                    );
                }
            }

            setForm(formularioInicial());
        } catch (error) {
            setErrorGeneral(
                error.message ||
                "Ocurrió un error al guardar el encargado."
            );
        } finally {
            setGuardando(false);
        }
    }

    function limpiarFormulario() {
        setForm(formularioInicial());
        setErrores({});
        setErrorGeneral("");
        setMensajeExito("");
        setModoEdicion(false);
        setIdEncargadoEdicion(null);
    }

    return (
        <div className="content">
            <div className="page-title">
                <h1>Gestionar encargados</h1>

                <p>
                    Registre, consulte y actualice los
                    encargados asociados al expediente
                    de un estudiante.
                </p>
            </div>

            {errorGeneral && (
                <div
                    className="banner banner-danger"
                    role="alert"
                >
                    {errorGeneral}
                </div>
            )}

            {mensajeExito && (
                <div
                    className="banner banner-success"
                    role="status"
                >
                    {mensajeExito}
                </div>
            )}

            <div className="card">
                <h2 className="card-title">
                    Consultar encargados
                </h2>

                <p className="card-subtitle">
                    Ingrese el ID del estudiante para
                    consultar los encargados asociados
                    a su expediente.
                </p>

                <form
                    onSubmit={buscarEncargados}
                >
                    <div className="grid-2">
                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="idBusqueda"
                            >
                                ID del estudiante
                            </label>

                            <input
                                className="input"
                                id="idBusqueda"
                                type="number"
                                min="1"
                                step="1"
                                value={idBusqueda}
                                onChange={(event) =>
                                    setIdBusqueda(
                                        event.target.value
                                    )
                                }
                                placeholder="Ej. 1"
                            />
                        </div>

                        <div
                            className="field"
                            style={{
                                display: "flex",
                                alignItems: "flex-end",
                            }}
                        >
                            <button
                                type="submit"
                                className="btn btn-primary"
                                disabled={consultando}
                            >
                                {consultando
                                    ? "Consultando..."
                                    : "Consultar"}
                            </button>
                        </div>
                    </div>
                </form>

                {encargadosEncontrados.map(
                    (encargado) => (
                        <div
                            className="subcard"
                            key={encargado.id}
                        >
                            <div className="subcard-header">
                                <span>
                                    {encargado.nombres}{" "}
                                    {
                                        encargado.primerApellido
                                    }{" "}
                                    {
                                        encargado.segundoApellido ||
                                        ""
                                    }
                                </span>

                                <button
                                    type="button"
                                    className="btn btn-secondary"
                                    onClick={() =>
                                        seleccionarParaEditar(
                                            encargado
                                        )
                                    }
                                >
                                    Modificar
                                </button>
                            </div>

                            <p className="hint">
                                Cédula:{" "}
                                {
                                    encargado.numeroIdentificacion
                                }
                            </p>

                            <p className="hint">
                                Parentesco:{" "}
                                {encargado.parentesco}
                            </p>

                            <p className="hint">
                                Teléfono:{" "}
                                {
                                    encargado.telefonoPrincipal
                                }
                            </p>

                            <p className="hint">
                                Contactos de emergencia:{" "}
                                {
                                    (
                                        encargado.contactosEmergencia ||
                                        []
                                    ).length
                                }
                            </p>
                        </div>
                    )
                )}
            </div>

            <form
                onSubmit={handleSubmit}
                noValidate
            >
                <div className="card">
                    <h2 className="card-title">
                        {modoEdicion
                            ? "Modificar encargado"
                            : "Registrar encargado"}
                    </h2>

                    <p className="card-subtitle">
                        {modoEdicion
                            ? "Actualice la información del encargado seleccionado."
                            : "Complete los datos del nuevo encargado."}
                    </p>

                    <div className="field">
                        <label
                            className="field-label"
                            htmlFor="idEstudiante"
                        >
                            ID del estudiante{" "}
                            <span className="required">
                                *
                            </span>
                        </label>

                        <input
                            className={`input ${
                                errores.idEstudiante
                                    ? "error"
                                    : ""
                            }`}
                            id="idEstudiante"
                            name="idEstudiante"
                            type="number"
                            min="1"
                            step="1"
                            value={form.idEstudiante}
                            onChange={handleChange}
                            disabled={modoEdicion}
                            placeholder="Ej. 1"
                        />

                        {errores.idEstudiante && (
                            <p className="error">
                                {
                                    errores.idEstudiante
                                }
                            </p>
                        )}
                    </div>
                </div>

                <div className="card">
                    <h2 className="card-title">
                        Información del encargado
                    </h2>

                    <p className="card-subtitle">
                        Los campos marcados con * son
                        obligatorios.
                    </p>

                    <div className="grid-2">
                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="numeroIdentificacion"
                            >
                                Cédula{" "}
                                <span className="required">
                                    *
                                </span>
                            </label>

                            <input
                                className={`input ${
                                    errores.numeroIdentificacion
                                        ? "error"
                                        : ""
                                }`}
                                id="numeroIdentificacion"
                                name="numeroIdentificacion"
                                type="text"
                                inputMode="numeric"
                                maxLength="9"
                                value={
                                    form.numeroIdentificacion
                                }
                                onChange={handleChange}
                                placeholder="Ej. 123456789"
                            />

                            {errores.numeroIdentificacion && (
                                <p className="error">
                                    {
                                        errores.numeroIdentificacion
                                    }
                                </p>
                            )}
                        </div>

                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="nombres"
                            >
                                Nombre(s){" "}
                                <span className="required">
                                    *
                                </span>
                            </label>

                            <input
                                className={`input ${
                                    errores.nombres
                                        ? "error"
                                        : ""
                                }`}
                                id="nombres"
                                name="nombres"
                                type="text"
                                maxLength="100"
                                value={form.nombres}
                                onChange={handleChange}
                            />

                            {errores.nombres && (
                                <p className="error">
                                    {errores.nombres}
                                </p>
                            )}
                        </div>

                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="primerApellido"
                            >
                                Primer apellido{" "}
                                <span className="required">
                                    *
                                </span>
                            </label>

                            <input
                                className={`input ${
                                    errores.primerApellido
                                        ? "error"
                                        : ""
                                }`}
                                id="primerApellido"
                                name="primerApellido"
                                type="text"
                                maxLength="80"
                                value={
                                    form.primerApellido
                                }
                                onChange={handleChange}
                            />

                            {errores.primerApellido && (
                                <p className="error">
                                    {
                                        errores.primerApellido
                                    }
                                </p>
                            )}
                        </div>

                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="segundoApellido"
                            >
                                Segundo apellido
                            </label>

                            <input
                                className="input"
                                id="segundoApellido"
                                name="segundoApellido"
                                type="text"
                                maxLength="80"
                                value={
                                    form.segundoApellido
                                }
                                onChange={handleChange}
                            />
                        </div>

                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="telefonoPrincipal"
                            >
                                Teléfono principal{" "}
                                <span className="required">
                                    *
                                </span>
                            </label>

                            <input
                                className={`input ${
                                    errores.telefonoPrincipal
                                        ? "error"
                                        : ""
                                }`}
                                id="telefonoPrincipal"
                                name="telefonoPrincipal"
                                type="text"
                                maxLength="30"
                                value={
                                    form.telefonoPrincipal
                                }
                                onChange={handleChange}
                            />

                            {errores.telefonoPrincipal && (
                                <p className="error">
                                    {
                                        errores.telefonoPrincipal
                                    }
                                </p>
                            )}
                        </div>

                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="telefonoSecundario"
                            >
                                Teléfono secundario
                            </label>

                            <input
                                className="input"
                                id="telefonoSecundario"
                                name="telefonoSecundario"
                                type="text"
                                maxLength="30"
                                value={
                                    form.telefonoSecundario
                                }
                                onChange={handleChange}
                            />
                        </div>

                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="correo"
                            >
                                Correo electrónico
                            </label>

                            <input
                                className={`input ${
                                    errores.correo
                                        ? "error"
                                        : ""
                                }`}
                                id="correo"
                                name="correo"
                                type="email"
                                maxLength="254"
                                value={form.correo}
                                onChange={handleChange}
                            />

                            {errores.correo && (
                                <p className="error">
                                    {errores.correo}
                                </p>
                            )}
                        </div>

                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="ocupacion"
                            >
                                Ocupación
                            </label>

                            <input
                                className="input"
                                id="ocupacion"
                                name="ocupacion"
                                type="text"
                                maxLength="120"
                                value={form.ocupacion}
                                onChange={handleChange}
                            />
                        </div>

                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="estadoCivil"
                            >
                                Estado civil
                            </label>

                            <input
                                className="input"
                                id="estadoCivil"
                                name="estadoCivil"
                                type="text"
                                maxLength="40"
                                value={form.estadoCivil}
                                onChange={handleChange}
                            />
                        </div>

                        <div className="field">
                            <label
                                className="field-label"
                                htmlFor="parentesco"
                            >
                                Parentesco{" "}
                                <span className="required">
                                    *
                                </span>
                            </label>

                            <select
                                className={`input ${
                                    errores.parentesco
                                        ? "error"
                                        : ""
                                }`}
                                id="parentesco"
                                name="parentesco"
                                value={form.parentesco}
                                onChange={handleChange}
                            >
                                <option value="">
                                    Seleccione una opción
                                </option>

                                <option value="MADRE">
                                    Madre
                                </option>

                                <option value="PADRE">
                                    Padre
                                </option>

                                <option value="TUTOR">
                                    Tutor
                                </option>

                                <option value="FAMILIAR">
                                    Familiar
                                </option>

                                <option value="OTRO">
                                    Otro
                                </option>
                            </select>

                            {errores.parentesco && (
                                <p className="error">
                                    {
                                        errores.parentesco
                                    }
                                </p>
                            )}
                        </div>
                    </div>

                    <div className="field">
                        <label
                            className="field-label"
                            htmlFor="direccion"
                        >
                            Dirección
                        </label>

                        <textarea
                            className="input textarea"
                            id="direccion"
                            name="direccion"
                            maxLength="500"
                            value={form.direccion}
                            onChange={handleChange}
                        />
                    </div>

                    <div className="field">
                        <label
                            className="field-label"
                            htmlFor="observaciones"
                        >
                            Observaciones
                        </label>

                        <textarea
                            className="input textarea"
                            id="observaciones"
                            name="observaciones"
                            maxLength="255"
                            value={form.observaciones}
                            onChange={handleChange}
                        />
                    </div>

                    <div className="radio-row">
                        <label className="radio-label">
                            <input
                                type="checkbox"
                                name="esResponsablePrincipal"
                                checked={
                                    form.esResponsablePrincipal
                                }
                                onChange={handleChange}
                            />

                            Responsable principal
                        </label>

                        <label className="radio-label">
                            <input
                                type="checkbox"
                                name="autorizadoRetiro"
                                checked={
                                    form.autorizadoRetiro
                                }
                                onChange={handleChange}
                            />

                            Autorizado para retirar al
                            estudiante
                        </label>
                    </div>
                </div>

                <div className="card">
                    <div className="card-header-row">
                        <div>
                            <h2 className="card-title">
                                Contactos de emergencia
                            </h2>

                            <p className="card-subtitle">
                                Puede registrar hasta dos
                                contactos.
                            </p>
                        </div>

                        <button
                            type="button"
                            className="btn btn-secondary"
                            onClick={agregarContacto}
                            disabled={
                                form.contactosEmergencia
                                    .length >=
                                MAX_CONTACTOS
                            }
                        >
                            Agregar contacto
                        </button>
                    </div>

                    {form.contactosEmergencia
                        .length === 0 && (
                        <p className="hint">
                            No se han agregado contactos
                            de emergencia.
                        </p>
                    )}

                    {form.contactosEmergencia.map(
                        (contacto, indice) => (
                            <div
                                className="subcard"
                                key={
                                    contacto.idContactoEmergencia ||
                                    `nuevo-${indice}`
                                }
                            >
                                <div className="subcard-header">
                                    <span>
                                        Contacto{" "}
                                        {indice + 1}
                                    </span>

                                    <button
                                        type="button"
                                        className="icon-btn danger"
                                        onClick={() =>
                                            eliminarContacto(
                                                indice
                                            )
                                        }
                                        aria-label={`Eliminar contacto ${
                                            indice + 1
                                        }`}
                                    >
                                        ×
                                    </button>
                                </div>

                                <div className="grid-2">
                                    <div className="field">
                                        <label className="field-label">
                                            Nombre{" "}
                                            <span className="required">
                                                *
                                            </span>
                                        </label>

                                        <input
                                            className={`input ${
                                                errores[
                                                    `contacto-${indice}-nombre`
                                                    ]
                                                    ? "error"
                                                    : ""
                                            }`}
                                            type="text"
                                            maxLength="180"
                                            value={
                                                contacto.nombre
                                            }
                                            onChange={(
                                                event
                                            ) =>
                                                cambiarContacto(
                                                    indice,
                                                    "nombre",
                                                    event.target
                                                        .value
                                                )
                                            }
                                        />

                                        {errores[
                                            `contacto-${indice}-nombre`
                                            ] && (
                                            <p className="error">
                                                {
                                                    errores[
                                                        `contacto-${indice}-nombre`
                                                        ]
                                                }
                                            </p>
                                        )}
                                    </div>

                                    <div className="field">
                                        <label className="field-label">
                                            Teléfono{" "}
                                            <span className="required">
                                                *
                                            </span>
                                        </label>

                                        <input
                                            className={`input ${
                                                errores[
                                                    `contacto-${indice}-telefono`
                                                    ]
                                                    ? "error"
                                                    : ""
                                            }`}
                                            type="text"
                                            maxLength="30"
                                            value={
                                                contacto.telefono
                                            }
                                            onChange={(
                                                event
                                            ) =>
                                                cambiarContacto(
                                                    indice,
                                                    "telefono",
                                                    event.target
                                                        .value
                                                )
                                            }
                                        />

                                        {errores[
                                            `contacto-${indice}-telefono`
                                            ] && (
                                            <p className="error">
                                                {
                                                    errores[
                                                        `contacto-${indice}-telefono`
                                                        ]
                                                }
                                            </p>
                                        )}
                                    </div>

                                    <div className="field">
                                        <label className="field-label">
                                            Parentesco o relación{" "}
                                            <span className="required">
                                                *
                                            </span>
                                        </label>

                                        <input
                                            className={`input ${
                                                errores[
                                                    `contacto-${indice}-relacion`
                                                    ]
                                                    ? "error"
                                                    : ""
                                            }`}
                                            type="text"
                                            maxLength="80"
                                            value={
                                                contacto.relacion
                                            }
                                            onChange={(
                                                event
                                            ) =>
                                                cambiarContacto(
                                                    indice,
                                                    "relacion",
                                                    event.target
                                                        .value
                                                )
                                            }
                                        />

                                        {errores[
                                            `contacto-${indice}-relacion`
                                            ] && (
                                            <p className="error">
                                                {
                                                    errores[
                                                        `contacto-${indice}-relacion`
                                                        ]
                                                }
                                            </p>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )
                    )}
                </div>

                <div className="actions-row">
                    {modoEdicion && (
                        <button
                            type="button"
                            className="btn btn-secondary"
                            onClick={cancelarEdicion}
                            disabled={guardando}
                        >
                            Cancelar edición
                        </button>
                    )}

                    <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={limpiarFormulario}
                        disabled={guardando}
                    >
                        Limpiar
                    </button>

                    <button
                        type="submit"
                        className="btn btn-primary"
                        disabled={guardando}
                    >
                        {guardando
                            ? "Guardando..."
                            : modoEdicion
                                ? "Guardar cambios"
                                : "Registrar encargado"}
                    </button>
                </div>
            </form>
        </div>
    );
}

export default Encargados;