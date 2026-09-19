import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";
import {
    CircleCheck,
    CircleQuestionMark,
    CircleX,
    Info,
    TriangleAlert,
} from "lucide-react";

import { DialogContext } from "./DialogContext";

import "./dialog.css";

const TIPOS = {
    exito: {
        titulo: "Operación exitosa",
        Icono: CircleCheck,
    },
    error: {
        titulo: "Ocurrió un error",
        Icono: CircleX,
    },
    aviso: {
        titulo: "Atención",
        Icono: TriangleAlert,
    },
    info: {
        titulo: "Información",
        Icono: Info,
    },
    validacion: {
        titulo: "Hay campos por corregir",
        Icono: TriangleAlert,
    },
    confirmar: {
        titulo: "Confirmar acción",
        Icono: CircleQuestionMark,
    },
};

/*
 * Acepta texto, Error o cualquier valor y devuelve un mensaje legible.
 */
function textoDe(valor) {
    if (valor instanceof Error) {
        return valor.message;
    }

    return valor == null ? "" : String(valor);
}

let contador = 0;

/*
 * Muestra un diálogo modal centrado en pantalla con botón(es) de acción.
 *
 * Si se disparan varios diálogos seguidos, se encolan y se muestran de a
 * uno, en el orden en que fueron solicitados.
 */
function DialogProvider({ children }) {
    /*
     * La cola vive en una referencia (fuente de verdad, síncrona) y se
     * refleja en el estado para renderizar. Así se detectan duplicados
     * incluso si se disparan varios avisos en el mismo instante.
     */
    const colaRef = useRef([]);
    const [cola, setCola] = useState([]);

    const botonPrincipalRef = useRef(null);
    const cajaRef = useRef(null);
    const foco = useRef(null);

    const actual = cola[0] || null;

    const abrir = useCallback((tipo, mensaje, opciones = {}) => {
        return new Promise((resolve) => {
            contador += 1;

            const nuevo = {
                id: contador,
                tipo,
                mensaje: textoDe(mensaje),
                titulo: opciones.titulo || TIPOS[tipo].titulo,
                lista: Array.isArray(opciones.lista)
                    ? opciones.lista.map(textoDe).filter(Boolean)
                    : [],
                textoAceptar: opciones.textoAceptar || "Aceptar",
                textoCancelar: opciones.textoCancelar || "Cancelar",
                peligro: Boolean(opciones.peligro),
                resolve,
            };

            /*
             * Un aviso idéntico a uno que ya espera (mismo tipo, título,
             * mensaje y detalle) no se apila. Las confirmaciones nunca se
             * descartan porque esperan una respuesta.
             */
            const duplicado =
                tipo !== "confirmar" &&
                colaRef.current.some(
                    (d) =>
                        d.tipo === nuevo.tipo &&
                        d.titulo === nuevo.titulo &&
                        d.mensaje === nuevo.mensaje &&
                        d.lista.join("\n") === nuevo.lista.join("\n")
                );

            if (duplicado) {
                resolve(true);
                return;
            }

            colaRef.current = [...colaRef.current, nuevo];
            setCola(colaRef.current);
        });
    }, []);

    const cerrar = useCallback((resultado) => {
        const [primero, ...resto] = colaRef.current;

        colaRef.current = resto;
        setCola(resto);

        primero?.resolve(resultado);
    }, []);

    const api = useMemo(
        () => ({
            exito: (mensaje, opciones) =>
                abrir("exito", mensaje, opciones).then(() => undefined),
            error: (mensaje, opciones) =>
                abrir("error", mensaje, opciones).then(() => undefined),
            aviso: (mensaje, opciones) =>
                abrir("aviso", mensaje, opciones).then(() => undefined),
            info: (mensaje, opciones) =>
                abrir("info", mensaje, opciones).then(() => undefined),

            /*
             * Resumen de validación de formulario. Recibe la lista de
             * mensajes (o el objeto de errores { campo: mensaje }).
             */
            validacion: (errores, opciones = {}) => {
                const lista = Array.isArray(errores)
                    ? errores
                    : Object.values(errores || {});

                return abrir(
                    "validacion",
                    opciones.mensaje ||
                        "Revise la información marcada e intente de nuevo.",
                    { ...opciones, lista }
                ).then(() => undefined);
            },

            confirmar: (mensaje, opciones) =>
                abrir("confirmar", mensaje, {
                    textoAceptar: "Confirmar",
                    ...opciones,
                }),
        }),
        [abrir]
    );

    /*
     * Al abrir un diálogo: bloquea el scroll de fondo, mueve el foco al
     * botón principal y, al cerrar, devuelve el foco a donde estaba.
     */
    useEffect(() => {
        if (!actual) {
            return undefined;
        }

        foco.current = document.activeElement;

        const overflowAnterior = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        botonPrincipalRef.current?.focus();

        return () => {
            document.body.style.overflow = overflowAnterior;

            if (foco.current && document.contains(foco.current)) {
                foco.current.focus?.();
            }
        };
    }, [actual?.id]); // eslint-disable-line react-hooks/exhaustive-deps

    /*
     * Esc cierra (cancela en confirmaciones) y Tab queda dentro del
     * diálogo.
     */
    useEffect(() => {
        if (!actual) {
            return undefined;
        }

        function alTeclear(evento) {
            if (evento.key === "Escape") {
                evento.preventDefault();
                cerrar(actual.tipo === "confirmar" ? false : true);
                return;
            }

            if (evento.key !== "Tab") {
                return;
            }

            const focales = cajaRef.current?.querySelectorAll("button");

            if (!focales || focales.length === 0) {
                return;
            }

            const primero = focales[0];
            const ultimo = focales[focales.length - 1];

            if (evento.shiftKey && document.activeElement === primero) {
                evento.preventDefault();
                ultimo.focus();
            } else if (!evento.shiftKey && document.activeElement === ultimo) {
                evento.preventDefault();
                primero.focus();
            }
        }

        document.addEventListener("keydown", alTeclear);

        return () => document.removeEventListener("keydown", alTeclear);
    }, [actual, cerrar]);

    const Icono = actual ? TIPOS[actual.tipo].Icono : null;
    const esConfirmacion = actual?.tipo === "confirmar";
    const tipoVisual = actual
        ? esConfirmacion && actual.peligro
            ? "peligro"
            : actual.tipo
        : "";

    return (
        <DialogContext.Provider value={api}>
            {children}

            {actual && (
                <div className="dialog-overlay">
                    <div
                        ref={cajaRef}
                        className={`dialog-box dialog-box--${tipoVisual}`}
                        role="alertdialog"
                        aria-modal="true"
                        aria-labelledby={`dialog-titulo-${actual.id}`}
                        aria-describedby={`dialog-mensaje-${actual.id}`}
                    >
                        <div className="dialog-icon" aria-hidden="true">
                            <Icono size={32} />
                        </div>

                        <h2
                            className="dialog-title"
                            id={`dialog-titulo-${actual.id}`}
                        >
                            {actual.titulo}
                        </h2>

                        {actual.mensaje && (
                            <p
                                className="dialog-message"
                                id={`dialog-mensaje-${actual.id}`}
                            >
                                {actual.mensaje}
                            </p>
                        )}

                        {actual.lista.length > 0 && (
                            <ul className="dialog-list">
                                {actual.lista.map((item, indice) => (
                                    <li key={`${indice}-${item}`}>{item}</li>
                                ))}
                            </ul>
                        )}

                        <div className="dialog-actions">
                            {esConfirmacion && (
                                <button
                                    type="button"
                                    className="dialog-btn dialog-btn--secondary"
                                    onClick={() => cerrar(false)}
                                >
                                    {actual.textoCancelar}
                                </button>
                            )}

                            <button
                                type="button"
                                ref={botonPrincipalRef}
                                className={`dialog-btn ${
                                    actual.peligro
                                        ? "dialog-btn--danger"
                                        : "dialog-btn--primary"
                                }`}
                                onClick={() => cerrar(true)}
                            >
                                {actual.textoAceptar}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </DialogContext.Provider>
    );
}

export default DialogProvider;
