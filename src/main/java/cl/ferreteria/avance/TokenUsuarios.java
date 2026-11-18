package cl.ferreteria.avance;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

@Component
class TokenUsuarios {
    private static final String EMISOR = "ferreteria-los-maestros";
    static final Duration VIGENCIA = Duration.ofHours(1);

    private final NimbusJwtEncoder encoder;

    TokenUsuarios(@Value("${ferreteria.auth.jwt-secret}") String secret) {
        if (!secret.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Configura el secreto JWT con openssl rand -hex 32.");
        }
        var llave = new SecretKeySpec(HexFormat.of().parseHex(secret), "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(llave));
    }

    String emitir(UsuariosRepository.Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet datos = JwtClaimsSet.builder()
            .issuer(EMISOR)
            .subject(Long.toString(usuario.id()))
            .issuedAt(ahora)
            .expiresAt(ahora.plus(VIGENCIA))
            .claim("rol", usuario.rol())
            .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), datos))
            .getTokenValue();
    }
}
