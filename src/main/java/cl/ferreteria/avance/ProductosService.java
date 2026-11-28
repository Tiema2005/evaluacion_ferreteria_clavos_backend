package cl.ferreteria.avance;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import cl.ferreteria.avance.ProductosRepository.Categoria;
import cl.ferreteria.avance.ProductosRepository.Producto;

@Service
class ProductosService {
    @Schema(name = "ProductoAlta")
    record Alta(Long idCategoria, String codigo, String nombre, String descripcion,
                BigDecimal precio, Long stockActual, Long stockMinimo) {}
    @Schema(name = "ProductoEdicion")
    record Edicion(Long idCategoria, String codigo, String nombre, String descripcion,
                   BigDecimal precio, Long stockMinimo) {}
    record CambioStock(Long stockActual, String motivo) {}

    private final ProductosRepository productos;
    private final UsuariosService usuarios;

    ProductosService(ProductosRepository productos, UsuariosService usuarios) {
        this.productos = productos;
        this.usuarios = usuarios;
    }

    List<Categoria> categorias() { return productos.categorias(); }
    List<Producto> catalogo() { return productos.listar(true); }

    Producto consultar(long id) {
        if (id < 1) throw error(HttpStatus.BAD_REQUEST, "ID inválido.");
        return productos.buscar(id, true)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Producto no encontrado."));
    }

    List<Producto> gestion(Jwt jwt) {
        exigirGestion(jwt);
        return productos.listar(false);
    }

    @Transactional
    Producto crear(Jwt jwt, Alta datos) {
        exigirAdministrador(jwt);
        if (datos == null) throw error(HttpStatus.BAD_REQUEST, "Envía los datos del producto.");
        long categoria = validarCategoria(datos.idCategoria());
        String codigo = texto(datos.codigo(), "código", 40);
        String nombre = texto(datos.nombre(), "nombre", 150);
        String descripcion = descripcion(datos.descripcion());
        BigDecimal precio = precio(datos.precio());
        long stock = noNegativo(datos.stockActual(), "stockActual");
        long minimo = noNegativo(datos.stockMinimo(), "stockMinimo");
        try {
            long id = productos.crear(categoria, codigo, nombre, descripcion, precio, stock, minimo);
            if (stock > 0) productos.registrarMovimiento(id, "ENTRADA", stock, "Stock inicial");
            return productos.buscar(id, false).orElseThrow();
        } catch (DuplicateKeyException e) {
            throw error(HttpStatus.CONFLICT, "Ya existe un producto con ese código.");
        }
    }

    @Transactional
    Producto modificar(Jwt jwt, long id, Edicion datos) {
        exigirAdministrador(jwt);
        if (id < 1 || datos == null) throw error(HttpStatus.BAD_REQUEST, "Datos inválidos.");
        long categoria = validarCategoria(datos.idCategoria());
        String codigo = texto(datos.codigo(), "código", 40);
        String nombre = texto(datos.nombre(), "nombre", 150);
        String descripcion = descripcion(datos.descripcion());
        BigDecimal precio = precio(datos.precio());
        long minimo = noNegativo(datos.stockMinimo(), "stockMinimo");
        try {
            if (!productos.actualizar(id, categoria, codigo, nombre, descripcion, precio, minimo))
                throw error(HttpStatus.NOT_FOUND, "Producto no encontrado o inactivo.");
            return productos.buscar(id, false).orElseThrow();
        } catch (DuplicateKeyException e) {
            throw error(HttpStatus.CONFLICT, "Ya existe un producto con ese código.");
        }
    }

    @Transactional
    Producto actualizarStock(Jwt jwt, long id, CambioStock datos) {
        exigirGestion(jwt);
        if (id < 1 || datos == null) throw error(HttpStatus.BAD_REQUEST, "Datos inválidos.");
        long nuevo = noNegativo(datos.stockActual(), "stockActual");
        String motivo = texto(datos.motivo(), "motivo", 200);
        long anterior = productos.bloquearStock(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Producto no encontrado o inactivo."));
        if (anterior != nuevo) {
            productos.guardarStock(id, nuevo);
            long diferencia = nuevo > anterior ? nuevo - anterior : anterior - nuevo;
            String tipo = nuevo > anterior ? "ENTRADA" : "SALIDA";
            productos.registrarMovimiento(id, tipo, diferencia, "Ajuste manual: " + motivo);
        }
        return productos.buscar(id, false).orElseThrow();
    }

    @Transactional
    void desactivar(Jwt jwt, long id) {
        exigirAdministrador(jwt);
        if (!productos.desactivar(id))
            throw error(HttpStatus.NOT_FOUND, "Producto no encontrado o ya inactivo.");
    }

    private long validarCategoria(Long id) {
        if (id == null || id < 1 || !productos.categoriaExiste(id))
            throw error(HttpStatus.BAD_REQUEST, "La categoría no existe.");
        return id;
    }

    private void exigirAdministrador(Jwt jwt) {
        if (!usuarios.usuarioActual(jwt).rol().equals("ADMINISTRADOR"))
            throw error(HttpStatus.FORBIDDEN, "Solo el administrador puede gestionar productos.");
    }

    private void exigirGestion(Jwt jwt) {
        String rol = usuarios.usuarioActual(jwt).rol();
        if (!rol.equals("ADMINISTRADOR") && !rol.equals("VENDEDOR"))
            throw error(HttpStatus.FORBIDDEN, "Solo administrador o vendedor puede gestionar inventario.");
    }

    private static String texto(String valor, String campo, int maximo) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.isBlank() || limpio.length() > maximo || limpio.indexOf(0) >= 0)
            throw error(HttpStatus.BAD_REQUEST, "El campo " + campo + " debe tener entre 1 y " + maximo + " caracteres.");
        return limpio;
    }

    private static String descripcion(String valor) {
        if (valor == null) return null;
        String limpio = valor.strip();
        if (limpio.length() > 500 || limpio.indexOf(0) >= 0)
            throw error(HttpStatus.BAD_REQUEST, "La descripción supera 500 caracteres o contiene datos inválidos.");
        return limpio;
    }

    private static BigDecimal precio(BigDecimal valor) {
        if (valor == null || valor.signum() < 0 || valor.scale() > 2 ||
            valor.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw error(HttpStatus.BAD_REQUEST, "Precio inválido: utiliza un monto no negativo y máximo 2 decimales.");
        return valor;
    }

    private static long noNegativo(Long valor, String nombre) {
        if (valor == null || valor < 0)
            throw error(HttpStatus.BAD_REQUEST, nombre + " debe ser un número entero no negativo.");
        return valor;
    }

    private static ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
