package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.PagoService;
import cr.acusab.una.sigac.presentation.dto.request.RegistrarPagoRequest;
import cr.acusab.una.sigac.presentation.dto.request.RegistrarPagosRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstadoMesPagoResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstadoPeriodoPagoResponse;
import cr.acusab.una.sigac.presentation.dto.response.PagoResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    /*
     * HU - Registrar pago. Administracion Academica registra el pago
     * de un estudiante y adjunta el comprobante como evidencia.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<PagoResponse> registrar(
            @Valid @RequestPart("pago") RegistrarPagoRequest request,
            @RequestPart("comprobante") MultipartFile comprobante) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.registrar(request, comprobante));
    }

    /*
     * Varios pagos del mismo período con un solo comprobante: los meses
     * marcados y, si se indica, el pago de matrícula.
     */
    @PostMapping(value = "/lote", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<EstadoPeriodoPagoResponse> registrarVarios(
            @Valid @RequestPart("pago") RegistrarPagosRequest request,
            @RequestPart("comprobante") MultipartFile comprobante) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.registrarVarios(request, comprobante));
    }

    /*
     * Estado de la matrícula y de cada mes de un período académico.
     */
    @GetMapping("/estudiante/{idEstudiante}/periodo/{idPeriodo}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<EstadoPeriodoPagoResponse> consultarEstadoPeriodo(
            @PathVariable Long idEstudiante,
            @PathVariable Long idPeriodo) {
        return ResponseEntity.ok(pagoService.consultarEstadoPeriodo(idEstudiante, idPeriodo));
    }

    /*
     * Comprobante del pago de matrícula (guardado en la solicitud de
     * matrícula del período).
     */
    @GetMapping("/matricula/{idAutorizacion}/comprobante")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<Resource> verComprobanteMatricula(@PathVariable Long idAutorizacion) {
        return archivo(pagoService.obtenerComprobanteMatricula(idAutorizacion));
    }

    /*
     * Quita un pago de matrícula registrado por error, mientras la
     * solicitud de matrícula siga pendiente.
     */
    @DeleteMapping("/matricula/{idAutorizacion}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<Void> eliminarPagoMatricula(@PathVariable Long idAutorizacion) {
        pagoService.eliminarPagoMatricula(idAutorizacion);
        return ResponseEntity.noContent().build();
    }

    /*
     * Historial financiero (pagos ya registrados) de un estudiante.
     */
    @GetMapping("/estudiante/{idEstudiante}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<List<PagoResponse>> listarHistorial(@PathVariable Long idEstudiante) {
        return ResponseEntity.ok(pagoService.listarHistorial(idEstudiante));
    }

    /*
     * Estado del pago de un estudiante para un mes especifico: si ya
     * esta pagado o pendiente, y cuanto corresponde pagar (con el
     * descuento de beca ya aplicado).
     */
    @GetMapping("/estudiante/{idEstudiante}/mes")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<EstadoMesPagoResponse> consultarEstadoMes(
            @PathVariable Long idEstudiante,
            @RequestParam Long idPeriodo,
            @RequestParam Integer anio,
            @RequestParam Short mes) {
        return ResponseEntity.ok(pagoService.consultarEstadoMes(idEstudiante, idPeriodo, anio, mes));
    }

    /*
     * Ver el comprobante adjunto a un pago ya registrado.
     */
    @GetMapping("/{idMensualidad}/comprobante")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<Resource> verComprobante(@PathVariable Long idMensualidad) {
        return archivo(pagoService.obtenerComprobante(idMensualidad));
    }

    private ResponseEntity<Resource> archivo(PagoService.ComprobanteArchivo comprobante) {
        Resource archivo = new FileSystemResource(comprobante.rutaArchivo());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(comprobante.tipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + comprobante.nombreOriginal() + "\"")
                .body(archivo);
    }

    /*
     * Elimina un pago registrado por error (y su comprobante), dejando
     * el mes pendiente de nuevo para poder registrarlo correctamente.
     */
    @DeleteMapping("/{idMensualidad}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<Void> eliminarPago(@PathVariable Long idMensualidad) {
        pagoService.eliminarPago(idMensualidad);
        return ResponseEntity.noContent().build();
    }
}
