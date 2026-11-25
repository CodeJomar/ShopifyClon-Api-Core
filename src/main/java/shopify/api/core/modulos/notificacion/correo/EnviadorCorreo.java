package shopify.api.core.modulos.notificacion.correo;

/** Puerto de envío de correo. La implementación SMTP vive en {@link SmtpEnviadorCorreo}. */
public interface EnviadorCorreo {

    /** Envía el correo con versión de texto plano y, si {@code html} no es nulo, también con diseño HTML. */
    void enviar(String para, String asunto, String texto, String html);

    default void enviar(String para, String asunto, String texto) {
        enviar(para, asunto, texto, null);
    }
}
