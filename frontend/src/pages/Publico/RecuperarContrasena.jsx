import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Check, Clock } from "lucide-react";

import { API_URL } from "../../config/api";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import PasswordField from "../../components/ui/PasswordField";
import RequisitosContrasena from "../../components/ui/RequisitosContrasena";
import { revisarContrasena } from "../../components/ui/politicaContrasena";
import AuthLayout from "./AuthLayout";

import "./publico.css";
import "./recuperarContrasena.css";

/*
 * Deben coincidir con el backend: CodigoVerificacionService
 * (15 min de vigencia, 1 min entre envíos). Las reglas de la contraseña
 * están en politicaContrasena.js.
 */
const MINUTOS_VIGENCIA = 15;
const SEGUNDOS_ENTRE_ENVIOS = 60;

/*
 * Lee el mensaje de error del backend ({ status, message }).
 * Si la respuesta no trae JSON, usa el mensaje por defecto.
 */
async function leerMensaje(response, porDefecto) {
    const data = await response.json().catch(() => null);
    return data?.message || porDefecto;
}

function formatearTiempo(segundos) {
    const m = String(Math.floor(segundos / 60)).padStart(2, "0");
    const s = String(segundos % 60).padStart(2, "0");
    return `${m}:${s}`;
}

/*
 * Código de 6 dígitos en casillas separadas (como el prototipo).
 * El valor sigue siendo un solo texto: cada casilla muestra un dígito,
 * se puede pegar el código completo y Retroceso vuelve a la anterior.
 */
function CasillasCodigo({ valor, onChange, invalido, describedBy }) {
    const refs = useRef([]);
    const digitos = Array.from({ length: 6 }, (_, i) => valor[i] || "");

    function enfocar(i) {
        const casilla = refs.current[Math.max(0, Math.min(5, i))];
        if (casilla) casilla.focus();
    }

    function alEscribir(i, texto) {
        const nuevos = texto.replace(/\D/g, "");
        if (!nuevos) return;
        const desde = Math.min(i, valor.length);
        const siguiente = (valor.slice(0, desde) + nuevos).slice(0, 6);
        onChange(siguiente);
        enfocar(siguiente.length);
    }

    function alPresionar(i, event) {
        if (event.key === "Backspace") {
            event.preventDefault();
            const hasta = digitos[i] ? i : i - 1;
            if (hasta < 0) return;
            onChange(valor.slice(0, hasta));
            enfocar(hasta);
        }
    }

    return (
        <div className="code-inputs">
            {digitos.map((d, i) => (
                <input
                    key={i}
                    ref={(el) => { refs.current[i] = el; }}
                    id={i === 0 ? "codigo" : undefined}
                    value={d}
                    inputMode="numeric"
                    autoComplete={i === 0 ? "one-time-code" : "off"}
                    maxLength={i === 0 ? 6 : 1}
                    aria-label={`Dígito ${i + 1} de 6`}
                    aria-invalid={invalido ? "true" : undefined}
                    aria-describedby={describedBy}
                    onChange={(e) => alEscribir(i, e.target.value)}
                    onKeyDown={(e) => alPresionar(i, e)}
                    onFocus={(e) => e.target.select()}
                />
            ))}
        </div>
    );
}

function RecuperarContrasena() {
    const navigate = useNavigate();

    const [paso, setPaso] = useState(1);
    const [cedula, setCedula] = useState("");
    const [codigo, setCodigo] = useState("");
    const [contrasena, setContrasena] = useState("");
    const [confirmacion, setConfirmacion] = useState("");
    const [error, setError] = useState("");
    const [cargando, setCargando] = useState(false);

    // Momentos (en ms) en que vence el código y en que se puede reenviar
    const [venceEn, setVenceEn] = useState(0);
    const [reenvioEn, setReenvioEn] = useState(0);
    const [ahora, setAhora] = useState(() => Date.now());

    // Reloj: actualiza "ahora" cada segundo mientras se está en el paso 2 o 3
    useEffect(() => {
        if (paso !== 2 && paso !== 3) return undefined;
        const intervalo = setInterval(() => setAhora(Date.now()), 1000);
        return () => clearInterval(intervalo);
    }, [paso]);

    // Al cambiar de paso, el foco va al título (lectores de pantalla)
    useEffect(() => {
        const titulo = document.getElementById("rec-title");
        if (titulo) titulo.focus();
    }, [paso]);

    const segundosVigencia = Math.max(0, Math.ceil((venceEn - ahora) / 1000));
    const segundosReenvio = Math.max(0, Math.ceil((reenvioEn - ahora) / 1000));
    const codigoVencido = segundosVigencia === 0;

    const requisitos = revisarContrasena(contrasena);
    const contrasenaValida = requisitos.every((r) => r.ok);
    const coinciden = confirmacion.length > 0 && contrasena === confirmacion;

    async function enviarCodigo() {
        setError("");
        setCargando(true);
        try {
            const response = await fetch(`${API_URL}/auth/recuperar-contrasena/solicitar`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ cedula: cedula.trim() }),
            });

            if (!response.ok) {
                setError(await leerMensaje(response, "No se pudo enviar el código. Intente de nuevo."));
                return false;
            }

            const t = Date.now();
            setAhora(t);
            setVenceEn(t + MINUTOS_VIGENCIA * 60 * 1000);
            setReenvioEn(t + SEGUNDOS_ENTRE_ENVIOS * 1000);
            setCodigo("");
            return true;
        } catch {
            setError("No se pudo conectar con el servidor. Intente de nuevo en unos momentos.");
            return false;
        } finally {
            setCargando(false);
        }
    }

    async function handleSolicitar(event) {
        event.preventDefault();
        if (!cedula.trim()) {
            setError("Escriba su número de cédula.");
            return;
        }
        if (await enviarCodigo()) setPaso(2);
    }

    function handleContinuar(event) {
        event.preventDefault();
        if (codigoVencido) {
            setError("El código venció. Solicite uno nuevo.");
            return;
        }
        if (codigo.length !== 6) {
            setError("Ingrese el código de 6 dígitos.");
            return;
        }
        setError("");
        setPaso(3);
    }

    async function handleRestablecer(event) {
        event.preventDefault();
        setError("");
        setCargando(true);
        try {
            const response = await fetch(`${API_URL}/auth/recuperar-contrasena/confirmar`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ cedula: cedula.trim(), codigo, contrasenaNueva: contrasena }),
            });

            if (response.ok) {
                setPaso(4);
                return;
            }

            // El código ya no sirve (incorrecto, vencido o gastado): volver al paso 2
            setError(await leerMensaje(response, "El código no es válido o ya venció. Solicite uno nuevo."));
            setCodigo("");
            setPaso(2);
        } catch {
            setError("No se pudo conectar con el servidor. Intente de nuevo en unos momentos.");
        } finally {
            setCargando(false);
        }
    }

    function irAPaso(n) {
        setError("");
        setPaso(n);
    }

    const avisoError = error && (
        <Alert tone="danger" title={error} autoFocus />
    );

    return (
        <AuthLayout>
            {paso < 4 && (
                <>
                    <button
                        type="button"
                        className="link-btn back-link"
                        onClick={paso === 1 ? () => navigate("/login") : () => irAPaso(paso - 1)}
                    >
                        <ArrowLeft size={16} strokeWidth={1.75} aria-hidden="true" />
                        {paso === 1 ? "Volver a iniciar sesión" : "Atrás"}
                    </button>
                    <p className="step-count">Paso {paso} de 3</p>
                </>
            )}

            {/* Paso 1: cédula */}
            {paso === 1 && (
                <>
                    <h1 id="rec-title" tabIndex={-1}>Recuperar contraseña</h1>
                    <p className="lead">
                        Le enviaremos un código de 6 dígitos al correo registrado en su cuenta para crear una contraseña nueva.
                    </p>

                    <form onSubmit={handleSolicitar} className="auth-form" noValidate>
                        {avisoError}

                        <Field id="cedula" label="Cédula">
                            <input
                                id="cedula"
                                type="text"
                                className="sg-input"
                                inputMode="numeric"
                                value={cedula}
                                onChange={(e) => setCedula(e.target.value)}
                                autoComplete="username"
                                maxLength={20}
                            />
                        </Field>

                        <button
                            type="submit"
                            className={`sg-btn sg-btn--primary sg-btn--lg auth-submit${cargando ? " is-loading" : ""}`}
                            disabled={cargando}
                            aria-busy={cargando || undefined}
                        >
                            {cargando && <span className="sg-spinner" aria-hidden="true" />}
                            {cargando ? "Enviando…" : "Enviar código"}
                        </button>
                    </form>
                </>
            )}

            {/* Paso 2: código */}
            {paso === 2 && (
                <>
                    <h1 id="rec-title" tabIndex={-1}>Revise su correo</h1>
                    <p className="lead">
                        Si la cédula <b>{cedula.trim()}</b> está registrada, recibirá un código en unos minutos.
                        Revise también la carpeta de correo no deseado.
                    </p>

                    <form onSubmit={handleContinuar} className="auth-form" noValidate>
                        {avisoError}

                        <fieldset className="fieldset">
                            <legend>Código de verificación</legend>
                            <CasillasCodigo
                                valor={codigo}
                                onChange={(v) => { setCodigo(v); setError(""); }}
                                invalido={Boolean(error)}
                                describedBy="vigencia-codigo"
                            />
                        </fieldset>

                        <div className="auth-row">
                            <span id="vigencia-codigo" className={`timer${codigoVencido ? " is-expired" : ""}`}>
                                <Clock size={16} strokeWidth={1.75} aria-hidden="true" />
                                {codigoVencido
                                    ? "El código venció. Solicite uno nuevo."
                                    : <>El código vence en <b>{formatearTiempo(segundosVigencia)}</b></>}
                            </span>
                            <button
                                type="button"
                                className="link-btn"
                                onClick={enviarCodigo}
                                disabled={cargando || segundosReenvio > 0}
                            >
                                {segundosReenvio > 0
                                    ? `Reenviar en ${segundosReenvio} s`
                                    : "Reenviar código"}
                            </button>
                        </div>

                        <button
                            type="submit"
                            className="sg-btn sg-btn--primary sg-btn--lg auth-submit"
                            disabled={codigoVencido || codigo.length !== 6}
                        >
                            Verificar código
                        </button>

                        <button type="button" className="link-btn auth-alt" onClick={() => irAPaso(1)}>
                            Usar otra cédula
                        </button>
                    </form>
                </>
            )}

            {/* Paso 3: nueva contraseña */}
            {paso === 3 && (
                <>
                    <h1 id="rec-title" tabIndex={-1}>Cree una contraseña nueva</h1>
                    <p className="lead">
                        Úsela la próxima vez que ingrese a SIGAC. El código se comprobará al guardarla.
                    </p>

                    <form onSubmit={handleRestablecer} className="auth-form" noValidate>
                        {avisoError}

                        <PasswordField
                            id="nuevaContrasena"
                            label="Contraseña nueva"
                            value={contrasena}
                            onChange={(e) => setContrasena(e.target.value.slice(0, 16))}
                            autoComplete="new-password"
                            describedBy="requisitos-contrasena"
                        />

                        <RequisitosContrasena id="requisitos-contrasena" requisitos={requisitos} />

                        <PasswordField
                            id="confirmarContrasena"
                            label="Confirme la contraseña"
                            value={confirmacion}
                            onChange={(e) => setConfirmacion(e.target.value.slice(0, 16))}
                            autoComplete="new-password"
                            error={confirmacion.length > 0 && !coinciden ? "Las contraseñas no coinciden." : ""}
                        />

                        <button
                            type="submit"
                            className={`sg-btn sg-btn--primary sg-btn--lg auth-submit${cargando ? " is-loading" : ""}`}
                            disabled={cargando || !contrasenaValida || !coinciden}
                            aria-busy={cargando || undefined}
                        >
                            {cargando && <span className="sg-spinner" aria-hidden="true" />}
                            {cargando ? "Guardando…" : "Guardar contraseña"}
                        </button>
                    </form>
                </>
            )}

            {/* Paso 4: éxito */}
            {paso === 4 && (
                <div className="success-screen auth-success">
                    <span className="success-mark"><Check size={32} strokeWidth={1.75} aria-hidden="true" /></span>
                    <h1 id="rec-title" tabIndex={-1}>Contraseña actualizada</h1>
                    <p className="muted">
                        Su contraseña se actualizó correctamente. Ya puede ingresar con la nueva contraseña.
                    </p>
                    <button
                        type="button"
                        className="sg-btn sg-btn--primary sg-btn--lg auth-submit"
                        onClick={() => navigate("/login", { replace: true })}
                    >
                        Ir a ingresar
                    </button>
                </div>
            )}
        </AuthLayout>
    );
}

export default RecuperarContrasena;
