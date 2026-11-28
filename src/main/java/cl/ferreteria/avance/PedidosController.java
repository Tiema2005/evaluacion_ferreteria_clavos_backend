package cl.ferreteria.avance;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pedidos")
@SecurityRequirement(name = "bearerAuth")
class PedidosController {

    private final PedidosService servicio;

    PedidosController(PedidosService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Crear pedido y descontar stock")
    @PostMapping
    ResponseEntity<Map<String, Object>> crear(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PedidosService.Solicitud datos) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicio.crear(jwt, datos));
    }

    @Operation(summary = "Listar pedidos según el rol")
    @GetMapping
    List<Map<String, Object>> listar(
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.listar(jwt);
    }

    @Operation(summary = "Consultar detalle de un pedido")
    @GetMapping("/{id}")
    Map<String, Object> obtener(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id) {
        return servicio.detalle(jwt, id);
    }

    @Operation(summary = "Cambiar estado del pedido")
    @PutMapping("/{id}/estado")
    Map<String, Object> estado(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @RequestBody PedidosService.CambioEstado datos) {
        return servicio.cambiarEstado(jwt, id,
                datos == null ? null : datos.estado());
    }

    @Operation(summary = "Cancelar pedido y devolver stock")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> cancelar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id) {

        servicio.cambiarEstado(jwt, id, "CANCELADO");
        return ResponseEntity.noContent().build();
    }
}
