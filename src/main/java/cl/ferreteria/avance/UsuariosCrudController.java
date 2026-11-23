package cl.ferreteria.avance;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
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
import cl.ferreteria.avance.UsuariosRepository.Usuario;
import cl.ferreteria.avance.UsuariosService.Edicion;

@RestController
@RequestMapping("/api/v1/usuarios")
@SecurityRequirement(name = "bearerAuth")
class UsuariosCrudController {
    record Alta(String nombre, String apellido, String correo, String password, String rol) {}
    private final UsuariosService usuarios;

    UsuariosCrudController(UsuariosService usuarios) { this.usuarios = usuarios; }

    @Operation(summary = "Listar usuarios (solo administrador)")
    @GetMapping
    List<Usuario> listar(@AuthenticationPrincipal Jwt jwt) { return usuarios.listar(jwt); }

    @Operation(summary = "Consultar usuario (propia cuenta o administrador)")
    @GetMapping("/{id}")
    Usuario buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return usuarios.buscar(jwt, id); }

    @Operation(summary = "Crear usuario con rol (solo administrador)")
    @PostMapping
    ResponseEntity<Usuario> crear(@AuthenticationPrincipal Jwt jwt, @RequestBody Alta datos) {
        if (datos == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(usuarios.crearComoAdministrador(jwt, datos.nombre(), datos.apellido(), datos.correo(),
                datos.password(), datos.rol()));
    }

    @Operation(summary = "Actualizar datos (propia cuenta o administrador); solo admin cambia roles")
    @PutMapping("/{id}")
    ResponseEntity<Usuario> modificar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                     @RequestBody Edicion datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usuarios.modificar(jwt, id, datos));
    }

    @Operation(summary = "Desactivar cuenta (solo administrador); responde 204")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> desactivar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        usuarios.desactivar(jwt, id);
        return ResponseEntity.noContent().build();
    }
}
