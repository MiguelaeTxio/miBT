# ANEXO H01 — Visor de diagnóstico y primeras notificaciones

**Estado:** EN PROGRESO

## Objetivo

Tener en el móvil una app que (a) lista los dispositivos Bluetooth
conectados con una notificación por dispositivo, (b) muestra la
batería cuando Android la expone, y (c) registra en un log todo lo que
cada dispositivo anuncia, para escribir con datos reales el módulo por
fabricante (H02).

## COMPLETADAS EN S1

- Esqueleto del proyecto, `DOCS/` y workflow de compilación.
- Servicio en primer plano con una notificación por dispositivo.
- Log de diagnóstico con botón de compartir.

## DATOS REALES RECOGIDOS (S1)

Dispositivo "TWS" (41:42:DE:1A:5B:D6, auriculares AliExpress),
Redmi Note 10 (M2101K6G), Android 13:

- Anuncia el perfil HFP con extensión Apple: `+XAPL`
  (`000D-0001-0101`, características `2` = informa de batería) y
  `+IPHONEACCEV` con args `[1,1,9]` = clave 1 (batería), valor 9 =
  **100 %**. Llega también por el broadcast oculto
  `BATTERY_LEVEL_CHANGED` (nivel=100) y por `getBatteryLevel()`
  oculto (100).
- Solo un porcentaje único. **No** hay izquierdo/derecho/estuche ni
  estado de carga por HFP en este dispositivo.
- La sonda GATT no dejó ninguna línea en el log (sin servicios ni
  errores): pendiente de revisar.
- Pendiente de registrar: Xiaomi Buds 6 y el resto de dispositivos.

## DATOS REALES RECOGIDOS (S2)

Log del 2026-10-10, Redmi Note 9 Pro, Android 12 (API 31).

**Redmi Buds 6 Lite** (78:99:87:B7:13:57; dirección LE aparte
F8:99:87:B7:13:57). Son los "Xiaomi Buds 6": el modelo real es Redmi
Buds 6 Lite.

- Igual que el TWS: `+XAPL` (`0000-0000-0100`, características `11`),
  `+IPHONEACCEV [1,1,9]`, broadcast oculto y `getBatteryLevel()`
  oculto: un único 100 %.
- **Además** envía comandos propietarios `+XIAOMI` por HFP con
  `companyid.911` (0x038F, Xiaomi), que la v1 ya captura:
  - `+XIAOMI [1,1,78,228,227,222,2]` (20:37:58) y
    `[1,1,78,228,227,228,1]` (20:38:57).
  - Tramas hex `FF…FF`: `FF01020101150004017BFF250B200501011C000001000205020C00FF`
    (repetida igual en ambas conexiones) y `FF010201010400020A03FF` /
    `FF010201010400020A06FF`.
- **Hipótesis S2 (sin verificar, no se usa en la notificación):** en
  la primera forma, los valores 228/227/222 = 0xE4/0xE3/0xDE serían
  bit 7 = cargando y 7 bits bajos = nivel → 100/99/94 % y luego
  100/99/100 %, cargando, coherente con auriculares dentro del
  estuche. Orden izquierdo/derecho/estuche, el 78 y el último campo:
  desconocidos.
- Antes de conectar bien hubo tres intentos 20:29–20:33 con
  ACL conectado/desconectado en segundos (emparejamiento rápido de
  Google fallido, "No se ha podido conectar"): ajeno a miBT.

**Prueba de auriculares fuera/dentro del estuche (S2, 21:08–21:11):**

- Durante el uso **no** vuelve a llegar la forma `[1,1,78,…]`: solo
  aparece al conectar. El porcentaje Apple bajó a 90 % con un auricular
  fuera y volvió a 100 %.
- Las tramas `FF010201010400020A{XX}FF` cambian con cada movimiento:
  `03` ambos en el estuche (conexión), `06` derecho fuera/izquierdo
  dentro, `0C` momento con ambos fuera, `09` izquierdo fuera/derecho
  dentro.
- **Hipótesis S2 (coherente con los 4 estados, sin verificar):** byte
  final = bits de posición: bit 0 derecho en estuche, bit 1 izquierdo
  en estuche, bit 2 derecho fuera, bit 3 izquierdo fuera.

**Reconexión con izquierdo puesto y derecho en estuche cerrado
(S2, 21:29):**

- `+XIAOMI [1,1,174,93,255,255,1]`. No llegó ninguna trama `0A{XX}`;
  la trama larga cambió un byte: `…7BFF25…` (ambos en estuche) →
  `…7BFF05…`.
- **Lectura S2 de la forma `[1,1,X,A,B,C,D]`:** `X` no es batería
  (78 = 0x4E, 174 = 0xAE: estado/flags desconocidos); `A`, `B`, `C` =
  bit 7 cargando + nivel en los 7 bits bajos, 255 = sin dato; `D`
  desconocido (2, 1, 1).
  - Ambos en estuche: A=100 % cargando, B=99 % cargando, C=94→100 %
    cargando.
  - Izquierdo fuera, derecho en estuche cerrado: A=93 % sin cargar,
    B=sin dato, C=sin dato.
- Conclusión provisional: `A` = auricular izquierdo (o "el que está
  fuera"), `B` = derecho, `C` = estuche. Con el estuche cerrado el
  derecho y el estuche no informan. Falta la prueba simétrica
  (derecho fuera, izquierdo en estuche) para descartar que `A` sea
  "el auricular activo" en vez de "el izquierdo".

**Otros dispositivos AliExpress:** "TWS" 41:42:AD:C8:FB:B3,
"TWS" 41:42:94:E4:E6:31, "Bluetooth music" 41:42:A0:A1:FF:7B y
"Bluetooth music" 11:21:AA:03:30:E9: solo eventos de conexión; ningún
evento de fabricante ni nivel de batería. Para ellos "sin datos" es
lo correcto.

**Sonda GATT:** tampoco dejó ninguna línea. Causa leída en el código:
`connectGatt(..., TRANSPORT_LE)` se lanza contra la dirección clásica
(`tipo=1`, BR/EDR), que no tiene LE; nunca llega `onConnectionStateChange`
y el temporizador hace `close()` sin registrar nada.

## HOJA DE RUTA PARA LA SIGUIENTE SESIÓN

1. Confirmar que el workflow de la última ejecución terminó en verde
   y que el APK está subido.
2. Miguel Ángel instala el APK, conecta cada dispositivo, pulsa
   *Compartir log* y envía el log al chat.
3. Analizar el log por dispositivo: qué porcentaje llega, por qué vía
   (HFP, GATT, oculta) y qué eventos de fabricante aparecen.
4. Decidir con datos si el H01 se cierra y se abre el H02.
