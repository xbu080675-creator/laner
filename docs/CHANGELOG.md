# Laner Changelog

All notable project changes must be recorded here at release or milestone level. Per-task detail lives in `docs/development/`.

## Unreleased

### Project Foundation
- Established mandatory engineering constitution.
- Defined the project as a function-preserving, bottom-up architectural rewrite of the existing esports viewing assistant.
- Defined `PRE_MATCH / LIVE_MATCH / POST_MATCH` (赛前 / 赛中 / 赛后) as the mandatory first-level product and architecture classification axis for all business pages, primary routes, HUDs, match queries and user-facing match information.
- Defined `Feature = Persona × Match Phase × User Question` as the mandatory feature derivation model, currently covering `SPECTATOR` and `COACH_ANALYST` personas.
- Defined a shared-domain / dual-presentation model so spectator and coach/analyst views do not fork core business logic.
- Added `docs/PRODUCT_PERSPECTIVE_MATRIX.md` with pre-match, live-match and post-match user questions and derived capabilities.
- Defined `Minimalist + Esports-Cool` as Laner's UX north stars, with clarity, speed and non-interference taking precedence over decorative effects.
- Added `docs/UX_PRINCIPLES.md`, including phase-specific visual rhythm, information priority and animation constraints.
- Defined target architecture boundaries for UI, application, domain, ports, adapters, sources and persistence.
- Added development planning, implementation status, testing, compatibility and troubleshooting policies.
- Established immutable per-task development records and mandatory delivery forms.

No business functionality has been implemented yet.
