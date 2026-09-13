# Riot LiveStats window cursor rework — 2026-09-13

## Scope

Improve the existing global Riot LiveStats adapter without adding a duplicate Riot provider and without changing UI/Core contracts.

## Evidence

The public `live-lol-esports` project demonstrates use of Riot `feed.lolesports.com/livestats/v1/window/{gameId}` with a `startingTime` cursor aligned to a 10-second grid and a look-back delay. Laner already uses the same official endpoint, but its discovery/read cursors were generated from arbitrary wall-clock seconds and a temporary empty window reset the entire event/game binding.

The external reference project is GPL-3.0. Laner does **not** copy its source code. This change is an independent implementation of the observed Riot LiveStats protocol behavior.

## Changes

- Added `RiotLiveStatsCursor` in the App/Adapter layer.
- Align every `startingTime` candidate to Riot's 10-second frame grid.
- Start with a 60-second look-back, tighten after stable successful reads, and back off after repeated misses.
- Reuse the same aligned cursor ladder for current-game discovery and normal live reads.
- Treat an empty/currently unavailable window as `WAITING_FOR_MATCH`; keep the resolved EventDetails/gameId binding instead of resetting the full source state.
- Keep the existing no-cursor request as the last compatibility fallback.
- Core remains unchanged; `MatchIdentityPolicy` and the global provider router remain the acceptance gates.

## Boundaries

Network access remains in App/Adapter code. No Android/platform API was introduced into Core. No UI behavior or provider priority was changed.

## Validation target

- `python3 tools/check_core_boundary.py`
- `python3 tools/check_repository_links.py`
- `gradle :app:compileDebugKotlin`
- `gradle :app:assembleDebug`

The feature branch must pass the repository build gate before promotion to `main`.
