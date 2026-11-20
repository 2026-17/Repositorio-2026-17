package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/*
 * Un profesor no puede tener dos grupos (o espacios de instrumento) a la
 * misma hora en períodos que se traslapan.
 */
@Service
public class ValidacionHorarioProfesorService {

    public record BloqueHorario(
            Short diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
    }

    // Bloque propuesto que choca con el horario de otro grupo del profesor.
    public record Conflicto(
            BloqueHorario propuesto,
            Grupo grupo,
            HorarioGrupo horario
    ) {
    }

    private final GrupoRepository grupoRepository;
    private final HorarioGrupoRepository horarioRepository;

    public ValidacionHorarioProfesorService(
            GrupoRepository grupoRepository,
            HorarioGrupoRepository horarioRepository
    ) {
        this.grupoRepository = grupoRepository;
        this.horarioRepository = horarioRepository;
    }

    public void validarDisponibilidad(
            Long idProfesor,
            Long idPeriodo,
            LocalDate fechaInicioDestino,
            LocalDate fechaFinDestino,
            Long idGrupoExcluido,
            List<BloqueHorario> horariosPropuestos
    ) {
        if (horariosPropuestos == null || horariosPropuestos.isEmpty()) {
            throw new BusinessException(
                    "El grupo debe tener al menos un horario registrado antes de asignarle un profesor."
            );
        }

        Optional<Conflicto> conflicto = buscarConflicto(
                idProfesor,
                idPeriodo,
                fechaInicioDestino,
                fechaFinDestino,
                idGrupoExcluido,
                horariosPropuestos
        );

        if (conflicto.isPresent()) {
            Grupo grupoConflicto = conflicto.get().grupo();

            String detalleGrupo = grupoConflicto == null
                    ? ""
                    : " (grupo " + grupoConflicto.getCodigo() + ")";

            throw new BusinessException(
                    "Conflicto de horario: el profesor ya tiene un grupo asignado en ese horario"
                            + detalleGrupo + "."
            );
        }
    }

    /*
     * Igual que validarDisponibilidad, pero sin lanzar: devuelve el primer
     * choque. Los espacios de instrumento lo usan para informar el motivo
     * de cada bloque de un lote.
     */
    public Optional<Conflicto> buscarConflicto(
            Long idProfesor,
            Long idPeriodo,
            LocalDate fechaInicioDestino,
            LocalDate fechaFinDestino,
            Long idGrupoExcluido,
            List<BloqueHorario> horariosPropuestos
    ) {
        if (horariosPropuestos == null || horariosPropuestos.isEmpty()) {
            return Optional.empty();
        }

        List<Grupo> gruposComparables = grupoRepository.findGruposProfesorEnPeriodo(
                        idProfesor,
                        idPeriodo,
                        fechaInicioDestino,
                        fechaFinDestino
                ).stream()
                .filter(grupo -> idGrupoExcluido == null
                        || !grupo.getId().equals(idGrupoExcluido))
                .toList();

        List<Long> idsGrupos = gruposComparables.stream()
                .map(Grupo::getId)
                .toList();

        if (idsGrupos.isEmpty()) {
            return Optional.empty();
        }

        List<HorarioGrupo> horariosOcupados =
                horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(idsGrupos);

        for (BloqueHorario propuesto : horariosPropuestos) {
            for (HorarioGrupo ocupado : horariosOcupados) {
                boolean mismoDia = propuesto.diaSemana().equals(ocupado.getDiaSemana());
                boolean traslape = propuesto.horaInicio().isBefore(ocupado.getHoraFin())
                        && ocupado.getHoraInicio().isBefore(propuesto.horaFin());

                if (mismoDia && traslape) {
                    Grupo grupoConflicto = gruposComparables.stream()
                            .filter(grupo -> grupo.getId().equals(ocupado.getIdGrupo()))
                            .findFirst()
                            .orElse(null);

                    return Optional.of(new Conflicto(propuesto, grupoConflicto, ocupado));
                }
            }
        }

        return Optional.empty();
    }
}
