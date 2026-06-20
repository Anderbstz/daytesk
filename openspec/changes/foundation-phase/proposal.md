# Proposal: Foundation Phase — Daytesk GTD App

## Intent

Replace placeholder scaffolding (default Material3 theme, single-screen navigation, dummy `List<String>` data) with the full Daytesk GTD foundation: custom design system, domain models, mock data matching the HTML prototype, and a 5-tab Navigation3 shell with bottom nav + FAB.

## Scope

### In Scope

- Design system: custom colors, typography (Inter/PJS), shapes (12/16/20/999dp), spacing (4–48dp), elevation tokens
- Domain models: `Tarea`, `InboxItem`, `Alerta`, enums (`Prioridad`, `Contexto`, `TareaEstado`, `AlertaTipo`)
- `MockData` object with 6 tareas, 5 inbox items, 2 alertas, computed stats, weekly-review items — matching `htmls/index.html`
- `DataRepository` refactored from `List<String>` to domain-model flows
- 5-tab Navigation3 bottom nav (Inicio, Inbox, Tareas, Utilidades, Perfil) + FAB
- Screen stubs: each tab shows its static content from mock data (no interactivity yet)
- `MainScreenViewModel` refactored for domain-model UiState

### Out of Scope

- Task creation/edit forms (interactive FAB modal)
- Inbox processing bottom sheet
- Data persistence (Room/DataStore)
- Authentication/profile management
- Utilidades tool screens (image converter, transcriber — stub only)

## Capabilities

### New Capabilities

- `design-system`: Daytesk color palette, typography scale (Display→Tiny), shape tokens, spacing & elevation constants
- `domain-models`: `Tarea`, `InboxItem`, `Alerta` data classes + enums (`Prioridad`, `Contexto`, `TareaEstado`, `AlertaTipo`) with computed color/label properties
- `navigation`: 5-tab Navigation3 scaffold, bottom nav bar (72dp, active dot indicator), FAB (56dp accent circle)

### Modified Capabilities

None — no existing specs to modify.

## Approach

1. **Color.kt / Type.kt** — Replace default Material3 style with Daytesk palette and full Inter/PJS type scale as `DayteskColors`, `DayteskTypography` objects. Keep `DayteskTheme` wrapper.
2. **domain/** — Create `model/` package with `Tarea.kt`, `InboxItem.kt`, `Alerta.kt`, enums. Each enum has `color()`, `colorLight()`, `label()` computed properties.
3. **data/** — Create `MockData.kt` with all sample objects. Refactor `DataRepository` to expose `Flow<DayteskData>` containing stats + lists.
4. **NavigationKeys.kt** — Add `@Serializable data object` for each tab (Inicio, Inbox, Tareas, Utilidades, Perfil). Wire `MainNavigation` with `BottomNavBar` + FAB.
5. **ui/screens/** — One file per screen, each reads from a shared `MainViewModel` and renders static content from mock data.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `theme/Color.kt` | Modified | Replace purple defaults with Daytesk palette |
| `theme/Type.kt` | Modified | Replace default Material3 scale with custom Inter/PJS scale |
| `theme/Theme.kt` | Modified | Wire custom colors + typography, disable dynamic color |
| `data/*` | Modified | Refactor from `List<String>` to domain models + MockData |
| `domain/model/*` | New | 6 files: enums, data classes |
| `NavigationKeys.kt` | Modified | Add 5 tab NavKeys |
| `Navigation.kt` | Modified | Bottom nav + FAB scaffold |
| `ui/main/MainScreen.kt` | Modified | Replace greeting with 5-tab shell |
| `ui/main/MainScreenViewModel.kt` | Modified | Domain-model UiState |
| `ui/screens/*` | New | 5 screen composables (Inicio, Inbox, Tareas, Utilidades, Perfil) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Navigation3 API changes | Low | Pinned to published 1.0.1 in version catalog |
| Font files not bundled | Low | Inter + PJS available via Google Fonts Compose or Downloadable Fonts |

## Rollback Plan

Revert all files in scope via git: `git checkout HEAD -- <affected-files>`. No DB migrations or config changes — pure code revert.

## Dependencies

- `libs.versions.toml` already has Navigation3, Compose BOM, Lifecycle — no new deps
- HTML prototype at `htmls/index.html` as visual reference for mock data

## Success Criteria

- [ ] All 5 tabs render in bottom nav, tap switches content
- [ ] FAB renders as 56dp accent circle with plus icon
- [ ] Mock data matches HTML prototype (6 tareas, 5 inbox, 2 alertas, stats)
- [ ] Each `Prioridad` and `Contexto` displays its assigned color
- [ ] `./gradlew assembleDebug` compiles without errors
- [ ] `./gradlew test` passes all unit tests
