/*
 * Laboratorio de endpoints: motor común.
 * Cada página de módulo declara sus endpoints y casos con Lab.pagina({...}); este archivo los dibuja,
 * ejecuta contra la API real, compara con lo esperado y recuerda el estado (token, tienda, ids...).
 */
(function () {
  'use strict';

  var CLAVE = 'laboratorio-estado-v1';
  var datos = {};
  try { datos = JSON.parse(localStorage.getItem(CLAVE) || '{}'); } catch (_) { datos = {}; }

  var MODULOS = [
    ['Empezar', [['index.html', 'Inicio y conexión']]],
    ['Cuenta y tienda', [
      ['cuenta.html', 'Cuenta y sesión'],
      ['tienda.html', 'Tienda y personal'],
      ['suscripcion.html', 'Suscripción']]],
    ['Catálogo', [
      ['catalogo.html', 'Catálogo'],
      ['inventario.html', 'Inventario'],
      ['cupones.html', 'Cupones'],
      ['tarifas.html', 'Tarifas de envío']]],
    ['Venta', [
      ['carrito.html', 'Carrito'],
      ['checkout.html', 'Checkout'],
      ['ordenes.html', 'Órdenes y cuenta del comprador'],
      ['pagos.html', 'Pagos y reembolsos']]],
    ['Entrega', [
      ['envios.html', 'Envíos y eventos'],
      ['rutas.html', 'Rutas de reparto'],
      ['seguimiento.html', 'Seguimiento público']]],
    ['Administración', [
      ['metricas.html', 'Métricas'],
      ['plataforma.html', 'Plataforma']]]
  ];

  // ---------- estado persistente ----------
  function persistir() { try { localStorage.setItem(CLAVE, JSON.stringify(datos)); } catch (_) { /* sin almacenamiento */ } }
  function baseDetectada() {
    var m = location.pathname.match(/^(.*?\/api\/v1)\//);
    return location.origin + (m ? m[1] : '/api/v1');
  }
  var memoria = {};   // valores que no se guardan en disco (contraseñas)
  var Estado = {
    obtener: function (k) {
      if (k === 'base') { return datos.base || baseDetectada(); }
      return k in memoria ? memoria[k] : datos[k];
    },
    poner: function (k, v) {
      if (v === undefined || v === null || v === '') { delete datos[k]; delete memoria[k]; } else { datos[k] = v; }
      persistir(); pintarEstado();
    },
    ponerSoloMemoria: function (k, v) { memoria[k] = v; try { sessionStorage.setItem('lab-' + k, v); } catch (_) {} },
    recuperarMemoria: function (k) { try { return memoria[k] || sessionStorage.getItem('lab-' + k) || ''; } catch (_) { return memoria[k] || ''; } },
    todo: function () { var c = {}; Object.keys(datos).forEach(function (k) { c[k] = datos[k]; }); c.base = Estado.obtener('base'); return c; },
    limpiar: function () { var b = datos.base; datos = {}; if (b) { datos.base = b; } persistir(); pintarEstado(); }
  };

  // ---------- utilidades ----------
  function azar(n) {
    var abc = 'abcdefghijklmnopqrstuvwxyz0123456789', s = '';
    for (var i = 0; i < (n || 6); i++) { s += abc[Math.floor(Math.random() * abc.length)]; }
    return s;
  }
  function uuid() {
    if (window.crypto && crypto.randomUUID) { return crypto.randomUUID(); }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      var r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 3 | 8)).toString(16);
    });
  }
  function fechaMas(dias) {
    var d = new Date(); d.setDate(d.getDate() + dias);
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }
  function fechaHora(dias) { var d = new Date(); d.setDate(d.getDate() + dias); return d.toISOString().replace(/\.\d+Z$/, 'Z'); }
  function escapar(t) {
    return String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }
  function formato(t) {
    return escapar(t).replace(/`([^`]+)`/g, '<code>$1</code>').replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  }
  function h(tag, attrs) {
    var el = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) {
        if (k === 'class') { el.className = attrs[k]; }
        else if (k === 'texto') { el.textContent = attrs[k]; }
        else if (k === 'html') { el.innerHTML = attrs[k]; }
        else if (k.indexOf('on') === 0) { el.addEventListener(k.slice(2), attrs[k]); }
        else if (attrs[k] !== false && attrs[k] !== undefined && attrs[k] !== null) { el.setAttribute(k, attrs[k]); }
      });
    }
    for (var i = 2; i < arguments.length; i++) {
      var hijo = arguments[i];
      if (hijo === null || hijo === undefined || hijo === false) { continue; }
      el.appendChild(typeof hijo === 'string' ? document.createTextNode(hijo) : hijo);
    }
    return el;
  }
  function jsonBonito(v) { try { return JSON.stringify(v, null, 2); } catch (_) { return String(v); } }
  function recortar(t, n) { t = String(t); return t.length > n ? t.slice(0, n) + '…' : t; }
  function copiar(texto, boton) {
    var ok = function () { if (boton) { var a = boton.textContent; boton.textContent = 'Copiado'; setTimeout(function () { boton.textContent = a; }, 1200); } };
    if (navigator.clipboard && navigator.clipboard.writeText) { navigator.clipboard.writeText(texto).then(ok, ok); return; }
    var t = h('textarea'); t.value = texto; document.body.appendChild(t); t.select();
    try { document.execCommand('copy'); } catch (_) {} document.body.removeChild(t); ok();
  }

  // ---------- llamadas a la API ----------
  function armarCurl(metodo, url, cabeceras, cuerpoTexto) {
    var partes = ['curl -X ' + metodo + " '" + url + "'"];
    Object.keys(cabeceras).forEach(function (k) { partes.push("-H '" + k + ': ' + cabeceras[k] + "'"); });
    if (cuerpoTexto) { partes.push("-d '" + cuerpoTexto.replace(/'/g, "'\\''") + "'"); }
    return partes.join(' \\\n  ');
  }

  /**
   * p: { metodo, ruta, query, cuerpo, cabeceras, sesion (false = sin Authorization), tienda (true = X-Tienda-Id del estado),
   *      carrito (true = X-Carrito-Token del estado), idem (true = Idempotency-Key nueva), archivo ({campo, nombre, tipo, base64}) }
   */
  function pedir(p) {
    var url = Estado.obtener('base') + p.ruta;
    if (p.query) {
      var qs = new URLSearchParams();
      Object.keys(p.query).forEach(function (k) { if (p.query[k] !== undefined && p.query[k] !== null && p.query[k] !== '') { qs.append(k, p.query[k]); } });
      var s = qs.toString(); if (s) { url += '?' + s; }
    }
    var cab = { 'Accept': 'application/json' };
    var cuerpoTexto = null, cuerpo = null;
    if (p.archivo) {
      var bin = atob(p.archivo.base64), bytes = new Uint8Array(bin.length);
      for (var i = 0; i < bin.length; i++) { bytes[i] = bin.charCodeAt(i); }
      cuerpo = new FormData();
      cuerpo.append(p.archivo.campo || 'archivo', new Blob([bytes], { type: p.archivo.tipo }), p.archivo.nombre);
      cuerpoTexto = '(multipart: ' + p.archivo.campo + '=' + p.archivo.nombre + ')';
    } else if (p.cuerpo !== undefined && p.cuerpo !== null) {
      cuerpoTexto = typeof p.cuerpo === 'string' ? p.cuerpo : JSON.stringify(p.cuerpo);
      cuerpo = cuerpoTexto; cab['Content-Type'] = 'application/json';
    }
    var token = p.token !== undefined ? p.token : Estado.obtener('token');
    if (p.sesion !== false && token) { cab['Authorization'] = 'Bearer ' + token; }
    if (p.tienda) { var t = p.tienda === true ? Estado.obtener('tiendaId') : p.tienda; if (t) { cab['X-Tienda-Id'] = t; } }
    if (p.carrito) { var c = p.carrito === true ? Estado.obtener('carritoToken') : p.carrito; if (c) { cab['X-Carrito-Token'] = c; } }
    if (p.idem) { cab['Idempotency-Key'] = p.idem === true ? uuid() : p.idem; }
    Object.keys(p.cabeceras || {}).forEach(function (k) { if (p.cabeceras[k] === null) { delete cab[k]; } else { cab[k] = p.cabeceras[k]; } });
    var curl = armarCurl(p.metodo, url, cab, cuerpoTexto);
    var peticion = { metodo: p.metodo, url: url, cabeceras: cab, cuerpo: p.archivo ? cuerpoTexto : p.cuerpo };
    var t0 = performance.now();
    var reintentos = p.reintentos === undefined ? 2 : p.reintentos;
    return fetch(url, { method: p.metodo, headers: cab, body: cuerpo }).then(function (r) {
      return r.text().then(function (texto) {
        var json = null; try { json = JSON.parse(texto); } catch (_) {}
        var espera = parseInt(r.headers.get('Retry-After'), 10);
        if (r.status === 429 && reintentos > 0 && espera > 0 && espera <= 20 && !p.quiero429) {
          // límite de velocidad de la API: se espera lo que pide y se reintenta solo
          return new Promise(function (ok) { setTimeout(ok, (espera + 1) * 1000); }).then(function () {
            var q = {}; Object.keys(p).forEach(function (k) { q[k] = p[k]; }); q.reintentos = reintentos - 1;
            return pedir(q);
          });
        }
        return {
          estado: r.status, ok: r.ok, ms: Math.round(performance.now() - t0), json: json, texto: texto, curl: curl, peticion: peticion,
          reintentarEn: r.headers.get('Retry-After'), correlacion: r.headers.get('X-Correlacion-Id')
        };
      });
    }, function (err) {
      return { falloRed: true, error: String(err), ms: Math.round(performance.now() - t0), curl: curl, peticion: peticion };
    });
  }

  function evaluar(espera, resp) {
    if (resp.falloRed) { return { ok: false, razones: ['Sin conexión con la API: ¿está prendido Docker? (' + resp.error + ')'] }; }
    var razones = [];
    if (!espera) { return { ok: resp.ok, razones: resp.ok ? [] : ['La API respondió HTTP ' + resp.estado] }; }
    if (espera.estado !== undefined) {
      var admitidos = [].concat(espera.estado);
      if (admitidos.indexOf(resp.estado) < 0) { razones.push('Se esperaba HTTP ' + admitidos.join(' o ') + ' y llegó ' + resp.estado); }
    }
    if (espera.codigo) {
      var cod = resp.json && resp.json.codigo;
      if (cod !== espera.codigo) { razones.push('Se esperaba el código ' + espera.codigo + ' y llegó ' + (cod || '(ninguno)')); }
    }
    if (espera.validacion) {
      var val = resp.json && resp.json.validaciones;
      if (!val || !val[espera.validacion]) { razones.push('Se esperaba un error de validación en «' + espera.validacion + '»'); }
    }
    if (espera.contiene) {
      if (!resp.texto || resp.texto.indexOf(espera.contiene) < 0) { razones.push('La respuesta debería contener «' + espera.contiene + '»'); }
    }
    if (espera.verifica) {
      try { var msg = espera.verifica(resp); if (msg) { razones.push(msg); } } catch (e) { razones.push('Verificación fallida: ' + e.message); }
    }
    return { ok: razones.length === 0, razones: razones };
  }

  // ---------- recetas compartidas (preparan el terreno con llamadas reales) ----------
  var Recetas = {};
  function exigir(resp, estados, que) {
    if (resp.falloRed) { throw new Error('Sin conexión con la API (¿Docker prendido?)'); }
    var admitidos = [].concat(estados);
    if (admitidos.indexOf(resp.estado) < 0) {
      var msg = resp.json && resp.json.mensaje ? ' — ' + resp.json.mensaje : '';
      var cod = resp.json && resp.json.codigo ? ' [' + resp.json.codigo + ']' : '';
      throw new Error(que + ': HTTP ' + resp.estado + cod + msg);
    }
    return resp.json && resp.json.datos !== undefined ? resp.json.datos : resp.json;
  }
  var PNG_1X1 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

  function inicioDeEstado() { return Estado; }

  // ---------- dibujo ----------
  var barraEstado = null;
  function pintarEstado() {
    if (!barraEstado) { return; }
    barraEstado.innerHTML = '';
    var correo = Estado.obtener('correo'), tokenPuesto = !!Estado.obtener('token');
    barraEstado.appendChild(h('span', { class: 'conexion', id: 'lab-conexion', texto: 'API: sin comprobar' }));
    barraEstado.appendChild(chip('Sesión', tokenPuesto ? (correo || 'iniciada') : 'sin iniciar', tokenPuesto));
    var sub = Estado.obtener('subdominio');
    barraEstado.appendChild(chip('Tienda', sub || 'ninguna', !!Estado.obtener('tiendaId')));
    if (Estado.obtener('ordenId')) { barraEstado.appendChild(chip('Orden', Estado.obtener('numeroOrden') || recortar(Estado.obtener('ordenId'), 8), true)); }
    barraEstado.appendChild(h('button', { class: 'chico', texto: 'Ver y editar estado', onclick: abrirEstado }));
    barraEstado.appendChild(h('button', { class: 'chico', texto: 'Probar conexión', onclick: comprobarConexion }));
  }
  function chip(etq, valor, ok) {
    return h('span', { class: 'chip ' + (ok ? 'ok' : 'falta'), title: etq + ': ' + valor }, etq + ': ', h('b', { texto: valor }));
  }
  function comprobarConexion() {
    var el = document.getElementById('lab-conexion');
    if (el) { el.className = 'conexion'; el.textContent = 'API: comprobando…'; }
    return pedir({ metodo: 'GET', ruta: '/planes', sesion: false }).then(function (r) {
      var el2 = document.getElementById('lab-conexion');
      if (!el2) { return r; }
      if (r.falloRed) { el2.className = 'conexion caida'; el2.textContent = 'API: sin conexión (¿Docker prendido?)'; }
      else { el2.className = 'conexion arriba'; el2.textContent = 'API conectada (' + r.ms + ' ms)'; }
      return r;
    });
  }

  var modal = null;
  function abrirEstado() {
    if (!modal) {
      modal = h('div', { class: 'modal-fondo', onclick: function (e) { if (e.target === modal) { modal.classList.remove('abierto'); } } });
      document.body.appendChild(modal);
    }
    modal.innerHTML = '';
    var tabla = h('table', { class: 'tabla-estado' });
    var todo = Estado.todo();
    var claves = Object.keys(todo).sort();
    if (!claves.length) { tabla.appendChild(h('tr', null, h('td', { texto: 'Aún no hay nada guardado.' }))); }
    claves.forEach(function (k) {
      var campo = h('input', { value: k === 'token' ? todo[k] : String(todo[k]) });
      campo.addEventListener('change', function () { Estado.poner(k, campo.value); });
      tabla.appendChild(h('tr', null, h('td', { texto: k, class: 'mono' }), h('td', null, campo),
        h('td', null, h('button', { class: 'chico', texto: 'Quitar', onclick: function () { Estado.poner(k, ''); abrirEstado(); } }))));
    });
    var nueva = h('input', { placeholder: 'clave nueva' }), valorNuevo = h('input', { placeholder: 'valor' });
    modal.appendChild(h('div', { class: 'panel modal' },
      h('h2', { texto: 'Estado del laboratorio' }),
      h('p', { class: 'espera', texto: 'Se guarda en este navegador (localStorage). Aquí viven el token de sesión, la tienda activa y los ids que van saliendo de las pruebas. Puedes editarlos a mano.' }),
      tabla,
      h('div', { class: 'campos' }, h('div', { class: 'campo' }, nueva), h('div', { class: 'campo' }, valorNuevo),
        h('button', { class: 'chico', texto: 'Agregar', onclick: function () { if (nueva.value) { Estado.poner(nueva.value, valorNuevo.value); abrirEstado(); } } })),
      h('div', { class: 'copiar-fila' },
        h('button', { texto: 'Limpiar todo (menos la URL base)', onclick: function () { Estado.limpiar(); abrirEstado(); } }),
        h('button', { class: 'primario', texto: 'Cerrar', onclick: function () { modal.classList.remove('abierto'); } }))));
    modal.classList.add('abierto');
  }

  function lateral(actual) {
    var nav = h('nav', { class: 'lateral' }, h('h2', { texto: 'Laboratorio de endpoints' }));
    MODULOS.forEach(function (grupo) {
      nav.appendChild(h('div', { class: 'grupo', texto: grupo[0] }));
      grupo[1].forEach(function (m) { nav.appendChild(h('a', { href: m[0], class: m[0] === actual ? 'activo' : '', texto: m[1] })); });
    });
    nav.appendChild(h('div', { class: 'grupo', texto: 'Referencia' }));
    nav.appendChild(h('a', { href: '../swagger-ui/index.html', texto: 'Swagger UI', target: '_blank' }));
    return nav;
  }

  // Valor actual de los campos de un caso
  function leerCampos(caso, elementos) {
    var v = {};
    (caso.campos || []).forEach(function (c) {
      var el = elementos[c.k]; if (!el) { return; }
      var val = el.value;
      if (c.tipo === 'numero') { val = val === '' ? '' : Number(val); }
      if (c.tipo === 'bool') { val = val === 'true'; }
      v[c.k] = val;
    });
    return v;
  }

  function dibujarResultado(zona, caso, resp, veredicto) {
    zona.innerHTML = '';
    var clase = veredicto.ok ? 'paso' : 'fallo';
    var caja = h('div', { class: 'resultado ' + clase });
    var cab = h('div', { class: 'res-cab' },
      h('span', { class: 'veredicto', texto: veredicto.ok ? '✔ Resultado esperado' : '✘ Distinto de lo esperado' }));
    if (!resp.falloRed) {
      cab.appendChild(h('span', { class: 'estado-http', texto: 'HTTP ' + resp.estado }));
      cab.appendChild(h('span', { class: 'espera', texto: resp.ms + ' ms' }));
      if (resp.reintentarEn) { cab.appendChild(h('span', { class: 'espera', texto: 'Retry-After: ' + resp.reintentarEn + ' s' })); }
    }
    caja.appendChild(cab);
    if (!veredicto.ok) { caja.appendChild(h('div', { class: 'razones' }, veredicto.razones.join(' · '))); }
    var j = resp.json;
    if (j && (j.mensaje || j.codigo)) {
      caja.appendChild(h('div', { class: 'mensaje-api' }, j.codigo ? h('span', { class: 'codigo', texto: j.codigo }) : null, j.mensaje || ''));
    }
    if (j && j.validaciones && Object.keys(j.validaciones).length) {
      var ul = h('ul', { class: 'validaciones' });
      Object.keys(j.validaciones).forEach(function (k) { ul.appendChild(h('li', null, h('code', { texto: k }), ': ' + j.validaciones[k])); });
      caja.appendChild(ul);
    }
    if (j && j.detalles && Object.keys(j.detalles).length) {
      caja.appendChild(h('div', { class: 'mensaje-api espera' }, 'Detalles: ' + JSON.stringify(j.detalles)));
    }
    var detalle = h('details', { class: 'json' }, h('summary', { texto: 'Respuesta completa' }),
      h('pre', { texto: resp.falloRed ? resp.error : (j ? jsonBonito(j) : (resp.texto || '(vacía)')) }));
    caja.appendChild(detalle);
    var peticion = h('details', { class: 'json' }, h('summary', { texto: 'Lo que se envió' }),
      h('pre', { texto: resp.peticion.metodo + ' ' + resp.peticion.url + '\n' + jsonBonito(resp.peticion.cabeceras) +
        (resp.peticion.cuerpo !== undefined && resp.peticion.cuerpo !== null ? '\n\n' + (typeof resp.peticion.cuerpo === 'string' ? resp.peticion.cuerpo : jsonBonito(resp.peticion.cuerpo)) : '') }));
    caja.appendChild(peticion);
    var bcurl = h('button', { class: 'chico', texto: 'Copiar curl', onclick: function () { copiar(resp.curl, bcurl); } });
    caja.appendChild(h('div', { class: 'copiar-fila' }, bcurl));
    zona.appendChild(caja);
  }

  var TIPOS = { ok: '✅ Éxito', error: '❌ Error esperado', validacion: '⚠️ Validación', seguridad: '🔒 Seguridad' };
  var contadores = { paso: 0, fallo: 0, omitido: 0 };
  var actualizarContadores = function () {};
  var casosTodos = [];

  function dibujarCaso(endpoint, caso) {
    var elementos = {};
    var zona = h('div');
    var botonEjecutar = h('button', { class: 'primario chico', texto: 'Ejecutar' });
    var cuadro = h('div', { class: 'caso' });
    var cab = h('div', { class: 'caso-cab' },
      h('span', { class: 'tipo ' + (caso.tipo || 'ok'), texto: TIPOS[caso.tipo || 'ok'] }),
      h('span', { class: 'caso-titulo', texto: caso.titulo }), botonEjecutar);
    cuadro.appendChild(cab);
    if (caso.descripcion) { cuadro.appendChild(h('div', { class: 'caso-desc', texto: caso.descripcion })); }
    if (caso.espera) {
      var partes = ['Esperado: HTTP ' + [].concat(caso.espera.estado).join(' o ')];
      if (caso.espera.codigo) { partes.push('código ' + caso.espera.codigo); }
      if (caso.espera.validacion) { partes.push('error en «' + caso.espera.validacion + '»'); }
      cuadro.appendChild(h('div', { class: 'espera', texto: partes.join(' · ') }));
    }
    if (caso.campos && caso.campos.length) {
      var grilla = h('div', { class: 'campos' });
      caso.campos.forEach(function (c) {
        var inicial = typeof c.valor === 'function' ? c.valor(Estado) : c.valor;
        var el;
        if (c.tipo === 'select') {
          el = h('select'); (c.opciones || []).forEach(function (o) { el.appendChild(h('option', { value: o, texto: o })); });
          if (inicial !== undefined) { el.value = inicial; }
        } else if (c.tipo === 'bool') {
          el = h('select', null, h('option', { value: 'true', texto: 'sí' }), h('option', { value: 'false', texto: 'no' }));
          el.value = String(inicial === undefined ? true : inicial);
        } else if (c.tipo === 'json') {
          el = h('textarea'); el.value = typeof inicial === 'string' ? inicial : jsonBonito(inicial === undefined ? {} : inicial);
        } else {
          el = h('input', { type: c.tipo === 'password' ? 'password' : (c.tipo === 'numero' ? 'number' : 'text'), step: c.tipo === 'numero' ? 'any' : false });
          el.value = inicial === undefined || inicial === null ? '' : inicial;
        }
        elementos[c.k] = el;
        el.addEventListener('input', function () { el.dataset.tocado = '1'; });
        el.addEventListener('change', function () { el.dataset.tocado = '1'; });
        grilla.appendChild(h('div', { class: 'campo' }, h('label', { texto: c.etiqueta || c.k }), el, c.ayuda ? h('div', { class: 'ayuda', texto: c.ayuda }) : null));
      });
      cuadro.appendChild(grilla);
    }
    var aviso = h('div', { class: 'falta-aviso' });
    cuadro.appendChild(aviso);
    cuadro.appendChild(zona);

    function faltantes() {
      return (caso.necesita || endpoint.necesita || []).filter(function (k) { return !Estado.obtener(k); });
    }
    function refrescarAviso() {
      var f = faltantes();
      aviso.textContent = f.length ? 'Falta en el estado: ' + f.join(', ') + '. Usa la preparación de arriba o corre antes el endpoint que lo genera.' : '';
    }
    refrescarAviso();
    cuadro.addEventListener('focusin', refrescarAviso);

    function ejecutar() {
      // Los valores por defecto que son funciones (al azar o leídos del estado) se recalculan en cada ejecución si no los tocaste.
      (caso.campos || []).forEach(function (c) {
        var el = elementos[c.k];
        if (typeof c.valor === 'function' && el && !el.dataset.tocado && c.tipo !== 'select' && c.tipo !== 'bool' && c.tipo !== 'json') { var nv = c.valor(Estado); el.value = nv === undefined || nv === null ? '' : nv; }
      });
      var v = leerCampos(caso, elementos);
      var ctx = { e: Estado, v: v, log: function () {}, azar: azar, uuid: uuid, fecha: fechaMas, hora: fechaHora, pedir: pedir, exigir: exigir, png: PNG_1X1 };
      botonEjecutar.disabled = true; botonEjecutar.textContent = 'Ejecutando…';
      var p;
      var pre = caso.antes ? Promise.resolve().then(function () { return caso.antes(ctx); }) : Promise.resolve();
      return pre.then(function () {
        try { p = caso.peticion(ctx); } catch (err) {
          throw { armado: true, mensaje: 'No se pudo armar la petición: ' + err.message };
        }
        return pedir(p);
      }).then(function (resp) {
        var ver = evaluar(caso.espera, resp);
        var seguir = Promise.resolve();
        if (ver.ok && caso.despues) {
          seguir = Promise.resolve().then(function () { return caso.despues(resp, ctx); })
            .catch(function (e) { ver = { ok: false, razones: ['El paso posterior falló: ' + e.message] }; });
        }
        return seguir.then(function () {
          dibujarResultado(zona, caso, resp, ver);
          botonEjecutar.disabled = false; botonEjecutar.textContent = 'Ejecutar de nuevo';
          cuadro.dataset.veredicto = ver.ok ? 'paso' : 'fallo';
          refrescarAviso();
          return ver;
        });
      }, function (err) {
        botonEjecutar.disabled = false; botonEjecutar.textContent = 'Ejecutar';
        var texto = err && err.armado ? err.mensaje : String(err);
        dibujarResultado(zona, caso, { falloRed: true, error: texto, peticion: { metodo: endpoint.metodo, url: endpoint.ruta, cabeceras: {} } }, { ok: false, razones: [texto] });
        cuadro.dataset.veredicto = 'fallo';
        return { ok: false };
      });
    }
    botonEjecutar.addEventListener('click', ejecutar);
    var registro = { ejecutar: ejecutar, faltantes: faltantes, titulo: caso.titulo, endpoint: endpoint, cuadro: cuadro, manual: !!caso.manual };
    casosTodos.push(registro);
    return { nodo: cuadro, registro: registro };
  }

  function dibujarEndpoint(ep) {
    var tarjeta = h('section', { class: 'panel endpoint', id: ep.id });
    var botonTodos = h('button', { class: 'chico endpoint-acciones', texto: 'Ejecutar todos los casos' });
    var cab = h('header', null,
      h('div', { class: 'linea' }, h('span', { class: 'metodo ' + ep.metodo, texto: ep.metodo }), h('span', { class: 'ruta', texto: ep.ruta }), botonTodos),
      h('h3', { texto: ep.titulo }),
      ep.sirve ? h('p', { class: 'sirve', texto: ep.sirve }) : null);
    var etiquetas = h('div', { class: 'etiquetas' });
    etiquetas.appendChild(h('span', { class: 'etiqueta', texto: ep.acceso || 'público' }));
    if (ep.permiso) { etiquetas.appendChild(h('span', { class: 'etiqueta', texto: 'permiso: ' + ep.permiso })); }
    if (ep.cabeceras) { etiquetas.appendChild(h('span', { class: 'etiqueta', texto: 'cabeceras: ' + ep.cabeceras })); }
    cab.appendChild(etiquetas);
    if (ep.notas && ep.notas.length) {
      var ul = h('ul'); ep.notas.forEach(function (n) { ul.appendChild(h('li', { html: formato(n) })); });
      cab.appendChild(h('div', { class: 'notas' }, h('strong', { texto: 'Importante' }), ul));
    }
    tarjeta.appendChild(cab);
    var mios = [];
    (ep.casos || []).forEach(function (c) { var d = dibujarCaso(ep, c); mios.push(d.registro); tarjeta.appendChild(d.nodo); });
    botonTodos.addEventListener('click', function () {
      botonTodos.disabled = true;
      secuencia(mios).then(function () { botonTodos.disabled = false; });
    });
    return tarjeta;
  }

  function secuencia(lista) {
    var cuenta = { paso: 0, fallo: 0, omitido: 0 };
    return lista.reduce(function (prev, c) {
      return prev.then(function () {
        if (c.manual || c.faltantes().length) { cuenta.omitido++; c.cuadro.dataset.veredicto = 'omitido'; return null; }
        return c.ejecutar().then(function (v) { if (v.ok) { cuenta.paso++; } else { cuenta.fallo++; } });
      });
    }, Promise.resolve()).then(function () { actualizarContadores(); return cuenta; });
  }

  function recetasUI(recetas) {
    var caja = h('div', { class: 'panel recetas' },
      h('h2', { texto: 'Preparación rápida' }),
      h('p', { texto: 'Cada botón hace llamadas reales a la API para dejar listo lo que los casos necesitan (y lo guarda en el estado).' }));
    var lista = h('div', { class: 'receta-lista' }), registro = h('div', { class: 'receta-registro' });
    recetas.forEach(function (r) {
      var b = h('button', { texto: r.titulo, title: r.descripcion || '' });
      b.addEventListener('click', function () {
        registro.style.display = 'block'; registro.textContent = '▶ ' + r.titulo + '…\n'; b.disabled = true;
        var log = function (t) { registro.textContent += '  · ' + t + '\n'; };
        Promise.resolve().then(function () { return r.ejecutar({ e: Estado, log: log, pedir: pedir, exigir: exigir, azar: azar, uuid: uuid, fecha: fechaMas, png: PNG_1X1 }); })
          .then(function () { registro.textContent += '✔ Listo\n'; })
          .catch(function (err) { registro.textContent += '✘ ' + err.message + '\n'; })
          .then(function () { b.disabled = false; pintarEstado(); });
      });
      lista.appendChild(b);
    });
    caja.appendChild(lista); caja.appendChild(registro);
    return caja;
  }

  function pagina(cfg) {
    document.title = cfg.titulo + ' · Laboratorio';
    var raiz = document.getElementById('app');
    barraEstado = h('div', { class: 'estado-barra' });
    var cuentas = h('div', { class: 'contadores' });
    var botonModulo = h('button', { class: 'primario', texto: 'Ejecutar todo el módulo' });
    actualizarContadores = function () {
      var p = 0, f = 0, o = 0;
      casosTodos.forEach(function (c) { var v = c.cuadro.dataset.veredicto; if (v === 'paso') { p++; } else if (v === 'fallo') { f++; } else if (v === 'omitido') { o++; } });
      cuentas.innerHTML = '';
      cuentas.appendChild(h('span', { texto: '✔ ' + p })); cuentas.appendChild(h('span', { texto: '✘ ' + f }));
      cuentas.appendChild(h('span', { texto: '⏭ ' + o + ' sin ejecutar' })); cuentas.appendChild(h('span', { class: 'espera', texto: casosTodos.length + ' casos' }));
    };
    var contenido = h('main', { class: 'contenido' },
      h('div', { class: 'cabecera' }, h('h1', { texto: cfg.titulo }), h('p', { texto: cfg.descripcion || '' })),
      barraEstado);
    if (cfg.avisos) { cfg.avisos.forEach(function (a) { contenido.appendChild(h('div', { class: 'notas', html: formato(a) })); contenido.appendChild(h('div', { style: 'height:12px' })); }); }
    if (cfg.recetas && cfg.recetas.length) { contenido.appendChild(recetasUI(cfg.recetas)); }
    if (cfg.endpoints && cfg.endpoints.length) { contenido.appendChild(h('div', { class: 'resumen-modulo' }, botonModulo, cuentas)); }
    if (cfg.antes) { cfg.antes(contenido); }
    (cfg.endpoints || []).forEach(function (ep) { contenido.appendChild(dibujarEndpoint(ep)); });
    raiz.appendChild(h('div', { class: 'disposicion' }, lateral(cfg.archivo), contenido));
    botonModulo.addEventListener('click', function () {
      botonModulo.disabled = true; botonModulo.textContent = 'Ejecutando…';
      secuencia(casosTodos).then(function () { botonModulo.disabled = false; botonModulo.textContent = 'Ejecutar todo el módulo'; });
    });
    actualizarContadores();
    pintarEstado();
    comprobarConexion();
    if (location.hash) { var objetivo = document.getElementById(location.hash.slice(1)); if (objetivo) { objetivo.scrollIntoView(); } }
  }

  window.Lab = {
    pagina: pagina, estado: Estado, pedir: pedir, exigir: exigir, azar: azar, uuid: uuid, fecha: fechaMas, hora: fechaHora,
    recetas: Recetas, png: PNG_1X1, comprobarConexion: comprobarConexion, modulos: MODULOS, h: h, escapar: escapar, formato: formato,
    inicioDeEstado: inicioDeEstado, pintarEstado: pintarEstado, copiar: copiar
  };
})();
