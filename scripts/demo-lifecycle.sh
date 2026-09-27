#!/usr/bin/env bash
#
# Parallax account-lifecycle demo. Run after `docker compose up --build` (the seeder
# opens accounts from the approved demo applications via the outbox).
#
#   ./scripts/demo-lifecycle.sh
#
# It waits until at least two accounts exist, then on the first account simulates six
# on-time months and requests a credit-line increase of (limit + $1,000); on the
# second it simulates two months with a missed payment and prints the collections
# queue. It only ever adds months, so it is safe to re-run.
#
# Config via env: ACCOUNT_URL (default http://localhost:8084), DEMO_PASSWORD
# (default demo-password). Requires curl and python3.
set -eu

ACCOUNT_URL="${ACCOUNT_URL:-http://localhost:8084}"
USER="priya.menon@parallax.dev"
PASS="${DEMO_PASSWORD:-demo-password}"
AUTH="${USER}:${PASS}"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

# GET/POST helpers (basic auth, JSON).
api_get()  { curl -fsS -u "$AUTH" "$ACCOUNT_URL$1"; }
api_post() { curl -fsS -u "$AUTH" -H 'Content-Type: application/json' -X POST -d "$2" "$ACCOUNT_URL$1"; }

# --- 1) Wait for at least two accounts ------------------------------------------------
say "Waiting for accounts to be opened by the outbox (up to ~90s)..."
ids=""
for i in $(seq 1 45); do
  body="$(api_get /api/v1/accounts || true)"
  if [ -n "$body" ]; then
    ids="$(printf '%s' "$body" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(" ".join(a["accountId"] for a in d.get("items",[])))' 2>/dev/null || true)"
    count="$(printf '%s' "$ids" | wc -w | tr -d ' ')"
    if [ "${count:-0}" -ge 2 ]; then break; fi
  fi
  sleep 2
done

set -- $ids
if [ "$#" -lt 2 ]; then
  echo "Fewer than two accounts after waiting. Is the stack up and seeded? (docker compose ps)" >&2
  exit 1
fi
FIRST="$1"; SECOND="$2"
say "Using accounts: $FIRST (healthy path) and $SECOND (delinquency path)"

limit_of() { api_get "/api/v1/accounts/$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["account"]["creditLimit"])'; }

# --- 2) First account: six on-time months, then a credit-line increase ----------------
say "[$FIRST] Simulating 6 on-time months..."
for m in 1 2 3 4 5 6; do
  api_post "/api/v1/accounts/$FIRST/simulate-month" \
    '{"purchasesCents":30000,"paymentCents":30000,"payOnTime":true}' >/dev/null
  printf '  month %s posted (paid on time)\n' "$m"
done

CUR="$(limit_of "$FIRST")"
REQ=$((CUR + 1000))
say "[$FIRST] Requesting a credit-line increase: current \$$CUR -> requested \$$REQ"
api_post "/api/v1/accounts/$FIRST/cli-requests" \
  "{\"requestedLimit\":$REQ,\"acceptCounterOffer\":true}" \
  | python3 -m json.tool

# --- 3) Second account: two missed months, then the collections queue -----------------
say "[$SECOND] Simulating 2 months with a MISSED payment..."
for m in 1 2; do
  api_post "/api/v1/accounts/$SECOND/simulate-month" \
    '{"purchasesCents":40000,"paymentCents":0,"payOnTime":false}' >/dev/null
  printf '  month %s posted (payment missed)\n' "$m"
done

say "Collections summary (buckets by days past due):"
api_get /api/v1/collections/summary | python3 -m json.tool

say "Collections queue:"
api_get /api/v1/collections | python3 -m json.tool

say "Done. Open http://localhost:5173 -> Accounts and Collections to see the results."
