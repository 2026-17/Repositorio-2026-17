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
@Table(name = "tokens_revocados", schema = "sigac_db")
public class TokensRevocado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token_revocado", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @Size(max = 36)
    @NotNull
    @Column(name = "jti", nullable = false, length = 36)
    private String jti;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario idUsuario;

    @NotNull
    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "revocado_en", nullable = false)
    private Instant revocadoEn;

    @PrePersist
    protected void alPersistir() {
        if (revocadoEn == null) revocadoEn = Instant.now();
    }
}
