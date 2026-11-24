package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Ejecuta la consulta nativa real de findNivelesInstrumentoAprobados
 * (leída de su @Query) en una base H2 en memoria con las columnas que usa.
 *
 * Comprueba que el instrumento de lo aprobado sale del detalle
 * (md.id_instrumento) y, en matrículas anteriores al cambio, del espacio.
 */
class NivelesInstrumentoConsultaTest {

    private static final int VIOLIN = 7;
    private static final int VIOLA = 8;

    private Connection conexion;

    @BeforeEach
    void setUp() throws Exception {
        conexion = DriverManager.getConnection("jdbc:h2:mem:niveles_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        try (Statement st = conexion.createStatement()) {
            st.execute("CREATE TABLE estados_academicos (id_estado_academico INT PRIMARY KEY, codigo VARCHAR(30))");
            st.execute("CREATE TABLE autorizaciones_matricula (id_autorizacion INT PRIMARY KEY, id_estudiante INT)");
            st.execute("CREATE TABLE matriculas (id_matricula INT PRIMARY KEY, id_autorizacion INT)");
            st.execute("CREATE TABLE grupos (id_grupo INT PRIMARY KEY, tipo_grupo VARCHAR(30), id_instrumento SMALLINT, id_periodo INT, id_curso INT)");
            st.execute("CREATE TABLE matriculas_detalles (id_detalle_matricula INT PRIMARY KEY, id_matricula INT, id_grupo INT, id_curso INT, id_instrumento SMALLINT)");
            st.execute("CREATE TABLE calificaciones (id_calificacion INT PRIMARY KEY, id_detalle_matricula INT, id_estado_academico INT, nota_final DECIMAL(5,2))");

            st.execute("INSERT INTO estados_academicos VALUES (1, 'APROBADO'), (2, 'REPROBADO')");
            st.execute("INSERT INTO autorizaciones_matricula VALUES (10, 4), (11, 4), (12, 99)");
            st.execute("INSERT INTO matriculas VALUES (20, 10), (21, 11), (22, 12)");

            // 1001: espacio de cualquier instrumento (sin instrumento). 1002: espacio de Violín.
            st.execute("INSERT INTO grupos VALUES (1001, 'ESPACIO_INSTRUMENTO', NULL, 3, NULL), "
                    + "(1002, 'ESPACIO_INSTRUMENTO', 7, 2, NULL)");

            // Estudiante 4: Instrumento I (201) en Viola en el espacio compartido;
            // Instrumento I en Violín en una matrícula anterior al cambio (sin id_instrumento).
            st.execute("INSERT INTO matriculas_detalles VALUES (30, 20, 1001, 201, 8), (31, 21, 1002, 201, NULL), "
                    + "(32, 22, 1001, 202, 7)");
            st.execute("INSERT INTO calificaciones VALUES (40, 30, 1, 90), (41, 31, 1, 85), (42, 32, 1, 70)");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        conexion.close();
    }

    @Test
    void elInstrumentoAprobadoSaleDelDetalleOSiNoDelEspacio() throws Exception {
        String sql = MatriculaDetalleRepository.class
                .getMethod("findNivelesInstrumentoAprobados", Long.class)
                .getAnnotation(Query.class)
                .value()
                .replace(":idEstudiante", "?");

        Map<Integer, Integer> cursoPorInstrumento = new HashMap<>();
        int filas = 0;

        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setLong(1, 4L);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas++;
                    cursoPorInstrumento.put(rs.getInt(2), rs.getInt(1));
                }
            }
        }

        // Una fila por instrumento; la del otro estudiante (99) no aparece.
        assertEquals(2, filas);
        assertEquals(201, cursoPorInstrumento.get(VIOLA));
        assertEquals(201, cursoPorInstrumento.get(VIOLIN));
    }
}
