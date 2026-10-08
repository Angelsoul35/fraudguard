# 03 — El motor de reglas explicable

## Por qué reglas primero, y explicables

Un emisor de tarjetas tiene que poder justificar por qué se rechazó o retuvo un
cargo — ante un regulador, ante un analista y ante el titular. "Lo dijo el modelo"
no vale. Además hay que decidir bien desde el primer día, antes de tener datos
etiquetados, y permitir que un operador ajuste el comportamiento.

Por eso el núcleo es un **motor de reglas deterministas**:

- Una `Rule` es una función pura de un `RuleContext` (la transacción + el
  *snapshot* de velocidad previo). Devuelve un `RuleOutcome` que **no se activa**
  (contribución 0) o **se activa** con una contribución positiva **y una razón
  legible construida con la evidencia real** (el ratio, la distancia, el conteo).
- Cada regla es una clase en `rules/`, independiente y testeada en aislamiento.

## Las cinco reglas

| Regla (`id`) | Qué detecta | Peso |
| --- | --- | ---: |
| `VELOCITY_AMOUNT` | Un cargo muy por encima de la media reciente de la tarjeta (≥ 5× la media de la ventana, con ≥ 3 previos). | 30 |
| `VELOCITY_FREQUENCY` | Demasiados cargos en poco tiempo (≥ 5 previos en 10 min): prueba automatizada de tarjeta. | 25 |
| `NEW_COUNTRY` | Un país nuevo para la tarjeta; peso mayor si el país es de alto riesgo (se activa incluso sin historial). | 20 / 30 |
| `IMPOSSIBLE_TRAVEL` | Dos países distantes demasiado juntos en el tiempo (velocidad implícita > 900 km/h). | 40 |
| `ROUND_AMOUNT_HIGH_VALUE` | Importe redondo y alto (múltiplo exacto de 100,00 y ≥ 1000,00): cifra típica de un *cash-out* manual. | 15 |

## Cómo se combina en un score

`RiskScorer` **suma** las contribuciones de las reglas activadas, acota a
`[0, 100]` y deriva la decisión por umbrales:

```
score >= 70  -> DECLINE
score >= 40  -> REVIEW
resto        -> APPROVE
```

La suma es deliberada: el assessment puede decir **exactamente** qué reglas
produjeron el número y cuánto aportó cada una. Todos los pesos y umbrales son
configuración (`fraudguard.*`), así que las reglas son **diales de operador**, no
cambios de código.

## El matiz: correlación

Sumar pesos puede contar dos veces señales correlacionadas (una ráfaga de fraude a
menudo dispara frecuencia **y** viaje imposible **y** país nuevo a la vez). Lo
aceptamos a propósito —empuja con decisión los casos claramente malos por encima
de `DECLINE`— y lo acotamos con el *clamp* a 100. Un modelo aprendido encajaría
como **una contribución más al mismo score**, con sus *features* registradas para
la misma auditoría, nunca como un veredicto opaco que anule la base explicable.
