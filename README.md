# Small Wins

A public cross-platform portfolio of calm, deterministic puzzle games.

## Clients

- `web/` — the complete React 19 + TypeScript + Vite portfolio with six playable games.
- `mobile/` — Android and iOS clients built with Kotlin Multiplatform and shared Compose Multiplatform UI.

The native portfolio is intentionally narrower: its shared catalog is complete, while Burrow Network is the first fully playable mobile vertical slice. The other catalog entries remain labelled web-only until their rules and interaction models are ported properly.

## Architecture

The web client keeps pure TypeScript game rules separate from React views. The mobile client follows the same boundary with pure Kotlin rules in `shared`, common Android/iOS UI in `composeApp`, and thin platform hosts in `androidApp` and `iosApp`.

## Run

Web:

```sh
cd web
npm ci
npm run dev
```

Android: open `mobile/` in Android Studio and run `androidApp`.

iOS: open `mobile/iosApp/SmallWinsIOS.xcodeproj` in Xcode and run `SmallWinsIOS`. Xcode builds the shared framework through the checked-in Gradle wrapper.

## Current mobile scope

- Shared catalog for Purrdoku, Burrow Network, Untangle, Waypoints, Shikaku, and Habitat Search.
- Shared deterministic Burrow Network engine.
- Playable tutorial and first level on Android and iOS.
- Shared engine tests plus a thin iOS host-link test.

Internal product specifications, agent instructions, private planning material, and local automation skills are intentionally kept outside this repository.
