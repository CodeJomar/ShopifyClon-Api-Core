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
                        boolean digital, List<String> variantes, List<VarianteDemo> detalle, List<FotoDemo> fotos) {

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

    /** Variante con precio y stock propios. El título sigue la convención «Color / Talla». */
    record VarianteDemo(String titulo, String precio, String comparacion, int stock) {
    }

    /** Foto del producto; {@code variante} es el título de la variante a la que se asocia (nulo = foto general). */
    record FotoDemo(String url, String variante) {
    }

    /** Tienda de la semilla. {@code propietario}: 0 = propietario, 1 = administrador, 2 a 5 = propietarios demo adicionales. */
    record TiendaDemo(String nombre, String subdominio, String descripcion, int propietario, String plan,
                      List<String> categorias, List<ProductoDemo> productos) {
    }

    private static ProductoDemo p(String titulo, String descripcion, String precio, String comparacion, int categoria,
                                  boolean digital, String... variantes) {
        return new ProductoDemo(titulo, descripcion, precio, comparacion, categoria, digital,
                variantes.length == 0 ? null : List.of(variantes), null, null);
    }

    /** Producto con variantes y fotos definidas una por una. */
    private static ProductoDemo pe(String titulo, String descripcion, String precio, String comparacion, int categoria,
                                   List<VarianteDemo> detalle, List<FotoDemo> fotos) {
        return new ProductoDemo(titulo, descripcion, precio, comparacion, categoria, false, null, detalle, fotos);
    }

    /** Producto físico de una sola variante con el stock indicado (3 = poco stock, 0 = agotado). */
    private static ProductoDemo s(String titulo, String descripcion, String precio, String comparacion, int categoria, int stock) {
        return pe(titulo, descripcion, precio, comparacion, categoria,
                List.of(new VarianteDemo("Por defecto", precio, comparacion, stock)), null);
    }

    private static String foto(String id) {
        return "https://images.unsplash.com/photo-" + id + "?w=800&q=80&auto=format&fit=crop";
    }

    /** Todas las combinaciones «Color / Talla» con el mismo precio. */
    private static List<VarianteDemo> prenda(List<String> colores, List<String> tallas, String precio, int stock) {
        List<VarianteDemo> lista = new java.util.ArrayList<>();
        for (String color : colores) {
            for (String talla : tallas) {
                lista.add(new VarianteDemo(color + " / " + talla, precio, null, stock));
            }
        }
        return lista;
    }

    private static final ProductoDemo POLO = pe("Polo de Algodón Pima",
            "Polo de algodón pima peruano de tacto suave y corte regular. Disponible en tres colores y en tallas de S a XL. "
                    + "Se lava a máquina y no se deforma ni destiñe.",
            "59.00", null, 0,
            prenda(List.of("Blanco", "Negro", "Crema"), List.of("S", "M", "L", "XL"), "59.00", 12),
            List.of(new FotoDemo(foto("1521572163474-6864f9cf17ab"), null),
                    new FotoDemo(foto("1583743814966-8936f5b7be1a"), "Negro / M"),
                    new FotoDemo(foto("1576566588028-4147f3842f27"), "Crema / M")));

    private static final ProductoDemo TAZA = pe("Taza de Cerámica Artesanal",
            "Taza de cerámica hecha a mano en Pisac, esmaltada por dentro y apta para microondas. Tiene 300 ml de capacidad y un asa cómoda. "
                    + "Cada pieza es única y puede variar ligeramente de tono.",
            "38.00", null, 1,
            List.of(new VarianteDemo("Blanco", "38.00", null, 20), new VarianteDemo("Arena", "38.00", null, 20),
                    new VarianteDemo("Terracota", "38.00", null, 20)),
            List.of(new FotoDemo(foto("1495474472287-4d71bcdd2085"), null),
                    new FotoDemo(foto("1514228742587-6b1558fcca3d"), "Blanco"),
                    new FotoDemo(foto("1610701596007-11502861dcfa"), "Arena")));

    /** Una variante con precio de comparación (oferta) y otra agotada. */
    private static final ProductoDemo AUDIFONOS = pe("Audífonos Inalámbricos Neo Buds",
            "Audífonos Bluetooth 5.3 con cancelación de ruido y hasta 24 horas de batería con el estuche. "
                    + "El color blanco está agotado por ahora. El negro está en oferta.",
            "129.00", "169.00", 0,
            List.of(new VarianteDemo("Negro", "129.00", "169.00", 25), new VarianteDemo("Blanco", "139.00", null, 0)),
            null);

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
                            TAZA,
                            p("Guía digital: barismo en casa", "E-book de 60 páginas con recetas y técnicas de preparación.", "19.90", null, 1, true),
                            p("Café Descafeinado 250 g", "Descafeinado por proceso de agua, suave y con notas a nuez.", "34.00", null, 0, false),
                            s("Filtro de Tela Reutilizable", "Filtro de algodón orgánico para cafetera de goteo. Quedan pocas unidades.", "18.00", null, 1, 3),
                            s("Edición Limitada Geisha 100 g", "Lote de microproductor, agotado por ahora. Vuelve con la próxima cosecha.", "95.00", null, 0, 0))),
            new TiendaDemo("Casa Quinua", "casa-quinua",
                    "Granos y snacks andinos: quinua, kiwicha y cañihua de cooperativas del altiplano.", 0, "GRATIS",
                    List.of("Granos andinos", "Snacks"),
                    List.of(
                            p("Quinua Blanca Orgánica 1 kg", "Quinua lavada y lista para cocinar, certificada orgánica.", "18.50", null, 0, false),
                            p("Quinua Roja 500 g", "Textura crocante y sabor intenso. Ideal para ensaladas.", "12.00", null, 0, false),
                            p("Harina de Quinua 500 g", "Molida en piedra, sin gluten. Para panes, tortas y papillas.", "9.90", null, 0, false),
                            p("Barras de Quinua y Miel x6", "Snack energético sin conservantes.", "16.50", "19.00", 1, false, "Miel", "Cacao", "Maracuyá"),
                            p("Kiwicha Pop 250 g", "Kiwicha reventada, ligera y lista para el desayuno.", "8.50", null, 1, false),
                            p("Recetario digital de quinua", "40 recetas fáciles con quinua, en PDF.", "14.90", null, 1, true),
                            p("Cañihua Tostada 250 g", "Cañihua tostada y molida, con aroma a cacao. Ideal para batidos.", "11.50", null, 0, false),
                            p("Mix Andino para Desayuno 400 g", "Quinua, kiwicha y cañihua listas para mezclar con leche o yogur.", "15.00", null, 0, false),
                            s("Chocolate con Quinua 80 g", "Chocolate oscuro con quinua crocante. Quedan pocas unidades.", "9.50", null, 1, 3),
                            s("Quinua Negra 500 g", "Variedad de sabor intenso. Agotada hasta la próxima cosecha.", "16.00", null, 0, 0))),
            new TiendaDemo("Taller Textil Cusco", "taller-textil-cusco",
                    "Tejidos de alpaca hechos a mano por artesanas de comunidades cusqueñas.", 0, "ESTANDAR",
                    List.of("Abrigos", "Accesorios"),
                    List.of(
                            p("Chullo de Alpaca", "Chullo tradicional con orejeras, tejido a mano.", "65.00", "79.00", 1, false, "Natural", "Gris", "Rojo"),
                            p("Poncho Tejido a Mano", "Poncho de alpaca con diseños andinos, talla única.", "240.00", "290.00", 0, false),
                            p("Bufanda de Alpaca Baby", "Suave y liviana, ideal para el invierno.", "98.00", null, 1, false),
                            p("Manta Andina Matrimonial", "Manta de lana de oveja y alpaca, 2 x 2,2 m.", "180.00", null, 0, false),
                            p("Guantes Tejidos", "Guantes de lana de alpaca con forro interior.", "45.00", null, 1, false, "S", "M", "L"),
                            POLO,
                            p("Patrón digital: tejer un chullo", "Patrón paso a paso en PDF para tejer tu propio chullo.", "12.00", null, 1, true),
                            p("Calcetines de Alpaca", "Calcetines gruesos y abrigadores, tejidos con hilo de alpaca.", "32.00", null, 1, false, "S", "M", "L"),
                            s("Chalina Tejida Multicolor", "Chalina con diseños de la comunidad de Chinchero. Pocas unidades.", "72.00", "85.00", 1, 3),
                            s("Alfombra de Lana Cusqueña", "Alfombra tejida a telar, 1 x 1,5 m. Agotada por ahora.", "220.00", null, 0, 0))),
            new TiendaDemo("Neo Gadgets", "neo-gadgets",
                    "Tecnología útil para el día a día: audio, carga y accesorios de escritorio.", 1, "AVANZADO",
                    List.of("Audio", "Accesorios"),
                    List.of(
                            AUDIFONOS,
                            p("Parlante Bluetooth Mini", "Resistente al agua, 12 horas de reproducción.", "99.00", null, 0, false),
                            p("Cargador Rápido 65 W", "Dos puertos USB-C y uno USB-A, compacto.", "79.00", null, 1, false),
                            p("Soporte para Laptop de Aluminio", "Plegable y ajustable en altura.", "85.00", null, 1, false),
                            p("Cable USB-C Trenzado 1 m", "Carga rápida y transferencia de datos.", "25.00", null, 1, false),
                            p("Curso digital: fotografía con el celular", "Seis módulos en video para mejorar tus fotos.", "49.00", null, 1, true),
                            p("Mouse Inalámbrico Silencioso", "Clics silenciosos, 18 meses de batería y receptor USB.", "45.00", "55.00", 1, false),
                            p("Teclado Mecánico Compacto", "Formato 65 %, interruptores táctiles y retroiluminación.", "159.00", null, 1, false),
                            s("Lámpara LED de Escritorio", "Tres temperaturas de luz y brazo flexible. Quedan pocas unidades.", "64.00", null, 1, 3),
                            s("Power Bank 20 000 mAh", "Carga rápida de 22,5 W. Agotado hasta nuevo lote.", "119.00", null, 1, 0))),
            new TiendaDemo("Aroma Selva", "aroma-selva",
                    "Velas y cuidado personal con insumos amazónicos, hechos en pequeños lotes.", 2, "BASICO",
                    List.of("Velas", "Cuidado personal"),
                    List.of(
                            p("Vela de Soja Cedro", "Cera de soja con aroma a cedro y madera. 40 horas.", "36.00", null, 0, false),
                            p("Vela de Soja Vainilla", "Aroma cálido a vainilla, mecha de algodón.", "36.00", null, 0, false),
                            p("Jabón Artesanal de Camu Camu", "Jabón en barra con vitamina C natural.", "14.50", null, 1, false),
                            p("Aceite Esencial de Palo Santo", "10 ml, para difusores y masajes.", "28.00", "32.00", 1, false),
                            p("Difusor de Cerámica", "Difusor de aromas con vela de té incluida.", "69.00", "85.00", 1, false),
                            p("Guía digital de aromaterapia", "Combinaciones de aromas para cada momento del día.", "17.00", null, 1, true),
                            p("Vela de Soja Lavanda", "Aroma relajante de lavanda, mecha de algodón. 40 horas.", "36.00", null, 0, false),
                            p("Bálsamo Labial de Copoazú", "Hidratación profunda con manteca de copoazú.", "12.00", null, 1, false),
                            s("Crema de Manos de Aguaje", "Crema nutritiva de absorción rápida. Quedan pocas unidades.", "22.00", null, 1, 3),
                            s("Set de Regalo Aromático", "Vela, jabón y aceite esencial en caja. Agotado por ahora.", "98.00", "115.00", 0, 0))),
            new TiendaDemo("Papel y Tinta", "papel-y-tinta",
                    "Papelería para escribir, planificar y dibujar, con papel de calidad.", 2, "GRATIS",
                    List.of("Cuadernos", "Escritura"),
                    List.of(
                            p("Cuaderno Cosido Tapa Dura A5", "Papel de 100 g, 160 hojas, cosido a mano.", "24.00", null, 0, false, "Liso", "Cuadriculado", "Punteado"),
                            p("Agenda 2027 Semanal", "Vista semanal, marcador de tela y bolsillo interior.", "39.00", "49.00", 0, false),
                            p("Set de 5 Lapiceros de Gel", "Tinta de secado rápido, punta de 0,5 mm.", "18.50", null, 1, false),
                            p("Pluma Fuente Clásica", "Punta de acero inoxidable y cuerpo de resina.", "85.00", null, 1, false),
                            p("Tinta Negra 50 ml", "Tinta para pluma fuente, no obstruye.", "16.00", null, 1, false),
                            p("Plantillas digitales de planificación", "Pack de plantillas imprimibles semanales y mensuales.", "9.90", null, 1, true),
                            p("Marcadores Acuarelables x12", "Doce colores con punta pincel. Se mezclan con agua.", "42.00", "50.00", 1, false),
                            p("Libreta de Bolsillo x3", "Tres libretas de 64 hojas con tapa blanda.", "19.00", null, 0, false),
                            s("Estuche de Lápices de Colores x24", "Minas blandas y pigmentadas. Pocas unidades.", "48.00", null, 1, 3),
                            s("Cuaderno Edición Limitada Cusco", "Tapa ilustrada con arte local. Agotado por ahora.", "36.00", null, 0, 0))),
            new TiendaDemo("Dulce Pacha", "dulce-pacha",
                    "Chocolates, mermeladas y dulces andinos hechos con cacao y frutas de pequeños productores.", 3, "ESTANDAR",
                    List.of("Chocolates", "Dulces y mermeladas"),
                    List.of(
                            p("Chocolate Oscuro 70 % 100 g", "Cacao fino de aroma con 70 % de cacao y poca azúcar.", "14.00", null, 0, false),
                            p("Chocolate con Ají Amarillo 100 g", "Chocolate oscuro con un toque picante de ají amarillo.", "16.00", "18.00", 0, false),
                            p("Tabletas de Cacao 100 % x3", "Tres tabletas de cacao puro, sin azúcar añadida.", "38.00", null, 0, false),
                            p("Mermelada de Aguaymanto 250 g", "Dulce y ácida, hecha con aguaymanto de la sierra.", "15.00", null, 1, false),
                            p("Mermelada de Saúco 250 g", "Receta tradicional con saúco y panela.", "15.00", null, 1, false),
                            p("Manjar Blanco Artesanal 300 g", "Cocido lentamente con leche fresca, textura cremosa.", "18.00", null, 1, false),
                            p("Cacao en Polvo 250 g", "Cacao sin azúcar para repostería y bebidas calientes.", "21.00", null, 0, false),
                            p("Alfajores de Maicena x6", "Rellenos de manjar blanco y cubiertos de coco.", "24.00", "28.00", 1, false),
                            s("Bombones Surtidos x12", "Doce bombones de cacao con rellenos de frutas. Pocas cajas.", "42.00", null, 0, 3),
                            s("Caja de Regalo Pacha", "Chocolates, mermelada y alfajores en caja. Agotada por ahora.", "89.00", "105.00", 1, 0))),
            new TiendaDemo("Patitas Andinas", "patitas-andinas",
                    "Accesorios y cuidado para perros y gatos, con fibras naturales y diseño andino.", 3, "BASICO",
                    List.of("Perros", "Gatos"),
                    List.of(
                            p("Collar de Alpaca para Perro", "Collar resistente y suave con cierre de seguridad.", "34.00", null, 0, false, "S", "M", "L"),
                            p("Correa Trenzada 1,5 m", "Correa de algodón trenzado con asa acolchada.", "39.00", "45.00", 0, false),
                            p("Cama Acolchada Mediana", "Relleno hipoalergénico y funda lavable.", "119.00", null, 0, false),
                            p("Juguete de Cuerda para Perro", "Cuerda de algodón para morder y jalar.", "16.00", null, 0, false),
                            p("Snacks Naturales de Quinua", "Galletas horneadas sin conservantes, 200 g.", "14.00", null, 0, false),
                            p("Rascador para Gatos", "Poste de sisal con base estable, 60 cm.", "75.00", null, 1, false),
                            p("Ratones de Fieltro x3", "Tres juguetes rellenos de hierba gatera.", "18.00", null, 1, false),
                            p("Comedero de Cerámica", "Antideslizante y apto para lavavajillas.", "29.00", null, 1, false),
                            s("Arnés Ajustable", "Arnés acolchado de fácil colocación. Quedan pocas unidades.", "52.00", null, 0, 3),
                            s("Casa Plegable para Gato", "Casa de tela plegable con cojín. Agotada por ahora.", "89.00", "99.00", 1, 0))),
            new TiendaDemo("Altura Outdoor", "altura-outdoor",
                    "Equipo de camping, trekking y ropa de montaña para las rutas de los Andes.", 4, "AVANZADO",
                    List.of("Camping", "Ropa de montaña"),
                    List.of(
                            p("Mochila de Trekking 40 L", "Espalda ventilada, funda de lluvia y múltiples bolsillos.", "189.00", "220.00", 0, false),
                            p("Carpa Ligera para 2 Personas", "Doble techo, 2,1 kg, montaje en cinco minutos.", "349.00", null, 0, false),
                            p("Saco de Dormir -5 °C", "Relleno sintético compacto, ideal para altura.", "159.00", null, 0, false),
                            p("Cantimplora de Acero 750 ml", "Doble pared, mantiene el frío 24 horas.", "55.00", null, 0, false),
                            p("Linterna Frontal Recargable", "400 lúmenes con tres modos y carga USB-C.", "69.00", null, 0, false),
                            p("Bastones de Trekking", "Aluminio plegable con puños de corcho.", "125.00", null, 0, false),
                            p("Polera Térmica", "Tela de secado rápido y protección UV.", "79.00", null, 1, false, "S", "M", "L", "XL"),
                            p("Gorro Impermeable", "Ala ancha con cordón ajustable.", "45.00", "55.00", 1, false),
                            s("Cocina Portátil a Gas", "Quemador compacto con encendido piezoeléctrico. Pocas unidades.", "89.00", null, 0, 3),
                            s("Botas de Montaña", "Cuero impermeable y suela antideslizante. Agotadas por ahora.", "329.00", "380.00", 1, 0))),
            new TiendaDemo("Hogar Quillay", "hogar-quillay",
                    "Decoración y utensilios de cocina con materiales naturales y hechos por artesanos.", 4, "GRATIS",
                    List.of("Decoración", "Cocina"),
                    List.of(
                            p("Cojín de Alpaca", "Funda tejida con relleno de fibra, 45 x 45 cm.", "68.00", null, 0, false),
                            p("Macetero de Cerámica", "Con plato incluido, para plantas medianas.", "35.00", null, 0, false),
                            p("Espejo Redondo de Madera", "Marco de madera de eucalipto, 50 cm de diámetro.", "110.00", "130.00", 0, false),
                            p("Posavasos de Madera x6", "Seis piezas con base de corcho.", "24.00", null, 1, false),
                            p("Tabla de Picar de Madera", "Madera de capirona, tratada con aceite mineral.", "59.00", null, 1, false),
                            p("Juego de Cuchillos x3", "Acero inoxidable con mango ergonómico.", "99.00", null, 1, false),
                            p("Frascos de Vidrio Herméticos x4", "Cuatro tamaños con tapa de bambú.", "49.00", null, 1, false),
                            p("Mantel de Algodón", "Mantel lavable de 1,5 x 2,2 m.", "62.00", null, 0, false),
                            s("Florero Artesanal", "Cerámica pintada a mano. Quedan pocas unidades.", "58.00", null, 0, 3),
                            s("Lámpara de Mesa de Mimbre", "Pantalla de mimbre tejido. Agotada por ahora.", "125.00", "145.00", 0, 0))),
            new TiendaDemo("Libros del Sur", "libros-del-sur",
                    "Libros ilustrados, guías y papelería literaria sobre la cultura y la naturaleza del Perú.", 5, "BASICO",
                    List.of("Libros", "Papelería literaria"),
                    List.of(
                            p("Cuentos de los Andes (edición ilustrada)", "Quince relatos tradicionales con ilustraciones a color.", "58.00", "65.00", 0, false),
                            p("Poemario Bilingüe Quechua-Español", "Selección de poesía contemporánea en dos idiomas.", "45.00", null, 0, false),
                            p("Atlas Ilustrado del Perú", "Mapas, fauna y cultura de cada región.", "98.00", null, 0, false),
                            p("Novela Gráfica: El Último Chasqui", "Aventura histórica ilustrada, 120 páginas.", "52.00", null, 0, false),
                            p("Guía de Aves del Cusco", "Más de 150 especies con fotografías.", "75.00", null, 0, false),
                            p("Diario de Viaje Tapa de Cuero", "Papel sin ácido y cierre de elástico.", "49.00", null, 1, false),
                            p("Marcapáginas de Alpaca x5", "Cinco marcapáginas con diseños tejidos.", "22.00", null, 1, false),
                            p("E-book: Leyendas Peruanas", "Colección de leyendas, en formato digital.", "15.00", null, 0, true),
                            s("Diccionario Quechua Edición de Colección", "Tapa dura numerada. Quedan pocas unidades.", "135.00", null, 0, 3),
                            s("Libro de Cocina Andina, primera edición", "Reimpresión agotada por ahora.", "92.00", null, 0, 0))),
            new TiendaDemo("Joyas Inti", "joyas-inti",
                    "Joyería artesanal en plata 950 con motivos incas, hecha por orfebres cusqueños.", 5, "ESTANDAR",
                    List.of("Plata", "Accesorios"),
                    List.of(
                            p("Aretes de Plata Chakana", "Plata 950 con el símbolo de la cruz andina.", "79.00", null, 0, false),
                            p("Collar Sol de Plata", "Dije inti de plata 950 con cadena de 45 cm.", "125.00", "149.00", 0, false),
                            p("Pulsera Trenzada de Plata", "Trenzado fino y cierre de seguridad.", "98.00", null, 0, false),
                            p("Anillo Ajustable Inti", "Plata 950, ajustable a todas las tallas.", "69.00", null, 0, false),
                            p("Dije de Lapislázuli", "Piedra natural engarzada en plata.", "89.00", null, 0, false),
                            p("Broche de Plata Colibrí", "Broche con detalle calado, 4 cm.", "65.00", null, 1, false),
                            p("Cadena de Plata 45 cm", "Cadena veneciana de plata 950.", "85.00", null, 1, false),
                            p("Llavero Chakana", "Llavero de aleación con baño de plata.", "18.00", null, 1, false),
                            s("Set de Novia de Plata", "Collar y aretes a juego en estuche. Quedan pocos sets.", "245.00", "280.00", 0, 3),
                            s("Colgante de Oro Laminado", "Edición limitada, agotado por ahora.", "159.00", null, 1, 0))));

    static final List<String> COMENTARIOS = List.of(
            "Llegó rápido y tal como se ve en las fotos.",
            "Muy buena calidad, lo volvería a comprar.",
            "Buen producto, el empaque podría mejorar.",
            "Excelente relación entre precio y calidad.",
            "Cumple con lo que promete. Recomendado.",
            "Me encantó, lo regalé y gustó mucho.",
            "Está bien, esperaba algo un poco más grande.",
            "Atención muy amable y envío bien cuidado.");

    private static final java.util.Map<String, List<String>> FOTOS_REALES = cargarFotos();

    /**
     * Fotos reales del producto (clave «tienda|producto») desde {@code semilla/fotos.json}, generado con
     * {@code scripts/buscar-fotos-pexels.py}. Sin archivo o sin entrada, la semilla usa imágenes de relleno.
     */
    static List<String> fotosReales(String tienda, String producto) {
        return FOTOS_REALES.getOrDefault(tienda + "|" + producto, List.of());
    }

    private static java.util.Map<String, List<String>> cargarFotos() {
        try (java.io.InputStream in = DatosDemo.class.getResourceAsStream("/semilla/fotos.json")) {
            if (in == null) {
                return java.util.Map.of();
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(in,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, List<String>>>() {
                    });
        } catch (java.io.IOException ex) {
            return java.util.Map.of();
        }
    }

    static String slug(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String limpio = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return limpio.length() > 190 ? limpio.substring(0, 190) : limpio;
    }
}
