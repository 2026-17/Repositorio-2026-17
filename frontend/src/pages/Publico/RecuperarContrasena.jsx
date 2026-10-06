import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Check, Circle, CircleCheck, Eye, EyeOff } from "lucide-react";

import acusabIcon from "../../assets/acusabIcon.png";
import slide1 from "../../assets/slideshow/slide1.jpg";
import slide2 from "../../assets/slideshow/slide2.jpg";
import slide3 from "../../assets/slideshow/slide3.jpg";

import { API_URL } from "../../config/api";

import "./publico.css";
import "./recuperarContrasena.css";

/*
 * Deben coincidir con el backend:
 * CodigoVerificacionService (15 min de vigencia, 1 min entre envíos)
 * y RestablecerContrasenaRequest / PoliticaContrasena (12 a 16 caracteres,
 * con mayúscula, número y carácter especial).
 */
const MINUTOS_VIGENCIA = 15;
const SEGUNDOS_ENTRE_ENVIOS = 60;

const REQUISITOS = [
    { id: "longitud", texto: "Entre 12 y 16 caracteres", cumple: (pw) => pw.length >= 12 && pw.length <= 16 },
    { id: "mayuscula", texto: "Una letra mayúscula", cumple: (pw) => /[A-Z]/.test(pw) },
    { id: "numero", texto: "Un número", cumple: (pw) => /[0-9]/.test(pw) },
    { id: "especial", texto: "Un carácter especial", cumple: (pw) => /[^A-Za-z0-9]/.test(pw) },
];

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

function RecuperarContrasena() {
    const navigate = useNavigate();

    const [paso, setPaso] = useState(1);
    const [cedula, setCedula] = useState("");
    const [codigo, setCodigo] = useState("");
    const [contrasena, setContrasena] = useState("");
    const [confirmacion, setConfirmacion] = useState("");
    const [mostrarContrasena, setMostrarContrasena] = useState(false);
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

    const segundosVigencia = Math.max(0, Math.ceil((venceEn - ahora) / 1000));
    const segundosReenvio = Math.max(0, Math.ceil((reenvioEn - ahora) / 1000));
    const codigoVencido = segundosVigencia === 0;

    const requisitos = REQUISITOS.map((r) => ({ ...r, ok: r.cumple(contrasena) }));
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

    return (
        <div className="app-container">
            <header className="sigac-header">
                <img src={acusabIcon} alt="Logo ACUSAB" className="header-logo" />
                <h1 className="header-title">SIGAC</h1>
                <div className="header-spacer" />
            </header>

            <main className="split-layout">
                <section className="form-panel">
                    <div className="login-card recuperar-card">

                        {paso < 4 && (
                            <button type="button" className="recuperar-volver" onClick={() => navigate("/login")}>
                                <ArrowLeft size={16} /> Volver a iniciar sesión
                            </button>
                        )}

                        {paso < 4 && (
                            <div className="recuperar-pasos" aria-label={`Paso ${paso} de 3`}>
                                {[1, 2, 3].map((n) => (
                                    <span key={n} className="recuperar-pasos__grupo">
                                        {n > 1 && <span className="recuperar-pasos__linea" />}
                                        <span
                                            className={
                                                "recuperar-pasos__item" +
                                                (n < paso ? " recuperar-pasos__item--hecho" : "") +
                                                (n === paso ? " recuperar-pasos__item--activo" : "")
                                            }
                                        >
                                            {n < paso ? <Check size={14} /> : n}
                                        </span>
                                    </span>
                                ))}
                            </div>
                        )}

                        {/* Paso 1: cédula */}
                        {paso === 1 && (
                            <form onSubmit={handleSolicitar}>
                                <h2 className="login-title">Recuperar contraseña</h2>
                                <p className="login-greeting">
                                    Ingrese su cédula y le enviaremos un código de verificación al correo registrado en su cuenta.
                                </p>

                                <div className="form-group">
                                    <label htmlFor="cedula" className="form-label">Cédula</label>
                                    <input
                                        id="cedula"
                                        type="text"
                                        className="form-input"
                                        value={cedula}
                                        onChange={(e) => setCedula(e.target.value)}
                                        autoComplete="username"
                                        maxLength={20}
                                        required
                                    />
                                </div>

                                <div className="form-actions">
                                    <button type="submit" className="btn-primary" disabled={cargando}>
                                        {cargando ? "Enviando..." : "Enviar código de verificación"}
                                    </button>
                                </div>
                            </form>
                        )}

                        {/* Paso 2: código */}
                        {paso === 2 && (
                            <form onSubmit={handleContinuar}>
                                <h2 className="login-title">Ingrese su código</h2>
                                <p className="login-greeting">
                                    Si la cédula <strong>{cedula.trim()}</strong> está registrada, se envió un código de
                                    6 dígitos al correo de la cuenta. Revise también la carpeta de spam.
                                </p>

                                <div className="form-group">
                                    <label htmlFor="codigo" className="form-label">Código de verificación</label>
                                    <input
                                        id="codigo"
                                        type="text"
                                        inputMode="numeric"
                                        autoComplete="one-time-code"
                                        maxLength={6}
                                        className="form-input recuperar-codigo-input"
                                        value={codigo}
                                        onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ""))}
                                        placeholder="000000"
                                        required
                                    />
                                </div>

                                <p className={"recuperar-vigencia" + (codigoVencido ? " recuperar-vigencia--expirada" : "")}>
                                    {codigoVencido
                                        ? "El código venció. Solicite uno nuevo."
                                        : `El código vence en ${formatearTiempo(segundosVigencia)}`}
                                </p>

                                <div className="form-actions">
                                    <button type="submit" className="btn-primary" disabled={codigoVencido || codigo.length !== 6}>
                                        Continuar
                                    </button>
                                </div>

                                <button
                                    type="button"
                                    className="recuperar-reenviar"
                                    onClick={enviarCodigo}
                                    disabled={cargando || segundosReenvio > 0}
                                >
                                    {segundosReenvio > 0
                                        ? `Reenviar código (${formatearTiempo(segundosReenvio)})`
                                        : "Reenviar código"}
                                </button>

                                <button type="button" className="recuperar-reenviar" onClick={() => irAPaso(1)}>
                                    Usar otra cédula
                                </button>
                            </form>
                        )}

                        {/* Paso 3: nueva contraseña */}
                        {paso === 3 && (
                            <form onSubmit={handleRestablecer}>
                                <h2 className="login-title">Cree su nueva contraseña</h2>
                                <p className="login-greeting">
                                    El código se comprobará al guardar la nueva contraseña.
                                </p>

                                <div className="form-group">
                                    <label htmlFor="nuevaContrasena" className="form-label">Nueva contraseña</label>
                                    <div className="recuperar-password-wrapper">
                                        <input
                                            id="nuevaContrasena"
                                            type={mostrarContrasena ? "text" : "password"}
                                            className="form-input"
                                            value={contrasena}
                                            onChange={(e) => setContrasena(e.target.value)}
                                            autoComplete="new-password"
                                            maxLength={16}
                                            required
                                        />
                                        <button
                                            type="button"
                                            className="recuperar-password-toggle"
                                            onClick={() => setMostrarContrasena((v) => !v)}
                                            aria-label={mostrarContrasena ? "Ocultar contraseña" : "Mostrar contraseña"}
                                        >
                                            {mostrarContrasena ? <EyeOff size={18} /> : <Eye size={18} />}
                                        </button>
                                    </div>
                                </div>

                                <ul className="recuperar-politica">
                                    {requisitos.map((r) => (
                                        <li
                                            key={r.id}
                                            className={"recuperar-politica__item" + (r.ok ? " recuperar-politica__item--ok" : "")}
                                        >
                                            {r.ok ? <Check size={14} /> : <Circle size={14} />} {r.texto}
                                        </li>
                                    ))}
                                </ul>

                                <div className="form-group">
                                    <label htmlFor="confirmarContrasena" className="form-label">Confirmar contraseña</label>
                                    <input
                                        id="confirmarContrasena"
                                        type={mostrarContrasena ? "text" : "password"}
                                        className="form-input"
                                        value={confirmacion}
                                        onChange={(e) => setConfirmacion(e.target.value)}
                                        autoComplete="new-password"
                                        maxLength={16}
                                        required
                                    />
                                    {confirmacion.length > 0 && !coinciden && (
                                        <span className="recuperar-campo-error">Las contraseñas no coinciden.</span>
                                    )}
                                </div>

                                <div className="form-actions">
                                    <button
                                        type="submit"
                                        className="btn-primary"
                                        disabled={cargando || !contrasenaValida || !coinciden}
                                    >
                                        {cargando ? "Guardando..." : "Restablecer contraseña"}
                                    </button>
                                </div>

                                <button type="button" className="recuperar-reenviar" onClick={() => irAPaso(2)}>
                                    Volver al código
                                </button>
                            </form>
                        )}

                        {/* Paso 4: éxito */}
                        {paso === 4 && (
                            <div className="recuperar-exito">
                                <CircleCheck size={56} className="recuperar-exito__icono" />
                                <h2 className="login-title">Contraseña restablecida</h2>
                                <p className="login-greeting">
                                    Su contraseña se actualizó correctamente. Ya puede iniciar sesión con la nueva contraseña.
                                </p>
                                <div className="form-actions">
                                    <button type="button" className="btn-primary" onClick={() => navigate("/login", { replace: true })}>
                                        Ir a iniciar sesión
                                    </button>
                                </div>
                            </div>
                        )}

                        {error && <p className="error-message" role="alert">{error}</p>}
                    </div>
                </section>

                <section className="slideshow-panel">
                    <div className="slide slide-1" style={{ backgroundImage: `url(${slide1})` }} />
                    <div className="slide slide-2" style={{ backgroundImage: `url(${slide2})` }} />
                    <div className="slide slide-3" style={{ backgroundImage: `url(${slide3})` }} />
                    <div className="slideshow-overlay" />
                </section>
            </main>
        </div>
    );
}

export default RecuperarContrasena;
