# Emulador Nintendo DS

Emulador embebible para [MiniJuegos](https://github.com/AlejandroHP17/MiniJuegos). Vive en su propio repo: library publicable (AAR) y app shell para probarlo aparte.

La app no incluye ROMs. El jugador aporta su archivo `.nds`.

## Artefacto Maven

| Coordenada | Valor |
|------------|--------|
| Group | `pelkidev.com.mx.minijuegos` |
| Artifact | `emuladornds` |
| Versión | `1.0.0-SNAPSHOT` |

Depende de: `pelkidev.com.mx.minijuegos:sdk:1.0.0`

| Campo | Valor |
|-------|--------|
| `id` | `emuladornds` |
| Título | Emulador Nintendo DS |
| Descripción | Juega un archivo .nds en el dispositivo |

## Módulos

| Módulo | Tipo | Rol |
|--------|------|-----|
| `:emuladornds` | `android-library` | `EmuladorNdsGameModule` → AAR |
| `:app` | `android-application` | Shell de prueba (`onExit = ::finish`) |

## Núcleo melonDS

La emulación la hace [melonDS](https://github.com/melonDS-emu/melonDS) `1.0`, compilado con el NDK dentro del AAR (`libemuladornds_melonds.so`). `CMakeLists.txt` lo descarga al compilar y comprueba su SHA-256. La primera compilación necesita red.

- ABIs publicadas: `arm64-v8a` y `x86_64`.
- Requisitos locales: Android SDK con NDK `30.0.16248370` y CMake `4.1.2`.

## Publicar AAR

Maven Local (iteración sin esperar CI):

```bash
./gradlew :emuladornds:publishToMavenLocal
```

GitHub Packages (repo `AlejandroHP17/EmuladorNds`): push a `main` (o *Run workflow*) publica `1.0.0-SNAPSHOT`. Hace falta el secret `GH_PACKAGES_TOKEN` (PAT classic con `read:packages` y `write:packages`) en Settings → Secrets → Actions. En un pull request el workflow compila y testa, y no publica.

El host ya apunta a `https://maven.pkg.github.com/AlejandroHP17/EmuladorNds` y reconsulta SNAPSHOT en cada build (`cacheChangingModulesFor(0)` y `--refresh-dependencies` en el CI de MiniJuegos). Tras un push a `main` de este repo, el siguiente compile de MiniJuegos baja el AAR nuevo.

## Integración en el host

Cableado en `MiniJuegos/`:

1. Maven repo `GitHubPackagesEmuladorNds`; `mavenLocal()` como fallback.
2. `implementation("pelkidev.com.mx.minijuegos:emuladornds:1.0.0-SNAPSHOT")`.
3. `EmuladorNdsGameModule` en `GameCatalog.games`.
4. Icono `ic_game_emuladornds` y rama en `GameVisuals.forId("emuladornds")`.

## Credenciales locales

En `~/.gradle/gradle.properties` (no subir al repo):

```properties
gpr.user=AlejandroHP17
gpr.token=ghp_xxx
```
