package cl.ferreteria.avance;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({UsuariosController.class, UsuariosService.class, UsuariosRepository.class, TokenUsuarios.class})
public class ConfiguracionUsuarios {
}
