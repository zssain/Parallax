# Bitbucket mirror

GitHub Actions (`.github/workflows/ci.yml`) is the source of truth for CI. Bitbucket is
kept as a push mirror so the project is visible on both, and `bitbucket-pipelines.yml`
runs the same Maven build plus the web build.

## One-time setup

1. Create an empty repository in Bitbucket (no README, no `.gitignore`), e.g.
   `your-workspace/parallax`.
2. Add it as a second remote and push everything, including tags:

   ```bash
   git remote add bitbucket git@bitbucket.org:your-workspace/parallax.git
   git push bitbucket --all
   git push bitbucket --tags
   ```

3. In Bitbucket, enable **Repository settings → Pipelines → Enable Pipelines**. The
   committed `bitbucket-pipelines.yml` will run on the next push:
   - **Build and verify** — `./mvnw -B verify` with the Docker service (Testcontainers,
     `TESTCONTAINERS_RYUK_DISABLED=true`).
   - **Web build** — `npm ci && npm run lint && npm run build` in `web/` on `node:20`.

## Keeping it in sync

Push to both remotes when you publish:

```bash
git push origin main && git push bitbucket main
git push origin --tags && git push bitbucket --tags
```

## Optional: automatic mirror from GitHub (documented, not enabled)

To mirror automatically instead of pushing by hand, add a workflow that pushes to
Bitbucket on every push to `main`, using a repository secret `BITBUCKET_URL` of the form
`https://x-token-auth:<app-password>@bitbucket.org/your-workspace/parallax.git`:

```yaml
# .github/workflows/mirror.yml  (create only if you want auto-mirroring)
name: Mirror to Bitbucket
on:
  push:
    branches: [main]
    tags: ['*']
jobs:
  mirror:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - run: |
          git push --prune "${{ secrets.BITBUCKET_URL }}" \
            "+refs/remotes/origin/*:refs/heads/*" "+refs/tags/*:refs/tags/*"
```

Leave this disabled unless you want it; manual dual-push is enough for a portfolio.
