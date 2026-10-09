import { useEffect, useState } from "react";
import { FileText, Upload, X } from "lucide-react";

import { API_URL } from "../../config/api.js";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import { useDialog } from "../../components/dialog/useDialog";

const MAX_PDF_SIZE = 50 * 1024 * 1024;

function getAuthHeaders() {
    const token = localStorage.getItem("token");

    return token
        ? { Authorization: `Bearer ${token}` }
        : {};
}

async function obtenerDatosDocumentos(idEstudiante) {
    const headers = getAuthHeaders();

    const [tiposResponse, documentosResponse] =
        await Promise.all([
            fetch(
                `${API_URL}/registro/catalogos/tipos-documentos`,
                { headers }
            ),
            fetch(
                `${API_URL}/estudiantes/${idEstudiante}/documentos`,
                { headers }
            ),
        ]);

    const tiposBody =
        await tiposResponse.json().catch(() => ({}));

    const documentosBody =
        await documentosResponse.json().catch(() => ({}));

    if (!tiposResponse.ok) {
        throw new Error(
            tiposBody.message ||
            "No fue posible consultar los tipos de documento."
        );
    }

    if (!documentosResponse.ok) {
        throw new Error(
            documentosBody.message ||
            "No fue posible consultar los documentos del estudiante."
        );
    }

    return {
        tiposDocumento: Array.isArray(tiposBody)
            ? tiposBody
            : [],

        documentos: Array.isArray(documentosBody)
            ? documentosBody
            : [],
    };
}

function DocumentosEstudiante({ estudiante, onClose }) {
    const dialog = useDialog();

    const estudianteId = estudiante?.id;

    const [tiposDocumento, setTiposDocumento] =
        useState([]);

    const [documentos, setDocumentos] =
        useState([]);

    const [idTipoDocumento, setIdTipoDocumento] =
        useState("");

    const [archivo, setArchivo] =
        useState(null);

    const [fechaEmision, setFechaEmision] =
        useState("");

    const [fechaVencimiento, setFechaVencimiento] =
        useState("");

    const [errores, setErrores] =
        useState({});

    const [cargando, setCargando] =
        useState(true);

    const [guardando, setGuardando] =
        useState(false);

    const [ultimoDocumento, setUltimoDocumento] =
        useState(null);

    const [errorCarga, setErrorCarga] =
        useState("");

    useEffect(() => {
        if (!estudianteId) {
            return undefined;
        }

        let activo = true;

        obtenerDatosDocumentos(estudianteId)
            .then((datos) => {
                if (!activo) {
                    return;
                }

                setTiposDocumento(
                    datos.tiposDocumento
                );

                setDocumentos(
                    datos.documentos
                );

                setErrorCarga("");
            })
            .catch((error) => {
                if (!activo) {
                    return;
                }

                setErrorCarga(
                    error.message ||
                    "No fue posible cargar la información de documentos."
                );
            })
            .finally(() => {
                if (activo) {
                    setCargando(false);
                }
            });

        return () => {
            activo = false;
        };
    }, [estudianteId]);

    const documentoActualMismoTipo =
        idTipoDocumento
            ? documentos.find(
            (documento) =>
                String(
                    documento.idTipoDocumento
                ) ===
                String(idTipoDocumento)
        ) || null
            : null;

    function limpiarError(campo) {
        setErrores((actuales) => ({
            ...actuales,
            [campo]: "",
        }));
    }

    function cambiarTipoDocumento(valor) {
        setIdTipoDocumento(valor);
        limpiarError("idTipoDocumento");
        setUltimoDocumento(null);
    }

    function cambiarArchivo(event) {
        const seleccionado =
            event.target.files?.[0] || null;

        setArchivo(seleccionado);
        limpiarError("archivo");
        setUltimoDocumento(null);
    }

    function cambiarFechaEmision(valor) {
        setFechaEmision(valor);

        limpiarError("fechaEmision");
        limpiarError("fechaVencimiento");

        setUltimoDocumento(null);
    }

    function cambiarFechaVencimiento(valor) {
        setFechaVencimiento(valor);

        limpiarError("fechaEmision");
        limpiarError("fechaVencimiento");

        setUltimoDocumento(null);
    }

    function validar() {
        const nuevosErrores = {};

        if (!idTipoDocumento) {
            nuevosErrores.idTipoDocumento =
                "Seleccione el tipo de documento.";
        }

        if (!archivo) {
            nuevosErrores.archivo =
                "Seleccione un archivo PDF.";

        } else {
            const nombre =
                archivo.name?.toLowerCase() || "";

            const tipo =
                archivo.type?.toLowerCase() || "";

            if (!nombre.endsWith(".pdf")) {
                nuevosErrores.archivo =
                    "El archivo debe tener extensión PDF.";

            } else if (tipo !== "application/pdf") {
                nuevosErrores.archivo =
                    "El archivo seleccionado debe ser un PDF.";

            } else if (archivo.size > MAX_PDF_SIZE) {
                nuevosErrores.archivo =
                    "El archivo PDF no puede superar 50 MB.";
            }
        }

        if (
            fechaEmision &&
            fechaVencimiento &&
            fechaVencimiento < fechaEmision
        ) {
            nuevosErrores.fechaVencimiento =
                "La fecha de vencimiento no puede ser anterior a la fecha de emisión.";
        }

        return nuevosErrores;
    }

    async function recargarDocumentos() {
        const datos =
            await obtenerDatosDocumentos(
                estudianteId
            );

        setTiposDocumento(
            datos.tiposDocumento
        );

        setDocumentos(
            datos.documentos
        );
    }

    async function guardar(event) {
        event.preventDefault();

        const nuevosErrores =
            validar();

        setErrores(nuevosErrores);

        if (
            Object.keys(nuevosErrores).length > 0
        ) {
            return;
        }

        if (documentoActualMismoTipo) {
            const confirmado =
                await dialog.confirmar(
                    `Ya existe un documento vigente de tipo "${documentoActualMismoTipo.nombreTipoDocumento}". El nuevo PDF lo reemplazará como documento actual, pero el anterior se conservará en el historial.`,
                    {
                        textoAceptar:
                            "Reemplazar documento",
                    }
                );

            if (!confirmado) {
                return;
            }
        }

        const data =
            new FormData();

        data.append(
            "idTipoDocumento",
            idTipoDocumento
        );

        data.append(
            "archivo",
            archivo
        );

        if (fechaEmision) {
            data.append(
                "fechaEmision",
                fechaEmision
            );
        }

        if (fechaVencimiento) {
            data.append(
                "fechaVencimiento",
                fechaVencimiento
            );
        }

        try {
            setGuardando(true);
            setUltimoDocumento(null);

            const response =
                await fetch(
                    `${API_URL}/estudiantes/${estudianteId}/documentos`,
                    {
                        method: "POST",
                        headers: getAuthHeaders(),
                        body: data,
                    }
                );

            const body =
                await response
                    .json()
                    .catch(() => ({}));

            if (!response.ok) {
                const mensaje =
                    body.message ||
                    "No fue posible adjuntar el documento.";

                const mensajeMinuscula =
                    mensaje.toLowerCase();

                if (
                    mensajeMinuscula.includes("pdf") ||
                    mensajeMinuscula.includes("archivo") ||
                    mensajeMinuscula.includes("dañado") ||
                    mensajeMinuscula.includes("válido")
                ) {
                    setErrores(
                        (actuales) => ({
                            ...actuales,
                            archivo: mensaje,
                        })
                    );

                    return;
                }

                if (
                    mensajeMinuscula.includes("fecha")
                ) {
                    setErrores(
                        (actuales) => ({
                            ...actuales,
                            fechaVencimiento: mensaje,
                        })
                    );

                    return;
                }

                throw new Error(mensaje);
            }

            setUltimoDocumento(body);

            setIdTipoDocumento("");
            setArchivo(null);
            setFechaEmision("");
            setFechaVencimiento("");
            setErrores({});

            const inputArchivo =
                document.getElementById(
                    "archivoDocumentoEstudiante"
                );

            if (inputArchivo) {
                inputArchivo.value = "";
            }

            await recargarDocumentos();

            dialog.exito(
                documentoActualMismoTipo
                    ? "Documento reemplazado correctamente."
                    : "Documento adjuntado correctamente."
            );

        } catch (error) {
            dialog.error(
                error.message ||
                "No fue posible adjuntar el documento."
            );

        } finally {
            setGuardando(false);
        }
    }

    return (
        <section className="panel documentos-estudiante">
            <div className="panel-head">
                <div>
                    <h2>
                        <FileText
                            size={18}
                            strokeWidth={1.75}
                            className="inline-icon"
                            aria-hidden="true"
                        />
                        Documentos del estudiante
                    </h2>

                    <p className="panel-head-sub">
                        {estudiante.nombreCompleto} ·{" "}
                        {estudiante.numeroIdentificacion}
                    </p>
                </div>

                <button
                    type="button"
                    className="sg-icon-btn"
                    onClick={onClose}
                    aria-label="Cerrar gestión de documentos"
                >
                    <X
                        size={20}
                        strokeWidth={1.75}
                        aria-hidden="true"
                    />
                </button>
            </div>

            <div className="panel-body stack">
                {cargando ? (
                    <Alert
                        tone="info"
                        title="Cargando documentos..."
                    />

                ) : errorCarga ? (
                    <Alert
                        tone="danger"
                        title="No fue posible cargar los documentos"
                    >
                        {errorCarga}
                    </Alert>

                ) : (
                    <>
                        {ultimoDocumento && (
                            <Alert
                                tone="success"
                                title="Documento listo"
                            >
                                {ultimoDocumento.nombreOriginal} quedó asociado correctamente al expediente del estudiante.
                            </Alert>
                        )}

                        <div className="documentos-estudiante__actuales">
                            <h3>
                                Documentos vigentes
                            </h3>

                            {documentos.length === 0 ? (
                                <p className="muted">
                                    El estudiante todavía no tiene documentos vigentes registrados.
                                </p>

                            ) : (
                                <div className="documentos-estudiante__lista">
                                    {documentos.map(
                                        (documento) => (
                                            <div
                                                key={documento.id}
                                                className="documentos-estudiante__item"
                                            >
                                                <div>
                                                    <b>
                                                        {documento.nombreTipoDocumento}
                                                    </b>

                                                    <span>
                                                        {documento.nombreOriginal}
                                                    </span>
                                                </div>

                                                <span className="documentos-estudiante__estado">
                                                    Vigente
                                                </span>
                                            </div>
                                        )
                                    )}
                                </div>
                            )}
                        </div>

                        <form
                            onSubmit={guardar}
                            noValidate
                        >
                            <div className="form-grid">
                                <Field
                                    id="tipoDocumentoEstudiante"
                                    label="Tipo de documento"
                                    error={errores.idTipoDocumento}
                                    errorKey="idTipoDocumento"
                                >
                                    <select
                                        id="tipoDocumentoEstudiante"
                                        className="sg-input sg-select"
                                        value={idTipoDocumento}
                                        onChange={(event) =>
                                            cambiarTipoDocumento(
                                                event.target.value
                                            )
                                        }
                                        aria-invalid={
                                            Boolean(
                                                errores.idTipoDocumento
                                            ) || undefined
                                        }
                                    >
                                        <option value="">
                                            Seleccione un tipo
                                        </option>

                                        {tiposDocumento.map(
                                            (tipo) => (
                                                <option
                                                    key={tipo.id}
                                                    value={tipo.id}
                                                >
                                                    {tipo.nombre}
                                                </option>
                                            )
                                        )}
                                    </select>
                                </Field>

                                <Field
                                    id="archivoDocumentoEstudiante"
                                    label="Archivo PDF"
                                    hint="Solo PDF. Tamaño máximo: 50 MB."
                                    error={errores.archivo}
                                    errorKey="archivo"
                                >
                                    <input
                                        id="archivoDocumentoEstudiante"
                                        className="sg-input"
                                        type="file"
                                        accept="application/pdf,.pdf"
                                        onChange={cambiarArchivo}
                                        aria-invalid={
                                            Boolean(
                                                errores.archivo
                                            ) || undefined
                                        }
                                    />
                                </Field>

                                <Field
                                    id="fechaEmisionDocumento"
                                    label="Fecha de emisión"
                                    optional
                                    error={errores.fechaEmision}
                                    errorKey="fechaEmision"
                                >
                                    <input
                                        id="fechaEmisionDocumento"
                                        className="sg-input"
                                        type="date"
                                        value={fechaEmision}
                                        onChange={(event) =>
                                            cambiarFechaEmision(
                                                event.target.value
                                            )
                                        }
                                    />
                                </Field>

                                <Field
                                    id="fechaVencimientoDocumento"
                                    label="Fecha de vencimiento"
                                    optional
                                    error={errores.fechaVencimiento}
                                    errorKey="fechaVencimiento"
                                >
                                    <input
                                        id="fechaVencimientoDocumento"
                                        className="sg-input"
                                        type="date"
                                        value={fechaVencimiento}
                                        onChange={(event) =>
                                            cambiarFechaVencimiento(
                                                event.target.value
                                            )
                                        }
                                    />
                                </Field>
                            </div>

                            {documentoActualMismoTipo && (
                                <Alert
                                    tone="warning"
                                    title="Este tipo ya tiene un documento vigente"
                                >
                                    Al guardar el nuevo archivo se reemplazará como documento vigente. El anterior se conservará como historial.
                                </Alert>
                            )}

                            <div className="page-actions documentos-estudiante__acciones">
                                <button
                                    type="button"
                                    className="sg-btn sg-btn--secondary"
                                    onClick={onClose}
                                    disabled={guardando}
                                >
                                    Cancelar
                                </button>

                                <button
                                    type="submit"
                                    className={`sg-btn sg-btn--primary${guardando ? " is-loading" : ""}`}
                                    disabled={
                                        guardando ||
                                        cargando
                                    }
                                    aria-busy={
                                        guardando ||
                                        undefined
                                    }
                                >
                                    <Upload
                                        size={18}
                                        strokeWidth={1.75}
                                        aria-hidden="true"
                                    />

                                    {guardando
                                        ? "Adjuntando..."
                                        : documentoActualMismoTipo
                                            ? "Reemplazar documento"
                                            : "Adjuntar documento"}
                                </button>
                            </div>
                        </form>
                    </>
                )}
            </div>
        </section>
    );
}

export default DocumentosEstudiante;