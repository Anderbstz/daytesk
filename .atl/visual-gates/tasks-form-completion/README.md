# Visual Gate — tasks-form-completion

This directory captures the human-performed device checks that the
automated `./gradlew connectedDebugAndroidTest` suite normally proves
on a Pixel API 36 emulator. Run these checks on a real device or
emulator **after** merging `feat/tasks-form-completion` into
`feature/inicio-screen` because the Compose instrumented tests could
not be executed on the apply host (no Android SDK, no JAVA_HOME, no
Java on PATH).

## Pre-conditions

1. Open `feature/inicio-screen` (which now contains the
   `tasks-form-completion` commits `a5abdf8`, `2b973ba`, `fd39b07`,
   `6c74a4f`) in Android Studio Hedgehog or newer with the
   AGP 9.0.1 / Gradle 9.1.0 toolchain.
2. Pick a Pixel emulator running the **API 36** system image with
   `enableEdgeToEdge()` forced by `MainActivity.kt:13`.
3. Build and launch the debug variant. Navigate to the **Tareas** tab
   via the bottom navigation.

## Visual checks

### (a) DatePicker opens when the Fecha row is tapped

1. Tap the FAB (the round "Agregar tarea" button in the bottom-right).
2. The "Nueva tarea" modal opens. Locate the **Fecha** row near the
   middle of the form; it should read "Sin fecha" with a chevron
   pointer on the right.
3. Tap the Fecha row.
4. PASS criteria:
   - A Material3 `DatePickerDialog` overlays the modal.
   - The dialog renders the current month with the day cells and
     the OK / Cancelar controls at the bottom.
   - The Fecha row behind the dialog is still visible but covered
     by the overlay (does not flash to a different value while
     the picker is open).
5. Tap **Cancelar**.
6. PASS criteria:
   - The dialog dismisses without committing a date.
   - The Fecha row still reads "Sin fecha".
7. Tap the Fecha row again, then tap **OK**.
8. PASS criteria:
   - A `TimePicker` dialog opens.
9. Tap **Cancelar** on the time picker.
10. PASS criteria:
    - Both dialogs close.
    - The Fecha row still reads "Sin fecha" — the cancelled
      TimePicker did NOT commit `fechaVencimiento`.
11. Tap the Fecha row, tap **OK**, then tap **OK** on the
    TimePicker (defaults to 12:00).
12. PASS criteria:
    - The Fecha row now displays a timestamp formatted as
      `dd MMM yyyy, HH:mm` (e.g., `15 mar 2026, 12:00`) — NOT
      "Sin fecha".
13. Capture: save a screenshot as
    `.atl/visual-gates/tasks-form-completion/datepicker.png`.

### (b) Title and description length counters visible on modal open

1. With the modal already open (after step (a) above), or by
   re-opening the modal.
2. Look at the area immediately below the **Título de la tarea**
   field (single-line) and below the **Descripción (opcional)**
   field (multi-line).
3. PASS criteria:
   - A counter reading `0/150` is right-aligned under the title
     field on first open.
   - A counter reading `0/500` is right-aligned under the
     description field on first open.
   - Both counters use a muted, small style (DayteskTypography.tiny,
     color TextDisabled).
4. Type any character into the title field.
5. PASS criteria:
   - The counter increments to `1/150` (or matches the current
     typed length).
6. Paste or `performTextInput` a string of 200 characters into the
   title field.
7. PASS criteria:
   - The counter caps at `150/150` — typing further does not
     increase the stored value past 150 characters.
   - Repeat with a 600-character string into the description field
     to confirm it caps at `500/500`.
8. Capture: save a screenshot as
   `.atl/visual-gates/tasks-form-completion/counters.png`.

### (c) Saved task carries the chosen date (not `System.currentTimeMillis()`)

1. Open the modal (FAB).
2. Type `Tarea con fecha` into the title field.
3. Tap the Fecha row, confirm a date (e.g., today), confirm the
   time picker (default 12:00 is fine).
4. Tap **Crear tarea** at the bottom.
5. PASS criteria:
   - The modal closes.
   - Navigating to the Inicio tab (or Tareas list) shows the new
     task with `Tarea con fecha` and the timestamp displayed in the
     chosen position (Today / This week / Hechas, depending on the
     selected date).
   - The displayed timestamp matches the date+time you confirmed
     (NOT the moment you tapped Crear tarea). The bug being fixed
     was that every saved task received `System.currentTimeMillis()`
     regardless of picker input — that must no longer happen.
6. Repeat without tapping the Fecha row at all (skip the picker
   entirely), type `Tarea sin fecha`, then tap **Crear tarea**.
7. PASS criteria:
   - The task appears in the Inbox or Tareas list with NO due date
     (the "Sin fecha" semantics are honored end-to-end through the
     nullable `Tarea.fechaVencimiento: Long?`).
8. Capture: save a screenshot as
   `.atl/visual-gates/tasks-form-completion/saved-with-date.png`.

## Optional automated check (if a connected emulator is available)

If a connected API 36 emulator or device is attached to the workstation
running these visual gates, the following commands re-run the
instrumented suite that the apply phase could not:

```bash
cd C:\Ander\Proyectos_Ander\DayTesk\daytesk
./gradlew test
./gradlew connectedDebugAndroidTest \
    --tests "com.nuitcode.daytesk.ui.modals.NuevaTareaModalTest.counter_visibleOnOpen" \
    --tests "com.nuitcode.daytesk.ui.modals.NuevaTareaModalTest.counter_updatesAsUserTypes" \
    --tests "com.nuitcode.daytesk.ui.modals.NuevaTareaModalTest.counter_tituloMaxLengthEnforced" \
    --tests "com.nuitcode.daytesk.ui.modals.NuevaTareaModalTest.counter_descripcionMaxLengthEnforced" \
    --tests "com.nuitcode.daytesk.ui.modals.NuevaTareaModalTest.datePicker_opensOnFechaRowClick" \
    --tests "com.nuitcode.daytesk.ui.modals.NuevaTareaModalTest.datePicker_cancelDoesNotMutateFecha" \
    --tests "com.nuitcode.daytesk.ui.modals.NuevaTareaModalTest.datePicker_confirmSetsFechaVencimiento" \
    --tests "com.nuitcode.daytesk.ui.main.MainScreenTest.nuevaTareaModal_canBeOpened"
./gradlew assembleDebug
```

All eight instrumented tests should be green. The three visual gates
above cover scenario 2 of REQ-01, the cancel-mutation guarantee of
REQ-01, and the save-binding guarantee of REQ-02 that the
node-visibility assertions cannot prove on their own.

## Static gates already verified by the apply phase

The following gates were confirmed by static analysis on the apply
host (no JDK required). They are included here so the user can
re-verify quickly without re-running the build:

```bash
# REQ-05 — no double-padding trap on Android 15+
grep -c "safeDrawingPadding" app/src/main/java/com/nuitcode/daytesk/ui/modals/NuevaTareaModal.kt
# expected: 0

# REQ-02 — saved task uses modal state, not System.currentTimeMillis()
grep -c "System.currentTimeMillis" app/src/main/java/com/nuitcode/daytesk/ui/modals/NuevaTareaModal.kt
# expected: 0 (TareaEntity.fechaCreacion default in the data class
#           is allowed to remain; only NuevaTareaModal.kt is checked)

# REQ-03 / REQ-04 — length constants exist on the Tarea companion
grep -n "TITULO_MAX_LENGTH\|DESCRIPCION_MAX_LENGTH" app/src/main/java/com/nuitcode/daytesk/model/Tarea.kt
# expected: const val TITULO_MAX_LENGTH = 150
#           const val DESCRIPCION_MAX_LENGTH = 500

# Cross-change invariant — edge-to-edge stays enabled (inicio-polish)
grep -n "enableEdgeToEdge" app/src/main/java/com/nuitcode/daytesk/MainActivity.kt
# expected: enableEdgeToEdge() call at line 13
```