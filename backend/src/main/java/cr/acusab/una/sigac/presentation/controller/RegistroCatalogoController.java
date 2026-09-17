package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.data.repository.CentroEducativoRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/registro/catalogos")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class RegistroCatalogoController {
    private final TipoIdentificacionRepository tipos;
    private final CentroEducativoRepository centros;
    private final InstrumentoRepository instrumentos;

    public RegistroCatalogoController(TipoIdentificacionRepository tipos, CentroEducativoRepository centros,
                                      InstrumentoRepository instrumentos) {
        this.tipos = tipos;
        this.centros = centros;
        this.instrumentos = instrumentos;
    }

    @GetMapping("/tipos-identificacion")
    public List<TipoIdentificacionOption> tiposIdentificacion() {
        return tipos.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getActivo()))
                .map(t -> new TipoIdentificacionOption(t.getId(), t.getCodigo(), t.getNombre())).toList();
    }

    @GetMapping("/centros-educativos")
    public List<CentroEducativoOption> centrosEducativos() {
        return centros.findAll().stream().filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .map(c -> new CentroEducativoOption(c.getId(), c.getNombre(), c.getTipo())).toList();
    }

    @GetMapping("/instrumentos")
    public List<InstrumentoOption> instrumentos() {
        return instrumentos.findAll().stream().filter(i -> Boolean.TRUE.equals(i.getActivo()))
                .map(i -> new InstrumentoOption(i.getId(), i.getCodigo(), i.getNombre())).toList();
    }

    public record TipoIdentificacionOption(Integer id, String codigo, String nombre) {}
    public record CentroEducativoOption(Long id, String nombre, String tipo) {}
    public record InstrumentoOption(Integer id, String codigo, String nombre) {}
}
