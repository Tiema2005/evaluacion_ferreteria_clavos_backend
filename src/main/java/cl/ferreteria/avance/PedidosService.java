package cl.ferreteria.avance;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class PedidosService {

    record Item(Long idProducto, Long cantidad) {}

    record Solicitud(
        String tipoEntrega,
        String metodoPago,
        String direccion,
        List<Item> productos
    ) {}

    record CambioEstado(String estado) {}

    private final JdbcTemplate jdbc;
    private final UsuariosService usuarios;

    PedidosService(JdbcTemplate jdbc, UsuariosService usuarios) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
    }

    private static final String CONSULTA = """
        SELECT p.*, c.id_usuario,
               u.nombre, u.apellido, u.correo,
               COALESCE((
                   SELECT SUM(d.cantidad * d.precio_unitario)
                   FROM public.detalle_pedido d
                   WHERE d.id_pedido = p.id_pedido
               ), 0) AS total
        FROM public.pedido p
        JOIN public.cliente c ON c.id_cliente = p.id_cliente
        JOIN public.usuario u ON u.id_usuario = c.id_usuario
        """;

    private boolean gestion(String rol) {
        return Set.of("ADMINISTRADOR", "VENDEDOR").contains(rol);
    }

    private ResponseStatusException error(
            HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    private String normalizar(String valor) {
        return valor == null ? "" :
            valor.strip().toUpperCase(Locale.ROOT);
    }

    List<Map<String, Object>> listar(Jwt jwt) {

        var usuario = usuarios.usuarioActual(jwt);

        if (gestion(usuario.rol())) {
            return jdbc.queryForList(
                CONSULTA + " ORDER BY p.id_pedido DESC");
        }

        return jdbc.queryForList(
            CONSULTA + """
                WHERE c.id_usuario = ?
                ORDER BY p.id_pedido DESC
                """, usuario.id());
    }

    Map<String, Object> detalle(Jwt jwt, long id) {

        var usuario = usuarios.usuarioActual(jwt);

        var filas = jdbc.queryForList(
            CONSULTA + " WHERE p.id_pedido = ?", id);

        if (filas.isEmpty())
            throw error(HttpStatus.NOT_FOUND,
                "Pedido no encontrado.");

        var pedido = new LinkedHashMap<>(filas.get(0));

        long propietario =
            ((Number) pedido.get("id_usuario")).longValue();

        if (!gestion(usuario.rol()) &&
                propietario != usuario.id())
            throw error(HttpStatus.FORBIDDEN,
                "No puedes consultar pedidos de otro usuario.");

        pedido.put("productos", jdbc.queryForList("""
            SELECT d.*, p.codigo, p.nombre,
                   d.cantidad * d.precio_unitario AS subtotal
            FROM public.detalle_pedido d
            JOIN public.producto p
                ON p.id_producto = d.id_producto
            WHERE d.id_pedido = ?
            ORDER BY d.id_producto
            """, id));

        pedido.put("despacho", jdbc.queryForList("""
            SELECT * FROM public.despacho
            WHERE id_pedido = ?
            """, id));

        return pedido;
    }

    @Transactional
    public Map<String, Object> crear(Jwt jwt, Solicitud datos) {

        var usuario = usuarios.usuarioActual(jwt);

        if (!Set.of("CLIENTE", "CONTRATISTA")
                .contains(usuario.rol()))
            throw error(HttpStatus.FORBIDDEN,
                "Este rol no puede realizar compras.");

        if (datos == null || datos.productos() == null ||
                datos.productos().isEmpty())
            throw error(HttpStatus.BAD_REQUEST,
                "El pedido debe contener productos.");

        String entrega = normalizar(datos.tipoEntrega());
        String pago = normalizar(datos.metodoPago());

        if (!Set.of("RETIRO", "DESPACHO").contains(entrega))
            throw error(HttpStatus.BAD_REQUEST,
                "Tipo de entrega inválido.");

        if (!Set.of("EFECTIVO", "TARJETA", "CUENTA_CORRIENTE")
                .contains(pago))
            throw error(HttpStatus.BAD_REQUEST,
                "Método de pago inválido.");

        String direccion = datos.direccion() == null
            ? "" : datos.direccion().strip();

        if (entrega.equals("DESPACHO") &&
                (direccion.isBlank() || direccion.length() > 250))
            throw error(HttpStatus.BAD_REQUEST,
                "Debes indicar una dirección válida.");

        var cantidades = new TreeMap<Long, Long>();

        for (Item item : datos.productos()) {

            if (item == null || item.idProducto() == null ||
                    item.idProducto() < 1 ||
                    item.cantidad() == null || item.cantidad() < 1)
                throw error(HttpStatus.BAD_REQUEST,
                    "Producto o cantidad inválida.");

            if (cantidades.putIfAbsent(
                    item.idProducto(), item.cantidad()) != null)
                throw error(HttpStatus.BAD_REQUEST,
                    "El pedido contiene productos duplicados.");
        }

        String tipoCliente = usuario.rol().equals("CONTRATISTA")
            ? "CONTRATISTA" : "PARTICULAR";

        Long cliente = jdbc.queryForObject("""
            INSERT INTO public.cliente
                (id_usuario, tipo_cliente)
            VALUES (?, ?)
            ON CONFLICT (id_usuario)
            DO UPDATE SET tipo_cliente = EXCLUDED.tipo_cliente
            RETURNING id_cliente
            """, Long.class, usuario.id(), tipoCliente);

        Long pedido = jdbc.queryForObject("""
            INSERT INTO public.pedido
                (id_cliente, estado, tipo_entrega, metodo_pago)
            VALUES (?, 'PENDIENTE', ?, ?)
            RETURNING id_pedido
            """, Long.class, cliente, entrega, pago);

        BigDecimal total = BigDecimal.ZERO;

        for (var item : cantidades.entrySet()) {

            long id = item.getKey();
            long cantidad = item.getValue();

            var filas = jdbc.queryForList("""
                SELECT nombre, precio, stock_actual
                FROM public.producto
                WHERE id_producto = ? AND activo = 1
                FOR UPDATE
                """, id);

            if (filas.isEmpty())
                throw error(HttpStatus.NOT_FOUND,
                    "Producto no encontrado o inactivo.");

            var producto = filas.get(0);

            long stock =
                ((Number) producto.get("stock_actual")).longValue();

            if (cantidad > stock)
                throw error(HttpStatus.CONFLICT,
                    "Stock insuficiente para " +
                    producto.get("nombre"));

            BigDecimal precio =
                (BigDecimal) producto.get("precio");

            jdbc.update("""
                INSERT INTO public.detalle_pedido
                    (id_pedido, id_producto, cantidad, precio_unitario)
                VALUES (?, ?, ?, ?)
                """, pedido, id, cantidad, precio);

            jdbc.update("""
                UPDATE public.producto
                SET stock_actual = stock_actual - ?
                WHERE id_producto = ?
                """, cantidad, id);

            jdbc.update("""
                INSERT INTO public.movimiento_inventario
                    (id_producto, id_pedido, tipo_movimiento,
                     cantidad, motivo)
                VALUES (?, ?, 'SALIDA', ?, 'Venta por pedido')
                """, id, pedido, cantidad);

            total = total.add(
                precio.multiply(BigDecimal.valueOf(cantidad)));
        }

        if (entrega.equals("DESPACHO")) {

            jdbc.update("""
                INSERT INTO public.despacho
                    (id_pedido, direccion_entrega, estado_despacho)
                VALUES (?, ?, 'PENDIENTE')
                """, pedido, direccion);
        }

        if (pago.equals("CUENTA_CORRIENTE")) {

            if (total.signum() <= 0 ||
                    total.compareTo(
                        new BigDecimal("9999999999.99")) > 0)
                throw error(HttpStatus.BAD_REQUEST,
                    "Monto de cuenta corriente inválido.");

            int registrados = jdbc.update("""
                INSERT INTO public.movimiento_cuenta
                    (id_cuenta, id_pedido, tipo_movimiento,
                     monto, descripcion)
                SELECT id_cuenta, ?, 'CARGO', ?, 'Compra'
                FROM public.cuenta_corriente
                WHERE id_cliente = ? AND estado = 'ACTIVA'
                """, pedido, total, cliente);

            if (registrados != 1)
                throw error(HttpStatus.CONFLICT,
                    "No tienes una cuenta corriente activa.");
        }

        return detalle(jwt, pedido);
    }

    @Transactional
    public Map<String, Object> cambiarEstado(
            Jwt jwt, long id, String nuevoEstado) {

        var usuario = usuarios.usuarioActual(jwt);

        if (!gestion(usuario.rol()))
            throw error(HttpStatus.FORBIDDEN,
                "Solo administrador o vendedor puede cambiar estados.");

        String nuevo = normalizar(nuevoEstado);

        if (!Set.of("CONFIRMADO", "EN_PREPARACION",
                "EN_CAMINO", "ENTREGADO", "CANCELADO")
                .contains(nuevo))
            throw error(HttpStatus.BAD_REQUEST,
                "Estado inválido.");

        var filas = jdbc.queryForList("""
            SELECT estado, tipo_entrega FROM public.pedido
            WHERE id_pedido = ? FOR UPDATE
            """, id);

        if (filas.isEmpty())
            throw error(HttpStatus.NOT_FOUND,
                "Pedido no encontrado.");

        String anterior = normalizar(
            (String) filas.get(0).get("estado"));

        if (anterior.equals(nuevo))
            return detalle(jwt, id);

        boolean permitido = switch (anterior) {
            case "PENDIENTE" ->
                Set.of("CONFIRMADO", "CANCELADO").contains(nuevo);
            case "CONFIRMADO" ->
                Set.of("EN_PREPARACION", "CANCELADO").contains(nuevo);
            case "EN_PREPARACION" ->
                Set.of("EN_CAMINO", "ENTREGADO", "CANCELADO")
                    .contains(nuevo);
            case "EN_CAMINO" ->
                nuevo.equals("ENTREGADO");
            default -> false;
        };

        if (!permitido)
            throw error(HttpStatus.CONFLICT,
                "No se permite ese cambio de estado.");

        if (nuevo.equals("EN_CAMINO") &&
                !"DESPACHO".equals(filas.get(0).get("tipo_entrega")))
            throw error(HttpStatus.CONFLICT,
                "Un pedido con retiro no puede estar en camino.");

        if (nuevo.equals("CANCELADO")) {

            var items = jdbc.queryForList("""
                SELECT id_producto, cantidad
                FROM public.detalle_pedido
                WHERE id_pedido = ?
                ORDER BY id_producto
                """, id);

            for (var item : items) {

                long producto =
                    ((Number) item.get("id_producto")).longValue();

                long cantidad =
                    ((Number) item.get("cantidad")).longValue();

                jdbc.update("""
                    UPDATE public.producto
                    SET stock_actual = stock_actual + ?
                    WHERE id_producto = ?
                    """, cantidad, producto);

                jdbc.update("""
                    INSERT INTO public.movimiento_inventario
                        (id_producto, id_pedido, tipo_movimiento,
                         cantidad, motivo)
                    VALUES (?, ?, 'ENTRADA', ?, 'Pedido cancelado')
                    """, producto, id, cantidad);
            }

            jdbc.update("""
                INSERT INTO public.movimiento_cuenta
                    (id_cuenta, id_pedido, tipo_movimiento,
                     monto, descripcion)
                SELECT id_cuenta, id_pedido, 'ABONO',
                       monto, 'Reversión por cancelación'
                FROM public.movimiento_cuenta
                WHERE id_pedido = ? AND tipo_movimiento = 'CARGO'
                """, id);
        }

        jdbc.update("""
            UPDATE public.pedido SET estado = ?
            WHERE id_pedido = ?
            """, nuevo, id);

        jdbc.update("""
            UPDATE public.despacho
            SET estado_despacho = ?,
                fecha_entregada = CASE
                    WHEN ? = 'ENTREGADO' THEN CURRENT_TIMESTAMP
                    ELSE fecha_entregada
                END
            WHERE id_pedido = ?
            """, nuevo, nuevo, id);

        return detalle(jwt, id);
    }
}
