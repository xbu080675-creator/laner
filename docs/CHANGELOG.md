# Laner Changelog

All notable project changes must be recorded here at release or milestone level. Per-task detail lives in `docs/development/`.

## Unreleased

### Project Foundation
- Established mandatory engineering constitution.
- Defined the project as a function-preserving, bottom-up architectural rewrite of the existing esports viewing assistant.
- Defined `PRE_MATCH / LIVE_MATCH / POST_MATCH` as the mandatory first-level product and architecture classification axis.
- Defined `Feature = Persona × Match Phase × User Question` as the feature derivation model for `SPECTATOR` and `COACH_ANALYST`.
- Defined shared-domain / dual-presentation behavior for spectator and coach/analyst views.
- Defined `Minimalist + Esports-Cool` as Laner's UX north stars, with clarity, speed and non-interference taking precedence over decorative effects.
- Defined four source classes: `PRE_MATCH_SOURCE`, `LIVE_MATCH_SOURCE`, `POST_MATCH_SOURCE`, `GLOBAL_AI_ASSIST`.
- Established the rule that the first three source classes provide/verify esports facts while `GLOBAL_AI_ASSIST` is non-authoritative and may only explain, summarize, infer or assist.
- Added source provenance, authority/freshness separation, provisional publishing and revision requirements.
- Replaced region-silo architecture with a global competition management model: Region is now a domain attribute/filter dimension rather than an independent business boundary.
- Added global Competition and Identity domain requirements so Team / Player / Match / Competition identities survive cross-region transfer, international events, renames and provider-specific IDs.
- Established that region-specific Providers may exist only behind unified Source Adapter / Normalization / Identity / Competition pipelines.
- Added `docs/PRODUCT_PERSPECTIVE_MATRIX.md`, `docs/UX_PRINCIPLES.md`, and `docs/SOURCE_ARCHITECTURE.md`.
- Defined target architecture boundaries for UI, application, domain, ports, adapters, sources and persistence.
- Added development planning, implementation status, testing, compatibility and troubleshooting policies.
- Established immutable per-task development records and mandatory delivery forms.

No business functionality has been implemented yet.
