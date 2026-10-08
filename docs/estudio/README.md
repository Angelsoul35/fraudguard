# Guía de estudio — FraudGuard

Una guía en español para entender el proyecto a fondo y poder defenderlo en una
entrevista. El código, los comentarios y la documentación técnica están en inglés
(como en un entorno profesional real); esta guía explica el *porqué* de cada
decisión.

## Orden sugerido

1. [01 — Arquitectura orientada a eventos y el bus](01-arquitectura-orientada-a-eventos.md)
   Por qué separar la ingesta del scoring con un `EventBus`, y por qué detrás de
   una interfaz.
2. [02 — Hilos virtuales, orden y consistencia](02-hilos-virtuales-y-consistencia.md)
   Por qué hilos virtuales de Java 21 y cómo mantenemos la consistencia por
   tarjeta.
3. [03 — El motor de reglas explicable](03-motor-de-reglas-explicable.md)
   Por qué reglas deterministas con una razón legible, y cómo se combinan en un
   score.
4. [04 — Estado de velocidad en memoria](04-estado-de-velocidad-en-memoria.md)
   Cómo funciona la ventana deslizante por tarjeta y cuáles son sus límites.
5. [05 — Preguntas de entrevista](05-preguntas-de-entrevista.md)
   Preguntas típicas y cómo responderlas con este proyecto.

## La idea en una frase

Un microservicio de **detección de fraude en tiempo real y orientado a eventos**:
una transacción se ingiere por HTTP, se publica en un **bus de eventos** en
memoria (hilos virtuales), y un suscriptor la puntúa con un **motor de reglas
explicable** sobre el **estado de velocidad** por tarjeta, devolviendo un score
(0–100), una decisión (`APPROVE`/`REVIEW`/`DECLINE`) y **las reglas que se
activaron con su razón**. Todo corre en un solo proceso y los tests pasan sin
broker ni base de datos, pero las costuras (`EventBus`, `VelocityStore`) están
pensadas para crecer a Kafka y a un feature store sin reescribir la lógica.
