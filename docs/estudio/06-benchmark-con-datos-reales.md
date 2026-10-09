# 06 — Benchmark con datos reales y métricas de fraude

Esta guía explica cómo FraudGuard se evalúa sobre un conjunto de datos **real y
etiquetado** de transacciones de tarjeta, qué métricas se usan (con sus
**fórmulas**), y cómo se concluye que el modelo **generaliza** y no está ni
sobreajustado (*overfitting*) ni infraajustado (*underfitting*). Todos los
números que aparecen son los que imprime el benchmark
(`RealDataBenchmarkTest`).

El código vive en `src/main/java/com/angelo/fraudguard/benchmark/`:
`Metrics`, `FraudDatasetLoader`, `ScoringHarness`, `DatasetSplit`,
`BenchmarkEvaluator`, `FitAssessment`, `RealDataBenchmark`.

## 1. La tarea

Dada una transacción de tarjeta, el *scorer* de FraudGuard produce un número de
riesgo de 0 a 100. Lo tratamos como un **clasificador binario con umbral**: a
mayor score, mayor sospecha de fraude. La etiqueta real (`is_fraud`) solo se usa
para **medir**, nunca para puntuar.

Lo importante: reutilizamos el *scorer* de producción **tal cual**
(`ScoringHarness` está fijado a `ScoringService` por
`ScoringHarnessEquivalenceTest`). No hay un modelo paralelo "de juguete" hecho
para la demo: es el mismo motor de reglas + ventana de velocidad + `RiskScorer`.

## 2. Los datos y la etiqueta

- **Dataset:** *Credit Card Transactions Fraud Detection* (generador Sparkov),
  espejo de Hugging Face `pointe77/credit-card-transaction` (Apache-2.0).
- **Por qué encaja:** FraudGuard puntúa señales *por transacción* (velocidad por
  tarjeta, importe muy por encima de la media reciente de la tarjeta, ráfagas,
  ubicación). Este dataset trae justo esos campos: `cc_num` (tarjeta), `amt`
  (importe), `unix_time` (tiempo del evento), comercio y `state`/`lat`/`long`,
  con la etiqueta `is_fraud`. El conocido set ULB `creditcard` solo tiene
  componentes PCA anónimos (`V1..V28`) más `Amount`: sin tarjeta, tiempo ni
  lugar, un motor de velocidad por tarjeta no puede correr sobre él.
- **Instantánea:** 150.000 transacciones, **1.377 fraudes** → tasa base
  **0,918 %**. Fuertemente **desbalanceado**, como el fraude real.
- **Clave del desbalance:** el importe de los fraudes es mucho mayor que el de las
  transacciones legítimas (media ≈ 511 \$ frente a ≈ 68 \$). Esa es exactamente la
  señal que captura la regla de **velocidad de importe**.

## 3. Las métricas, con fórmulas

Sea, fijado un umbral de score:

- **VP** (verdaderos positivos): fraudes marcados como fraude.
- **FP** (falsos positivos): legítimas marcadas como fraude.
- **FN** (falsos negativos): fraudes no marcados.

### Precisión y recall

```
Precisión = VP / (VP + FP)        "de lo que marco, cuánto es fraude"
Recall    = VP / (VP + FN)        "del fraude total, cuánto atrapo"
```

### Tasa base (la línea de referencia)

```
tasa_base = positivos / total = 1377 / 150000 = 0,00918
```

Un clasificador trivial que marca todo como "no fraude" acierta el 99,08 %: por
eso la *accuracy* es inútil aquí. Las métricas correctas son de **ranking**.

### ROC-AUC (área bajo la curva ROC)

Mide la capacidad de **ordenar**: la probabilidad de que un fraude al azar reciba
mayor score que una legítima al azar (empates cuentan 0,5). Se calcula con el
estadístico de Mann-Whitney usando rangos medios:

```
ROC-AUC = ( Σ rango(positivos) − n₊·(n₊+1)/2 ) / ( n₊ · n₋ )
```

donde `n₊`, `n₋` son el número de positivos y negativos. Separación perfecta → 1,0;
orden invertido → 0,0; sin información → 0,5. **Un clasificador aleatorio da 0,5.**

### PR-AUC / *Average Precision* (la métrica titular)

Área bajo la curva precisión-recall, calculada como suma por umbrales
decrecientes (convención de scikit-learn):

```
AP = Σ_n ( R_n − R_{n−1} ) · P_n
```

donde `P_n`, `R_n` son precisión y recall en cada umbral (los scores iguales se
agrupan en un mismo umbral). **Por qué importa bajo desbalance:** la ROC-AUC usa
`n₋` en el denominador de la tasa de falsos positivos; con 99 % de negativos,
miles de FP apenas mueven la ROC, que luce optimista. La PR-AUC solo mira VP, FP y
FN, así que **penaliza los falsos positivos** que de verdad saturan una cola de
revisión. Para un clasificador sin información, `AP → tasa_base`.

### Recall a precisión fija

El recall máximo alcanzable en cualquier umbral cuya precisión sea ≥ un piso
(aquí 0,10): "si la cola de revisión debe operar a ≥ 10 % de precisión, ¿cuánto
fraude seguimos atrapando?".

## 4. Mini-ejemplo numérico (con los números reales del *held-out*)

En el conjunto *held-out* (38.586 transacciones, **349 fraudes**, tasa base
0,00904) el *scorer* solo produjo cuatro valores de score. Agrupados de mayor a
menor, con cuántos fraudes y legítimas caen en cada valor:

| score | fraudes | legítimas | VP acum. | FP acum. | Precisión acum. | Recall acum. |
| ----: | ------: | --------: | -------: | -------: | --------------: | -----------: |
| 55    | 0       | 7         | 0        | 7        | 0,000           | 0,000        |
| 30    | 166     | 1.335     | 166      | 1.342    | 166/1508 = 0,110| 166/349 = 0,476 |
| 25    | 0       | 78        | 166      | 1.420    | 0,105           | 0,476        |
| 0     | 183     | 36.817    | 349      | 38.237   | 349/38586 = 0,0090 | 1,000     |

**Average Precision** (sumando `(R_n − R_{n−1}) · P_n`):

```
umbral 55: (0,000 − 0)     · 0,000 = 0
umbral 30: (0,476 − 0,000) · 0,110 = 0,05236
umbral 25: (0,476 − 0,476) · 0,105 = 0
umbral 0 : (1,000 − 0,476) · 0,0090 = 0,00474
AP ≈ 0,05236 + 0,00474 = 0,0571     ✓ (= PR-AUC held-out del informe)
```

Es decir, la PR-AUC real de **0,0571** sale exactamente de esta tabla. Es **6,3×**
la tasa base (0,0090): el ranking es muy superior al azar. Y el recall a precisión
≥ 0,10 es **0,476** (la fila de score 30): a ~11 % de precisión atrapamos el
47,6 % del fraude, un orden de magnitud por encima del 0,9 % del azar.

La regla de **velocidad de importe** (peso 30) es la que separa: el grueso del
fraude cae en el score 30. La regla de frecuencia (peso 25) solo aporta falsos
positivos aquí (fila 25: 0 fraudes), porque a cadencia mensual "muchas
transacciones" no distingue fraude; por eso su umbral se sube para que solo salte
en extremos.

## 5. Validación cruzada (*k-fold*) frente a *held-out*

Partimos **por tarjeta** (nunca por transacción: todo el historial de una tarjeta
va a un solo lado, para que la velocidad no se "filtre" entre particiones). Sobre
el *pool* de desarrollo hacemos validación cruzada de 5 pliegues; reservamos
tarjetas no vistas como *held-out*.

```
CV (5 pliegues, media ± desv.):  ROC-AUC = 0,7172 ± 0,0191   PR-AUC = 0,0564 ± 0,0039
HELD-OUT (tarjetas no vistas):   ROC-AUC = 0,7197            PR-AUC = 0,0571
```

La **desviación entre pliegues** mide la estabilidad; que el *held-out* caiga
dentro de ese ruido es la señal de que **generaliza**.

## 6. ¿Sobreajuste o infraajuste? El *gap* y cómo lo juzgamos

- **Infraajuste (*underfitting*):** el modelo no capta la señal ni en
  entrenamiento ni en test; las métricas rondan la línea base (ROC ≈ 0,5).
- **Sobreajuste (*overfitting*):** el modelo memoriza el desarrollo y **cae** en
  datos no vistos; el *gap* entre desarrollo y *held-out* se dispara.

```
gap ROC (desarrollo − held-out) = 0,7138 − 0,7197 = −0,0058
```

Un *gap* prácticamente nulo (y hasta negativo) indica que no hay memorización. De
hecho, el *scorer* de FraudGuard **no tiene parámetros ajustados a los datos**: es
un sistema experto determinista de pesos fijos, así que estructuralmente no puede
memorizar filas de entrenamiento. Por eso el criterio principal **no** es el
*gap* de entrenamiento sino el **acuerdo CV ↔ held-out**:

```
|CV_ROC − heldout_ROC| = |0,7172 − 0,7197| = 0,0025   (tolerancia ≈ 0,057)
```

0,0025 está muy dentro del ruido: CV y *held-out* coinciden.

## 7. El control negativo (etiquetas barajadas)

Para descartar fugas en la evaluación, repetimos la medición con las **etiquetas
barajadas** sobre los mismos scores del *held-out*. Si hubiera fuga, seguiría
saliendo señal; si todo está bien, debe **colapsar al azar**:

```
Control (barajado):  ROC-AUC = 0,5066 (→ 0,5)   PR-AUC = 0,0091 (→ tasa base 0,0090)
```

Colapsa: ROC a 0,5 y PR-AUC a la tasa base. La señal del modelo es **real**, no un
artefacto del montaje.

## 8. Derivación del veredicto

`FitAssessment.judge(...)` aplica reglas puras (por eso se puede testear con
números sintéticos):

1. **¿Supera la línea base en *held-out*?** `ROC ≥ 0,60` **y**
   `PR-AUC ≥ 2 × tasa_base`. Aquí 0,7197 ≥ 0,60 y 0,0571 ≥ 2·0,0090 = 0,0181. ✔
2. **¿Coinciden CV y *held-out*?** `|CV_ROC − heldout_ROC| ≤ máx(3·desv_CV, 0,05)`.
   Aquí 0,0025 ≤ 0,057. ✔ Si no coincidieran → `OVERFIT`.
3. Si (1) falla → `UNDERFIT`.

Resultado: **`WELL_FIT`**. Y el control barajado, pasado por la misma función, da
`UNDERFIT` (no supera el paso 1): la lógica **discrimina**, no etiqueta de sano un
modelo roto. Esto lo verifica
`RealDataBenchmarkTest.the_verdict_logic_does_not_call_a_shuffled_run_well_fit`.

## 9. Cómo ejecutarlo

```bash
mvn -q test -Dtest=RealDataBenchmarkTest -Dsurefire.useFile=false
```

## 10. Cómo extenderlo

El techo de precisión es el límite natural de unas pocas reglas binarias que
comparten **una sola** ventana de velocidad. Las vías concretas para subirlo:

- **Ventanas por regla:** la regla de importe quiere semanas (línea base estable);
  una regla de ráfaga quiere minutos. Hoy comparten `fraudguard.window`.
- **Pesos calibrados por el operador** a partir de estas métricas, en lugar de
  pesos fijos ilustrativos.
- **Un modelo aprendido** (p. ej. *gradient boosting*) que aporte al **mismo**
  score 0–100, con las reglas dando el suelo explicable y auditable.
