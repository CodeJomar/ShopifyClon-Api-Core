package shopify.api.core.modulos.notificacion.plantilla;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Constructor del HTML de los correos de OTemu. Diseño de tablas con estilos en línea (lo único que respetan todos los
 * clientes de correo), 600 px de ancho, sin imágenes externas (el logo es texto) y con la paleta de la web: menta
 * {@code #00E699}, verde oscuro {@code #062A1F} y azul marino {@code #1F2A44}. Todo texto dinámico se escapa; los
 * enlaces solo se aceptan si son {@code http(s)}. Sin motor de plantillas ni dependencias nuevas.
 */
public final class PlantillaCorreo {

    static final String MARCA = "OTemu";
    private static final String MENTA = "#00E699";
    private static final String VERDE_OSCURO = "#062A1F";
    private static final String MARINO = "#1F2A44";
    private static final String TEXTO = "#334155";
    private static final String TEXTO_SUAVE = "#64748B";
    private static final String FONDO = "#EEF2F7";
    private static final String FUENTE = "'Space Grotesk','Segoe UI',Helvetica,Arial,sans-serif";

    /** Una fila de la tabla de detalles (por ejemplo «Orden» → «#1001»). */
    public record Detalle(String etiqueta, String valor) {
    }

    private final String preencabezado;
    private final String titulo;
    private final List<String> bloques = new ArrayList<>();

    private PlantillaCorreo(String preencabezado, String titulo) {
        this.preencabezado = preencabezado;
        this.titulo = titulo;
    }

    /** {@code preencabezado} es el texto que muestra la bandeja de entrada junto al asunto. */
    public static PlantillaCorreo nuevo(String preencabezado, String titulo) {
        return new PlantillaCorreo(preencabezado, titulo);
    }

    public PlantillaCorreo saludo(String nombre) {
        String limpio = nombre == null || nombre.isBlank() ? "" : " " + nombre.strip();
        return parrafo("Hola" + limpio + ",");
    }

    public PlantillaCorreo parrafo(String texto) {
        bloques.add("<p style=\"margin:0 0 16px 0;font-size:15px;line-height:24px;color:" + TEXTO + ";\">"
                + escapar(texto).replace("\n", "<br>") + "</p>");
        return this;
    }

    /** Código de un solo uso, grande y centrado, con su vigencia debajo. */
    public PlantillaCorreo codigo(String codigo, String vigencia) {
        bloques.add("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:8px 0 20px 0;\">"
                + "<tr><td align=\"center\" style=\"background:#ECFDF5;border:2px solid " + MENTA + ";border-radius:14px;padding:22px 12px;\">"
                + "<div style=\"font-family:" + FUENTE + ";font-size:36px;line-height:40px;font-weight:700;letter-spacing:10px;color:"
                + VERDE_OSCURO + ";\">" + escapar(codigo) + "</div>"
                + (vigencia == null ? "" : "<div style=\"margin-top:8px;font-size:13px;color:" + TEXTO_SUAVE + ";\">"
                + escapar(vigencia) + "</div>")
                + "</td></tr></table>");
        return this;
    }

    /** Tabla de datos clave (orden, total, transportista…). */
    public PlantillaCorreo detalles(List<Detalle> filas) {
        StringBuilder s = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"margin:4px 0 20px 0;border:1px solid #E2E8F0;border-radius:12px;\">");
        for (int i = 0; i < filas.size(); i++) {
            Detalle d = filas.get(i);
            String borde = i == 0 ? "" : "border-top:1px solid #E2E8F0;";
            s.append("<tr><td style=\"padding:12px 16px;font-size:13px;color:").append(TEXTO_SUAVE).append(";").append(borde)
                    .append("\" width=\"38%\">").append(escapar(d.etiqueta())).append("</td>")
                    .append("<td style=\"padding:12px 16px;font-size:15px;font-weight:700;color:").append(MARINO).append(";")
                    .append(borde).append("\" align=\"right\">").append(escapar(d.valor())).append("</td></tr>");
        }
        bloques.add(s.append("</table>").toString());
        return this;
    }

    /** Caja de aviso (seguridad o nota importante). */
    public PlantillaCorreo aviso(String texto) {
        bloques.add("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:4px 0 16px 0;\">"
                + "<tr><td style=\"background:#FFF7ED;border-left:4px solid #FB923C;border-radius:8px;padding:14px 16px;font-size:13px;"
                + "line-height:20px;color:#7C2D12;\">" + escapar(texto).replace("\n", "<br>") + "</td></tr></table>");
        return this;
    }

    /** Botón de acción. Si el enlace no es http(s), no se agrega (nunca {@code javascript:} ni similares). */
    public PlantillaCorreo boton(String texto, String url) {
        if (!esUrlSegura(url)) {
            return this;
        }
        bloques.add("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\" style=\"margin:8px auto 20px auto;\">"
                + "<tr><td align=\"center\" style=\"background:" + MENTA + ";border-radius:10px;\">"
                + "<a href=\"" + escapar(url) + "\" target=\"_blank\" style=\"display:inline-block;padding:14px 30px;font-family:" + FUENTE
                + ";font-size:15px;font-weight:700;color:" + VERDE_OSCURO + ";text-decoration:none;\">" + escapar(texto) + "</a>"
                + "</td></tr></table>"
                + "<p style=\"margin:0 0 16px 0;font-size:12px;line-height:18px;color:" + TEXTO_SUAVE + ";text-align:center;\">"
                + "Si el botón no funciona, copia este enlace en tu navegador:<br><span style=\"word-break:break-all;color:" + MARINO + ";\">"
                + escapar(url) + "</span></p>");
        return this;
    }

    public String html() {
        StringBuilder s = new StringBuilder();
        s.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"light dark\"><meta name=\"supported-color-schemes\" content=\"light dark\">")
                .append("<title>").append(escapar(titulo)).append("</title>")
                .append("<style>@media (max-width:620px){.contenedor{width:100%!important}.relleno{padding:24px 20px!important}}"
                        + "@media (prefers-color-scheme:dark){body,.fondo{background:#070B10!important}"
                        + ".tarjeta{background:#0F1724!important}.titulo{color:#F1F5F9!important}}</style>")
                .append("</head><body style=\"margin:0;padding:0;background:").append(FONDO).append(";\" class=\"fondo\">")
                // texto de vista previa en la bandeja de entrada (oculto en el cuerpo)
                .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">")
                .append(escapar(preencabezado)).append("</div>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"fondo\" style=\"background:")
                .append(FONDO).append(";\"><tr><td align=\"center\" style=\"padding:32px 12px;\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"contenedor\" ")
                .append("style=\"width:600px;max-width:100%;font-family:").append(FUENTE).append(";\">")
                // cabecera con el logo de texto
                .append("<tr><td style=\"background:").append(MARINO).append(";border-radius:18px 18px 0 0;padding:22px 32px;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td align=\"center\" width=\"38\" height=\"38\" style=\"background:").append(MENTA)
                .append(";border-radius:10px;font-family:").append(FUENTE).append(";font-size:22px;font-weight:700;color:")
                .append(VERDE_OSCURO).append(";\">O</td>")
                .append("<td style=\"padding-left:12px;font-family:").append(FUENTE)
                .append(";font-size:24px;font-weight:700;letter-spacing:.3px;color:#FFFFFF;\">").append(MARCA).append("</td>")
                .append("</tr></table></td></tr>")
                .append("<tr><td style=\"background:").append(MENTA).append(";height:4px;font-size:0;line-height:0;\">&nbsp;</td></tr>")
                // tarjeta
                .append("<tr><td class=\"tarjeta relleno\" style=\"background:#FFFFFF;padding:32px;\">")
                .append("<h1 class=\"titulo\" style=\"margin:0 0 20px 0;font-family:").append(FUENTE)
                .append(";font-size:24px;line-height:30px;font-weight:700;color:").append(MARINO).append(";\">")
                .append(escapar(titulo)).append("</h1>");
        bloques.forEach(s::append);
        s.append("</td></tr>")
                // pie
                .append("<tr><td style=\"background:#F8FAFC;border-radius:0 0 18px 18px;padding:20px 32px;border-top:1px solid #E2E8F0;\">")
                .append("<p style=\"margin:0 0 6px 0;font-size:12px;line-height:18px;color:").append(TEXTO_SUAVE).append(";\">")
                .append("Este mensaje se envió automáticamente desde ").append(MARCA).append(". Por favor, no respondas a este correo.</p>")
                .append("<p style=\"margin:0;font-size:12px;line-height:18px;color:").append(TEXTO_SUAVE).append(";\">&copy; ")
                .append(MARCA).append(" &middot; Tu marketplace en línea</p></td></tr>")
                .append("</table></td></tr></table></body></html>");
        return s.toString();
    }

    // ------------------------------------------------------------------ utilidades

    static boolean esUrlSegura(String url) {
        if (url == null) {
            return false;
        }
        String minuscula = url.strip().toLowerCase(Locale.ROOT);
        return (minuscula.startsWith("https://") || minuscula.startsWith("http://")) && !minuscula.contains(" ")
                && !minuscula.contains("\"") && !minuscula.contains("<") && !minuscula.contains(">");
    }

    /** Escapa los caracteres que HTML interpreta, para meter texto de personas dentro del correo sin riesgo. */
    static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder s = new StringBuilder(texto.length() + 16);
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '&' -> s.append("&amp;");
                case '<' -> s.append("&lt;");
                case '>' -> s.append("&gt;");
                case '"' -> s.append("&quot;");
                case '\'' -> s.append("&#39;");
                default -> s.append(c);
            }
        }
        return s.toString();
    }
}
