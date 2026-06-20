# Foundation Phase — Daytesk GTD App — Full Specs

All capabilities are NEW — no existing specs.

---

## 1. Design System

| Token | Value | Strength |
|-------|-------|----------|
| `DayteskColors` | bg #F7F8FC, surface #FFFFFF, textPrimary #1A1A1A, textSecondary #6B7280, textDisabled #9CA3AF, border #E5E7EB, divider #F3F4F6, accent #7C8CF5, accentLight #EEF0FF, success #6BCB77, successLight #E8F5E9, warning #FBBF24, warningLight #FFFBEB, urgent #F87171, urgentLight #FFF3F3, contextCasa #FBC4AB, contextTrabajo #7DD6F0, contextPersonal #D4B8FD, contextSalud #B2E87A | MUST |
| `DayteskTypography` | Display 700/22sp, H1 700/20sp, H2 600/18sp, H3 700/16sp, bodyLg 400/16sp, bodyMd 400/15sp, label 500/13sp, caption 400/12sp, badge 700/11sp, tiny 500/10sp | MUST |
| Shape tokens | small 12dp, medium 16dp, large 20dp, pill 999dp | MUST |
| Spacing | 4, 8, 12, 16, 20, 24, 32, 48 dp | MUST |
| Elevation | card 2dp, elevated 4dp, nav 8dp | MUST |
| `DayteskTheme` | Wires colors + typography, disables dynamic color | MUST |

**Fonts**: Inter (body), Plus Jakarta Sans (headings) — Google Fonts Compose or Downloadable Fonts.

**Scenarios**: (1) Colors render matching hex. (2) Typography scale renders correct sizes/weights. (3) `DayteskSpacing.s16` = 16dp, `DayteskElevation.card` = 2dp.

---

## 2. Domain Models

| File | Exports | Strength |
|------|---------|----------|
| `Prioridad.kt` | `enum` { Baja, Media, Alta } + `color()`, `colorLight()`, `label()` | MUST |
| `Contexto.kt` | `enum` { Casa, Trabajo, Personal, Salud } + `color()`, `colorLight()`, `label()` | MUST |
| `TareaEstado.kt` | `enum` { Pendiente, Completada } | MUST |
| `AlertaTipo.kt` | `enum` { vencimiento, recordatorio } + `icon()` | MUST |
| `Tarea.kt` | `data class`(id, titulo, descripcion, prioridad, contexto, estado, fechaCreacion, fechaVencimiento, orden) | MUST |
| `InboxItem.kt` | `data class`(id, texto, timestamp) | MUST |
| `Alerta.kt` | `data class`(id, mensaje, tipo, leida) | MUST |

**Color map**: Baja→success, Media→warning, Alta→urgent. Casa→#FBC4AB, Trabajo→#7DD6F0, Personal→#D4B8FD, Salud→#B2E87A.

**Scenarios**: (1) `Prioridad.Alta.color()` = `urgent`. (2) `Tarea(id=1).id` = 1. (3) Toggling estado updates label.

---

## 3. Mock Data & Data Layer

| ID | File | Exports | Strength |
|----|------|---------|----------|
| MD‑1 | `MockData.kt` | `object` — 6 tareas, 5 inbox items, 2 alertas, `stats`, `weeklyReviewItems` | MUST |
| DR‑1 | `DataRepository.kt` | Refactored: `val data: Flow<DayteskData>` with stats + lists + alertas | MUST |

**Data matches** `htmls/index.html`: Hoy (Llamar al dentista urgente/10am, Informe trimestral/5pm, Sacar basura completed), Esta semana (Limpiar garaje, Leer libro), Inbox (5 items). Stats: hoy=3, inbox=5, completadas=12.

**Scenarios**: (1) `MockData.tareas` → 3 Hoy, 2 Esta semana, 1 completed. (2) `DefaultDataRepository().data` emits `DayteskData` with matching counts. (3) All typed accessors present.

---

## 4. Navigation & Screen Stubs

| ID | File | Exports | Strength |
|----|------|---------|----------|
| NAV‑1 | `NavigationKeys.kt` | 5 `@Serializable data object`: Inicio, Inbox, Tareas, Utilidades, Perfil | MUST |
| NAV‑2 | `Navigation.kt` | `MainNavigation`: 5-tab Navigation3 bottom nav (72dp, active dot, accent) + FAB (56dp circle, plus icon, 8dp elev) | MUST |
| SS‑1 | `InicioScreen.kt` | Greeting + 3 stat cards + próximas acciones + vencen hoy + empty state | MUST |
| SS‑2 | `InboxScreen.kt` | Quick capture + 5 items + "Toca para procesar" | MUST |
| SS‑3 | `TareasScreen.kt` | Filter chips + Hoy (3) + Esta semana (2) + completed | MUST |
| SS‑4 | `UtilidadesScreen.kt` | 2×2 grid: Convertir imágenes, Transcribir audio, Video a texto, + placeholder | MUST |
| SS‑5 | `PerfilScreen.kt` | Avatar + info + 3 stats + menu (Config, Ayuda, Cerrar) | MUST |

**Scenarios**: (1) Bottom nav shows 5 tabs. (2) Tap Tareas → hides Inicio, shows Tareas. (3) FAB = 56dp accent circle. (4) Inicio stats = hoy=3, inbox=5, completadas=12.

---

## 5. ViewModel

| Exports | Strength |
|---------|----------|
| `MainScreenViewModel.uiState: StateFlow<DayteskUiState.Success>` holds `DayteskData` (not `List<String>`) | MUST |

**Scenarios**: (1) ViewModel emits `Success` with typed domain objects. (2) Repository failure emits `Error`.
