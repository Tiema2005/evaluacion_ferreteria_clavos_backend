package cl.ferreteria.avance;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class CuentaCorrienteService {
    record Abono(BigDecimal monto, String descripcion) {}

    private final JdbcTemplate jdbc;
    private final UsuariosService usuarios;

    CuentaCorrienteService(JdbcTemplate jdbc, UsuariosService usuarios) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
    }

    private static ResponseStatusException error(HttpStatus codigo, String mensaje) {
        return new ResponseStatusException(codigo, mensaje);
    }

    private void exigirAdmin(Jwt jwt) {
        if (!"ADMINISTRADOR".equals(usuarios.usuarioActual(jwt).rol())) {
            throw error(HttpStatus.FORBIDDEN, "Solo el administrador puede gestionar cuentas corrientes.");
        }
    }

    private long exigirContratista(Jwt jwt) {
        var actual = usuarios.usuarioActual(jwt);
        if (!"CONTRATISTA".equals(actual.rol())) {
            throw error(HttpStatus.FORBIDDEN, "Solo los contratistas pueden consultar su cuenta corriente.");
        }
        return actual.id();
    }

    private BigDecimal saldo(long idCuenta) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(CASE tipo_movimiento
                WHEN 'CARGO' THEN monto
                WHEN 'ABONO' THEN -monto
                ELSE 0 END), 0)
            FROM public.movimiento_cuenta WHERE id_cuenta = ?
            """, BigDecimal.class, idCuenta);
    }

    private Map<String, Object> cuentaDeUsuario(long idUsuario) {
        var cuentas = jdbc.queryForList("""
            SELECT cc.id_cuenta, cc.id_cliente, cc.estado, cc.fecha_apertura
            FROM public.cuenta_corriente cc
            JOIN public.cliente c ON c.id_cliente = cc.id_cliente
            WHERE c.id_usuario = ?
            ORDER BY cc.id_cuenta DESC LIMIT 1
            """, idUsuario);
        var resultado = new LinkedHashMap<String, Object>();
        if (cuentas.isEmpty()) {
            resultado.put("existe", false);
            resultado.put("activa", false);
            resultado.put("estado", "SIN_CUENTA");
            resultado.put("saldo", BigDecimal.ZERO);
            resultado.put("movimientos", List.of());
            return resultado;
        }
        var cuenta = cuentas.get(0);
        long idCuenta = ((Number) cuenta.get("id_cuenta")).longValue();
        resultado.put("existe", true);
        resultado.put("activa", "ACTIVA".equals(cuenta.get("estado")));
        resultado.put("idCuenta", idCuenta);
        resultado.put("estado", cuenta.get("estado"));
        resultado.put("fechaApertura", cuenta.get("fecha_apertura"));
        resultado.put("saldo", saldo(idCuenta));
        resultado.put("movimientos", jdbc.queryForList("""
            SELECT id_movimiento_cuenta AS id, id_pedido AS "idPedido",
                   fecha_movimiento AS fecha, tipo_movimiento AS tipo,
                   monto, descripcion
            FROM public.movimiento_cuenta WHERE id_cuenta = ?
            ORDER BY fecha_movimiento DESC, id_movimiento_cuenta DESC LIMIT 200
            """, idCuenta));
        return resultado;
    }

    Map<String, Object> miCuenta(Jwt jwt) {
        return cuentaDeUsuario(exigirContratista(jwt));
    }

    List<Map<String, Object>> contratistas(Jwt jwt) {
        exigirAdmin(jwt);
        var filas = jdbc.queryForList("""
            SELECT u.id_usuario AS "idUsuario", u.nombre, u.apellido, u.correo,
                   c.id_cliente AS "idCliente", cc.id_cuenta AS "idCuenta",
                   COALESCE(cc.estado, 'SIN_CUENTA') AS estado
            FROM public.usuario u
            JOIN public.rol r ON r.id_rol = u.id_rol
            LEFT JOIN public.cliente c ON c.id_usuario = u.id_usuario
            LEFT JOIN public.cuenta_corriente cc ON cc.id_cliente = c.id_cliente
            WHERE r.nombre = 'CONTRATISTA' AND u.activo = 1
            ORDER BY u.id_usuario
            """);
        for (var fila : filas) {
            Object idCuenta = fila.get("idCuenta");
            fila.put("saldo", idCuenta == null ? BigDecimal.ZERO :
                saldo(((Number) idCuenta).longValue()));
        }
        return filas;
    }

    @Transactional
    public Map<String, Object> habilitar(Jwt jwt, long idUsuario) {
        exigirAdmin(jwt);
        if (idUsuario < 1) throw error(HttpStatus.BAD_REQUEST, "ID de usuario inválido.");
        // Bloqueamos el usuario para serializar altas concurrentes de la misma cuenta.
        var usuariosEncontrados = jdbc.queryForList("""
            SELECT r.nombre AS rol, u.activo
            FROM public.usuario u JOIN public.rol r ON r.id_rol = u.id_rol
            WHERE u.id_usuario = ? FOR UPDATE OF u
            """, idUsuario);
        if (usuariosEncontrados.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Usuario no encontrado.");
        var usuario = usuariosEncontrados.get(0);
        if (!"CONTRATISTA".equals(usuario.get("rol")) ||
                ((Number) usuario.get("activo")).intValue() != 1) {
            throw error(HttpStatus.CONFLICT, "La cuenta requiere un contratista activo.");
        }
        Long idCliente = jdbc.queryForObject("""
            INSERT INTO public.cliente (id_usuario, tipo_cliente)
            VALUES (?, 'CONTRATISTA')
            ON CONFLICT (id_usuario)
            DO UPDATE SET tipo_cliente = 'CONTRATISTA'
            RETURNING id_cliente
            """, Long.class, idUsuario);
        var existentes = jdbc.queryForList("""
            SELECT id_cuenta, estado FROM public.cuenta_corriente
            WHERE id_cliente = ? FOR UPDATE
            """, idCliente);
        if (existentes.size() > 1) throw error(HttpStatus.CONFLICT,
            "Existe más de una cuenta para este contratista. Requiere revisión manual.");
        if (existentes.isEmpty()) {
            jdbc.update("""
                INSERT INTO public.cuenta_corriente
                    (id_cliente, fecha_apertura, estado)
                VALUES (?, CURRENT_DATE, 'ACTIVA')
                """, idCliente);
        } else if (!"ACTIVA".equals(existentes.get(0).get("estado"))) {
            jdbc.update("""
                UPDATE public.cuenta_corriente SET estado = 'ACTIVA'
                WHERE id_cuenta = ?
                """, existentes.get(0).get("id_cuenta"));
        }
        return cuentaDeUsuario(idUsuario);
    }

    @Transactional
    public Map<String, Object> registrarAbono(Jwt jwt, long idUsuario, Abono abono) {
        exigirAdmin(jwt);
        if (idUsuario < 1 || abono == null || abono.monto() == null ||
                abono.monto().scale() > 2 || abono.monto().signum() <= 0 ||
                abono.monto().compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw error(HttpStatus.BAD_REQUEST, "El abono debe ser un monto positivo con hasta dos decimales.");
        }
        var cuentas = jdbc.queryForList("""
            SELECT cc.id_cuenta FROM public.cuenta_corriente cc
            JOIN public.cliente c ON c.id_cliente = cc.id_cliente
            JOIN public.usuario u ON u.id_usuario = c.id_usuario
            JOIN public.rol r ON r.id_rol = u.id_rol
            WHERE c.id_usuario = ? AND cc.estado = 'ACTIVA'
                  AND r.nombre = 'CONTRATISTA' AND u.activo = 1
            FOR UPDATE OF cc
            """, idUsuario);
        if (cuentas.size() != 1) throw error(HttpStatus.CONFLICT,
            "El contratista no tiene una cuenta corriente activa.");
        long idCuenta = ((Number) cuentas.get(0).get("id_cuenta")).longValue();
        if (abono.monto().compareTo(saldo(idCuenta)) > 0) {
            throw error(HttpStatus.CONFLICT, "El abono supera el saldo pendiente.");
        }
        String descripcion = abono.descripcion() == null ? "Pago registrado por administrador" :
            abono.descripcion().strip();
        if (descripcion.isEmpty()) descripcion = "Pago registrado por administrador";
        if (descripcion.length() > 200) {
            throw error(HttpStatus.BAD_REQUEST, "La descripción excede 200 caracteres.");
        }
        // Un pago independiente del pedido no tiene id_pedido; la columna debe permitir NULL.
        jdbc.update("""
            INSERT INTO public.movimiento_cuenta
                (id_cuenta, tipo_movimiento, monto, descripcion)
            VALUES (?, 'ABONO', ?, ?)
            """, idCuenta, abono.monto(), descripcion);
        return cuentaDeUsuario(idUsuario);
    }
}
