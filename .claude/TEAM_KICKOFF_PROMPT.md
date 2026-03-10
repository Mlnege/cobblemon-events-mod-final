Use agent teams for this repository with the following role split:

1) datapack-generator
- Own datapack structure/function updates for event arenas and templates.
- Keep datapack references stable and compatible with runtime calls.

2) mod-generator
- Own Kotlin/Fabric server-side implementation changes.
- Apply minimal edits and preserve existing behavior.

3) qa-build-verifier
- Run compile/build verification and regression checks before release.

4) gradle-build-engineer
- Own Gradle/dependency/source-generation stability and release artifact readiness.

5) mod-api-integrator
- Own optional external mod API integration and fallback-safe hooks.

Execution contract:
- Work in this order:
  datapack-generator -> mod-api-integrator -> mod-generator -> gradle-build-engineer -> qa-build-verifier.
- Do not add immersive-portals dependency.
- Keep ultrabeast mod id as cobblemon_ultrabeast.
- Keep Legendary AI event chance fixed at 0.25%.
- Keep event duration random 5 to 10 minutes and goal scaling at 1.5 per minute.
- Ensure Temporal Rift and Explorer/Legendary Raid behaviors follow locked project specs.
- Keep server-side-only architecture.

Final report must be in this order:
1. Changed files
2. Core logic changes
3. Verification results
4. Remaining risks
