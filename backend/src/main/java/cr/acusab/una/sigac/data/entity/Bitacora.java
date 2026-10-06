package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

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

    @NotNull
    @Size(max = 64)
    @Column(name = "tabla_afectada", nullable = false, length = 64)
    private String tablaAfectada;

    @NotNull
    @Column(name = "id_registro", nullable = false)
    private Long idRegistro;

    // INSERT / UPDATE / DELETE (ver CHECK ck_bitacoras_accion en la BD)
    @NotNull
    @Size(max = 10)
    @Column(name = "accion", nullable = false, length = 10)
    private String accion;

    @Column(name = "datos_anteriores", columnDefinition = "json")
    private String datosAnteriores;

    @Column(name = "datos_nuevos", columnDefinition = "json")
    private String datosNuevos;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "fecha_evento", nullable = false)
    private Instant fechaEvento;
}
