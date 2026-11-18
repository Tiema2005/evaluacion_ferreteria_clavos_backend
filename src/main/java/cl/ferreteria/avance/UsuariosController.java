package cl.ferreteria.avance;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
            .body(Map.of("mensaje", error.getReason()));
    }
}
