package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.CodigoVerificacion;
import cr.acusab.una.sigac.data.entity.CodigoVerificacion.Proposito;
import cr.acusab.una.sigac.data.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacion, Long> {
    /**
     * Marca como usados todos los códigos pendientes del usuario, para que un
     * código viejo que quedó en el correo ya no sirva. Devuelve cuántos invalidó.
     */
    @Modifying
    @Query("UPDATE CodigoVerificacion c SET c.usadoEn = :ahora "
            + "WHERE c.idUsuario = :usuario AND c.usadoEn IS NULL")
    int invalidarPendientes(@Param("usuario") Usuario usuario, @Param("ahora") Instant ahora);


    Optional<CodigoVerificacion> findFirstByIdUsuarioAndPropositoOrderByIdDesc(Usuario usuario, Proposito proposito);
    long countByIdUsuarioAndPropositoAndCreadoEnAfter(Usuario usuario, Proposito proposito, Instant desde);
    void deleteByExpiraEnBefore(Instant instante);
}
