package cl.ferreteria.avance;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cl.ferreteria.avance.ProductosRepository.Producto;
import cl.ferreteria.avance.ProductosService.Alta;
import cl.ferreteria.avance.ProductosService.Edicion;
import cl.ferreteria.avance.ProductosService.CambioStock;

@RestController
@RequestMapping("/api/v1/productos")
class ProductosController {
    private final ProductosService servicio;

    ProductosController(ProductosService servicio) { this.servicio = servicio; }

    @Operation(summary = "Consultar productos activos (catálogo público)")
    @GetMapping
    List<Producto> listar() { return servicio.catalogo(); }

    @Operation(summary = "Consultar producto activo por ID")
    @GetMapping("/{id}")
    Producto obtener(@PathVariable long id) { return servicio.consultar(id); }

    @Operation(summary = "Listar todos los productos, incluidos inactivos (admin/vendedor)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/gestion")
    List<Producto> gestion(@AuthenticationPrincipal Jwt jwt) { return servicio.gestion(jwt); }

    @Operation(summary = "Crear producto (solo administrador)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    ResponseEntity<Producto> crear(@AuthenticationPrincipal Jwt jwt, @RequestBody Alta datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(jwt, datos));
    }

    @Operation(summary = "Modificar producto sin alterar su stock (solo administrador)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}")
    Producto modificar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody Edicion datos) {
        return servicio.modificar(jwt, id, datos);
    }

    @Operation(summary = "Ajustar stock y registrar movimiento (admin/vendedor)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}/stock")
    Producto cambiarStock(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                          @RequestBody CambioStock datos) {
        return servicio.actualizarStock(jwt, id, datos);
    }

    @Operation(summary = "Desactivar producto sin borrarlo (solo administrador)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{id}")
    ResponseEntity<Void> desactivar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        servicio.desactivar(jwt, id);
        return ResponseEntity.noContent().build();
    }
}
