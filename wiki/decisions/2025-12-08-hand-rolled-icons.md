# Hand-written `ImageVector` icons instead of Material icons

**Status:** Active · **Date:** 2025-12-08 · **Commits:** `d0d171a` (Settings), `dd9c1fd` (ArrowBack, Edit)

## Context
`ListScreen` imported `androidx.compose.material.icons.Icons` / `filled.Settings`. The project never declared a
`material-icons-*` dependency; the icons came in transitively through `material3` under Compose BOM
`2024.09.00`. The library upgrade in `d0d171a` (BOM → `2025.12.00`) is titled "updated libraries **fixed build
errors**", and it removed those imports.

## Options considered
- Add `androidx.compose.material:material-icons-core` / `-extended` explicitly.
- Copy only the needed icons as `ImageVector` builders into the project.

## Decision
`app/src/main/java/com/bdaniel/loyscan/ui/theme/icons/Icons.kt` defines `Icons.Default.{Settings, ArrowBack, Edit}`
as lazily built `ImageVector`s (960×960 viewport — Material Symbols path data). Screens import
`com.bdaniel.loyscan.ui.theme.icons.Icons`, which mirrors the Material API shape so call sites look the same.

## Why
Not recorded (inferred from the diff): newer `material3` versions no longer depend on `material-icons-core`, so
the import broke on the BOM bump. `material-icons-extended` is large and deprecated in favor of Material
Symbols, and three icons don't justify a dependency.

## Consequences
- New icons: add another lazy property to `Icons.Default` (copy the path data from Material Symbols / Android
  Studio's Vector Asset tool). Don't add the extended icons artifact casually.
- The name `Icons` clashes with Material's `Icons` if both are imported — import only ours.
