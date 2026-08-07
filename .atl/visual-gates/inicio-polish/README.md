# Visual Gate — inicio-polish

This directory captures the human-performed device checks that the
automated `./gradlew connectedDebugAndroidTest` suite normally proves
on a Pixel API 36 emulator. Run these checks on a real device or
emulator **after** merging `feat/inicio-polish` into
`feature/inicio-screen` because the Compose instrumented tests could
not be executed on the apply host (no Android SDK, no JAVA_HOME, no
Java on PATH).

## Pre-conditions

1. Open `feature/inicio-screen` (which now contains the `inicio-polish`
   commits `7b7534c`, `7c8acd9`, `113cbb4`) in Android Studio Hedgehog
   or newer with the AGP 9.0.1 / Gradle 9.1.0 toolchain.
2. Pick a Pixel emulator running the **API 36** system image with
   `enableEdgeToEdge()` forced by `MainActivity.kt:13`.
3. Build and launch the debug variant. The Inicio tab is selected by
   default on cold start.

## Visual checks

### (a) Bottom nav clears the gesture bar with no excessive gap

1. From the cold-start Inicio tab, look at the bottom of the screen.
2. PASS criteria:
   - The five tabs ("Inicio", "Inbox", "Tareas", "Utilidades", "Perfil")
     are all fully visible — no tab is covered by the system gesture bar.
   - The vertical gap between the last scrollable row of Inicio content
     and the top of the bottom nav does **not** exceed the bottom nav's
     own visual height. (No double-padding trap.)
3. FAIL criteria: any tab is partially clipped by the gesture bar, or
   there is a visible empty band between the content and the nav bar
   that is roughly the height of the nav bar itself.
4. Capture: save a screenshot as
   `.atl/visual-gates/inicio-polish/nav-inset.png`.

### (b) Three stat cards equal height, no clipping

1. Look at the "Hoy / Inbox / Hechas" row near the top of Inicio.
2. PASS criteria:
   - The three cards have identical heights (they should match within
     a single pixel of tolerance).
   - The number on each card is fully visible — no clipped digit.
   - The subtitle under each number ("tareas", "pendientes",
     "completadas") is rendered on a single line and ends with an
     ellipsis if it would otherwise wrap.
3. FAIL criteria: the cards have visibly different heights, or any
   number is clipped.
4. Capture: save a screenshot as
   `.atl/visual-gates/inicio-polish/stat-cards.png`.

### (c) Avatar within 8 dp of the trailing edge

1. Look at the Inicio header Row.
2. PASS criteria:
   - "Buenos d\u00edas" / "Buenas tardes" / "Buenas noches" greeting on
     the left.
   - "Andr\u00e9s" displayed below the greeting, no clipping at the
     bottom.
   - On the right: star icon, the points number ("12"), and the "AN"
     avatar bubble, all on a single Row.
   - The "AN" avatar bubble's right edge is **within 8 dp** of the
     screen's right edge (i.e. flush to the trailing edge with the
     normal 20 dp horizontal padding absorbed by the Row).
3. FAIL criteria: "AN" avatar is centered, pulled away from the right
   edge, or "Andr\u00e9s" is cut off at the bottom.
4. Capture: save a screenshot as
   `.atl/visual-gates/inicio-polish/header.png`.

## Optional automated check (if a connected emulator is available)

If a connected API 36 emulator or device is attached to the workstation
running these visual gates, the following commands re-run the
instrumented suite that the apply phase could not:

```bash
cd C:\Ander\Proyectos_Ander\DayTesk\daytesk
./gradlew test
./gradlew connectedDebugAndroidTest \
    --tests "com.nuitcode.daytesk.ui.main.MainScreenTest.bottomNav_allFiveTabsAreDisplayed" \
    --tests "com.nuitcode.daytesk.ui.main.MainScreenTest.quickStatsRow_allThreeCardsRender" \
    --tests "com.nuitcode.daytesk.ui.main.MainScreenTest.quickStatsRow_statNumbersDoNotClip" \
    --tests "com.nuitcode.daytesk.ui.main.MainScreenTest.quickStatsRow_cardsHaveEqualHeight" \
    --tests "com.nuitcode.daytesk.ui.main.MainScreenTest.inicioTopBar_avatarBubbleIsDisplayed" \
    --tests "com.nuitcode.daytesk.ui.main.MainScreenTest.inicioTopBar_displayNameIsDisplayed"
./gradlew assembleDebug
```

All six instrumented tests should be green. The two visual gates
above cover scenario 2 of REQ-01 and scenario 1 of REQ-03 that the
node-visibility assertions cannot prove on their own.