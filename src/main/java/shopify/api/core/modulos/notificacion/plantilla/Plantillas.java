package shopify.api.core.modulos.notificacion.plantilla;

import java.math.BigDecimal;

/** Textos de los correos. Bloques de texto Java: sin motor de plantillas ni dependencias nuevas. */
public final class Plantillas {

    private Plantillas() {
    }

    /** Asunto y cuerpo de un correo. */
    public record Mensaje(String asunto, String texto) {
    }

    public static Mensaje activacion(String nombres, String codigo) {
        return new Mensaje("Activa tu cuenta en Shopify Clon",
                "Hola " + nombres + ",\n\nTu código de activación es: " + codigo + " (vence en 24 horas).");
    }

    public static Mensaje recuperacion(String nombres, String codigo) {
        return new Mensaje("Código de recuperación de contraseña",
                "Hola " + nombres + ",\n\nTu código para restablecer la contraseña es: " + codigo + " (vence en 30 minutos).");
    }

    public static Mensaje ordenCreadaComprador(String numero, String nombre, BigDecimal total, String moneda, String enlace) {
        return new Mensaje("Recibimos tu orden " + numero,
                "Hola " + nombre + ",\n\nRecibimos tu orden " + numero + " por " + total.toPlainString() + " " + moneda
                        + ".\nPuedes ver su estado en: " + enlace);
    }

    public static Mensaje ordenCreadaTienda(String numero, String tienda, BigDecimal total, String moneda) {
        return new Mensaje("Nueva orden " + numero + " en " + tienda,
                "Se creó la orden " + numero + " por " + total.toPlainString() + " " + moneda + ".");
    }

    public static Mensaje ordenPagada(String numero, String nombre) {
        return new Mensaje("Pago confirmado de tu orden " + numero,
                "Hola " + nombre + ",\n\nConfirmamos el pago de tu orden " + numero + ". Gracias por tu compra.");
    }

    public static Mensaje ordenCancelada(String numero, String nombre, String motivo) {
        return new Mensaje("Tu orden " + numero + " fue cancelada",
                "Hola " + nombre + ",\n\nTu orden " + numero + " fue cancelada por " + motivoLegible(motivo) + ".");
    }

    public static Mensaje envioDespachado(String numero, String nombre, String transportista, String guia, String url) {
        return new Mensaje("Tu orden " + numero + " va en camino",
                "Hola " + nombre + ",\n\nTu orden " + numero + " fue despachada.\nTransportista: " + transportista
                        + "\nGuía: " + guia + "\nSeguimiento: " + url);
    }

    public static Mensaje envioEntregado(String numero, String nombre) {
        return new Mensaje("Tu orden " + numero + " fue entregada",
                "Hola " + nombre + ",\n\nRegistramos la entrega de tu orden " + numero + ". ¡Gracias por tu compra!");
    }

    public static Mensaje suscripcionVencida(String tienda, String enlacePanel) {
        return new Mensaje("Tu suscripción de " + tienda + " venció",
                "La suscripción de " + tienda + " venció y ya no puedes vender.\nPaga para seguir vendiendo en: "
                        + enlacePanel);
    }

    public static Mensaje suscripcionRenovada(String tienda) {
        return new Mensaje("Tu suscripción de " + tienda + " se renovó",
                "Renovamos la suscripción de " + tienda + ". Puedes seguir vendiendo sin interrupciones.");
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
