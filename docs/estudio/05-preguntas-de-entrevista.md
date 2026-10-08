# 05 — Preguntas de entrevista

Preguntas típicas sobre este proyecto y cómo responderlas.

## "¿Por qué orientado a eventos y no una llamada directa?"

Porque el scoring es *streaming* y no debe acoplarse a la latencia HTTP.
Publicando un evento, la ingesta responde `202` al instante y el scoring escala,
falla y evoluciona por separado. La interfaz `EventBus` mantiene el transporte
intercambiable: en memoria hoy, Kafka (con clave `cardId`) en producción, sin
tocar el controlador ni el scorer.

## "¿Por qué hilos virtuales? ¿No bastaba un pool?"

La carga es muchas tareas cortas con alguna espera. Los hilos virtuales hacen
correcto y simple "un hilo por tarea": sin tamaño de pool ni cola que ajustar, y
una tarea bloqueada es casi gratis. Un suscriptor lento aparca solo su hilo. Pero
dejo claro que los hilos virtuales no hacen seguro el estado compartido: la
corrección viene del candado por tarjeta en el `VelocityStore`.

## "¿Cómo garantizas el orden y la consistencia?"

`VelocityStore.observe` es atómico por tarjeta (un `ReentrantLock` por `cardId`),
así que leer→evaluar→escribir no se entrelaza para una misma tarjeta, y tarjetas
distintas van en paralelo. El candado da atomicidad, no orden de llegada; por eso
el demo/tests esperan cada assessment antes del siguiente, y en producción el
orden por tarjeta lo da el particionado por `cardId` en Kafka.

## "¿Por qué reglas y no un modelo de ML?"

Por explicabilidad y porque hay que decidir bien sin datos etiquetados. Cada regla
da una contribución **y una razón** con la evidencia real; el score es su suma y
la decisión un umbral. Un modelo encaja **al lado** de las reglas, sumando al
mismo score con sus *features* auditadas, nunca como un veredicto opaco.

## "¿Cómo se prueba algo asíncrono de forma determinista?"

Con Awaitility: tras publicar/`POST`, se espera (`await().atMost(...).until(...)`)
a que el assessment exista, en vez de usar `sleep`. Así el test es determinista y
además comprueba que el bus **entrega** de verdad al suscriptor.

## "¿Qué simplificaste y qué pondrías en producción?"

Simplificado a propósito: bus en memoria (at-most-once, un proceso), estado de
velocidad en memoria (no acotado, no compartido), tabla de centroides por país y
lista de alto riesgo ilustrativas, pesos fijos. En producción: Kafka particionado
por `cardId`; un feature store compartido y acotado (Redis/streaming); geolocalización
real; y un modelo aprendido junto a las reglas, con las reglas como base
explicable y defendible ante el regulador.

## "¿Cómo añades una regla nueva?"

Creo una clase `@Component` que implemente `Rule`. El `RuleEngine` recibe la lista
completa de *beans* `Rule` por inyección, así que no hay registro que actualizar;
le añado su test en aislamiento y su peso/umbral en `application.yml`.

## "¿Por qué el importe en céntimos enteros (`long`)?"

El dinero nunca es `double`: evita errores de redondeo en coma flotante. Se guarda
en céntimos enteros y solo se formatea a unidades mayores para mostrarlo.
