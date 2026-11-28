package cl.ferreteria.avance;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cl.ferreteria.avance.ProductosRepository.Categoria;

@RestController
@RequestMapping("/api/v1/categorias")
class CategoriasController {
    private final ProductosService servicio;

    CategoriasController(ProductosService servicio) { this.servicio = servicio; }

    @Operation(summary = "Consultar categorías del catálogo")
    @GetMapping
    List<Categoria> listar() { return servicio.categorias(); }
}
