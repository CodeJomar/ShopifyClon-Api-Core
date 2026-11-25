"""
Busca en Pixabay una o dos fotos por producto de la semilla de demostración, las descarga a
src/main/resources/semilla-fotos/ y escribe src/main/resources/semilla/fotos.json (clave "tienda|producto").

Uso (la clave NUNCA se guarda en el repositorio; entra por variable de entorno):
    PIXABAY_KEY=<tu clave> python scripts/buscar-fotos-pixabay.py [--rehacer]

Pixabay no permite enlazar sus fotos directamente: hay que descargarlas, y eso hace este script. Solo rehace los
productos que aún no tienen fotos (usa --rehacer para empezar de cero). Límite de la API: 100 búsquedas por minuto.
"""
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
JAVA = RAIZ / "src/main/java/shopify/api/core/modulos/semilla/servicio/DatosDemo.java"
FOTOS = RAIZ / "src/main/resources/semilla-fotos"
INDICE = RAIZ / "src/main/resources/semilla/fotos.json"

# Una búsqueda (en inglés, rinde mejor) por producto, en el mismo orden que DatosDemo.java. None = ya tiene fotos propias.
CONSULTAS = {
    "cafe-andino": ["coffee beans bag", "roasted coffee beans", "cold brew coffee glass", "french press coffee", "manual coffee grinder",
                    None, "barista coffee brewing", "espresso coffee cup", "coffee filter paper", "green coffee beans"],
    "casa-quinua": ["white quinoa grain", "red quinoa seeds", "quinoa flour", "granola bars honey", "puffed cereal grain bowl", "quinoa dish recipe",
                    "toasted grain seeds", "muesli breakfast bowl", "dark chocolate bar", "black quinoa seeds"],
    "taller-textil-cusco": ["alpaca wool hat peru", "wool poncho cape fashion", "wool scarf folded", "andean wool blanket", "knitted wool gloves",
                            None, "knitting wool hat", "wool socks", "colorful knitted scarf", "wool rug carpet pattern"],
    "neo-gadgets": [None, "bluetooth speaker", "phone charger usb plug", "laptop stand desk", "usb c cable braided", "smartphone photography",
                    "computer mouse white", "keyboard keys closeup", "desk lamp", "portable charger battery phone"],
    "aroma-selva": ["candle wax jar", "scented candle jar", "handmade soap bar", "essential oil bottle", "ceramic aroma diffuser",
                    "aromatherapy essential oils", "lavender candle", "natural lip balm", "hand cream tube", "spa gift set candles soap"],
    "papel-y-tinta": ["hardcover notebook", "weekly planner agenda", "gel pens set", "fountain pen nib", "black ink bottle",
                      "planner printable template", "markers pens colorful", "notebook small kraft", "colored pencils box", "leather journal notebook"],
    "dulce-pacha": ["dark chocolate bar", "chili chocolate", "cocoa bars", "apricot jam jar", "berry jam jar", "caramel spread jar",
                    "cocoa powder", "alfajores cookies", "assorted chocolate pralines", "chocolate gift box"],
    "patitas-andinas": ["dog collar", "dog leash walking", "dog bed cushion", "dog rope toy", "dog treats biscuits", "cat scratching post",
                        "cat toy mouse", "ceramic pet bowl", "dog harness", "cat bed cushion"],
    "altura-outdoor": ["hiking backpack", "camping tent", "sleeping bag camping", "stainless steel water bottle", "headlamp", "trekking poles",
                       "hiking shirt outdoor", "bucket hat outdoor", "camping gas stove", "hiking boots mountain"],
    "hogar-quillay": ["decorative cushion pillow", "ceramic plant pot", "round mirror wall", "wooden coasters cup", "wooden cutting board",
                      "kitchen knives set", "glass jars bamboo lid", "cotton tablecloth", "handmade ceramic vase", "rattan lamp"],
    "libros-del-sur": ["children storybook open", "poetry book", "atlas book world map", "comic book", "bird guide book",
                       "leather travel journal", "bookmark book", "ebook reader", "vintage dictionary book", "cookbook recipe open"],
    "joyas-inti": ["silver earrings jewelry", "silver pendant necklace", "silver bracelet braided", "silver ring jewelry", "lapis lazuli pendant",
                   "silver brooch pin jewelry", "silver chain necklace", "keychain keyring", "silver jewelry set necklace earrings", "gold pendant necklace"],
}
CONSTANTES = {"POLO": "Polo de Algodón Pima", "TAZA": "Taza de Cerámica Artesanal", "AUDIFONOS": "Audífonos Inalámbricos Neo Buds"}
POR_PRODUCTO = 2


def slug(texto):
    sin = unicodedata.normalize("NFD", texto)
    sin = "".join(c for c in sin if unicodedata.category(c) != "Mn")
    return re.sub(r"(^-+|-+$)", "", re.sub(r"[^a-z0-9]+", "-", sin.lower()))[:190]


def productos():
    """(tienda, título) en el orden de DatosDemo.java."""
    texto = JAVA.read_text(encoding="utf-8")
    cuerpo = texto[texto.index("static final List<TiendaDemo> TIENDAS"):]
    resultado = []
    for parte in re.split(r'new TiendaDemo\("', cuerpo)[1:]:
        tienda = parte.split('"')[2]
        for m in re.finditer(r'\b(?:p|s)\("([^"]+)"|\b(POLO|TAZA|AUDIFONOS)\b', parte):
            resultado.append((tienda, m.group(1) or CONSTANTES[m.group(2)]))
    return resultado


def buscar(clave, consulta):
    url = "https://pixabay.com/api/?" + urllib.parse.urlencode({
        "key": clave, "q": consulta, "image_type": "photo", "safe_search": "true", "per_page": 20, "min_width": 640})
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "otemu-semilla/1.0"}), timeout=30) as r:
        hits = json.loads(r.read())["hits"]
    palabras = [p for p in consulta.lower().split() if len(p) > 2]
    # Mejor foto = la que más palabras de la búsqueda tiene en sus etiquetas y menos personas muestra; desempate por popularidad
    def puntaje(h):
        etiquetas = h.get("tags", "").lower()
        return (sum(p in etiquetas for p in palabras) - (2 if any(x in etiquetas for x in ("woman", "girl", "man ", "people", "person", "child")) else 0),
                h.get("likes", 0))
    return sorted(hits, key=puntaje, reverse=True)


def descargar(url, destino):
    req = urllib.request.Request(url, headers={"User-Agent": "otemu-semilla/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        destino.write_bytes(r.read())


def main():
    clave = os.environ.get("PIXABAY_KEY", "").strip()
    if not clave:
        sys.exit("Falta la variable de entorno PIXABAY_KEY")
    FOTOS.mkdir(parents=True, exist_ok=True)
    INDICE.parent.mkdir(parents=True, exist_ok=True)
    indice = {} if "--rehacer" in sys.argv or not INDICE.exists() else json.loads(INDICE.read_text(encoding="utf-8"))
    por_tienda = {}
    for tienda, titulo in productos():
        por_tienda.setdefault(tienda, []).append(titulo)
    sin_foto = []
    usadas = set()
    for tienda, titulos in por_tienda.items():
        consultas = CONSULTAS[tienda]
        assert len(consultas) == len(titulos), f"{tienda}: {len(consultas)} consultas para {len(titulos)} productos"
        for titulo, consulta in zip(titulos, consultas):
            if consulta is None:
                continue
            id_producto = f"{tienda}|{slug(titulo)}"
            if indice.get(id_producto):
                continue
            hits = buscar(clave, consulta)
            time.sleep(0.8)
            archivos = []
            libres = [h for h in hits if h["id"] not in usadas][:POR_PRODUCTO]
            for i, hit in enumerate(libres, start=1):
                usadas.add(hit["id"])
                nombre = f"{tienda}--{slug(titulo)}-{i}.jpg"
                descargar(hit["webformatURL"], FOTOS / nombre)
                archivos.append(nombre)
            if archivos:
                indice[id_producto] = archivos
            else:
                sin_foto.append((tienda, titulo, consulta))
            print(f"{id_producto}: {len(archivos)} fotos ({consulta})", flush=True)
            INDICE.write_text(json.dumps(indice, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"Listo: {len(indice)} productos con fotos; sin resultados: {sin_foto}")


if __name__ == "__main__":
    main()
