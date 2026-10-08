package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.data.repository.CantonRepository;
import cr.acusab.una.sigac.data.repository.CentroEducativoRepository;
import cr.acusab.una.sigac.data.repository.DistritoRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.ProvinciaRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/registro/catalogos")
public class RegistroCatalogoController {
    private final TipoIdentificacionRepository tipos;
    private final CentroEducativoRepository centros;
    private final InstrumentoRepository instrumentos;
    private final ProvinciaRepository provincias;
    private final CantonRepository cantones;
    private final DistritoRepository distritos;

    public RegistroCatalogoController(TipoIdentificacionRepository tipos, CentroEducativoRepository centros,
                                      InstrumentoRepository instrumentos, ProvinciaRepository provincias,
                                      CantonRepository cantones, DistritoRepository distritos) {
        this.tipos = tipos;
        this.centros = centros;
        this.instrumentos = instrumentos;
        this.provincias = provincias;
        this.cantones = cantones;
        this.distritos = distritos;
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

    // Selectores en cascada de la dirección: provincia -> cantón -> distrito.
    @GetMapping("/provincias")
    public List<UbicacionOption> provincias() {
        return provincias.findAllByOrderByIdAsc().stream()
                .map(p -> new UbicacionOption(p.getId(), p.getNombre())).toList();
    }

    @GetMapping("/provincias/{idProvincia}/cantones")
    public List<UbicacionOption> cantones(@PathVariable Integer idProvincia) {
        return cantones.findByProvincia_IdOrderByCodigoAsc(idProvincia).stream()
                .map(c -> new UbicacionOption(c.getCodigo(), c.getNombre())).toList();
    }

    @GetMapping("/cantones/{codigoCanton}/distritos")
    public List<UbicacionOption> distritos(@PathVariable Integer codigoCanton) {
        return distritos.findByCanton_CodigoOrderByCodigoAsc(codigoCanton).stream()
                .map(d -> new UbicacionOption(d.getCodigo(), d.getNombre())).toList();
    }

    public record TipoIdentificacionOption(Integer id, String codigo, String nombre) {}
    public record CentroEducativoOption(Long id, String nombre, String tipo) {}
    public record InstrumentoOption(Integer id, String codigo, String nombre) {}
    public record UbicacionOption(Integer id, String nombre) {}
}
