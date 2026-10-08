# 02 — Hilos virtuales, orden y consistencia

## Por qué hilos virtuales de Java 21

La entrega del bus es **una tarea por suscriptor y por evento**, y el scoring es
un paso corto (un par de lecturas en memoria y aritmética). Bajo carga puede
haber muchas tareas a la vez. Un *pool* de hilos clásico obliga a elegir entre uno
pequeño (que serializa las ráfagas) y uno grande (que malgasta memoria en hilos de
plataforma casi inactivos), más una cola acotada que puede desbordarse.

Los hilos virtuales hacen que "un hilo por tarea" sea el modelo correcto más
simple: no hay tamaño de *pool* que ajustar, no hay cola que desbordar, y una
tarea bloqueada cuesta unos cientos de bytes en vez de un hilo de plataforma.

- El bus usa un ejecutor de hilos virtuales por tarea
  (`Executors.newThreadPerTaskExecutor(Thread.ofVirtual()...)`, en
  `config/VirtualThreadConfig`).
- La capa web corre sobre hilos virtuales con
  `spring.threads.virtual.enabled=true`.

Un suscriptor lento aparca solo **su** hilo virtual; no agota un *pool*
compartido ni retrasa al publicador.

## El matiz importante: orden y consistencia

Los hilos virtuales hacen **barato** ejecutar muchas tareas, pero no hacen seguro
compartir estado mutable. La velocidad es **sensible al orden**: una regla debe
ver el estado que **precedió** al evento que puntúa.

La pieza que hace correcto el scoring concurrente no son los hilos virtuales, sino
que `VelocityStore.observe` es **atómico por tarjeta**: calcula el *snapshot*
previo y registra la transacción en un solo paso, bajo un `ReentrantLock` por
tarjeta. Así:

- dos eventos concurrentes de **la misma** tarjeta no entrelazan su lectura y
  escritura;
- tarjetas **distintas** se puntúan en paralelo (candados separados, un
  `ConcurrentHashMap` de estados).

El candado da **atomicidad**, no **orden de llegada**: el bus en memoria no
garantiza el orden entre eventos. Por eso el demo y los tests **esperan** cada
assessment (con Awaitility) antes de enviar el siguiente, y por eso en producción
el orden por tarjeta se consigue particionando por `cardId` en Kafka, no
dependiendo del ritmo del cliente.
