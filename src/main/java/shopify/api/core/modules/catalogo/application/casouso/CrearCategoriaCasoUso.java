package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.application.dto.request.CrearCategoriaRequest;
import shopify.api.core.modules.catalogo.domain.modelo.Categoria;
import shopify.api.core.modules.catalogo.domain.puerto.salida.CategoriaRepositorioPuerto;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.ReglaNegocioException;
import shopify.api.core.shared.multitenancy.TenantContextHolder;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CrearCategoriaCasoUso {

    private final CategoriaRepositorioPuerto categoriaRepositorio;
    private static final Pattern NO_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Transactional
    public EstadoOperacionDto ejecutar(CrearCategoriaRequest request) {
        UUID tiendaId = TenantContextHolder.getTenantId();
        String nombreLimpio = request.getNombre().trim();

        if (categoriaRepositorio.existePorNombreYTiendaId(nombreLimpio, tiendaId)) {
            throw new ReglaNegocioException("Ya existe una categoría con el nombre: " + nombreLimpio);
        }

        String slug = generarSlug(nombreLimpio);

        Categoria categoria = Categoria.builder()
                .tiendaId(tiendaId)
                .nombre(nombreLimpio)
                .slug(slug)
                .descripcion(request.getDescripcion())
                .urlImagen(request.getUrlImagen())
                .estaActiva(request.getEstaActiva() != null ? request.getEstaActiva() : true)
                .build();

        Categoria guardada = categoriaRepositorio.guardar(categoria);

        return EstadoOperacionDto.creado(
                guardada.getId(),
                guardada.getSlug(),
                "Categoría creada exitosamente"
        );
    }

    private String generarSlug(String entrada) {
        String sinEspacios = WHITESPACE.matcher(entrada).replaceAll("-");
        String normalizado = Normalizer.normalize(sinEspacios, Normalizer.Form.NFD);
        String slug = NO_LATIN.matcher(normalizado).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}