# Design: Foundation Phase — Daytesk GTD App

## Technical Approach

Replace M3 scaffolding with Daytesk's design system, domain models, and 5-tab Navigation3 shell. `MockData` feeds a typed `Flow<DayteskData>` through `DataRepository` → `MainScreenViewModel` → screen composables. Theme wraps `MaterialTheme` with custom `ColorScheme` + exposed Daytesk tokens via `MaterialTheme` extensions.

## Architecture Decisions

| Decision | Choice | Alternatives | Rationale |
|----------|--------|-------------|-----------|
| Theme structure | Custom `DayteskColors` object + M3 `ColorScheme` mapping | Full custom theme without M3 | M3 components need `ColorScheme`; exposing extras via `MaterialTheme.appColors` gives access without breaking M3 API |
| Typography | Standalone `DayteskTypography` object + subset mapped to M3 `Typography` | Only M3 Typography | Our scale (Display→tiny) has roles M3 doesn't have; store all in custom object, map a subset for M3 defaults |
| Color scheme mapping | accent→primary, urgent→error, success→tertiary, warning→secondary, plus extra tokens | Map everything to M3 roles | M3 has fixed roles; context colors (Casa, Trabajo, etc.) and light variants have no M3 equivalent — expose via `appColors` |
| Dynamic color | Disabled | Enabled by default | Daytesk has a fixed brand palette; dynamic color would override it |
| Font strategy | Downloadable Fonts (Inter + Plus Jakarta Sans) via Compose `fonts` XML | Google Fonts Compose library | Zero dependency, works offline after first download, follows Android best practice |
| Bottom nav | Navigation3 `NavDisplay` + `Scaffold` | Custom BottomNavBar | Navigation3 already provides `NavDisplay` — no need for custom nav wiring |
| Navigation pattern | `Scaffold` wrapping `NavDisplay` | NavDisplay alone | `Scaffold` provides slots for bottom bar and FAB in one composable |

## Data Flow

```
MockData (object)
    │
    ▼
DefaultDataRepository
    └── val data: Flow<DayteskData>
            │
            ▼
    MainScreenViewModel
        └── val uiState: StateFlow<DayteskUiState>
                │
                ▼
        Screen Composables
            ├── InicioScreen  (stats, próximas, vencen hoy)
            ├── InboxScreen   (quick capture, items list)
            ├── TareasScreen  (filter chips, grouped lists)
            ├── UtilidadesScreen (2×2 grid)
            └── PerfilScreen  (avatar, stats, menu)
```

```
DayteskData ──▶ MainScreenViewModel ──▶ MainNavigation
                  │                        │
              uiState:              NavDisplay {
           StateFlow<               entry<Inicio> → InicioScreen
             DayteskUiState         entry<Inbox>  → InboxScreen
               .Success>            entry<Tareas> → TareasScreen
                                    entry<Utilidades>→ UtilidadesScreen
                                    entry<Perfil> → PerfilScreen
                                  }
```

## Theme Architecture

```
DayteskTheme(darkTheme, content)
  │
  ├── DayteskColors.light / DayteskColors.dark  (custom palette)
  ├── buildColorScheme()  (maps DayteskColors → M3 ColorScheme)
  ├── DayteskTypography   (full custom scale: Display→tiny)
  ├── DayteskShapes       (small=12dp, medium=16dp, large=20dp, pill=999dp)
  │
  └── MaterialTheme(colorScheme, typography, shapes) { content() }

Extensions:
  MaterialTheme.appColors: DayteskColors     (extra tokens)
  MaterialTheme.appTypography: DayteskTp     (full scale)
  MaterialTheme.appShapes: DayteskShapes     (named tokens)
  MaterialTheme.appSpacing: DayteskSpacing   (4,8,12,16,20,24,32,48dp)
  MaterialTheme.appElevation: DayteskElevation (card=2, elevated=4, nav=8dp)
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `theme/Color.kt` | **Modify** | Replace default M3 colors w/ `DayteskColors` object (22 vals: bg, surface, accent, success, warning, urgent, 4 context colors, 3 text, border, divider + dark variants + light variants) |
| `theme/Type.kt` | **Modify** | Replace M3 scale w/ `DayteskTypography` object (10 vals: Display, H1-H3, bodyLg/Md/Sm, label, caption, badge, tiny) using Inter & Plus Jakarta Sans |
| `theme/Shape.kt` | **Create** | `DayteskShapes` (4 named RoundedCornerShape), `DayteskSpacing` (8 dp vals), `DayteskElevation` (3 dp vals) |
| `theme/Theme.kt` | **Modify** | Remove dynamic color, wire custom colors/typography/shapes into MaterialTheme, add extension properties |
| `domain/model/Prioridad.kt` | **Create** | `enum Prioridad` (BAJA, MEDIA, ALTA) + `color()`, `colorLight()`, `label()` |
| `domain/model/Contexto.kt` | **Create** | `enum Contexto` (CASA, TRABAJO, PERSONAL, SALUD) + `color()`, `colorLight()`, `label()` |
| `domain/model/TareaEstado.kt` | **Create** | `enum TareaEstado` (PENDIENTE, COMPLETADA, VENCIDA) |
| `domain/model/AlertaTipo.kt` | **Create** | `enum AlertaTipo` (VENCIMIENTO, RECORDATORIO) + `icon()` |
| `domain/model/Tarea.kt` | **Create** | `data class Tarea(id, titulo, descripcion, prioridad, contexto, estado, fechaCreacion, fechaVencimiento, orden)` |
| `domain/model/InboxItem.kt` | **Create** | `data class InboxItem(id, texto, timestamp)` |
| `domain/model/Alerta.kt` | **Create** | `data class Alerta(id, mensaje, tipo, leida)` |
| `data/DayteskData.kt` | **Create** | `data class DayteskData(stats, tareasHoy, tareasSemana, completadas, inbox, alertas, weeklyReview)` |
| `data/MockData.kt` | **Create** | `object MockData` — 6 tareas, 5 inbox items, 2 alertas, computed stats |
| `data/DataRepository.kt` | **Modify** | Interface returns `Flow<DayteskData>`; impl wraps MockData |
| `NavigationKeys.kt` | **Modify** | Keep `Main` + add 5 `@Serializable data object` keys: Inicio, Inbox, Tareas, Utilidades, Perfil |
| `Navigation.kt` | **Modify** | Remove old NavDisplay. New: `Scaffold` + `NavDisplay` w/5 entries + `BottomNavBar` (72dp, active dot) + FAB (56dp) |
| `ui/main/MainScreen.kt` | **Modify** | Remove Greeting. Keep only as entry point delegating to `MainNavigation` |
| `ui/main/MainScreenViewModel.kt` | **Modify** | `uiState: StateFlow<DayteskUiState>` → Success holds `DayteskData` |
| `ui/screens/InicioScreen.kt` | **Create** | Greeting + 3 stat cards + próximas acciones + vencen hoy + empty state |
| `ui/screens/InboxScreen.kt` | **Create** | Quick capture + 5 inbox items + "Toca para procesar" |
| `ui/screens/TareasScreen.kt` | **Create** | Filter chips + grouped by day (Hoy 3, Semana 2, Completadas) |
| `ui/screens/UtilidadesScreen.kt` | **Create** | 2×2 grid: Convertir imágenes, Transcribir audio, Video a texto, placeholder |
| `ui/screens/PerfilScreen.kt` | **Create** | Avatar + info + 3 stats + menu items |

## Interfaces / Contracts

```kotlin
// Repository
interface DataRepository {
  val data: Flow<DayteskData>
}

// Ui State
sealed interface DayteskUiState {
  data object Loading : DayteskUiState
  data class Success(val data: DayteskData) : DayteskUiState
  data class Error(val throwable: Throwable) : DayteskUiState
}

// Theme access
val MaterialTheme.appColors: DayteskColors
val MaterialTheme.appTypography: DayteskTypography
val MaterialTheme.appShapes: DayteskShapes
val MaterialTheme.appSpacing: DayteskSpacing
val MaterialTheme.appElevation: DayteskElevation
```

## Dependency Graph

```
DayteskData ──── dayteskdata/  (stats + lists)
    │
    ├── Tarea ──────────── domain/model/  (prioridad, contexto, estado)
    │                       ├── Prioridad enum ─── DayteskColors
    │                       ├── Contexto enum  ─── DayteskColors
    │                       └── TareaEstado enum
    ├── InboxItem ───────── domain/model/
    └── Alerta ──────────── domain/model/
                            └── AlertaTipo enum

Screen Composables ──── MainScreenViewModel ──── DataRepository
      │                                              └── MockData
      └── DayteskTheme
            ├── Color.kt / Type.kt / Shape.kt
            └── MaterialTheme
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Domain enums (color/label values) | JUnit — assert `Prioridad.Alta.color() == DayteskColors.urgent` |
| Unit | MockData counts & types | JUnit — `MockData.tareas.size == 6` |
| Unit | ViewModel emits Success with DayteskData | JUnit + coroutines test — `FakeRepo` → `uiState.first() is Success` |
| Unit | Theme extensions return correct tokens | JUnit — `DayteskShapes.small == 12.dp` |
| Instrumented | Bottom nav 5 tabs render + switch | Compose UI Test — `onNodeWithText("Inicio").assertExists()`, click "Tareas" → assert TareasScreen visible |
| Instrumented | FAB renders as 56dp | Compose UI Test — assert FAB visible |
| Instrumented | Screen stubs show mock data counts | Compose UI Test — Inicio shows "3" for hoy stat |

## Migration / Rollout

No migration required. All changes are pure code — no data to migrate, no feature flags. The app goes from single-screen `List<String>` to full 5-tab shell in one commit.

## Open Questions

- [ ] Font files: confirm Downloadable Fonts XML for Inter & Plus Jakarta Sans are configured, or switch to `com.google.android.gms:play-services-fonts`
- [ ] Navigation3 NavDisplay bottom bar: confirm `bottomNavigation` slot works with `Scaffold` in Navigation3 1.0.1, or if we need `NavigationSuiteScaffold`
