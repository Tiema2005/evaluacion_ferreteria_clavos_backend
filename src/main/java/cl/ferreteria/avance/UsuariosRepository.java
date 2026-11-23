package cl.ferreteria.avance;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class UsuariosRepository {
    record Usuario(long id, String nombre, String apellido, String correo, String rol, boolean activo) {}
    record Credenciales(Usuario usuario, String hash) {}

    private static final String CAMPOS = "u.id_usuario, u.nombre, u.apellido, u.correo, u.activo, r.nombre AS rol";
    private static final String TABLAS = " FROM public.usuario u JOIN public.rol r ON r.id_rol = u.id_rol ";
    private static final RowMapper<Usuario> USUARIO = (rs, fila) -> new Usuario(
        rs.getLong("id_usuario"), rs.getString("nombre"), rs.getString("apellido"),
        rs.getString("correo"), rs.getString("rol"), rs.getShort("activo") == 1
    );
    private final JdbcTemplate jdbc;

    UsuariosRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Optional<Usuario> crear(String nombre, String apellido, String correo, String hash, String rol) {
        return jdbc.query("""
            WITH creado AS (
                INSERT INTO public.usuario (id_rol, nombre, apellido, correo, password_hash)
                SELECT id_rol, ?, ?, ?, ? FROM public.rol WHERE nombre = ?
                RETURNING id_usuario, id_rol, nombre, apellido, correo, activo
            )
            SELECT %s FROM creado u JOIN public.rol r ON r.id_rol = u.id_rol
            """.formatted(CAMPOS), USUARIO, nombre, apellido, correo, hash, rol).stream().findFirst();
    }

    List<Usuario> listar() {
        return jdbc.query("SELECT " + CAMPOS + TABLAS + "ORDER BY u.id_usuario", USUARIO);
    }

    Optional<Usuario> buscar(long id) {
        return jdbc.query("SELECT " + CAMPOS + TABLAS + "WHERE u.id_usuario = ?",
            USUARIO, id).stream().findFirst();
    }

    Optional<Usuario> actualizar(long id, String nombre, String apellido, String correo,
                                 String hashOpcional, String rol) {
        return jdbc.query("""
            WITH modificado AS (
                UPDATE public.usuario
                SET nombre = ?, apellido = ?, correo = ?,
                    password_hash = COALESCE(?, password_hash),
                    id_rol = (SELECT id_rol FROM public.rol WHERE nombre = ?)
                WHERE id_usuario = ? AND activo = 1
                RETURNING id_usuario, id_rol, nombre, apellido, correo, activo
            )
            SELECT %s FROM modificado u JOIN public.rol r ON r.id_rol = u.id_rol
            """.formatted(CAMPOS), USUARIO, nombre, apellido, correo, hashOpcional, rol, id)
            .stream().findFirst();
    }

    boolean desactivar(long id) {
        return jdbc.update("UPDATE public.usuario SET activo = 0 WHERE id_usuario = ? AND activo = 1", id) == 1;
    }

    boolean rolExiste(String rol) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM public.rol WHERE nombre = ?)", Boolean.class, rol));
    }

    boolean correoExiste(String correo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM public.usuario WHERE lower(correo) = ?)", Boolean.class, correo));
    }

    Optional<Credenciales> credenciales(String correo) {
        List<Credenciales> coincidencias = jdbc.query(
            "SELECT " + CAMPOS + ", u.password_hash" + TABLAS + "WHERE lower(u.correo) = ? LIMIT 2",
            (rs, fila) -> new Credenciales(USUARIO.mapRow(rs, fila), rs.getString("password_hash")), correo
        );
        return coincidencias.size() == 1 ? Optional.of(coincidencias.get(0)) : Optional.empty();
    }
}
