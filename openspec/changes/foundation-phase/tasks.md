# Tasks: Foundation Phase — Daytesk GTD App

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 900–1100 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1: Theme+Domain+Data → PR2: Nav+Stubs+Tests |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |
| Decision needed before apply | Yes |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Design System + Domain Models + Data Layer | PR 1 | Base: main. ~450 lines. Theme, domain enums/data classes, MockData, repository |
| 2 | Navigation + Screen Stubs + Tests | PR 2 | Base: main or PR1 branch. Nav shell, 5 screens, viewmodel wiring, test updates |

## Phase 1: Design System

- [ ] 1.1 Create `theme/Shape.kt` — `DayteskShapes` (small/medium/large/pill), `DayteskSpacing` (4–48dp, 8 vals), `DayteskElevation` (card/elevated/nav)
- [ ] 1.2 Rewrite `theme/Color.kt` — `DayteskColors`: bg, surface, accent, success, warning, urgent, 4 context colors, 3 text, border, divider + dark + light variants (22 vals)
- [ ] 1.3 Rewrite `theme/Type.kt` — `DayteskTypography`: 10 named styles (Display→tiny) using Inter (body) & Plus Jakarta Sans (headings) via downloadable fonts
- [ ] 1.4 Rewrite `theme/Theme.kt` — Remove dynamic color, map `DayteskColors` → M3 `ColorScheme`, add `MaterialTheme.appColors/typography/shapes/spacing/elevation` extensions

## Phase 2: Domain Models

- [ ] 2.1 Create `domain/model/Prioridad.kt` — enum {BAJA, MEDIA, ALTA} + `color()`/`colorLight()`/`label()` (Baja→success, Media→warning, Alta→urgent)
- [ ] 2.2 Create `domain/model/Contexto.kt` — enum {CASA, TRABAJO, PERSONAL, SALUD} + `color()`/`colorLight()`/`label()` with hex palette
- [ ] 2.3 Create `domain/model/TareaEstado.kt` (PENDIENTE, COMPLETADA) + `AlertaTipo.kt` (VENCIMIENTO, RECORDATORIO) + `icon()`
- [ ] 2.4 Create `domain/model/Tarea.kt`, `InboxItem.kt`, `Alerta.kt` — data classes with id, titulo/texto, timestamps, typed enums

## Phase 3: Data Layer

- [ ] 3.1 Create `data/DayteskData.kt` — data class: stats (hoy/inbox/completadas ints), tareasHoy/Semana/completadas, inbox items, alertas, weeklyReview
- [ ] 3.2 Create `data/MockData.kt` — object: 6 tareas (3 Hoy, 2 Semana, 1 completed), 5 inbox items, 2 alertas, stats matching HTML prototype
- [ ] 3.3 Rewrite `data/DataRepository.kt` — interface returns `Flow<DayteskData>`, default impl wraps MockData

## Phase 4: ViewModel + Navigation

- [ ] 4.1 Rewrite `MainScreenViewModel.kt` — `DayteskUiState` sealed interface (Loading/Success with `DayteskData`/Error), `uiState: StateFlow<DayteskUiState>`
- [ ] 4.2 Update `NavigationKeys.kt` — Keep `Main`, add 5 `@Serializable data object` keys: Inicio, Inbox, Tareas, Utilidades, Perfil
- [ ] 4.3 Rewrite `Navigation.kt` — `Scaffold` + `NavDisplay` with 5 entries, `BottomNavBar` (72dp, active dot accent), FAB (56dp, plus icon, 8dp elev)
- [ ] 4.4 Simplify `MainScreen.kt` — Remove Greeting, keep as entry point delegating to `MainNavigation`

## Phase 5: Screen Stubs

- [ ] 5.1 Create `ui/screens/InicioScreen.kt` — greeting + 3 stat cards (hoy=3, inbox=5, completadas=12) + próximas acciones + vencen hoy
- [ ] 5.2 Create `ui/screens/InboxScreen.kt` — quick capture field + 5 items list + "Toca para procesar" hint
- [ ] 5.3 Create `ui/screens/TareasScreen.kt` — filter chips + grouped lists: Hoy (3), Esta semana (2), Completadas
- [ ] 5.4 Create `ui/screens/UtilidadesScreen.kt` — 2×2 grid: Convertir imágenes, Transcribir audio, Video a texto, placeholder
- [ ] 5.5 Create `ui/screens/PerfilScreen.kt` — avatar + info + 3 stats + menu items (Config, Ayuda, Cerrar sesión)

## Phase 6: Tests (strict TDD — red/green/refactor per task)

- [ ] 6.1 Update `MainScreenViewModelTest.kt` — FakeRepo emitting `DayteskData`. Tests: loading→Success with typed data, repository failure→Error
- [ ] 6.2 Update `MainScreenTest.kt` — Instrumented tests: 5 tabs render+switch, FAB 56dp visible, InicioScreen stat labels exist
