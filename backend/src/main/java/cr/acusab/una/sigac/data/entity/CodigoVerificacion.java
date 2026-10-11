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
@Table(name = "codigos_verificacion", schema = "sigac_db")
public class CodigoVerificacion {

    public enum Proposito { RECUPERACION, CAMBIO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_codigo_verificacion", columnDefinition = "bigint UNSIGNED not null")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario idUsuario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "proposito", nullable = false, length = 20)
    private Proposito proposito;

    @Size(max = 255)
    @NotNull
    @Column(name = "hash_codigo", nullable = false)
    private String hashCodigo;

    @NotNull
    @ColumnDefault("'0'")
    @Column(name = "intentos", columnDefinition = "tinyint UNSIGNED not null")
    private Short intentos;

    @NotNull
    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @PrePersist
    protected void alPersistir() {
        if (intentos == null) intentos = 0;
        if (creadoEn == null) creadoEn = Instant.now();
    }
}
