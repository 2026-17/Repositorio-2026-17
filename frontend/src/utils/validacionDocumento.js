/*
 * Reglas de los documentos PDF del estudiante. Las usan Documentos del
 * estudiante y la póliza de Editar estudiante; el backend aplica las mismas.
 */
export const MAX_PDF_SIZE = 50 * 1024 * 1024;

// Mensaje de error del archivo, o "" si es un PDF aceptable.
export function errorArchivoPdf(archivo) {
    if (!archivo) return "Seleccione un archivo PDF.";
    const nombre = archivo.name?.toLowerCase() || "";
    const tipo = archivo.type?.toLowerCase() || "";
    if (!nombre.endsWith(".pdf")) return "El archivo debe tener extensión PDF.";
    if (tipo !== "application/pdf") return "El archivo seleccionado debe ser un PDF.";
    if (archivo.size > MAX_PDF_SIZE) return "El archivo PDF no puede superar 50 MB.";
    return "";
}

/*
 * Póliza del estudiante: PDF o imagen (JPG, PNG o WEBP), hasta 50 MB. Los
 * demás documentos siguen solo en PDF. El backend revisa además el
 * contenido real del archivo.
 */
export const CODIGO_POLIZA = "POLIZA";
export const ACCEPT_POLIZA = "application/pdf,.pdf,image/jpeg,image/png,image/webp";
export const AYUDA_FORMATO_POLIZA = "PDF o imagen (JPG, PNG o WEBP)";
export const MENSAJE_FORMATO_POLIZA = "La póliza debe ser un PDF o una imagen (JPG, PNG o WEBP).";
const TIPOS_POLIZA = ["application/pdf", "image/jpeg", "image/png", "image/webp"];

// Mensaje de error de la póliza, o "" si es un PDF o una imagen aceptable.
export function errorArchivoPoliza(archivo) {
    if (!archivo) return "Seleccione la póliza.";
    const tipo = archivo.type?.toLowerCase() || "";
    if (!TIPOS_POLIZA.includes(tipo)) return MENSAJE_FORMATO_POLIZA;
    if (archivo.size > MAX_PDF_SIZE) return "La póliza no puede superar 50 MB.";
    return "";
}

// Mensaje de error de las fechas, o "" si son coherentes.
export function errorFechasDocumento(fechaEmision, fechaVencimiento) {
    if (fechaEmision && fechaVencimiento && fechaVencimiento < fechaEmision) {
        return "La fecha de vencimiento no puede ser anterior a la fecha de emisión.";
    }
    return "";
}
