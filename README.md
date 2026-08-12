# GMP Offline — App Android (Fase 4 + Fase 5)

Proyecto Android nuevo, desde cero, para `plan-gmp-offline-first.md`. Este README cubre lo entregado hasta Fase 5 (motor de sincronización). La Fase 4 (capa Room) sigue igual que antes; `OneShotSyncLoader` de esa fase fue reemplazado por `SyncEngine`.

## Novedades de la Fase 5 (sobre la Fase 4)

- **`SyncEngine`**: igual que `OneShotSyncLoader` pero persiste el `cursor` (`SyncCursorStore`), así que después del primer full dump las llamadas son incrementales de verdad.
- **Outbox activo**: `CommandQueue` (encolar), `CommandDispatcher` (mandar por HTTP genérico con OkHttp crudo, porque el path/método/body son dinámicos), `OutboxProcessor` (reproducir en orden, FIFO).
- **`SyncWorker`** (WorkManager + Hilt): push del outbox primero, pull después. 3 disparadores: periódico (15 min), manual (botón), y por conectividad (`NetworkConnectivityObserver` escuchado desde `GmpApplication`).
- **`JobsRepository.startJob()`**: ejemplo end-to-end del patrón — actualiza Room al toque (optimista) y encola el comando real. El resto de las acciones (finish, pay, assign, ...) siguen este mismo patrón cuando se conecten en Fase 6.
- La UI de debug ahora tiene "Sincronizar ahora" y "Probar outbox", más una lista de operaciones pendientes.

⚠️ El valor `"in_progress"` que usa `JobsRepository.startJob()` como nuevo `status` es un **placeholder ilustrativo**, no confirmado contra `jobsActionsController.js` — revisar antes de usar esta acción de verdad.

---

## ⚠️ Este proyecto fue generado sin Android SDK/Gradle disponibles

Se escribió a mano, con cuidado de sintaxis, pero **no se compiló ni se corrió** en este entorno (no hay Android SDK ni emulador). El primer paso real es abrirlo en Android Studio y dejar que sincronice — ahí van a aparecer errores de compilación si los hay, y hay que corregirlos ahí.

## Cómo arrancar

1. Abre la carpeta `GMPOffline/` con Android Studio (versión reciente, Iguana o superior recomendado — necesita soporte para AGP 8.5 y Kotlin 1.9).
2. Deja que Android Studio genere el resto del Gradle Wrapper automáticamente al sincronizar (falta `gradlew`, `gradlew.bat` y `gradle-wrapper.jar` — no se pudieron generar en este entorno porque no hay acceso a `services.gradle.org`; Android Studio los crea solo al abrir el proyecto).
3. **Antes de correr la app**, edita `app/build.gradle.kts` y reemplaza:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"http://REEMPLAZAR_IP_VPS:3002/\"")
   ```
   por la IP o dominio real del VPS, en **ambos** bloques (`debug` y `release`). Tiene que terminar en `/` (Retrofit lo requiere para baseUrl).
4. Si pruebas en emulador y el backend corre en tu propia máquina (no en el VPS), usa `10.0.2.2` en vez de `localhost`.
5. Sync de Gradle → Run.

## Qué hace esta versión (y qué NO)

La pantalla única (`MainActivity`) es deliberadamente de **debug**, no una UI final:

- Formulario con `company_id` / `phone` / `password` (precargado con los datos de prueba: `1` / `5551234`).
- Botón "Login + cargar desde /sync": hace login contra el backend, guarda el token, y llama `OneShotSyncLoader.loadOnce()` — que pagina `GET /sync` hasta agotarlo y hace upsert directo en Room.
- Debajo, dos listas (`jobs`, `staff`) que se llenan solas porque están conectadas por `Flow` a Room — si insertaras algo en la base a mano, la lista cambiaría sin tocar la UI. Eso es lo que hay que verificar: **la UI nunca habla con la red directamente, solo lee Room**.

**`OneShotSyncLoader` NO es el motor de sincronización real.** Es un cargador de un solo uso para poder probar la capa Room sin esperar a que exista el `SyncEngine`. No guarda el cursor para sync incremental, no corre en background, no reacciona a conectividad y no toca el outbox. Todo eso es la Fase 5.

`PendingOperationEntity` / `PendingOperationDao` (el outbox) están **definidos pero no usados** todavía — nadie inserta ni lee de esa tabla en esta fase. Es a propósito: el resto de la capa de datos ya sabe que va a convivir con operaciones optimistas, pero el mecanismo que las escribe y reproduce es Fase 5.

## Estructura

```
app/src/main/java/com/gmp/offline/
├── GmpApplication.kt          # @HiltAndroidApp
├── MainActivity.kt            # UI de debug (Compose)
├── di/
│   ├── DatabaseModule.kt      # provee GmpDatabase + DAOs
│   └── NetworkModule.kt       # provee Retrofit/OkHttp/ApiService
├── data/
│   ├── local/
│   │   ├── GmpDatabase.kt
│   │   ├── entities/          # espejo 1:1 de jobs, job_workers, materials,
│   │   │                      # job_materials, job_photos, staff + PendingOperationEntity
│   │   └── dao/                # un DAO por entidad, todos con Flow
│   ├── remote/
│   │   ├── ApiService.kt      # login + sync (nada más, por ahora)
│   │   ├── AuthInterceptor.kt
│   │   └── dto/                # DTOs + mappers dto -> entity
│   ├── repository/             # leen SOLO de Room (JobsRepository, StaffRepository, ...)
│   └── session/
│       └── SessionManager.kt  # guarda el token (SharedPreferences simple por ahora)
├── sync/
│   └── OneShotSyncLoader.kt   # ver advertencia arriba
└── ui/
    └── DebugViewModel.kt
```

## Siguiente paso al cerrar esta fase

Fase 5 — App Android: motor de sincronización real (`SyncEngine`, outbox activo, `SyncWorker` con WorkManager, manejo de IDs locales vs. confirmación del servidor).
