package cl.ferreteria.avance;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import cl.ferreteria.avance.UsuariosRepository.Usuario;
import cl.ferreteria.avance.UsuariosService.Sesion;

@RestController
@RequestMapping("/api/v1/auth")
class UsuariosController {
    record Registro(String nombre, String apellido, String correo, String password) {}
    record Login(String correo, String password) {}

    private final UsuariosService usuarios;

    UsuariosController(UsuariosService usuarios) {
        this.usuarios = usuarios;
    }

    @PostMapping("/registro")
    ResponseEntity<Usuario> registrar(@RequestBody Registro datos) {
        Usuario usuario = usuarios.registrar(datos.nombre(), datos.apellido(), datos.correo(), datos.password());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(usuario);
    }

    @PostMapping("/login")
    ResponseEntity<Sesion> login(@RequestBody Login datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(usuarios.iniciarSesion(datos.correo(), datos.password()));
    }

    @Operation(summary = "Validar la sesión JWT y consultar el usuario activo")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    ResponseEntity<Usuario> yo(@AuthenticationPrincipal Jwt jwt) {
        // En cada consulta verifica contra PostgreSQL que la cuenta siga activa.
        // El servicio responde 401 si la cuenta fue desactivada.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(usuarios.usuarioActual(jwt));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
            .body(Map.of("mensaje", error.getReason()));
    }
}
