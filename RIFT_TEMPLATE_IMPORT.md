# Temporal Rift Template Import (License-Safe)

Temporal Rift now supports two arena build modes:

1. Imported template (preferred when provided)
2. Built-in procedural dome fallback (always available)

The event tries the following template IDs in order:

1. `cobblemon-events:rift/<type_id>`
2. `cobblemon-events:rift/default`

If both are missing, it generates a themed dome with vanilla blocks.

## Template path

Put your NBT templates into a datapack or mod resources at:

`data/cobblemon-events/structure/rift/<type_id>.nbt`

Optional default fallback:

`data/cobblemon-events/structure/rift/default.nbt`

## Supported type IDs

- `normal`
- `fire`
- `water`
- `electric`
- `grass`
- `ice`
- `fighting`
- `poison`
- `ground`
- `flying`
- `psychic`
- `bug`
- `rock`
- `ghost`
- `dragon`
- `dark`
- `steel`
- `fairy`
- `legendary`

## Licensing notes

- Use only assets with explicit reuse permission (for example MIT, CC0, CC-BY with attribution).
- Avoid importing ARR templates into this repository.
- If attribution is required, add credit in your server docs/changelog.
