# Daytesk — notas para el futuro

App Android (Kotlin/Compose) + API Ktor + Postgres en **Neon**.
La app **no** habla con Neon directo: siempre va `app → servidor → Neon`.

## Dónde vive cada cosa

| Qué | Dónde | Notas |
|---|---|---|
| Tareas, inbox, contextos | **Room en el teléfono** (`daytesk.db`) | Fuente de verdad mientras usás la app |
| Cuentas y copia en la nube | **Neon** vía Ktor | Login, register, `GET/PUT /sync` |
| API | **Render** `https://daytesk.onrender.com` | Plan free se duerme; el primer request tarda ~30–50 s |
| Secretos | Render Environment + `env/.env` local (gitignored) | Nunca commitear `.env` |

Hay **dos bases**. Crear una cuenta nueva no vacía sola el teléfono. Si Room todavía tiene tareas de otro usuario y la nube de la cuenta nueva está vacía, un `PUT /sync` **sube esas tareas a Neon**. Eso se vio como “tareas hardcodeadas” al registrarse.

Al registrar o cambiar de usuario hay que **borrar tareas/inbox locales antes de pushear**. Si el `GET /sync` falla (Render despertando) y no se limpia antes, el debounce de sync vuelve a subir basura.

## Deploy

- Publicar **solo** `:server` (Ktor). No la app Android. No Vercel (no corre JVM 24/7).
- En Render: `DATABASE_URL`, `JWT_SECRET`. `PORT` lo pone Render.
- El servidor debe leer `System.getenv`, no solo el archivo `.env` (en Docker ese archivo no existe).
- Health: `GET /health` → `{"ok":true,"database":"neon"}`. Si dice `"memory"`, no está leyendo Neon.
- `GET /` puede dar 404: **no es una web**, es API. Útil: `GET /health`, `POST /auth/login`.
- Tras cambiar Kotlin del server: **push a GitHub + Clear build cache & deploy**. Capas Docker `CACHED` = código viejo.

## App Android

- URL de producción: `AuthApi.BASE_URL = https://daytesk.onrender.com`
- APK debug: `app/build/outputs/apk/debug/app-debug.apk` (a veces se copia a Descargas).
- Usuario semilla en Neon (lo crea el server al arrancar): `ander` / `12345678` (`anderbstz@gmail.com`).
- Registro: email con `@`, usuario ≥ 3, password ≥ 8. Botón **Crear cuenta** debajo de Entrar.
- Login en tema oscuro + fondo claro: hay que setear `focusedTextColor` / `unfocusedTextColor` o el texto se ve gris.
- Render dormido a veces responde HTML de “waking up”: no parsear como JSON; reintentar.

## Sync (contrato)

- `GET /sync` con `Authorization: Bearer <token>`
- `PUT /sync` reemplaza **todo** lo de ese usuario (contextos, tareas, inbox).
- Cada fila: `cloudKey` + `updatedAt`.
- Cuenta nueva / cambio de usuario: wipe local de tareas e inbox, después pull; si la nube está vacía, push de lo local ya limpio.
- Logout: no debería dejar las tareas del usuario A para que las herede el usuario B.

## Piezas de producto ya hechas

- Editar tarea, repetición (al completar crea la siguiente).
- Recordatorios locales: 1 h antes + al vencer; en Configuración, alarmas exactas (Android 12+).
- Widget de inicio: hay que agregarlo a mano; fijar en Inicio no refresca el widget al toque.
- Perfil: las filas del menú deben ser `clickable` **antes** del `padding` o el ripple queda solo sobre el texto.
- Contextos: máximo 8; dejar al menos 1.

## Local vs nube (respuesta corta)

Sí: lo que ves en el teléfono **es local (Room)**. Neon es la copia por cuenta. Si el sync está mal, ves datos viejos del teléfono aunque la cuenta sea nueva, y esos datos pueden **quedar guardados en Neon** de esa cuenta. Borrar en la app una vez; las cuentas siguientes no deberían heredarlos si el wipe corre **antes** de cualquier push.
