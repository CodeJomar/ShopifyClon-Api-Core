package shopify.api.core.modulos.notificacion.plantilla;

import java.math.BigDecimal;
import java.util.List;
import shopify.api.core.modulos.notificacion.plantilla.PlantillaCorreo.Detalle;

/**
 * Textos de los correos. Cada mensaje trae su versión en texto plano (para clientes que no muestran HTML) y su versión
 * HTML con la marca de OTemu (ver {@link PlantillaCorreo}). Sin motor de plantillas ni dependencias nuevas.
 */
public final class Plantillas {

    private Plantillas() {
    }

    /** Asunto y cuerpo de un correo, en texto plano y en HTML. */
    public record Mensaje(String asunto, String texto, String html) {
    }

    public static Mensaje activacion(String nombres, String codigo) {
        String html = PlantillaCorreo.nuevo("Tu código de activación es " + codigo, "Activa tu cuenta")
                .saludo(nombres)
                .parrafo("¡Bienvenido a OTemu! Para terminar de crear tu cuenta, ingresa este código de activación:")
                .codigo(codigo, "Vence en 24 horas")
                .parrafo("Si no creaste una cuenta en OTemu, puedes ignorar este correo.")
                .html();
        return new Mensaje("Activa tu cuenta en OTemu",
                "Hola " + nombres + ",\n\nTu código de activación es: " + codigo + " (vence en 24 horas).", html);
    }

    public static Mensaje recuperacion(String nombres, String codigo) {
        String html = PlantillaCorreo.nuevo("Tu código para restablecer la contraseña es " + codigo, "Restablece tu contraseña")
                .saludo(nombres)
                .parrafo("Recibimos una solicitud para restablecer la contraseña de tu cuenta. Usa este código:")
                .codigo(codigo, "Vence en 30 minutos")
                .aviso("Si no pediste este cambio, ignora este correo: tu contraseña no se modificará.")
                .html();
        return new Mensaje("Código de recuperación de contraseña",
                "Hola " + nombres + ",\n\nTu código para restablecer la contraseña es: " + codigo + " (vence en 30 minutos).", html);
    }

    public static Mensaje cuentaBloqueada(String nombres, String codigo, long minutos) {
        String html = PlantillaCorreo.nuevo("Bloqueamos tu cuenta durante " + minutos + " minutos por seguridad",
                        "Bloqueamos tu cuenta temporalmente")
                .saludo(nombres)
                .parrafo("Detectamos varios intentos fallidos de inicio de sesión en tu cuenta y la bloqueamos durante "
                        + minutos + " minutos para protegerla.")
                .parrafo("Si fuiste tú, puedes desbloquearla ahora con este código:")
                .codigo(codigo, "Vence en 30 minutos")
                .aviso("Si no fuiste tú, nadie entró: tu contraseña sigue protegida. Te recomendamos cambiarla y activar "
                        + "la verificación en dos pasos.")
                .html();
        return new Mensaje("Bloqueamos temporalmente tu cuenta",
                "Hola " + nombres + ",\n\nDetectamos varios intentos fallidos de inicio de sesión en tu cuenta y la "
                        + "bloqueamos durante " + minutos + " minutos para protegerla.\n\n"
                        + "Si fuiste tú, puedes desbloquearla ahora con este código: " + codigo + " (vence en 30 minutos).\n\n"
                        + "Si no fuiste tú, nadie entró: tu contraseña sigue protegida. Te recomendamos cambiarla "
                        + "y activar la verificación en dos pasos.", html);
    }

    public static Mensaje segundoFactor(String nombres, String codigo) {
        String html = PlantillaCorreo.nuevo("Tu código para iniciar sesión es " + codigo, "Tu código para iniciar sesión")
                .saludo(nombres)
                .parrafo("Ingresa este código para terminar de iniciar sesión:")
                .codigo(codigo, "Vence en 10 minutos")
                .aviso("Si no intentaste entrar, alguien conoce tu contraseña: cámbiala cuanto antes.")
                .html();
        return new Mensaje("Tu código para iniciar sesión",
                "Hola " + nombres + ",\n\nTu código para terminar de iniciar sesión es: " + codigo
                        + " (vence en 10 minutos).\n\nSi no intentaste entrar, alguien conoce tu contraseña: cámbiala cuanto antes.",
                html);
    }

    public static Mensaje ordenCreadaComprador(String numero, String nombre, BigDecimal total, String moneda, String enlace) {
        String html = PlantillaCorreo.nuevo("Recibimos tu orden " + numero, "¡Recibimos tu orden!")
                .saludo(nombre)
                .parrafo("Gracias por tu compra. Ya registramos tu orden y te avisaremos cuando avance.")
                .detalles(List.of(new Detalle("Orden", numero), new Detalle("Total", total.toPlainString() + " " + moneda)))
                .boton("Ver el estado de mi orden", enlace)
                .html();
        return new Mensaje("Recibimos tu orden " + numero,
                "Hola " + nombre + ",\n\nRecibimos tu orden " + numero + " por " + total.toPlainString() + " " + moneda
                        + ".\nPuedes ver su estado en: " + enlace, html);
    }

    public static Mensaje ordenCreadaTienda(String numero, String tienda, BigDecimal total, String moneda) {
        String html = PlantillaCorreo.nuevo("Nueva orden " + numero + " en " + tienda, "¡Tienes una nueva orden!")
                .parrafo("Se creó una orden en tu tienda " + tienda + ".")
                .detalles(List.of(new Detalle("Orden", numero), new Detalle("Total", total.toPlainString() + " " + moneda)))
                .parrafo("Revísala desde tu panel para prepararla y despacharla.")
                .html();
        return new Mensaje("Nueva orden " + numero + " en " + tienda,
                "Se creó la orden " + numero + " por " + total.toPlainString() + " " + moneda + ".", html);
    }

    public static Mensaje ordenPagada(String numero, String nombre) {
        String html = PlantillaCorreo.nuevo("Confirmamos el pago de tu orden " + numero, "Pago confirmado")
                .saludo(nombre)
                .parrafo("Confirmamos el pago de tu orden. ¡Gracias por tu compra!")
                .detalles(List.of(new Detalle("Orden", numero), new Detalle("Estado", "Pagada")))
                .html();
        return new Mensaje("Pago confirmado de tu orden " + numero,
                "Hola " + nombre + ",\n\nConfirmamos el pago de tu orden " + numero + ". Gracias por tu compra.", html);
    }

    public static Mensaje ordenCancelada(String numero, String nombre, String motivo) {
        String html = PlantillaCorreo.nuevo("Tu orden " + numero + " fue cancelada", "Tu orden fue cancelada")
                .saludo(nombre)
                .parrafo("Tu orden fue cancelada por " + motivoLegible(motivo) + ".")
                .detalles(List.of(new Detalle("Orden", numero), new Detalle("Estado", "Cancelada")))
                .parrafo("Si tienes dudas, responde desde la tienda donde compraste.")
                .html();
        return new Mensaje("Tu orden " + numero + " fue cancelada",
                "Hola " + nombre + ",\n\nTu orden " + numero + " fue cancelada por " + motivoLegible(motivo) + ".", html);
    }

    public static Mensaje envioDespachado(String numero, String nombre, String transportista, String guia, String url) {
        String html = PlantillaCorreo.nuevo("Tu orden " + numero + " va en camino", "¡Tu orden va en camino!")
                .saludo(nombre)
                .parrafo("Despachamos tu orden. Aquí tienes los datos del envío:")
                .detalles(List.of(new Detalle("Orden", numero), new Detalle("Transportista", transportista),
                        new Detalle("Guía", guia)))
                .boton("Seguir mi envío", url)
                .html();
        return new Mensaje("Tu orden " + numero + " va en camino",
                "Hola " + nombre + ",\n\nTu orden " + numero + " fue despachada.\nTransportista: " + transportista
                        + "\nGuía: " + guia + "\nSeguimiento: " + url, html);
    }

    public static Mensaje envioEntregado(String numero, String nombre) {
        String html = PlantillaCorreo.nuevo("Entregamos tu orden " + numero, "¡Tu orden fue entregada!")
                .saludo(nombre)
                .parrafo("Registramos la entrega de tu orden. ¡Gracias por tu compra!")
                .detalles(List.of(new Detalle("Orden", numero), new Detalle("Estado", "Entregada")))
                .parrafo("Si te gustó, puedes dejar una reseña de tu compra desde tu cuenta.")
                .html();
        return new Mensaje("Tu orden " + numero + " fue entregada",
                "Hola " + nombre + ",\n\nRegistramos la entrega de tu orden " + numero + ". ¡Gracias por tu compra!", html);
    }

    public static Mensaje suscripcionVencida(String tienda, String enlacePanel) {
        String html = PlantillaCorreo.nuevo("La suscripción de " + tienda + " venció", "Tu suscripción venció")
                .parrafo("La suscripción de " + tienda + " venció y ya no puedes vender.")
                .aviso("Renueva tu suscripción para volver a vender.")
                .boton("Pagar y seguir vendiendo", enlacePanel)
                .html();
        return new Mensaje("Tu suscripción de " + tienda + " venció",
                "La suscripción de " + tienda + " venció y ya no puedes vender.\nPaga para seguir vendiendo en: "
                        + enlacePanel, html);
    }

    public static Mensaje suscripcionRenovada(String tienda) {
        String html = PlantillaCorreo.nuevo("Renovamos la suscripción de " + tienda, "Suscripción renovada")
                .parrafo("Renovamos la suscripción de " + tienda + ". Puedes seguir vendiendo sin interrupciones.")
                .html();
        return new Mensaje("Tu suscripción de " + tienda + " se renovó",
                "Renovamos la suscripción de " + tienda + ". Puedes seguir vendiendo sin interrupciones.", html);
    }

    /** Motivo en lenguaje del comprador; un código desconocido se muestra tal cual. */
    static String motivoLegible(String motivo) {
        return switch (motivo == null ? "" : motivo) {
            case "CLIENTE" -> "tu solicitud";
            case "FRAUDE" -> "una revisión de seguridad";
            case "SIN_STOCK" -> "falta de stock";
            case "PAGO_VENCIDO" -> "no recibir el pago a tiempo";
            default -> "un motivo de la tienda";
        };
    }
}
