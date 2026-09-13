# Riot next-game handoff fix — 2026-09-13

## Task
- ID: RIOT-LIVE-G3-HANDOFF
- Executor: OpenAI / ChatGPT
- Baseline: `main@9d97dacb7a838df8afdbef87fe8ec13cdbb73a92`

## User-visible failure
During AL vs BLG, Riot public Event `117155436343202202` had Game 3 in progress while Laner remained `BETWEEN GAMES · COMM · waiting G3`. The app had the correct series/event identity but did not hand off to the Riot G3 LiveStats game id quickly enough.

## Root cause
Laner preferred state-driven discovery and historical-window probing. EventDetails already contains all BO game ids, but the next game can still be marked `unstarted` while the previous game's historical LiveStats stays readable. That makes handoff depend on probing the first frame of the next game and can keep the old binding too long.

## Fix
- Derive expected next game as `sum(gameWins) + 1` from the current series target.
- Bind that EventDetails Riot game id before historical-window fallback, even if state is still `unstarted`.
- Keep the game id through temporary empty LiveStats windows and wait for its first valid frame.
- Prefer the highest `InProgress` game when Riot state has already advanced.
- Exclude completed/unneeded/cancelled games from historical current-game discovery.

## Scope / boundaries
App/Adapter Riot live selection only. Core, UI, provider priority, MatchIdentityPolicy, and external API contracts are unchanged. No GPL source copied.

## Validation
- `python3 tools/check_core_boundary.py`
- `python3 tools/check_repository_links.py`
- `gradle :app:compileDebugKotlin`
- `gradle :app:assembleDebug`

Real-device acceptance is still required on a live between-games -> next-game transition.

## Rollback
Revert the final implementation commit.

## Compliance
The implementation remains in Adapter code and preserves the Core boundary.
