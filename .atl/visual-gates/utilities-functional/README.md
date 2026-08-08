# Visual Gate — utilities-functional (final PR3)

> **Pattern**: skip-verify (#95) — this Windows host has no Java/Android SDK.
> `./gradlew test` and `connectedDebugAndroidTest` are BLOCKED at execution.
> The 4 manual checks below are the runtime proof that the full
> utilities-functional change (OCR + audio transcription + video transcription
> + visual gate) is wired end-to-end.
>
> **Target**: Pixel API 36 emulator (or any API 33+ device) with the
> `feat/utilities-pr3` build (or any stack of `feat/utilities-pr3` →
> `feat/utilities-pr2` → `feat/utilities-pr1` → `feature/inicio-screen` after
> integration). Permissions must be grantable on the device/emulator.

## How to run

```bash
# 1. Pick up the chained stack on the integration branch
git switch feature/inicio-screen
git merge --no-ff feat/utilities-pr1
git merge --no-ff feat/utilities-pr2
git merge --no-ff feat/utilities-pr3

# 2. Build and install on a Pixel API 36 emulator
./gradlew installDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Launch the app, navigate to the Utilidades tab, and run the 4 checks below.
```

> **Do not skip the `INTERNET` permission.** PR3 added
> `<uses-permission android:name="android.permission.INTERNET" />` to the
> manifest so ML Kit Speech can download its model on first run and benefit
> from Google services for best results. Without it the audio + video tools
> fail silently with `ERROR_NETWORK`.

---

## Check 1 — Image OCR works on a real-world photo

**Goal**: prove the image → text round-trip end-to-end (REQ-01 / REQ-02).

1. Tap the **Utilidades** tab.
2. Tap **Convertir imágenes**.
3. Grant the `READ_MEDIA_IMAGES` permission when prompted.
4. Pick a real photo (a billboard, a handwritten note, or a printed page).
5. **Expected**: a progress overlay appears briefly, then a `ResultCard` shows
   the extracted text. The Copy and Share buttons are present and functional.
6. Tap **Copiar** → clipboard receives the text; **Compartir** → system chooser
   appears with `text/plain`.

**Pass criteria**: text is readable, copy + share work, no crash. The first
run may add ~5 s of model-download latency (indeterminate progress bar);
subsequent runs are sub-second.

---

## Check 2 — Audio transcription works on a clear voice recording

**Goal**: prove the audio file → text round-trip and the network disclosure
(REQ-03 / REQ-06).

1. Tap **Utilidades** → **Transcribir audio**.
2. Confirm the network disclosure banner is visible **above** the start button.
   It must read (paraphrased): "La transcripción usa el reconocimiento de voz
   en tu dispositivo. Requiere conexión para descargar el modelo la primera
   vez y para mejores resultados. Tu audio NO se envía a la nube."
3. Grant `READ_MEDIA_AUDIO` and `RECORD_AUDIO` when prompted.
4. Tap **Grabar audio**, speak for at least 5 s, tap again to stop.
5. **Expected**: progress overlay shows during transcription; the `ResultCard`
   displays the recognized text; copy + share work.

**Pass criteria**: transcribed text matches the spoken words (within ML Kit's
es-AR accuracy), disclosure is visible, no crash. The first run may add
~10 s of model-download latency.

---

## Check 3 — Video transcription works on a short MP4

**Goal**: prove the video → audio → text pipeline (REQ-05 / REQ-08 / REQ-09).

1. Tap **Utilidades** → **Video a texto/audio**.
2. Confirm the network disclosure banner is visible above the start button.
3. Grant `READ_MEDIA_VIDEO` when prompted.
4. Pick a short MP4 (5–30 s) with clear speech (no music).
5. **Expected**: a determinate progress bar advances through the extraction
   phase (0–50%) and then the transcription phase (50–100%); the `ResultCard`
   shows the recognized text; copy + share work; the **Cancelar** button
   cancels mid-flight and transitions the state to "Transcripción cancelada".
6. Tap **Cancelar** mid-flight on a second run → worker transitions to
   `CANCELLED`; the overlay closes; the state surfaces in the UI.

**Pass criteria**: final transcribed text matches the spoken words, cancel
works, the progress bar is determinate, no crash.

---

## Check 4 — Network disclosure is visible on audio + video screens

**Goal**: prove REQ-06 — every tool that uses ML Kit Speech discloses the
network requirement (no false "100% offline" claim).

1. Open **Utilidades** → **Transcribir audio**. Confirm the disclosure banner
   is visible above the start button.
2. Back out, open **Utilidades** → **Video a texto/audio**. Confirm the same
   disclosure banner is visible.
3. **Expected**: both screens show the disclosure; both show the small
   `Info` icon to the left of the text.

**Pass criteria**: the banner is present on both screens, the disclosure text
includes the phrases "Requiere conexión" and "Tu audio NO se envía a la nube".

---

## Rollback

If any check fails:

```bash
git switch feature/inicio-screen
git branch -D feat/utilities-pr3     # local only; never pushed
```

The 3-PR chain can be re-stacked on a fresh base. No persistent data is lost
(transcripts live only in memory per REQ-12).

---

## Related artifacts

- **Proposal**: engram `#115` — `sdd/utilities-functional/proposal`
- **Spec**: engram `#117` — `sdd/utilities-functional/spec`
- **Design**: engram `#118` — `sdd/utilities-functional/design`
- **Tasks**: engram `#119` — `sdd/utilities-functional/tasks`
- **PR1 apply-progress**: engram `#120` — `sdd/utilities-pr1/apply-progress`
- **PR1 archive-report**: engram `#122` — `sdd/utilities-pr1/archive-report`
- **PR2 apply-progress**: engram `#124` — `sdd/utilities-pr2/apply-progress`
- **PR2 archive-report**: engram `#125` — `sdd/utilities-pr2/archive-report`
- **PR3 apply-progress**: engram `#126` (assigned on save) —
  `sdd/utilities-pr3/apply-progress`
- **Skip-verify pattern**: engram `#95` — `workflow/skip-verify-daytesk`
- **Stack decision**: engram `#116` — `decision/utilities-stack-mlkit`
