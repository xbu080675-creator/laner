# Riot persisted mirror game handoff fix

Date: 2026-09-13

## Baseline

- `main@fceba5d3356db331aedda996e435c2fd6646d469`
- User-visible reproduction: AL vs BLG, Event `117155436343202202`, G4 already in game while Laner still showed `BETWEEN GAMES · COMM · waiting G4` / `GAME COMM:13505:G4`.

## Root cause

`RiotEventDrivenLiveDataSource` refreshed `getEventDetails` through `RiotResilientHttp`. On Android networks where the direct Riot persisted gateway is unavailable, the transport legitimately falls back to RiftLab's GitHub persisted mirror. That mirror is suitable for schedule and static EventDetails identity, but its per-game `state` can be stale by minutes and must not be used as second-level live truth.

This meant the refactored state machine could still bind an older game (for example G3) even though Riot LiveStats already had a meaningful G4 window.

## Change

The Riot adapter now distinguishes the effective EventDetails source using the existing resilient transport source label:

- Direct Riot EventDetails: game `state` remains authoritative.
- RiftLab Riot Mirror EventDetails: game `state` is treated as stale; the payload contributes BO game ids/metadata only.

Mirror mode uses a monotonic handoff rule:

1. Use schedule score only as a soft starting anchor when it has advanced.
2. Never move backwards from the currently bound game.
3. Probe only higher-numbered BO game ids.
4. Promote a newer game only when `/window/{gameId}` without `startingTime` returns a meaningful Riot LiveStats frame.
5. Keep the normal 1 s live loop after promotion and 500 ms handoff cadence while waiting.

This preserves Riot as the source of the real game id and actual live frame. The mirror supplies identity only; it cannot declare a stale game live.

## Files

- `app/src/main/java/com/riftlab/app/data/RiotEventDrivenLiveDataSource.kt`

## Expected diagnostics

Direct path:

`Riot EventDriven Live · DIRECT · Gx · POLL 1s`

Mirror fallback with verified handoff:

`Riot EventDriven Live · MIRROR IDs → LiveStats verified Gx · POLL 1s`

## Verification gates

Required before merge to `main`:

- Core boundary gate
- Laner repository link gate
- Kotlin compile diagnostics
- Android APK build

Do not mark this change accepted from static review alone; final acceptance requires on-device transition testing across a real `BETWEEN_GAMES → next game LIVE` boundary.
