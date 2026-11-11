/*
 * Cliente STOMP 1.2 minimalista sobre WebSocket nativo del navegador (sin dependencias externas).
 * Pensado para funcionar offline y en Railway con heartbeats periódicos.
 */
(function () {
  'use strict';

  function escaparValor(valor) {
    return String(valor)
      .replace(/\\/g, '\\\\')
      .replace(/\r/g, '\\r')
      .replace(/\n/g, '\\n')
      .replace(/:/g, '\\c');
  }

  function desescaparValor(valor) {
    return String(valor)
      .replace(/\\r/g, '\r')
      .replace(/\\n/g, '\n')
      .replace(/\\c/g, ':')
      .replace(/\\\\/g, '\\');
  }

  function armarTrama(comando, cabeceras, cuerpo) {
    var lineas = [comando];
    Object.keys(cabeceras || {}).forEach(function (k) {
      if (cabeceras[k] !== undefined && cabeceras[k] !== null && cabeceras[k] !== '') {
        lineas.push(k + ':' + escaparValor(cabeceras[k]));
      }
    });
    lineas.push('');
    lineas.push(cuerpo || '');
    return lineas.join('\n') + '\x00';
  }

  function parsearTrama(crudo) {
    var normalizado = crudo.replace(/\r\n/g, '\n');
    var sep = normalizado.indexOf('\n\n');
    if (sep < 0) {
      return null;
    }
    var bloqueCabeceras = normalizado.slice(0, sep).split('\n');
    var comando = bloqueCabeceras.shift().trim();
    var cabeceras = {};
    bloqueCabeceras.forEach(function (linea) {
      var pos = linea.indexOf(':');
      if (pos > 0) {
        var clave = linea.slice(0, pos);
        if (!(clave in cabeceras)) {
          cabeceras[clave] = desescaparValor(linea.slice(pos + 1));
        }
      }
    });
    var cuerpo = normalizado.slice(sep + 2);
    return { comando: comando, cabeceras: cabeceras, cuerpo: cuerpo };
  }

  function urlHost(wsUrl) {
    try {
      return new URL(wsUrl.replace(/^ws/i, 'http')).host;
    } catch (_) {
      return location.host;
    }
  }

  function crearCliente(opciones) {
    var cfg = opciones || {};
    var socket = null;
    var buffer = '';
    var conectado = false;
    var timerHeartbeat = null;
    var subContador = 0;
    var suscripciones = {};
    var huboError = false;

    function limpiarTimer() {
      if (timerHeartbeat) {
        clearInterval(timerHeartbeat);
        timerHeartbeat = null;
      }
    }

    function avisarEstado(estado, detalle) {
      if (cfg.onEstado) { cfg.onEstado(estado, detalle || ''); }
    }

    function iniciarHeartbeats(cabeceraHb, deseadoMs) {
      limpiarTimer();
      var partes = String(cabeceraHb || '0,0').split(',');
      var servidorEspera = parseInt(partes[1], 10) || 0;
      var intervalo = 0;
      if (deseadoMs > 0 && servidorEspera > 0) {
        intervalo = Math.max(deseadoMs, servidorEspera);
      } else if (deseadoMs > 0 && servidorEspera === 0) {
        intervalo = deseadoMs;
      }
      if (intervalo > 0) {
        timerHeartbeat = setInterval(function () {
          if (socket && socket.readyState === WebSocket.OPEN && conectado) {
            socket.send('\n');
            if (cfg.onHeartbeat) { cfg.onHeartbeat('enviado'); }
          }
        }, intervalo);
      }
    }

    function procesarDatos(texto) {
      buffer += texto;
      while (buffer.length > 0) {
        // Descartar pings vacíos (\n o \r\n) que llegan entre tramas
        if (buffer[0] === '\n' || buffer[0] === '\r') {
          while (buffer.length > 0 && (buffer[0] === '\n' || buffer[0] === '\r')) {
            buffer = buffer.slice(1);
          }
          if (cfg.onHeartbeat) { cfg.onHeartbeat('recibido'); }
          continue;
        }
        var fin = buffer.indexOf('\x00');
        if (fin < 0) {
          break;
        }
        var crudo = buffer.slice(0, fin);
        buffer = buffer.slice(fin + 1);
        var trama = parsearTrama(crudo);
        if (!trama || !trama.comando) {
          continue;
        }
        if (trama.comando === 'CONNECTED') {
          conectado = true;
          iniciarHeartbeats(trama.cabeceras['heart-beat'], cfg.heartbeatMs === undefined ? 10000 : cfg.heartbeatMs);
          avisarEstado('conectado', trama.cabeceras['user-name'] || 'anónimo');
          if (cfg.onConectado) { cfg.onConectado(trama); }
        } else if (trama.comando === 'MESSAGE') {
          var subId = trama.cabeceras.subscription;
          var json = null;
          try { json = trama.cuerpo ? JSON.parse(trama.cuerpo) : null; } catch (_) {}
          var msg = { cabeceras: trama.cabeceras, cuerpo: trama.cuerpo, json: json };
          if (subId && suscripciones[subId]) { suscripciones[subId](msg); }
          if (cfg.onMensaje) { cfg.onMensaje(msg); }
        } else if (trama.comando === 'ERROR') {
          huboError = true;
          avisarEstado('error', trama.cabeceras.message || trama.cuerpo || 'Error STOMP');
          if (cfg.onError) { cfg.onError(trama); }
        } else if (trama.comando === 'RECEIPT' && cfg.onRecibo) {
          cfg.onRecibo(trama.cabeceras['receipt-id']);
        }
      }
    }

    function conectar() {
      desconectar();
      avisarEstado('conectando', cfg.url);
      try {
        socket = new WebSocket(cfg.url);
      } catch (err) {
        avisarEstado('error', err.message);
        if (cfg.onError) { cfg.onError({ comando: 'ERROR', cabeceras: { message: err.message }, cuerpo: '' }); }
        return;
      }

      socket.onopen = function () {
        var hb = cfg.heartbeatMs === undefined ? 10000 : cfg.heartbeatMs;
        var headers = {
          'accept-version': '1.2,1.1',
          'host': urlHost(cfg.url),
          'heart-beat': hb + ',' + hb
        };
        Object.keys(cfg.headers || {}).forEach(function (k) {
          if (cfg.headers[k]) { headers[k] = cfg.headers[k]; }
        });
        socket.send(armarTrama('CONNECT', headers, ''));
      };

      socket.onmessage = function (ev) {
        procesarDatos(typeof ev.data === 'string' ? ev.data : '');
      };

      socket.onerror = function () {
        avisarEstado('error', 'Fallo de red o handshake WebSocket rechazado');
      };

      socket.onclose = function (ev) {
        var estuvoConectado = conectado;
        var errorPrevio = huboError;
        conectado = false;
        huboError = false;
        limpiarTimer();
        suscripciones = {};
        if (!errorPrevio) {
          if (!ev.wasClean && !estuvoConectado) {
            avisarEstado('error', 'Conexión rechazada (código ' + ev.code + ')');
          } else {
            avisarEstado('desconectado', ev.reason || ('código ' + ev.code));
          }
        }
        if (cfg.onCerrado) { cfg.onCerrado(ev); }
      };
    }

    function suscribir(destino, callback) {
      if (!socket || socket.readyState !== WebSocket.OPEN || !conectado) {
        throw new Error('Primero conecta el WebSocket.');
      }
      subContador += 1;
      var id = 'sub-' + subContador;
      suscripciones[id] = callback || function () {};
      socket.send(armarTrama('SUBSCRIBE', { id: id, destination: destino, ack: 'auto' }, ''));
      return {
        id: id,
        cancelar: function () {
          delete suscripciones[id];
          if (socket && socket.readyState === WebSocket.OPEN && conectado) {
            socket.send(armarTrama('UNSUBSCRIBE', { id: id }, ''));
          }
        }
      };
    }

    function enviar(destino, cuerpoObj) {
      if (!socket || socket.readyState !== WebSocket.OPEN || !conectado) {
        throw new Error('Primero conecta el WebSocket.');
      }
      var texto = typeof cuerpoObj === 'string' ? cuerpoObj : JSON.stringify(cuerpoObj);
      socket.send(armarTrama('SEND', { destination: destino, 'content-type': 'application/json' }, texto));
    }

    function desconectar() {
      limpiarTimer();
      var anterior = socket;
      socket = null;
      conectado = false;
      suscripciones = {};
      if (anterior) {
        try {
          if (anterior.readyState === WebSocket.OPEN) {
            anterior.send(armarTrama('DISCONNECT', {}, ''));
          }
          anterior.close(1000, 'Cierre del laboratorio');
        } catch (_) {}
      }
      avisarEstado('desconectado', '');
    }

    return {
      conectar: conectar,
      suscribir: suscribir,
      enviar: enviar,
      desconectar: desconectar,
      estaConectado: function () { return conectado; }
    };
  }

  window.LabStomp = { crearCliente: crearCliente };
})();
