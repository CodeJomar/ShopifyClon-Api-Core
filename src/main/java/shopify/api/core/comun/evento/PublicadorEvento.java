package shopify.api.core.comun.evento;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicadorEvento {

    private final ApplicationEventPublisher eventPublisher;

    public void publicar(Object evento) {
        if (evento != null) {
            this.eventPublisher.publishEvent(evento);
        }
    }
}