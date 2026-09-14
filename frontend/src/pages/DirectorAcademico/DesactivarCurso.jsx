import { useState } from "react";
import { API_URL } from "../../config/api";
import "./desactivarCurso.css";

function DesactivarCurso() {

    const token = localStorage.getItem("token");

    const [idCurso, setIdCurso] = useState("");
    const [desactivando, setDesactivando] = useState(false);
    const [error, setError] = useState("");
    const [mensajeExito, setMensajeExito] = useState("");
    const [mostrarConfirmacion, setMostrarConfirmacion] = useState(false);

    function handleChange(e) {

        setIdCurso(e.target.value);

        setError("");
        setMensajeExito("");
        setMostrarConfirmacion(false);
    }

    function solicitarConfirmacion(e) {

        e.preventDefault();

        setError("");
        setMensajeExito("");

        const id = Number(idCurso);

        if (!idCurso || !Number.isInteger(id) || id <= 0) {
            setError(
                "Ingrese un identificador de curso válido."
            );
            return;
        }

        setMostrarConfirmacion(true);
    }

    function cancelarDesactivacion() {
        setMostrarConfirmacion(false);
    }

    async function confirmarDesactivacion() {

        setDesactivando(true);
        setError("");
        setMensajeExito("");

        try {

            const response = await fetch(
                `${API_URL}/cursos/${Number(idCurso)}/desactivar`,
                {
                    method: "PATCH",
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }
            );

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
                    "No tiene permisos para desactivar cursos."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.message ||
                    "No fue posible desactivar el curso."
                );
            }

            setMensajeExito(
                data.mensaje ||
                "El curso fue desactivado correctamente."
            );

            setIdCurso("");
            setMostrarConfirmacion(false);

        } catch (e) {

            setError(
                e.message ||
                "Ocurrió un error al desactivar el curso."
            );

            setMostrarConfirmacion(false);

        } finally {

            setDesactivando(false);
        }
    }

    return (
        <div className="container">

            <div className="desactivar-curso-page">

                <div className="desactivar-curso-page__header">
                    <div>
                        <h1>Desactivar curso</h1>

                        <p>
                            Desactive un curso de la malla curricular sin
                            eliminar su información histórica.
                        </p>
                    </div>
                </div>

                {error && (
                    <div
                        className="desactivar-message desactivar-message--error"
                        role="alert"
                    >
                        {error}
                    </div>
                )}

                {mensajeExito && (
                    <div
                        className="desactivar-message desactivar-message--success"
                        role="status"
                    >
                        {mensajeExito}
                    </div>
                )}

                <form
                    className="desactivar-form"
                    onSubmit={solicitarConfirmacion}
                    noValidate
                >

                    <div className="desactivar-form__section">

                        <h2>Curso a desactivar</h2>

                        <p className="desactivar-form__description">
                            Ingrese el identificador del curso que desea
                            retirar de la oferta académica vigente.
                        </p>

                        <div className="desactivar-field">

                            <label htmlFor="idCurso">
                                Identificador del curso *
                            </label>

                            <input
                                id="idCurso"
                                name="idCurso"
                                type="number"
                                min="1"
                                step="1"
                                value={idCurso}
                                onChange={handleChange}
                                placeholder="Ej. 1"
                                disabled={desactivando}
                                required
                            />

                            <small>
                                El curso será marcado como inactivo. Su
                                información histórica no será eliminada.
                            </small>

                        </div>

                    </div>

                    <p className="desactivar-required">
                        * Campo obligatorio
                    </p>

                    <div className="desactivar-form__actions">

                        <button
                            type="submit"
                            className="btn btn--danger"
                            disabled={desactivando}
                        >
                            Desactivar curso
                        </button>

                    </div>

                </form>

                {mostrarConfirmacion && (
                    <div
                        className="desactivar-confirmacion"
                        role="alertdialog"
                        aria-modal="true"
                        aria-labelledby="titulo-confirmacion"
                    >

                        <div className="desactivar-confirmacion__contenido">

                            <h2 id="titulo-confirmacion">
                                Confirmar desactivación
                            </h2>

                            <p>
                                ¿Está seguro de que desea desactivar el curso
                                con identificador <strong>{idCurso}</strong>?
                            </p>

                            <p>
                                El curso dejará de estar disponible para nuevas
                                configuraciones académicas, pero su información
                                histórica se conservará.
                            </p>

                            <div className="desactivar-confirmacion__actions">

                                <button
                                    type="button"
                                    className="btn curso-btn--secondary"
                                    onClick={cancelarDesactivacion}
                                    disabled={desactivando}
                                >
                                    Cancelar
                                </button>

                                <button
                                    type="button"
                                    className="btn btn--danger"
                                    onClick={confirmarDesactivacion}
                                    disabled={desactivando}
                                >
                                    {desactivando
                                        ? "Desactivando..."
                                        : "Sí, desactivar curso"}
                                </button>

                            </div>

                        </div>

                    </div>
                )}

            </div>

        </div>
    );
}

export default DesactivarCurso;