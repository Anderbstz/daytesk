# custom-contexts — Visual Gate

> **Skip-verify pattern #95** — This Windows host has no Java/Android SDK.
> `./gradlew test` and `connectedDebugAndroidTest` cannot run. The 31
> authored tests (21 from PR1 + 10 from PR2) MUST be executed by the
> maintainer on a workstation with Android SDK + JDK 17. This file documents
> the **manual visual checks** that complement the test suite and cover the
> behaviors the unit/instrumented tests cannot reach (Room migration on a
> real device, real Compose UI render, real `ContextChip` text-color
> contrast on every default color).

## Scope

The `custom-contexts` change replaces the hardcoded `Contexto` enum with a
user-managed Room table (`contextos`), adds a single-transaction migration
v1→v2 that preserves existing `Tarea.contexto` associations, and ships a
CRUD screen in Perfil (long-press delete with FK guard, FAB add, name +
8-swatch palette, locked color/icon for defaults).

PR1 = data foundation (entity, DAO, migration, repository, FK swap).
PR2 = UI rewire (ContextosScreen, modals, navigation, all consumer
files). PR3 = this file + the propagation test in
`MainScreenViewModelTest.kt`.

## Pre-flight

| Requirement | Why |
|-------------|-----|
| Pixel API 36 emulator (or physical device) | Matches the production target; API 33+ required for Material3 1.2 + the ModalBottomSheet used by `ContextEditModal`. |
| Clean app data OR a v1 DB on disk | To exercise both the first-install seed path AND the v1→v2 migration. |
| Android Studio Hedgehog or newer | For the layout inspector and Material3 design view. |
| `./gradlew assembleDebug` succeeded | If the build is red, the visual checks below are meaningless. |

## Manual checks (4)

Run these on a fresh emulator instance **and** on a device that previously
ran the v1 codebase.

### Check 1 — Default contexts are visible on a healthy DB

**Setup**: clear app data (`adb shell pm clear com.nuitcode.daytesk`) and
launch the app.

**Expected**:
- `Inbox` and `Tareas` screens render 4 default `@casa`, `@trabajo`,
  `@personal`, `@salud` chips on task cards.
- `Perfil → Contextos` opens `ContextosScreen` showing exactly 4 rows in
  this order: `casa`, `trabajo`, `personal`, `salud`. Each row shows a
  color swatch and the label "Predeterminado" under the name.
- No row shows a long-press delete affordance (defaults are
  `esDefault = true`; the long-press is gated by `if (!contexto.isDefault())`).

**Why this matters**: confirms the `populateDatabase` callback seeded the
4 defaults on first install (REQ-02) and that the `ContextosScreen`
ordering matches `orden ASC` from the DAO.

### Check 2 — Add a custom context flows through every consumer

**Setup**: from `Perfil → Contextos`, tap the FAB (Add, `+`).

**Expected**:
- A `ModalBottomSheet` opens with title "Nuevo contexto".
- Typing `compras` and tapping color #2 (`#9CCC65` — green) then Guardar
  closes the modal; a new row `@compras` appears at the bottom of the
  list.
- Navigate to `Tareas` — the filter-chip row now shows **5** chips
  (Todas + 4 defaults + compras), in `orden` order.
- Tap `+ Nueva tarea` — the context selector in `NuevaTareaModal` shows
  the 4 defaults + `@compras`. Pick `compras`, save the tarea; reopen
  the modal — the selected chip is `compras`.
- Open `Inbox`, tap a captured item to open `ProcesarInboxModal` — the
  context selector also shows `@compras`.

**Why this matters**: confirms the 3-way `combine` in
`DefaultDataRepository.data` re-emits on insert (REQ-06), the
`TareasScreen` filter chips iterate `data.contextos` (REQ-07), and both
modals read `contextos` from the caller (REQ-06).

### Check 3 — Rename works; defaults lock the color picker

**Setup**: long-tap `@compras` row from Check 2 (or tap to open the
edit modal).

**Expected** (custom):
- The edit modal opens pre-populated with name `compras` and the
  selected color (green).
- The 8-color `LazyVerticalGrid` is interactive; tapping another swatch
  updates the selection.
- Typing `shopping` and tapping Guardar closes the modal; the row
  re-renders as `@shopping`.
- The list and every consumer (`Tareas` chips, both modals) now show
  `@shopping` instead of `@compras`.

**Expected** (default — tap `@casa`):
- The edit modal opens pre-populated with name `casa`.
- The 8-color grid is replaced by a single static color swatch with
  testTag `context_modal_color_locked`; tapping it does nothing.
- Typing a new name and tapping Guardar closes the modal; the row
  re-renders with the new name; the underlying color is unchanged.

**Why this matters**: confirms the rename path persists (REQ-04), the
locked-color branch renders for `esDefault = true` (REQ-04), and the
repository re-emits after update (REQ-06).

### Check 4 — Delete refused when tareas reference the context

**Setup**: keep the `shopping` tarea from Check 2/3. From
`Perfil → Contextos`, long-press the `@shopping` row.

**Expected**:
- An `AlertDialog` opens with title "¿Eliminar contexto?", body
  referencing `@shopping`, and Cancelar / Eliminar buttons.
- Tap Eliminar — the dialog closes; the row **stays** (in-use guard);
  the dialog re-opens or an `AlertDialog` with "En uso por 1 tarea(s)"
  appears.
- After dismissing the error, long-press a different custom context
  with **zero** associated tareas (e.g. add `@viajes`, do not assign
  any tarea, then long-press it) — the confirmation dialog opens;
  tap Eliminar — the row is removed; the list now shows 4 defaults
  only; the `Tareas` chip row shows 4 chips only.

**Expected** (default — long-press `@casa`):
- **No** `AlertDialog` opens. The long-press is a no-op; the
  `deleting` state is only armed by `if (!contexto.isDefault())`
  in `ContextosScreen.kt:128`.

**Why this matters**: confirms the triple-gate (DAO `deleteIfUnreferenced`
sentinel -1 for default / +count for in-use; `DefaultContextoRepository`
translates sentinels to typed `DefaultContextProtectedException` /
`ContextoInUseException`; UI hides the affordance for defaults) holds
end-to-end (REQ-05).

## v1→v2 migration smoke test

> If the user has a v1 install on a physical device or older emulator
> instance, exercise the migration path BEFORE clearing data.

**Setup**:
- On a device with the **v1** app installed and a few tareas using each
  of the 4 enum contexts (CASA, TRABAJO, PERSONAL, SALUD), install the
  v2 build **on top** (no data clear).
- Launch the app.

**Expected**:
- The app launches without crashing (no Room schema-hash mismatch).
- The v1 tareas now appear in `Tareas` and `Inbox` with the **same**
  context labels (`@casa`, `@trabajo`, `@personal`, `@salud`) as before.
- `Perfil → Contextos` shows exactly 4 rows in `orden` order.
- `AppDatabase.kt:39` registers `.addMigrations(Migrations.MIGRATION_1_2)`;
  the migration is a single `transaction { ... }` so a crash mid-flight
  rolls back to the v1 schema.

If the migration is silently rejected, the app will crash on
`Room.databaseBuilder` with `IllegalStateException: Room cannot verify
the data integrity`. Capture the full stack trace; the
`Migrations.MIGRATION_1_2` SQL is documented in
`data/local/Migrations.kt`.

## Command block (user-side run on a host with the SDK)

```bash
# 1. Static gates (should all pass on PR3's branch)
grep -c "MigrationTest"              app/src/test/
grep -c "ContextoRepositoryTest"      app/src/test/
grep -c "ContextosScreenTest"         app/src/androidTest/
grep -c "contextos"                   app/src/test/java/com/nuitcode/daytesk/ui/main/MainScreenViewModelTest.kt
ls .atl/visual-gates/custom-contexts/README.md

# 2. Build the debug APK
./gradlew :app:assembleDebug

# 3. Run all PR1 + PR2 + PR3 unit + instrumented tests
./gradlew :app:test --tests "*ContextoRepositoryTest*" --tests "*ContextoDaoTest*" --tests "*MigrationTest*" --tests "*MainScreenViewModelTest*"
./gradlew :app:connectedDebugAndroidTest --tests "*ContextosScreenTest*" --tests "*MainScreenTest*"

# 4. (Optional) Export the Room v2 schema for future-proofing
./gradlew :app:room.schemaLocation
```

Expected: 4 test classes, 1 visual-gate README, 1 debug APK; the unit
test class produces 16 cases (12 repository + 1 DAO FK case + 2
migration + 4 ViewModel). The connectedDebugAndroidTest class produces
7 ContextosScreenTest + 3 MainScreenTest reachability cases = 10
instrumented cases.

## Open risks for visual gate

| # | Risk | Mitigation |
|---|------|------------|
| 1 | `ContextChip` text contrast fails on a chosen color (luminance near 0.5 boundary) | Threshold is `luminance() > 0.5f`; fallback is `Color.Black` at the boundary. If a real-device render shows unreadable text, raise the threshold to `>= 0.5f` in `ui/inicio/ContextChip.kt`. |
| 2 | `ContextosScreen` first-render flash (empty CTA before flow emits) | Acceptable; if user notices, hoist the seed into a synchronous path in a follow-up. |
| 3 | Migration v1→v2 fails on a real device with hand-edited `contexto` values | `DEFAULT 3` on the new column covers unknown values; all 4 known enum names are in the `CASE-WHEN`. Hand-edited garbage values fall back to PERSONAL id 3. |
| 4 | Review budget exceeded | PR1 = 1264 insertions, PR2 = 1143 insertions, PR3 ≈ ~120 (tests + this file). Cumulative ~2500 lines across 3 commits. Each commit is reviewable on its own with clear scope. |

## Sign-off

- [ ] All 4 manual checks PASS on a Pixel API 36 emulator
- [ ] (Optional) v1→v2 migration smoke test PASSES on a v1 device
- [ ] `assembleDebug` produces a green build
- [ ] (Optional) All 26 unit + instrumented test cases PASS (21 PR1 + 4 new ViewModel + 1 ViewModel propagation)
- [ ] Sign-off date: __________
- [ ] Sign-off by: __________
