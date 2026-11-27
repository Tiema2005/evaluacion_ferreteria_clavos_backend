package cl.ferreteria.avance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class ProductosRepository {
    record Categoria(long id, String nombre, String descripcion) {}
    record Producto(long id, long idCategoria, String categoria, String codigo,
                    String nombre, String descripcion, BigDecimal precio,
                    long stockActual, long stockMinimo, boolean activo) {}

    private static final String CAMPOS = """
        p.id_producto, p.id_categoria, c.nombre AS categoria, p.codigo,
        p.nombre, p.descripcion, p.precio, p.stock_actual, p.stock_minimo, p.activo
        """;
    private static final String TABLAS = """
        FROM public.producto p
        JOIN public.categoria c ON c.id_categoria = p.id_categoria
        """;
    private static final RowMapper<Producto> PRODUCTO = (rs, fila) -> new Producto(
        rs.getLong("id_producto"), rs.getLong("id_categoria"), rs.getString("categoria"),
        rs.getString("codigo"), rs.getString("nombre"), rs.getString("descripcion"),
        rs.getBigDecimal("precio"), rs.getLong("stock_actual"), rs.getLong("stock_minimo"),
        rs.getShort("activo") == 1
    );

    private final JdbcTemplate jdbc;

    ProductosRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<Categoria> categorias() {
        return jdbc.query("SELECT id_categoria, nombre, descripcion FROM public.categoria ORDER BY nombre",
            (rs, fila) -> new Categoria(rs.getLong("id_categoria"), rs.getString("nombre"),
                rs.getString("descripcion")));
    }

    boolean categoriaExiste(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM public.categoria WHERE id_categoria = ?)", Boolean.class, id));
    }

    List<Producto> listar(boolean soloActivos) {
        String filtro = soloActivos ? "WHERE p.activo = 1 " : "";
        return jdbc.query("SELECT " + CAMPOS + TABLAS + filtro + "ORDER BY p.id_producto", PRODUCTO);
    }

    Optional<Producto> buscar(long id, boolean soloActivos) {
        String filtro = soloActivos ? " AND p.activo = 1" : "";
        return jdbc.query("SELECT " + CAMPOS + TABLAS + "WHERE p.id_producto = ?" + filtro,
            PRODUCTO, id).stream().findFirst();
    }

    long crear(long idCategoria, String codigo, String nombre, String descripcion,
               BigDecimal precio, long stockActual, long stockMinimo) {
        return jdbc.queryForObject("""
            INSERT INTO public.producto
              (id_categoria, codigo, nombre, descripcion, precio, stock_actual, stock_minimo, activo)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1)
            RETURNING id_producto
            """, Long.class, idCategoria, codigo, nombre, descripcion, precio, stockActual, stockMinimo);
    }

    boolean actualizar(long id, long idCategoria, String codigo, String nombre,
                       String descripcion, BigDecimal precio, long stockMinimo) {
        return jdbc.update("""
            UPDATE public.producto
               SET id_categoria = ?, codigo = ?, nombre = ?, descripcion = ?,
                   precio = ?, stock_minimo = ?
             WHERE id_producto = ? AND activo = 1
            """, idCategoria, codigo, nombre, descripcion, precio, stockMinimo, id) == 1;
    }

    boolean desactivar(long id) {
        return jdbc.update("UPDATE public.producto SET activo = 0 WHERE id_producto = ? AND activo = 1",
            id) == 1;
    }

    Optional<Long> bloquearStock(long id) {
        return jdbc.query("""
            SELECT stock_actual FROM public.producto
             WHERE id_producto = ? AND activo = 1 FOR UPDATE
            """, (rs, fila) -> rs.getLong("stock_actual"), id).stream().findFirst();
    }

    void guardarStock(long id, long stock) {
        jdbc.update("UPDATE public.producto SET stock_actual = ? WHERE id_producto = ? AND activo = 1", stock, id);
    }

    void registrarMovimiento(long id, String tipo, long cantidad, String motivo) {
        jdbc.update("""
            INSERT INTO public.movimiento_inventario
              (id_producto, tipo_movimiento, cantidad, motivo)
            VALUES (?, ?, ?, ?)
            """, id, tipo, cantidad, motivo);
    }
}
