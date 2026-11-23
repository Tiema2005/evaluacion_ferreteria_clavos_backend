package cl.ferreteria.avance;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import cl.ferreteria.avance.UsuariosRepository.Usuario;

@Service
class UsuariosService {
    record Sesion(String token, String tipo, long expiraEnSegundos, Usuario usuario) {}
    record Edicion(String nombre, String apellido, String correo, String password, String rol) {}

    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UsuariosRepository usuarios;
    private final TokenUsuarios tokens;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
    private final String hashDeComparacion = bcrypt.encode(UUID.randomUUID().toString());
    private final String rolCliente;

    UsuariosService(UsuariosRepository usuarios, TokenUsuarios tokens,
                    @Value("${ferreteria.auth.rol-cliente:CLIENTE}") String rolCliente) {
        if (rolCliente.isBlank()) throw new IllegalArgumentException("Configura el rol de cliente.");
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.rolCliente = rolCliente;
    }

    Usuario registrar(String nombre, String apellido, String correo, String password) {
        return registrarConRol(nombre, apellido, correo, password, rolCliente);
    }

    Usuario crearComoAdministrador(Jwt jwt, String nombre, String apellido, String correo,
                                   String password, String rol) {
        exigirAdmin(jwt);
        return registrarConRol(nombre, apellido, correo, password, rol == null ? rolCliente : rol);
    }

    private Usuario registrarConRol(String nombre, String apellido, String correo, String password, String rol) {
        nombre = texto(nombre, "nombre");
        apellido = texto(apellido, "apellido");
        correo = normalizarCorreo(correo);
        if (!correoValido(correo)) throw error(HttpStatus.BAD_REQUEST, "Ingresa un correo válido.");
        if (!passwordValida(password) || password.codePointCount(0, password.length()) < 8)
            throw error(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 8 caracteres y hasta 72 bytes UTF-8.");
        rol = rolValido(rol);
        if (usuarios.correoExiste(correo)) throw correoDuplicado();
        try {
            return usuarios.crear(nombre, apellido, correo, bcrypt.encode(password), rol)
                .orElseThrow(() -> error(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo registrar el usuario."));
        } catch (DuplicateKeyException e) {
            throw correoDuplicado();
        }
    }

    Sesion iniciarSesion(String correo, String password) {
        correo = normalizarCorreo(correo);
        if (!correoValido(correo) || !passwordValida(password)) throw credencialesIncorrectas();
        var cuenta = usuarios.credenciales(correo).orElse(null);
        String hash = cuenta == null ? hashDeComparacion : cuenta.hash();
        if (!bcrypt.matches(password, hash) || cuenta == null || !cuenta.usuario().activo())
            throw credencialesIncorrectas();
        Usuario usuario = cuenta.usuario();
        return new Sesion(tokens.emitir(usuario), "Bearer", TokenUsuarios.VIGENCIA.toSeconds(), usuario);
    }

    Usuario usuarioActual(Jwt jwt) {
        if (jwt == null) throw error(HttpStatus.UNAUTHORIZED, "No se ha iniciado sesión.");
        long id;
        try { id = Long.parseLong(jwt.getSubject()); }
        catch (RuntimeException e) { throw error(HttpStatus.UNAUTHORIZED, "Sesión no válida."); }
        Usuario usuario = usuarios.buscar(id).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Sesión no válida."));
        if (!usuario.activo()) throw error(HttpStatus.UNAUTHORIZED, "Sesión no válida.");
        return usuario;
    }

    private Usuario exigirAdmin(Jwt jwt) {
        Usuario actual = usuarioActual(jwt); // Consulta el rol actualizado en PostgreSQL, no solo el claim del JWT.
        if (!actual.rol().equals("ADMINISTRADOR"))
            throw error(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta operación.");
        return actual;
    }

    private void exigirPropietarioOAdmin(Jwt jwt, long id) {
        Usuario actual = usuarioActual(jwt);
        if (actual.id() != id && !actual.rol().equals("ADMINISTRADOR"))
            throw error(HttpStatus.FORBIDDEN, "No tienes permisos para consultar o modificar esta cuenta.");
    }

    List<Usuario> listar(Jwt jwt) {
        exigirAdmin(jwt);
        return usuarios.listar();
    }

    Usuario buscar(Jwt jwt, long id) {
        exigirPropietarioOAdmin(jwt, id);
        return usuarios.buscar(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    }

    Usuario modificar(Jwt jwt, long id, Edicion datos) {
        if (datos == null) throw error(HttpStatus.BAD_REQUEST, "Envía un JSON válido.");
        exigirPropietarioOAdmin(jwt, id);
        Usuario actual = usuarioActual(jwt);
        Usuario antes = usuarios.buscar(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
        if (!antes.activo()) throw error(HttpStatus.NOT_FOUND, "Usuario no encontrado.");
        if (datos.rol() != null && !actual.rol().equals("ADMINISTRADOR"))
            throw error(HttpStatus.FORBIDDEN, "Solo el administrador puede modificar roles.");

        String nombre = datos.nombre() == null ? antes.nombre() : texto(datos.nombre(), "nombre");
        String apellido = datos.apellido() == null ? antes.apellido() : texto(datos.apellido(), "apellido");
        String correo = datos.correo() == null ? antes.correo() : normalizarCorreo(datos.correo());
        if (!correoValido(correo)) throw error(HttpStatus.BAD_REQUEST, "Ingresa un correo válido.");
        String rol = datos.rol() == null ? antes.rol() : rolValido(datos.rol());
        String hash = null;
        if (datos.password() != null) {
            if (!passwordValida(datos.password()) || datos.password().codePointCount(0, datos.password().length()) < 8)
                throw error(HttpStatus.BAD_REQUEST, "Contraseña inválida (8 caracteres mínimo, 72 bytes máximo).");
            hash = bcrypt.encode(datos.password());
        }
        try {
            return usuarios.actualizar(id, nombre, apellido, correo, hash, rol)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
        } catch (DuplicateKeyException e) {
            throw correoDuplicado();
        }
    }

    void desactivar(Jwt jwt, long id) {
        exigirAdmin(jwt);
        if (!usuarios.desactivar(id)) throw error(HttpStatus.NOT_FOUND, "Usuario no encontrado o ya desactivado.");
    }

    private String rolValido(String rol) {
        if (rol == null || rol.isBlank() || !usuarios.rolExiste(rol))
            throw error(HttpStatus.BAD_REQUEST, "Rol no válido; revisa public.rol.");
        return rol;
    }

    private static boolean correoValido(String valor) {
        return valor.length() <= 150 && valor.indexOf(0) < 0 && CORREO.matcher(valor).matches();
    }

    private static String normalizarCorreo(String valor) {
        return valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean passwordValida(String valor) {
        return valor != null && !valor.isBlank() && valor.length() <= 72
            && valor.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private static String texto(String valor, String campo) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.isEmpty() || limpio.length() > 100 || limpio.indexOf(0) >= 0)
            throw error(HttpStatus.BAD_REQUEST, "El campo " + campo + " debe tener entre 1 y 100 caracteres válidos.");
        return limpio;
    }

    private static ResponseStatusException correoDuplicado() {
        return error(HttpStatus.CONFLICT, "El correo ya está registrado.");
    }

    private static ResponseStatusException credencialesIncorrectas() {
        return error(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas.");
    }

    private static ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
