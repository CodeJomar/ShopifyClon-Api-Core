package shopify.api.core.modulos.notificacion.correo;

/** Puerto de envío de correo. La implementación SMTP vive en {@link SmtpEnviadorCorreo}. */
public interface EnviadorCorreo {

    void enviar(String para, String asunto, String texto);
}
