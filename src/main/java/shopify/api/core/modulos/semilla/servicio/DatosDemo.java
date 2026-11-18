package shopify.api.core.modulos.semilla.servicio;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Datos fijos de la semilla de demostración (R-014): tiendas, catálogo y textos. Todo es ficticio y en soles. */
final class DatosDemo {

    private DatosDemo() {
    }

    /** Producto de la semilla. {@code variantes} nulo = una sola variante «Por defecto». */
    record ProductoDemo(String titulo, String descripcion, String precio, String comparacion, int categoria,
                        boolean digital, List<String> variantes) {

        String slug() {
            return DatosDemo.slug(titulo);
        }

        BigDecimal precioDecimal() {
            return new BigDecimal(precio);
        }

        BigDecimal comparacionDecimal() {
            return comparacion == null ? null : new BigDecimal(comparacion);
        }
    }

    /** Tienda de la semilla. {@code propietario} es 0 o 1 (los dos propietarios demo). */
    record TiendaDemo(String nombre, String subdominio, String descripcion, int propietario, String plan,
                      List<String> categorias, List<ProductoDemo> productos) {
    }

    private static ProductoDemo p(String titulo, String descripcion, String precio, String comparacion, int categoria,
                                  boolean digital, String... variantes) {
        return new ProductoDemo(titulo, descripcion, precio, comparacion, categoria, digital,
                variantes.length == 0 ? null : List.of(variantes));
    }

    static final List<TiendaDemo> TIENDAS = List.of(
            new TiendaDemo("Café Andino", "cafe-andino",
                    "Café de especialidad de pequeños productores de Cusco y Junín, tostado en lotes pequeños.", 0, "BASICO",
                    List.of("Café en grano", "Accesorios"),
                    List.of(
                            p("Café Cusco Selecto 250 g", "Café de altura con notas a cacao y panela. Tostado medio.", "32.00", "38.00", 0, false, "Grano", "Molido"),
                            p("Café de Altura Chanchamayo 500 g", "Lote de selva alta, cuerpo medio y acidez cítrica suave.", "58.00", null, 0, false),
                            p("Cold Brew Concentrado 350 ml", "Concentrado listo para diluir, preparado en frío durante 18 horas.", "24.00", null, 0, false),
                            p("Cafetera Prensa Francesa 600 ml", "Vidrio borosilicato y filtro de acero inoxidable.", "89.00", "110.00", 1, false),
                            p("Molinillo Manual de Cerámica", "Muelas de cerámica con ajuste de molienda para todos los métodos.", "75.00", null, 1, false),
                            p("Guía digital: barismo en casa", "E-book de 60 páginas con recetas y técnicas de preparación.", "19.90", null, 1, true))),
            new TiendaDemo("Casa Quinua", "casa-quinua",
                    "Granos y snacks andinos: quinua, kiwicha y cañihua de cooperativas del altiplano.", 0, "GRATIS",
                    List.of("Granos andinos", "Snacks"),
                    List.of(
                            p("Quinua Blanca Orgánica 1 kg", "Quinua lavada y lista para cocinar, certificada orgánica.", "18.50", null, 0, false),
                            p("Quinua Roja 500 g", "Textura crocante y sabor intenso. Ideal para ensaladas.", "12.00", null, 0, false),
                            p("Harina de Quinua 500 g", "Molida en piedra, sin gluten. Para panes, tortas y papillas.", "9.90", null, 0, false),
                            p("Barras de Quinua y Miel x6", "Snack energético sin conservantes.", "16.50", "19.00", 1, false, "Miel", "Cacao", "Maracuyá"),
                            p("Kiwicha Pop 250 g", "Kiwicha reventada, ligera y lista para el desayuno.", "8.50", null, 1, false),
                            p("Recetario digital de quinua", "40 recetas fáciles con quinua, en PDF.", "14.90", null, 1, true))),
            new TiendaDemo("Taller Textil Cusco", "taller-textil-cusco",
                    "Tejidos de alpaca hechos a mano por artesanas de comunidades cusqueñas.", 0, "ESTANDAR",
                    List.of("Abrigos", "Accesorios"),
                    List.of(
                            p("Chullo de Alpaca", "Chullo tradicional con orejeras, tejido a mano.", "65.00", "79.00", 1, false, "Natural", "Gris", "Rojo"),
                            p("Poncho Tejido a Mano", "Poncho de alpaca con diseños andinos, talla única.", "240.00", "290.00", 0, false),
                            p("Bufanda de Alpaca Baby", "Suave y liviana, ideal para el invierno.", "98.00", null, 1, false),
                            p("Manta Andina Matrimonial", "Manta de lana de oveja y alpaca, 2 x 2,2 m.", "180.00", null, 0, false),
                            p("Guantes Tejidos", "Guantes de lana de alpaca con forro interior.", "45.00", null, 1, false, "S", "M", "L"),
                            p("Patrón digital: tejer un chullo", "Patrón paso a paso en PDF para tejer tu propio chullo.", "12.00", null, 1, true))),
            new TiendaDemo("Neo Gadgets", "neo-gadgets",
                    "Tecnología útil para el día a día: audio, carga y accesorios de escritorio.", 1, "AVANZADO",
                    List.of("Audio", "Accesorios"),
                    List.of(
                            p("Audífonos Inalámbricos Neo Buds", "Bluetooth 5.3, cancelación de ruido y 24 horas de batería con estuche.", "129.00", "169.00", 0, false, "Negro", "Blanco"),
                            p("Parlante Bluetooth Mini", "Resistente al agua, 12 horas de reproducción.", "99.00", null, 0, false),
                            p("Cargador Rápido 65 W", "Dos puertos USB-C y uno USB-A, compacto.", "79.00", null, 1, false),
                            p("Soporte para Laptop de Aluminio", "Plegable y ajustable en altura.", "85.00", null, 1, false),
                            p("Cable USB-C Trenzado 1 m", "Carga rápida y transferencia de datos.", "25.00", null, 1, false),
                            p("Curso digital: fotografía con el celular", "Seis módulos en video para mejorar tus fotos.", "49.00", null, 1, true))),
            new TiendaDemo("Aroma Selva", "aroma-selva",
                    "Velas y cuidado personal con insumos amazónicos, hechos en pequeños lotes.", 1, "BASICO",
                    List.of("Velas", "Cuidado personal"),
                    List.of(
                            p("Vela de Soja Cedro", "Cera de soja con aroma a cedro y madera. 40 horas.", "36.00", null, 0, false),
                            p("Vela de Soja Vainilla", "Aroma cálido a vainilla, mecha de algodón.", "36.00", null, 0, false),
                            p("Jabón Artesanal de Camu Camu", "Jabón en barra con vitamina C natural.", "14.50", null, 1, false),
                            p("Aceite Esencial de Palo Santo", "10 ml, para difusores y masajes.", "28.00", "32.00", 1, false),
                            p("Difusor de Cerámica", "Difusor de aromas con vela de té incluida.", "69.00", "85.00", 1, false),
                            p("Guía digital de aromaterapia", "Combinaciones de aromas para cada momento del día.", "17.00", null, 1, true))),
            new TiendaDemo("Papel y Tinta", "papel-y-tinta",
                    "Papelería para escribir, planificar y dibujar, con papel de calidad.", 1, "GRATIS",
                    List.of("Cuadernos", "Escritura"),
                    List.of(
                            p("Cuaderno Cosido Tapa Dura A5", "Papel de 100 g, 160 hojas, cosido a mano.", "24.00", null, 0, false, "Liso", "Cuadriculado", "Punteado"),
                            p("Agenda 2027 Semanal", "Vista semanal, marcador de tela y bolsillo interior.", "39.00", "49.00", 0, false),
                            p("Set de 5 Lapiceros de Gel", "Tinta de secado rápido, punta de 0,5 mm.", "18.50", null, 1, false),
                            p("Pluma Fuente Clásica", "Punta de acero inoxidable y cuerpo de resina.", "85.00", null, 1, false),
                            p("Tinta Negra 50 ml", "Tinta para pluma fuente, no obstruye.", "16.00", null, 1, false),
                            p("Plantillas digitales de planificación", "Pack de plantillas imprimibles semanales y mensuales.", "9.90", null, 1, true))));

    static final List<String> COMENTARIOS = List.of(
            "Llegó rápido y tal como se ve en las fotos.",
            "Muy buena calidad, lo volvería a comprar.",
            "Buen producto, el empaque podría mejorar.",
            "Excelente relación entre precio y calidad.",
            "Cumple con lo que promete. Recomendado.",
            "Me encantó, lo regalé y gustó mucho.",
            "Está bien, esperaba algo un poco más grande.",
            "Atención muy amable y envío bien cuidado.");

    static String slug(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String limpio = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return limpio.length() > 190 ? limpio.substring(0, 190) : limpio;
    }
}
