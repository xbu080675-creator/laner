# Router synthetic BETWEEN_GAMES fix

## Problem
LPL fallback providers emitted synthetic game IDs such as `COMM:<bmid>:Gx` and `TJ:<bmid>:Gx` while they only knew the next expected game number. The global router treated those placeholders as authoritative `BETWEEN_GAMES`, masking Riot LiveStats handoff/diagnostics even after the game had started.

## Change
- Router rejects synthetic COMM/TJ placeholder statuses as authoritative BETWEEN_GAMES evidence.
- TJ current-game fallback reports WAITING_FOR_MATCH when the expected game object/frame is absent.
- COMM realtime reports WAITING_FOR_MATCH while the expected game route is undiscovered.
- True LIVE frames remain unchanged and still win by provider priority after identity validation.

## Boundary
No Core API or UI contract change. This is App/Adapter lifecycle arbitration only.
