# ANEXO H02 — Módulo Xiaomi: izquierdo/derecho/estuche/carga y posición

Abierto en S2 (2026-10-10) por PCH, al cerrar el H01 con datos reales.
Alcance concertado con Miguel Ángel en S2.

## Objetivo

Para los Redmi Buds 6 Lite (y cualquier dispositivo que envíe el mismo
protocolo `+XIAOMI`), la notificación del dispositivo muestra:

1. Batería del auricular izquierdo, del derecho y del estuche, con
   indicación de carga, tomada de la última lista `+XIAOMI` recibida,
   indicando la hora de esa lectura.
2. Dónde está cada auricular (puesto o en el estuche), en tiempo real.

Los dispositivos AliExpress se quedan con el porcentaje único o "sin
datos" (directriz §4.6: nada se inventa).

## Contexto técnico (verificado en S2 con logs reales)

Fuente: evento HFP `ACTION_VENDOR_SPECIFIC_HEADSET_EVENT`, categoría
`companyid.911` (Xiaomi, ya registrada en `COMPANY_IDS` de
`BtMonitorService.kt`), comando `+XIAOMI`. Los `args` llegan en
`EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS` como `Array<*>`.

**Forma de batería** — 7 elementos `[1,1,X,L,R,C,D]`, solo al conectar:

- `X`: estado/flags sin descifrar (vistos 78 y 174). Se ignora.
- `L` izquierdo, `R` derecho, `C` estuche: bit 7 (≥128) = cargando,
  7 bits bajos = porcentaje; 255 = sin dato.
- `D`: desconocido (vistos 1 y 2). Se ignora.
- Ejemplos reales: `[1,1,78,228,227,222,2]` ambos en estuche (100 %,
  99 %, 94 %, todos cargando); `[1,1,174,93,255,255,1]` izquierdo
  puesto al 93 %; `[1,1,174,255,100,255,1]` derecho puesto al 100 %.
- Con el estuche cerrado, el auricular de dentro y el estuche llegan
  como 255.

**Forma de posición** — un solo elemento, trama hex
`FF010201010400020A{XX}FF`, llega en cada cambio. Byte `{XX}`:

| Bit | Significado |
|---|---|
| 0 (0x01) | derecho en el estuche |
| 1 (0x02) | izquierdo en el estuche |
| 2 (0x04) | derecho fuera |
| 3 (0x08) | izquierdo fuera |

Valores reales: `03` ambos en estuche, `06` derecho fuera/izquierdo
dentro, `09` izquierdo fuera/derecho dentro, `0C` ambos fuera.

Otras tramas `FF…FF` (la larga `FF01020101150004017BFF{25|05}0B…`) no
están descifradas y no se usan.

Durante el uso solo se actualiza el porcentaje único
(`+IPHONEACCEV`); la batería por elemento no se refresca hasta la
siguiente conexión.

## COMPLETADAS EN S2

- Apertura del hito por PCH tras descifrar con cuatro logs reales el
  protocolo `+XIAOMI` de los Redmi Buds 6 Lite (detalle en
  `DOCS/ANNEX_H01.md`, "DATOS REALES RECOGIDOS (S2)").
- Alcance concertado con Miguel Ángel: batería por elemento con hora
  de lectura + posición de cada auricular en tiempo real; AliExpress
  sin cambios. Sin código escrito todavía.

## HOJA DE RUTA PARA LA SIGUIENTE SESIÓN

1. Crear `XiaomiProtocol.kt` (paquete `com.miguelaetxio.mibt`) con dos
   funciones puras: `parseBattery(args): XiaomiBattery?` para la forma
   de 7 elementos y `parsePosition(args): XiaomiPosition?` para la
   trama `FF010201010400020A{XX}FF`. Cualquier forma no reconocida
   devuelve `null`.
2. En `vendorReceiver` de `BtMonitorService.kt`, cuando
   `cmd == "+XIAOMI"`, guardar por dirección del dispositivo la última
   `XiaomiBattery` con su hora y la última `XiaomiPosition`, y llamar a
   `refresh()`. Al desconectarse el dispositivo, borrar la posición
   (la batería se conserva con su hora).
3. En la construcción de la notificación por dispositivo: si hay datos
   Xiaomi, sustituir la línea "Izquierdo / derecho / estuche / carga:
   sin datos" por izquierdo, derecho y estuche (porcentaje, icono de
   carga, "sin dato" para 255), la hora de la lectura y la posición de
   cada auricular. Sin datos Xiaomi, la notificación queda como hoy.
4. Mantener el log de diagnóstico tal cual (sigue siendo la vía para
   descifrar lo que falta).
5. Commit + push, confirmar workflow en verde y Release publicada.
6. Miguel Ángel prueba en el móvil: conectar con ambos en el estuche,
   ponerse uno, ponerse el otro, guardar uno, y comprobar que la
   notificación refleja cada cambio de posición y los porcentajes de
   la conexión.

## COMPLETADAS EN S3

- `XiaomiProtocol.kt` (commit `1dc8920`): `parseBattery` para la forma
  de 7 elementos y `parsePosition` para la trama `0A{XX}`; cualquier
  forma no reconocida devuelve `null`.
- `BtMonitorService.kt` (commit `f7e7bdc`): `vendorReceiver` guarda por
  dirección la última batería `+XIAOMI` con su hora y la última
  posición; la posición se borra al dejar de estar conectado el
  dispositivo. La notificación muestra izquierdo, derecho y estuche
  (porcentaje, ⚡ cargando, "sin dato" para 255), "Lectura de las
  HH:mm (al conectar)" y la posición de cada auricular. Sin datos
  Xiaomi queda como antes. Log de diagnóstico intacto.
- Corrección tras la primera prueba (commit `eecce93`): la cifra de
  `+IPHONEACCEV` va en tramos de 10 % (un 65 % real aparecía como
  70 %); ahora se marca como aproximada: "≈70% (HFP, en tramos de
  10 %)".
- Workflows en verde y Releases publicadas en `miBTReleases`.
- Prueba real de Miguel Ángel (Redmi Buds 6 Lite): tras reconectar,
  la notificación mostró izquierdo 63 % cargando, derecho 100 %
  cargando, estuche 100 %, lectura 08:33 y la posición correcta de
  cada auricular. La primera prueba salió "sin datos" porque el
  servicio se reinició con la actualización estando los auriculares
  ya conectados (la lista solo llega al conectar): origen del H03.
- Hito cerrado en S3 a petición de Miguel Ángel, abriendo el H03.
