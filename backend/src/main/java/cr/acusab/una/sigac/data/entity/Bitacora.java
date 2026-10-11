package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "bitacoras")
public class Bitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bitacora", columnDefinition = "bigint UNSIGNED not null")
    private Long id;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "tabla_afectada", nullable = false, length = 64)
    private String tablaAfectada;

    @Column(name = "id_registro", nullable = false)
    private Long idRegistro;

    @Column(name = "accion", nullable = false, length = 10)
    private String accion;

    @Column(name = "datos_anteriores", columnDefinition = "json")
    private String datosAnteriores;

    @Column(name = "datos_nuevos", columnDefinition = "json")
    private String datosNuevos;

    @Column(name = "fecha_evento", nullable = false)
    private Instant fechaEvento;
}
