import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Check, Clock } from "lucide-react";

import { API_URL } from "../../config/api";
import Alert from "../../components/ui/Alert";
import CedulaInput from "../../components/ui/CedulaInput";
import Field from "../../components/ui/Field";
import PasswordField from "../../components/ui/PasswordField";
import RequisitosContrasena from "../../components/ui/RequisitosContrasena";
import { revisarContrasena } from "../../components/ui/politicaContrasena";
import { CONTACTO_ACUSAB, soloDigitos } from "../../constants/contacto";
import { mostrarIdentificacion } from "../../utils/cedula";
import { limpiarIdentificacion } from "../../utils/validaciones";
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

// Hora local para los avisos, por ejemplo «4:52 p. m.»
function horaCorta(ms) {
    return new Date(ms).toLocaleTimeString("es-CR", { hour: "numeric", minute: "2-digit" });
}

// proximoEn del backend (ISO-8601 UTC) en ms; si no viene, null
function msDe(iso) {
    const ms = iso ? Date.parse(iso) : NaN;
    return Number.isFinite(ms) ? ms : null;
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

    // Momentos (en ms) en que vence el código y en que se puede reenviar.
    // venceEn = null: no se sabe cuándo vence el último código (llegó un 429
    // desde el paso 1, por ejemplo tras recargar); se deja escribirlo igual.
    const [venceEn, setVenceEn] = useState(0);
    const [reenvioEn, setReenvioEn] = useState(0);
    const [ahora, setAhora] = useState(() => Date.now());

    // Límite de códigos por hora que informa el backend (igual exista o no la cédula).
    // aviso: null | "nuevo" (reenvío) | "uno" (le queda 1) | "hora" (ya pidió todos)
    const [restantes, setRestantes] = useState(null);
    const [aviso, setAviso] = useState(null);

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

    const vigenciaDesconocida = venceEn === null;
    const segundosVigencia = vigenciaDesconocida ? 0 : Math.max(0, Math.ceil((venceEn - ahora) / 1000));
    const segundosReenvio = Math.max(0, Math.ceil((reenvioEn - ahora) / 1000));
    const codigoVencido = !vigenciaDesconocida && segundosVigencia === 0;

    const requisitos = revisarContrasena(contrasena);
    const contrasenaValida = requisitos.every((r) => r.ok);
    const coinciden = confirmacion.length > 0 && contrasena === confirmacion;

    /*
     * Pide un código. Devuelve true si se pidió, "limite" si el backend
     * respondió 429 DEMASIADOS_CODIGOS (igual se pasa al paso 2, para escribir
     * el último código recibido) o false si hubo otro error.
     */
    async function enviarCodigo({ reenvio = false } = {}) {
        setError("");
        setCargando(true);
        try {
            const response = await fetch(`${API_URL}/auth/recuperar-contrasena/solicitar`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ cedula: limpiarIdentificacion(cedula.trim()) }),
            });

            const t = Date.now();
            setAhora(t);

            if (response.status === 429) {
                const data = await response.clone().json().catch(() => null);
                if (data?.codigo === "DEMASIADOS_CODIGOS") {
                    setReenvioEn(msDe(data.proximoEn) ?? t + SEGUNDOS_ENTRE_ENVIOS * 1000);
                    setRestantes(data.restantes);
                    // Por la hora: aviso rojo. Por el minuto: solo el contador del botón.
                    if (data.restantes === 0) setAviso("hora");
                    // Desde el paso 1 no se sabe cuándo vence el último código
                    if (paso === 1) {
                        setVenceEn(null);
                        setCodigo("");
                    }
                    return "limite";
                }
            }

            if (!response.ok) {
                setError(await leerMensaje(response, "No se pudo enviar el código. Intente de nuevo."));
                return false;
            }

            const data = await response.json().catch(() => null);
            const quedan = Number.isInteger(data?.restantes) ? data.restantes : null;
            setVenceEn(t + MINUTOS_VIGENCIA * 60 * 1000);
            setReenvioEn(msDe(data?.proximoEn) ?? t + SEGUNDOS_ENTRE_ENVIOS * 1000);
            setRestantes(quedan);
            if (quedan === 0) setAviso("hora");
            else if (quedan === 1) setAviso("uno");
            else if (reenvio && quedan !== null) setAviso("nuevo");
            else setAviso(null);
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
        if (await enviarCodigo({ reenvio: false })) setPaso(2);
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
                body: JSON.stringify({ cedula: limpiarIdentificacion(cedula.trim()), codigo, contrasenaNueva: contrasena }),
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

    /*
     * Aviso del límite de códigos (paso 2), según lo último que respondió el
     * backend. El rojo se quita cuando ya puede pedir otro código.
     */
    let avisoLimite = null;
    if (aviso === "hora" && segundosReenvio > 0) {
        avisoLimite = (
            <Alert tone="danger" title="Ya pidió 5 códigos en la última hora">
                Por seguridad, podrá pedir otro a las {horaCorta(reenvioEn)}. Mientras tanto, el último código
                que le llegó sigue sirviendo 15 minutos: búsquelo también en Correo no deseado o Spam.
            </Alert>
        );
    } else if (aviso === "uno") {
        avisoLimite = (
            <Alert tone="warning" title="Le queda 1 código en esta hora">
                Antes de pedir otro, revise Correo no deseado o Spam: el código pudo haber llegado ahí.
            </Alert>
        );
    } else if (aviso === "nuevo" && restantes >= 2) {
        avisoLimite = (
            <Alert tone="info" title="Le enviamos un código nuevo">
                El código anterior ya no sirve: use el del correo más reciente. Puede pedir {restantes} códigos
                más en esta hora.
            </Alert>
        );
    }

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
                        Le enviaremos un código de 6 dígitos al correo registrado en la cuenta de esa cédula. Si varias cuentas comparten el correo, el mensaje indica de qué cuenta es el código.
                    </p>

                    <form onSubmit={handleSolicitar} className="auth-form" noValidate>
                        {avisoError}

                        <Field id="cedula" label="Cédula">
                            {/* Con dígitos se muestra como 1-1111-1111; con letras (DIMEX o pasaporte) queda libre */}
                            <CedulaInput
                                id="cedula"
                                className="sg-input"
                                mascara="auto"
                                inputMode="numeric"
                                placeholder="1-1111-1111"
                                value={cedula}
                                onChange={setCedula}
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
                        Si la cédula <b>{mostrarIdentificacion(limpiarIdentificacion(cedula.trim()))}</b> está registrada, recibirá un código en unos minutos.
                        Revise también la carpeta de correo no deseado.
                    </p>

                    <form onSubmit={handleContinuar} className="auth-form" noValidate>
                        {avisoError}

                        {avisoLimite}

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
                            {vigenciaDesconocida
                                ? <span id="vigencia-codigo" className="timer" />
                                : (
                                    <span id="vigencia-codigo" className={`timer${codigoVencido ? " is-expired" : ""}`}>
                                        <Clock size={16} strokeWidth={1.75} aria-hidden="true" />
                                        {codigoVencido
                                            ? "El código venció. Solicite uno nuevo."
                                            : <>El código vence en <b>{formatearTiempo(segundosVigencia)}</b></>}
                                    </span>
                                )}
                            <button
                                type="button"
                                className="link-btn"
                                onClick={() => enviarCodigo({ reenvio: true })}
                                disabled={cargando || segundosReenvio > 0}
                            >
                                {segundosReenvio === 0
                                    ? "Reenviar código"
                                    : segundosReenvio < 120
                                        ? `Reenviar en ${segundosReenvio} s`
                                        : `Disponible a las ${horaCorta(reenvioEn)}`}
                            </button>
                        </div>

                        {/* Hotmail/Outlook suele mandar el código a Correo no deseado */}
                        <Alert tone="info">
                            ¿No le llegó el código? Revise también la carpeta Correo no deseado o Spam.
                            Si no lo encuentra, comuníquese con ACUSAB por WhatsApp al{" "}
                            <a href={`https://wa.me/${soloDigitos(CONTACTO_ACUSAB.whatsapp)}`} target="_blank" rel="noopener noreferrer">
                                8708-5959
                            </a>{" "}
                            para que le den acceso.
                        </Alert>

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
