# Repository structure

**Parallax** is a Java 21 / Spring Boot credit decisioning platform (see the top-level
[`README.md`](../../README.md)). The folders below hold the planning inputs it was built from.

## `docs/build-pack/`
The prompts used to build the project. Run them **in order, one per session**,
starting with `00-start-here.md`.
- `00-start-here.md` — the entry point / overview.
- `prompts/01-foundation.md` … `prompts/23-release.md` — the numbered, self-contained
  build steps. Prompts cross-reference each other by number (e.g. "Prompt 02"), not
  by filename.

## `docs/design/`
- `brand/` — logo and icon assets for the web UI (added later): favicons, app icons,
  symbols, lockups, and wordmarks (light / dark / navy / white variants).
- `prototype.html` — the clickable prototype of the product.

## `docs/reference/`
- `README.md` — this file.

## Archived outside the repo
The original build-spec PDF has been moved out of the repo to `~/Documents/parallax-archive/`.
It used an earlier working codename for the same project, since renamed to **Parallax**.
