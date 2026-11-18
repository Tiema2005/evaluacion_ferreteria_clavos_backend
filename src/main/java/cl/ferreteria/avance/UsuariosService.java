package cl.ferreteria.avance;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import cl.ferreteria.avance.UsuariosRepository.Usuario;

@Service
class UsuariosService {
    record Sesion(String token, String tipo, long expiraEnSegundos, Usuario usuario) {}

    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UsuariosRepository usuarios;
    private final TokenUsuarios tokens;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
    private final String hashDeComparacion = bcrypt.encode(UUID.randomUUID().toString());
    private final String rolCliente;

    UsuariosService(UsuariosRepository usuarios, TokenUsuarios tokens,
                    @Value("${ferreteria.auth.rol-cliente:CLIENTE}") String rolCliente) {
        if (rolCliente.isBlank()) {
            throw new IllegalArgumentException("Configura el rol de registro del cliente.");
        }
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.rolCliente = rolCliente;
    }

    Usuario registrar(String nombre, String apellido, String correo, String password) {
        nombre = texto(nombre, "nombre");
        apellido = texto(apellido, "apellido");
        correo = normalizarCorreo(correo);
        if (!correoValido(correo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un correo válido.");
        }
        if (!passwordValida(password) || password.codePointCount(0, password.length()) < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La contraseña debe tener al menos 8 caracteres y ocupar hasta 72 bytes en UTF-8.");
        }

        if (usuarios.correoExiste(correo)) {
            throw correoDuplicado();
        }
        try {
            return usuarios.crear(nombre, apellido, correo, bcrypt.encode(password), rolCliente)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El registro de usuarios no está disponible."));
        } catch (DuplicateKeyException e) {
            throw correoDuplicado();
        }
    }

    Sesion iniciarSesion(String correo, String password) {
        correo = normalizarCorreo(correo);
        if (!correoValido(correo) || !passwordValida(password)) {
            throw credencialesIncorrectas();
        }
        var cuenta = usuarios.credenciales(correo).orElse(null);
        String hash = cuenta == null ? hashDeComparacion : cuenta.hash();
        if (!bcrypt.matches(password, hash) || cuenta == null || !cuenta.usuario().activo()) {
            throw credencialesIncorrectas();
        }
        Usuario usuario = cuenta.usuario();
        return new Sesion(tokens.emitir(usuario), "Bearer", TokenUsuarios.VIGENCIA.toSeconds(), usuario);
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
        if (limpio.isEmpty() || limpio.length() > 100 || limpio.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El campo " + campo + " debe tener entre 1 y 100 caracteres válidos.");
        }
        return limpio;
    }

    private static ResponseStatusException correoDuplicado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado.");
    }

    private static ResponseStatusException credencialesIncorrectas() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas.");
    }
}
