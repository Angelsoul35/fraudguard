# 04 — Estado de velocidad en memoria

## Qué guarda y para qué

Las reglas de velocidad necesitan el historial por tarjeta: cuántas transacciones
en los últimos N minutos, su importe medio y máximo, el país y la hora de la
transacción anterior, y el conjunto de países que la tarjeta ha usado alguna vez.
`InMemoryVelocityStore` lo mantiene en memoria detrás de la interfaz
`VelocityStore`.

## La operación atómica `observe`

Hay una sola operación:

```java
VelocitySnapshot observe(Transaction tx);
```

Por tarjeta guarda:

- un **deque** ordenado por tiempo, podado a la ventana configurada usando
  **tiempo de evento** (el `timestamp` de la transacción, no el reloj de pared);
- la **transacción anterior** (país y hora), que se conserva aunque salga de la
  ventana, para que viaje imposible siempre tenga referencia;
- el conjunto de **países vistos** de todo el histórico.

`observe` toma un `ReentrantLock` **por tarjeta** y, en un solo paso: calcula el
*snapshot* del estado **previo** a `tx` (conteo y medias de la ventana, país/hora
anterior, países vistos) y luego registra `tx`. Devuelve el *snapshot* previo.

## Por qué así

- **Consistencia**: la secuencia leer→evaluar→escribir es atómica por tarjeta, así
  que dos eventos de la misma tarjeta no se entrelazan; tarjetas distintas van en
  paralelo.
- **Tiempo de evento**: una ráfaga reproducida o algo desordenada se juzga por los
  *timestamps* propios de las transacciones.

## Los límites (honestos)

- **Local al proceso**: el estado no se comparte entre instancias ni sobrevive a
  un reinicio. Escalar en horizontal partiría el historial de una tarjeta entre
  nodos.
- **No acotado**: tarjetas y conjuntos de países se acumulan sin expiración; vale
  para un demo, no para la memoria de producción.
- **El orden de llegada no lo garantiza el bus en memoria**: el candado da
  atomicidad, no orden. El demo y los tests esperan cada assessment antes de
  enviar el siguiente.

En producción esto se sustituye por un **feature store** compartido y acotado
(p. ej. Redis con TTL, o un agregador de *streaming*), y el orden por tarjeta se
obtiene particionando por `cardId` en Kafka, no con el ritmo del cliente.
