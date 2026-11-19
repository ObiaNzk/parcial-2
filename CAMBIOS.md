# Cambios realizados

Cada pedido sigue el formato problema / riesgo / cambio / verificación. Las verificaciones manuales se hicieron en un emulador (Medium Phone), mirando la pantalla y el contador de peticiones de Mesa.
## 41 · Programa sin señal

### Problema 1: la falla ocultaba la lista

- **Problema:** `Catalogo` mostraba el aviso *en lugar de* la grilla.
- **Riesgo:** sin señal, el usuario perdía el programa que ya tenía guardado.
- **Cambio:** el aviso y el botón Reintentar se muestran arriba y la lista sigue visible debajo. El aviso distingue dos casos:
  - "Sin conexión: usando datos guardados", si ya hubo una descarga;
  - "No se pudo descargar el programa", si nunca la hubo.

  Se capturan `IOException` y `HttpException`; cualquier otro error se relanza.
- **Verificación:** con datos guardados, se apaga el servicio en Mesa y se toca Actualizar. Aparece el aviso y la lista, el detalle y Mi agenda siguen disponibles.

### Problema 2: no se distinguía "nunca descargué" de "descargué un programa vacío"

- **Problema:** la única pista era si la lista estaba vacía, y v4 es una edición válida sin charlas.
- **Riesgo:** al reabrir sin señal con v4 guardada, se ofrecía reintentar como si no hubiera datos.
- **Cambio:**
  - Nueva tabla `edicion` en Room (una fila), que se escribe en la misma transacción que el programa.
  - El catálogo muestra "Sin charlas programadas" cuando hay edición y la lista está vacía.
  - La base pasa a la versión 2 con `MIGRACION_1_2`: crea la tabla y, si el usuario ya tenía charlas, registra que hubo una edición. No se usa una migración destructiva, para no perder las favoritas de quienes ya tienen la app instalada.
- **Verificación:**
  1. Se instaló sobre una base v1 con datos y se abrió sin señal: "usando datos guardados" con la lista, así que la migración conservó los datos.
  2. Con v4 y señal: "Sin charlas programadas".
  3. Al reabrir sin señal: el aviso y "Sin charlas programadas".
  4. Con `pm clear` y sin señal: "No se pudo descargar el programa" y Reintentar.

### Problema 3: una edición vacía no borraba nada

- Se resolvió con el cambio del pedido 42: la transacción reemplaza el programa completo.

## 42 · Agenda personal

- **Problema:** `sincronizar()` guardaba con `REPLACE`. Eso tenía dos efectos:
  - pisaba la fila entera, con `favorita = false`, así que se perdía la marca en cada actualización;
  - nunca borraba nada, así que las charlas retiradas seguían en el catálogo y en Mi agenda.
- **Riesgo:**
  - las favoritas de los usuarios desaparecían al actualizar;
  - se mostraban charlas que ya no existían;
  - una marca hecha durante una descarga se perdía al llegar la respuesta.
- **Cambio:** `CarteleraDao.reemplazarPrograma`, una `@Transaction` que hace tres cosas:
  1. lee los IDs marcados;
  2. borra todas las charlas;
  3. inserta la instantánea nueva con `favorita = id in marcadas`.

  Como el ID es estable, esto cubre todos los casos:
  - una charla que cambia de título o sala conserva la marca;
  - una charla retirada desaparece junto con su marca;
  - una charla que vuelve, vuelve sin marcar.

  Las marcas se leen al momento de escribir, no al iniciar la petición, así que se respeta un cambio hecho durante la espera.
- **Verificación:** con una charla marcada, se pasó a v2 y se actualizó: la marca se mantuvo. En v3, la 11 desapareció del Programa y de Mi agenda. El test `markMadeDuringDownloadIsKept` pasa.

## 43 · Volver al punto de lectura

- **Problema:** los dos filtros se perdían, cada uno por un motivo distinto:
  - el tema estaba en `remember` dentro de `Catalogo`, y se perdía al abrir una ficha (el catálogo sale de la composición) y al rotar;
  - el día estaba en un `MutableStateFlow` del ViewModel, y se perdía si Android mataba el proceso.
- **Riesgo:** al volver de una ficha, el usuario perdía la búsqueda.
- **Cambio:** los dos filtros pasan al `SavedStateHandle` del ViewModel (`getStateFlow`, `elegirDia`, `elegirTema`). La sección y la ficha abierta ya usaban `rememberSaveable` y no se tocaron.
- **Verificación:**
  1. Día 2 y Datos, abrir «Cachés para viajeros», Volver: quedan los dos filtros y 2 resultados.
  2. Al rotar: igual.
  3. Ficha abierta, Inicio, `adb shell am kill ar.jornadas`, volver desde Recientes: reaparece la ficha, y al tocar Volver, los dos filtros.
  4. Tarea nueva: arranca sin filtros.

## 44 · Control de descargas

- **Problema:** había tres disparadores de descarga sin control:
  - `MainActivity.onStart`, que descargaba en cada rotación y cada vez que se volvía de Inicio;
  - `LaunchedEffect` en `Catalogo`, que descargaba al volver de una ficha o de otra pestaña;
  - `recargar()`, que lanzaba una petición nueva en cada toque.

  Además, `Servicios` (Room, Retrofit y Mesa) se creaba de nuevo en cada ViewModel.
- **Riesgo:**
  - peticiones de más;
  - descargas en paralelo que podían terminar en cualquier orden y pisar el resultado;
  - varias instancias de la base abiertas a la vez.
- **Cambio:**
  - `Servicios` pasa a ser una única instancia por proceso (`Servicios.de(context)`, con `applicationContext`). Guarda la marca `cargaInicialHecha`.
  - La carga automática se hace en el `init` del ViewModel, una vez por proceso.
  - `recargar()` ignora los toques si ya hay una descarga activa (`descarga?.isActive`).
  - Se eliminaron el `onStart` y el `LaunchedEffect`.
  - `MainActivity` aplica el extra `servicio_disponible` antes de crear el ViewModel.
- **Verificación:** contador de Mesa en cada paso.

  | Acción | Contador |
  |---|---|
  | Abrir la app | 1 |
  | Rotar | 1 |
  | Abrir una ficha y cambiar de pestaña | 1 |
  | Inicio y volver | 1 |
  | Demora larga, dos toques en Actualizar | 2 |
  | Elegir filtros durante la espera | Responden |
  | Al terminar, Actualizar otra vez | 3 |
  | Atrás y reabrir en el mismo proceso | 3 |

## Pruebas automatizadas

Hay algunos test que prueban del 41. 42. pero por tiempo no segui