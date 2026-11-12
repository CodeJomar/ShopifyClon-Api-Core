package shopify.api.core.comun.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Las fechas con hora (Instant) viajan en JSON con el desfase de la zona de la plataforma, por ejemplo
 * {@code 2026-11-11T16:38:02.017-05:00} en vez de {@code …21:38:02.017Z}. Es el mismo instante: quien lo lee con
 * cualquier librería de fechas obtiene el mismo momento, pero a simple vista se ve la hora de Lima.
 * La zona se cambia con {@code APP_ZONA_HORARIA} (por defecto America/Lima). La base de datos sigue guardando
 * instantes absolutos.
 */
@Configuration
public class ZonaHorariaJsonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer instantesConZonaDeLaPlataforma(
            @Value("${app.zona-horaria:America/Lima}") String zona) {
        ZoneId zonaId = ZoneId.of(zona);
        DateTimeFormatter formato = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zonaId);
        return builder -> builder.serializerByType(Instant.class, new StdSerializer<Instant>(Instant.class) {
            @Override
            public void serialize(Instant valor, JsonGenerator generador, SerializerProvider proveedor) throws IOException {
                generador.writeString(formato.format(valor));
            }
        });
    }
}
