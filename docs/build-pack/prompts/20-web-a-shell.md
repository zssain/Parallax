# Prompt 20 of 23 — Web app A: scaffold, design system, API layer, marketing, login, shell

## Context

The backend is complete. Now the web app, which must look and behave **exactly like the approved prototype** at `docs/design/prototype.html` (a single HTML file whose script simulates everything in the browser). The React app keeps its markup, CSS classes, copy and interactions, and replaces the simulation with real calls to the §15 endpoints. This prompt builds the foundation and the public pages: the Vite project, the prototype's CSS verbatim, icons, a typed API layer **generated from the services' OpenAPI** (so no field is ever invented), in-memory auth with the demo roles, toasts, modals, the marketing site, the login page and the app shell (sidebar, page header, themes). Prompts 21 and 22 build the screens.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`. Red → stop.
2. Read `AGENTS.md` (invariant 9), `docs/SPEC.md` §9, §15, §16, `docs/design/UI-INVENTORY.md`, and **all of** `docs/design/prototype.html`.
3. When copy, spacing, colours or behaviour are in doubt, the prototype wins. Do not “improve” the design. Do not add a component library, Tailwind or CSS-in-JS.
4. Verify every npm package version installs; record choices in DECISIONS.md.
5. No `localStorage` or `sessionStorage` anywhere (grep proves it at the end).

## Build (`web/`)

### 1. Scaffold

`npm create vite@latest web -- --template react-ts`. Add `react-router-dom` (v6 or v7), `@tanstack/react-query` (v5), dev: `openapi-typescript`, `@playwright/test` (used in Prompt 23), ESLint from the template. Scripts: `dev`, `build`, `lint`, `preview`, `gen:api`, `e2e`.

`vite.config.ts` dev proxy (no CORS anywhere): `/api/v1/assistant` → http://localhost:8083; `/api/v1/accounts` and `/api/v1/collections` → http://localhost:8084; every other `/api` → http://localhost:8080. Allow overriding targets with env vars `VITE_APP_URL`, `VITE_ASSISTANT_URL`, `VITE_ACCOUNT_URL` (read in the config file, used by Docker in Prompt 23).

### 2. Styles and assets (verbatim)

- `src/styles/prototype.css`: copy the prototype's entire `<style>` block **unchanged**.
- `src/styles/additions.css`: only what the two §16 additions need (LIFECYCLE nav uses existing classes; add nothing unless required, and list every rule you add).
- `src/ui/icons.tsx`: the prototype's `I` object and `ic()` helper as a React `Icon` component (same 1.7 stroke, sizes). Add two icons in the same style for the new nav items: `accounts` (a card: rect + stripe) and `collections` (a phone/receiver or clipboard) — simple paths.
- `src/ui/ArchArt.tsx`: the prototype's `archArt(uid)` SVG generator ported to JSX, including the `.plx` layers with `data-d` depths.

**Brand assets.** `docs/design/brand/` holds the real logo files. Copy them into `web/public/brand/` and use them: `favicon.svg`, `favicon-32.png` and `favicon-16-16.png` as the favicons in `index.html`; `parallax-app-icon-180.png` as the apple-touch-icon; `parallax-app-icon-512.png` in a minimal `manifest.webmanifest`; and a `<title>` of “Parallax — Two views. One decision.” Keep the prototype's text wordmark “parallax.” in the nav, login and sidebar exactly as designed. Use the SVG wordmarks and lockups only where the prototype has no wordmark (README header, social preview), with the light variants on light backgrounds and the dark/white variants on navy.

### 3. API layer

- Add `springdoc-openapi-starter-webmvc-ui` to assistant-service if it lacks it (dev), so all three services expose `/v3/api-docs`. List this change.
- `npm run gen:api` runs `openapi-typescript` against `http://localhost:8080/v3/api-docs`, `:8083/v3/api-docs` and `:8084/v3/api-docs` into `src/api/generated/{application,assistant,account}.ts`. Commit the generated files. Start the stack and run it now.
- `src/api/client.ts`: `apiFetch<T>(path, {method, body, headers, auth})` adding `Authorization: Basic …` from the auth store; JSON in/out; text for the adverse-action notice; on non-2xx parse ProblemDetail into `ApiError {status, title, detail, fieldErrors}`; on 401 clear auth and navigate to `/login`.
- `src/api/hooks/`: one TanStack Query hook per §15 endpoint (queries and mutations), typed from the generated types only. Query keys in one `keys.ts`. Mutations invalidate the queries they affect (e.g. a review invalidates queue, decisions, overview, ledger).

### 4. App services

- `AuthProvider` (React context, memory only): `{username, password, me}`; `login(email, password)` calls `GET /api/v1/me`; `switchUser(email)` re-authenticates as another demo user with `demo-password`; `logout()`.
- `ToastProvider`: replicates `#toast` (bottom centre, 4.2 s, kinds `ok`, `bad`, `warn`, default), accepts limited inline markup (`<b>`) safely.
- `ModalProvider`: replicates `.modal-wrap` / `.modal`, click-outside closes unless locked (the pipeline modal locks), theme variables synced like `syncModalTheme`.
- `ThemeProvider`: light / system / dark, applied as the `dark` class on the `.app` root; system follows `prefers-color-scheme` and its change event.
- `denyToast(role)` helper: “This action requires the \<b>ROLE\</b> role. Switch user from the account menu.” Used when the API returns 403, with the role each action needs.

### 5. Marketing site (`/`) — port `#site` exactly

Sticky nav (wordmark “parallax.”, Platform / Strategy Lab / Governance scroll links, “Request a demo ↗” and “Open workspace ↗” → `/login`); hero with the 3-slide rotator (`SLIDES` content verbatim, 7 s auto-advance, arrows, 01/02/03 progress bars with the CSS animation), `ArchArt` with mouse-move parallax on `.plx` layers; the five-question check (`QZ` data, stepper, auto-advance after 380 ms, score out of 10, result labels and “Where Parallax helps” map, retake); the “Every decision has a deadline.” regulations grid with the four external links (open in a new tab, `rel="noopener"`); “Decide. Record. Replay.”; Strategy Lab teaser card; governance grid; footer “© 2026 Parallax · Portfolio project · All data is synthetic”.

### 6. Login (`/login`) — port `#login` exactly

Left panel (“Welcome back.”, four demo-account buttons from SPEC §9 — Aditi Rao strategist, Vikram Nair approver, Priya Menon underwriter, Sam Iyer auditor — with the prototype's descriptions; email field filled by the chosen button; password field prefilled `demo-password`; “Sign in ↗”). Right navy panel (“Two views. One decision.”, ArchArt frame, “Decide. Record. Replay.”). On success: navigate `/app` and toast “Signed in as {name} · {ROLE}” (ok). On 401: toast “Sign-in failed — check the email and password” (bad). Empty email: “Enter an email address”.

### 7. App shell (`/app/*`, guarded: no auth → `/login`)

- `Sidebar` = the prototype's `renderSide()`: wordmark, collapse button (`.collapsed`), sections WORKSPACE (Overview, New application, Decisions, Review queue), STRATEGY (Strategy Lab, Drift monitor), GOVERNANCE (Decision ledger, Assistant, System), plus **LIFECYCLE** (Accounts, Collections). Active item styling (Decision detail highlights Decisions). Badges: review queue length (from `GET /api/v1/reviews/queue`, refetched every 30 s) and “1” on Strategy Lab when any version is PROPOSED. User card: avatar initial, name, role; sign-out icon → `/`; “Switch user: …” select (toast “Now acting as {name} · {ROLE}”); theme toggle.
- `PageHeader` = `head(eyebrow, title, description, right)`. `LiveChips` = “Live data” or “Bureau outage” (from `GET /api/v1/system/status` bureauCircuit OPEN, refetched every 15 s) + “Rules vX” (live version).
- Shared components used by every screen: `Kpi`, `OutcomePill` (`.oc`), `StatusChip` (`.st`), `Card`, `DecisionTable` (the prototype's `decTable` columns: Application, Applicant, Product, Score, Outcome, Limit, Rules, Recorded; override / re-decided pills), `money`, `pct`, `fmt` helpers identical to the prototype's.
- Placeholder route components for every screen of §16 rendering only their `PageHeader` (Prompts 21–22 fill them).

## Checks

- `npm run build` and `npm run lint` pass.
- Side-by-side check: run `npm run dev` and open the prototype file; compare the marketing site, login and the empty shell at 1440 px and 1100 px wide. Take Playwright screenshots of both (`docs/screenshots/compare/`) and list any visible difference you could not remove.
- `grep -rn "localStorage\|sessionStorage" web/src` prints nothing.

## Definition of Done (real output)

1. Build and lint output; the `gen:api` output file sizes; the grep result.
2. With the stack running: sign in as each demo user and paste the toast text you saw (from a Playwright script or your own run).
3. Tick 20. Commit `PX-20: web scaffold, prototype design system, typed API layer, marketing, login, shell`. REPORT, including the list of CSS rules added in additions.css.

## Do not

Build screen content (Prompts 21–22), change prototype CSS values, or hand-write API types.
