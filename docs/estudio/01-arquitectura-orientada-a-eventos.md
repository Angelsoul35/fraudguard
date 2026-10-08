# 01 — Arquitectura orientada a eventos y el bus

## El problema es de streaming

Detectar fraude es un problema de *streaming*: hay que decidir **ya**, en función
de lo que la tarjeta hizo hace segundos, y el cliente HTTP no debería esperar a
que ese trabajo termine. Si el controlador llamara directamente al scorer:

- la latencia de la respuesta HTTP incluiría la del scoring;
- las dos capas no podrían escalar por separado;
- cambiar a un broker más adelante obligaría a tocar el controlador y el scorer.

## La solución: publicar un evento

`POST /transactions` valida la petición, construye un `Transaction` del dominio
(importe en **céntimos enteros**, nunca `double`), publica un evento
`TransactionReceived` en el `EventBus` y devuelve `202 Accepted` de inmediato. El
scoring ocurre fuera del hilo de la petición, en el suscriptor `ScoringService`.

## Por qué una interfaz `EventBus`

El bus es una **costura**: una interfaz pequeña entre ingesta y scoring.

```java
<E extends DomainEvent> void subscribe(Class<E> type, Consumer<E> handler);
void publish(DomainEvent event);
```

El controlador solo depende de `publish`; el suscriptor solo de `subscribe`. La
implementación que se entrega, `InMemoryEventBus`, entrega cada evento a cada
suscriptor de forma asíncrona en un hilo virtual, y **aísla** a un suscriptor
lento o que falla (lo registra y sigue con los demás).

`KafkaEventBus` existe como **stub documentado** detrás de la misma interfaz (no
se registra como bean). Deja concreto el camino a producción: publicar registros
**con clave `cardId`** para que los eventos de una tarjeta caigan en la misma
partición y se consuman **en orden** (el orden por tarjeta que la velocidad
necesita), un *consumer group* por suscriptor confirmando offsets tras procesar
con éxito (entrega *at-least-once*, por lo que los handlers deben ser
idempotentes), y dejar a Kafka la durabilidad, el *replay* y el escalado
horizontal.

## Lo que ganamos y lo que cedemos

- **Ganamos**: desacoplar ingesta y scoring; tests y ejecución local sin broker;
  transporte intercambiable sin tocar la lógica de negocio.
- **Cedemos** (de forma explícita): el bus en memoria es de **un solo proceso**,
  **at-most-once** y pierde eventos si el proceso cae. Los tres se resuelven con
  la implementación de Kafka — que es un cambio de implementación y configuración,
  no una reescritura.
