/*
 * Recetas compartidas del laboratorio: cada una hace llamadas reales y deja ids en el estado.
 * Se usan como botones de "Preparación rápida" en las páginas de módulo (Lab.R.nombre).
 */
(function () {
  'use strict';
  var L = window.Lab;
  var R = {};

  function exigirSesion(c) {
    if (!c.e.obtener('token')) { throw new Error('Primero inicia sesión en «Cuenta y sesión».'); }
  }
  function exigirTienda(c) {
    exigirSesion(c);
    if (!c.e.obtener('tiendaId')) { throw new Error('Primero elige o crea una tienda (botón «Crear una tienda de prueba» o «Usar mi primera tienda operativa»).'); }
  }

  // Cada compra del laboratorio consume stock: si queda poco, se repone a 100 para que las pruebas no se agoten.
  function asegurarStock(c, variante) {
    if (!c.e.obtener('token') || !c.e.obtener('tiendaId')) { return Promise.resolve(); }
    var ruta = '/panel/inventario/variantes/' + variante;
    return c.pedir({ metodo: 'GET', ruta: ruta, tienda: true }).then(function (r) {
      if (r.estado !== 200) { return null; }
      var d = r.json.datos || {};
      var vendible = d.cantidad_vendible !== undefined ? d.cantidad_vendible : (d.vendible !== undefined ? d.vendible : null);
      if (vendible !== null && vendible >= 10) { return null; }
      var reservada = d.cantidad_reservada || d.reservada || 0;
      return c.pedir({ metodo: 'PUT', ruta: ruta, tienda: true, cuerpo: { nueva_cantidad: 100 + reservada, motivo: 'Reposición automática del laboratorio' } })
        .then(function (a) { if (a.estado === 200) { c.log('Stock repuesto a ' + (100 + reservada)); } });
    });
  }

  R.usarTiendaExistente = {
    titulo: 'Usar mi primera tienda operativa',
    descripcion: 'Lee GET /cuenta/tiendas y deja la primera tienda operativa como tienda activa.',
    ejecutar: function (c) {
      exigirSesion(c);
      return c.pedir({ metodo: 'GET', ruta: '/cuenta/tiendas' }).then(function (r) {
        var lista = c.exigir(r, 200, 'Listar mis tiendas');
        var tienda = lista.filter(function (t) { return t.operativa; })[0] || lista[0];
        if (!tienda) { throw new Error('No tienes tiendas: usa «Crear una tienda de prueba».'); }
        c.e.poner('tiendaId', tienda.tienda_id); c.e.poner('subdominio', tienda.subdominio); c.e.poner('rolTienda', tienda.rol);
        c.log('Tienda activa: ' + tienda.nombre + ' (' + tienda.subdominio + ', rol ' + tienda.rol + ', operativa=' + tienda.operativa + ')');
      });
    }
  };

  R.crearTienda = {
    titulo: 'Crear una tienda de prueba',
    descripcion: 'POST /cuenta/tiendas con nombre y subdominio al azar. La primera tienda del usuario recibe prueba gratuita; las siguientes nacen VENCIDAS.',
    ejecutar: function (c) {
      exigirSesion(c);
      var sub = 'lab-' + c.azar(7);
      return c.pedir({ metodo: 'POST', ruta: '/cuenta/tiendas', cuerpo: { nombre: 'Tienda Lab ' + sub, subdominio: sub, correo_contacto: 'lab-' + sub + '@laboratorio.test' } })
        .then(function (r) {
          var d = c.exigir(r, 201, 'Crear tienda');
          c.e.poner('tiendaId', d.id); c.e.poner('subdominio', d.subdominio);
          c.log('Tienda ' + d.subdominio + ' creada; suscripción ' + d.suscripcion.estado + (d.operativa ? ' (operativa)' : ' (nace VENCIDA porque ya usaste tu prueba gratuita)'));
          if (d.operativa) { return null; }
          // Para que el laboratorio pueda seguir escribiendo en el panel, se paga con la pasarela simulada de suscripciones
          c.log('Pagando la suscripción con la pasarela simulada (token tok_lab)…');
          return c.pedir({ metodo: 'POST', ruta: '/panel/suscripcion/pagar', tienda: true, cuerpo: { ciclo: 'MENSUAL', token_pago: 'tok_lab' } })
            .then(function (p) { c.exigir(p, 200, 'Pagar la suscripción'); c.log('Suscripción pagada: la tienda ya es operativa'); });
        });
    }
  };

  R.crearTarifa = {
    titulo: 'Crear una tarifa de envío',
    descripcion: 'POST /panel/envios/tarifas (5.00, gratis desde 500). Sin tarifa activa no se puede comprar con envío.',
    ejecutar: function (c) {
      exigirTienda(c);
      return c.pedir({ metodo: 'POST', ruta: '/panel/envios/tarifas', tienda: true,
        cuerpo: { nombre: 'Estándar Lab ' + c.azar(3), descripcion: 'Entrega en 3 a 5 días', precio: 5, monto_minimo_gratis: 500, esta_activa: true, orden: 1 } })
        .then(function (r) { var d = c.exigir(r, 201, 'Crear tarifa'); c.e.poner('tarifaId', d.id); c.log('Tarifa ' + d.id + ' creada'); });
    }
  };

  R.crearProducto = {
    titulo: 'Crear producto publicado (con stock)',
    descripcion: 'Crea un producto con una variante (precio 50.00, 20 en stock), le pone imagen principal y lo publica.',
    ejecutar: function (c) {
      exigirTienda(c);
      var sku = 'LAB-' + c.azar(6).toUpperCase();
      return c.pedir({ metodo: 'POST', ruta: '/panel/catalogo/productos', tienda: true, cuerpo: {
        titulo: 'Producto Lab ' + sku, descripcion: 'Creado por el laboratorio', es_digital: false,
        variantes: [{ titulo: 'Única', sku: sku, precio: 50, requiere_envio: true, cantidad_inicial: 20, rastrea_inventario: true, permitir_venta_sin_stock: false }] } })
        .then(function (r) {
          var d = c.exigir(r, 201, 'Crear producto');
          c.e.poner('productoId', d.id); c.e.poner('varianteId', d.variantes[0].id); c.e.poner('sku', d.variantes[0].sku); c.e.poner('slug', d.identificador_url);
          c.log('Producto ' + d.id + ' con variante ' + d.variantes[0].id);
          return c.pedir({ metodo: 'POST', ruta: '/panel/catalogo/productos/' + d.id + '/imagenes', tienda: true,
            cuerpo: { url_imagen: 'https://picsum.photos/seed/' + sku + '/600/600', texto_alternativo: 'Imagen del laboratorio', es_principal: true } });
        }).then(function (r) {
          c.exigir(r, [200, 201], 'Agregar imagen'); c.log('Imagen principal agregada');
          return c.pedir({ metodo: 'POST', ruta: '/panel/catalogo/productos/' + c.e.obtener('productoId') + '/publicar', tienda: true });
        }).then(function (r) { c.exigir(r, 200, 'Publicar producto'); c.log('Producto publicado'); });
    }
  };

  R.crearCarrito = {
    titulo: 'Crear un carrito con 2 unidades',
    descripcion: 'POST /vitrina/{subdominio}/carrito y agrega la variante del estado. Guarda el token del carrito.',
    ejecutar: function (c) {
      var sub = c.e.obtener('subdominio'), variante = c.e.obtener('varianteId');
      if (!sub) { throw new Error('Falta la tienda (subdominio).'); }
      if (!variante) { throw new Error('Falta una variante: usa «Crear producto publicado» en Catálogo.'); }
      return asegurarStock(c, variante).then(function () { return c.pedir({ metodo: 'POST', ruta: '/vitrina/' + sub + '/carrito', sesion: false }); }).then(function (r) {
        var d = c.exigir(r, 201, 'Crear carrito'); c.e.poner('carritoToken', d.token); c.log('Carrito creado');
        return c.pedir({ metodo: 'POST', ruta: '/vitrina/' + sub + '/carrito/items', sesion: false, carrito: true, cuerpo: { variante_id: variante, cantidad: 2 } });
      }).then(function (r) { var d = c.exigir(r, 200, 'Agregar al carrito'); c.log('Carrito con ' + d.items.length + ' línea(s); subtotal ' + d.subtotal); });
    }
  };

  R.hacerCheckout = {
    titulo: 'Comprar el carrito (pago manual)',
    descripcion: 'Cotiza con la tarifa del estado y hace el checkout con coordenadas. Guarda orden, número y token de acceso.',
    ejecutar: function (c) {
      var sub = c.e.obtener('subdominio'), tarifa = c.e.obtener('tarifaId');
      if (!c.e.obtener('carritoToken')) { throw new Error('Falta el carrito: usa «Crear un carrito con 2 unidades» en Carrito.'); }
      if (!tarifa) { throw new Error('Falta una tarifa: usa «Crear una tarifa de envío» en Tarifas de envío.'); }
      return c.pedir({ metodo: 'GET', ruta: '/vitrina/' + sub + '/carrito', sesion: false, carrito: true, query: { tarifa_envio_id: tarifa } }).then(function (r) {
        var cart = c.exigir(r, 200, 'Cotizar'); c.log('Total estimado con envío: ' + cart.total_estimado);
        return c.pedir({ metodo: 'POST', ruta: '/vitrina/' + sub + '/checkout', carrito: true, idem: true, cuerpo: {
          correo_cliente: 'comprador-' + c.azar(4) + '@laboratorio.test', nombre_cliente: 'Comprador Laboratorio', telefono_cliente: '999888777',
          direccion_envio: { nombre_contacto: 'Comprador Laboratorio', telefono: '999888777', direccion_linea1: 'Av. Siempre Viva 123', ciudad: 'Lima', estado_provincia: 'Lima', codigo_postal: '15001', pais: 'PE', latitud: -12.0464, longitud: -77.0428 },
          tarifa_envio_id: tarifa, pasarela: 'MANUAL', total_esperado: cart.total_estimado } });
      }).then(function (r) {
        var d = c.exigir(r, [200, 201], 'Checkout');
        c.e.poner('ordenId', d.orden_id); c.e.poner('numeroOrden', d.numero_orden); c.e.poner('tokenAcceso', d.token_acceso); c.e.poner('totalOrden', d.total);
        c.log('Orden ' + d.numero_orden + ' creada (total ' + d.total + ')');
      });
    }
  };

  R.pagarOrden = {
    titulo: 'Registrar el pago manual de la orden',
    descripcion: 'POST /panel/ordenes/{id}/pagos/manual. Deja la orden pagada para poder prepararla y enviarla.',
    ejecutar: function (c) {
      exigirTienda(c);
      if (!c.e.obtener('ordenId')) { throw new Error('Falta la orden: usa «Comprar el carrito» en Checkout.'); }
      return c.pedir({ metodo: 'POST', ruta: '/panel/ordenes/' + c.e.obtener('ordenId') + '/pagos/manual', tienda: true, cuerpo: { referencia: 'LAB-' + c.azar(5) } })
        .then(function (r) { c.exigir(r, 201, 'Pago manual'); c.log('Orden pagada'); });
    }
  };

  R.crearEnvio = {
    titulo: 'Preparar la orden (crear el envío)',
    descripcion: 'POST /panel/ordenes/{id}/envio. La orden debe estar pagada. Guarda el envío y su guía.',
    ejecutar: function (c) {
      exigirTienda(c);
      if (!c.e.obtener('ordenId')) { throw new Error('Falta la orden pagada.'); }
      return c.pedir({ metodo: 'POST', ruta: '/panel/ordenes/' + c.e.obtener('ordenId') + '/envio', tienda: true, cuerpo: { empresa_transporte: 'Transporte Lab' } })
        .then(function (r) {
          var d = c.exigir(r, 201, 'Crear envío'); c.e.poner('envioId', d.id); c.e.poner('guia', d.numero_guia);
          c.log('Envío ' + d.id + ' con guía ' + d.numero_guia);
        });
    }
  };

  R.ordenCompleta = {
    titulo: 'Todo de una vez: tienda → producto → carrito → orden pagada → envío',
    descripcion: 'Encadena las recetas anteriores (crea tienda, tarifa, producto, carrito, compra, pago y envío).',
    ejecutar: function (c) {
      var pasos = [R.crearTienda, R.crearTarifa, R.crearProducto, R.crearCarrito, R.hacerCheckout, R.pagarOrden, R.crearEnvio];
      return pasos.reduce(function (prev, p) {
        return prev.then(function () { c.log('— ' + p.titulo); return p.ejecutar(c); });
      }, Promise.resolve());
    }
  };

  // Una tienda nueva que ya no tiene prueba gratuita nace VENCIDA: se paga con la pasarela simulada para poder operar.
  L.pagarTiendaNueva = function (c, d) {
    if (d.operativa) { return Promise.resolve(); }
    return c.pedir({ metodo: 'POST', ruta: '/panel/suscripcion/pagar', tienda: d.id, cuerpo: { ciclo: 'MENSUAL', token_pago: 'tok_lab' } })
      .then(function (p) { c.exigir(p, 200, 'Pagar la suscripción de la tienda nueva'); });
  };

  L.R = R;
})();
