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
@RequestMapping("/api/v1/cuenta-corriente")
@SecurityRequirement(name = "bearerAuth")
class CuentaCorrienteController {
    private final CuentaCorrienteService servicio;

    CuentaCorrienteController(CuentaCorrienteService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Consultar saldo y movimientos del contratista autenticado")
    @GetMapping("/mi-cuenta")
    Map<String, Object> miCuenta(@AuthenticationPrincipal Jwt jwt) {
        return servicio.miCuenta(jwt);
    }

    @Operation(summary = "Listar las cuentas corrientes de contratistas (administrador)")
    @GetMapping("/admin")
    List<Map<String, Object>> contratistas(@AuthenticationPrincipal Jwt jwt) {
        return servicio.contratistas(jwt);
    }

    @Operation(summary = "Habilitar cuenta corriente de un contratista (administrador)")
    @PostMapping("/admin/usuarios/{idUsuario}/habilitar")
    Map<String, Object> habilitar(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long idUsuario) {
        return servicio.habilitar(jwt, idUsuario);
    }

    @Operation(summary = "Registrar un abono verificado (administrador)")
    @PostMapping("/admin/usuarios/{idUsuario}/abonos")
    Map<String, Object> abonar(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long idUsuario,
            @RequestBody CuentaCorrienteService.Abono datos) {
        return servicio.registrarAbono(jwt, idUsuario, datos);
    }
}
