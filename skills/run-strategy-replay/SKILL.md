---
name: run-strategy-replay
description: >-
  Use to run the full Strategy Lab loop against a running application-service: create a candidate rule
  version, edit its config, replay it over history, read the impact report, then propose it as a strategist
  and approve it as a different approver (maker-checker), and roll back. Gives the exact endpoints and demo
  users. Requires the stack up and seeded (see the README seeding section).
---

# Run a Strategy Lab replay end to end

The Strategy Lab (SPEC §10) re-runs recorded history under a candidate rule version and reports the impact
before anything ships. Governance is maker-checker: the strategist who proposes can never approve. All the
routes below are on application-service (`http://localhost:8080`, SPEC §15). The demo users (SPEC §9,
password `demo-password`) are `aditi.rao@parallax.dev` (STRATEGIST) and `vikram.nair@parallax.dev`
(APPROVER). Read `docs/SPEC.md` §10 for the report schema and the lifecycle states.

Set a base URL and reusable auth:

```bash
BASE=http://localhost:8080
STRAT='-u aditi.rao@parallax.dev:demo-password'
APPR='-u vikram.nair@parallax.dev:demo-password'
```

1. **Create a candidate** (STRATEGIST). Config defaults to a copy of LIVE; 409 if an open candidate exists.

```bash
V=$(curl -s $STRAT -X POST $BASE/api/v1/lab/versions -H 'Content-Type: application/json' \
  -d '{"note":"raise approve cutoff to 700"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["version"])')
echo "candidate $V"
```

2. **Edit its config** (STRATEGIST). Send the full RuleConfig; editing a REPLAYED config returns it to
   DRAFT. Fetch LIVE, bump `approveCutoff`, and PUT it back:

```bash
curl -s $STRAT $BASE/api/v1/lab/versions/live | python3 -c 'import sys,json;print(json.dumps(json.load(sys.stdin)["config"]))' \
  | python3 -c 'import sys,json;c=json.load(sys.stdin);c["approveCutoff"]=700;print(json.dumps(c))' \
  | curl -s $STRAT -X PUT $BASE/api/v1/lab/versions/$V/config -H 'Content-Type: application/json' --data-binary @-
```

3. **Queue the replay** (STRATEGIST) → 202 with a job id:

```bash
JOB=$(curl -s $STRAT -X POST $BASE/api/v1/lab/versions/$V/replays \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["jobId"])')
echo "job $JOB"
```

4. **Poll the job** (any INTERNAL user) until `status` is `DONE`, then read the report fields
   (approval rates, flips, expectedLossObserved, outcomeUnknown, immature, segments):

```bash
until curl -s $STRAT $BASE/api/v1/lab/replays/$JOB | grep -q '"status":"DONE"'; do sleep 1; done
curl -s $STRAT $BASE/api/v1/lab/replays/$JOB | python3 -m json.tool
```

5. **Propose** (STRATEGIST). Needs the version REPLAYED with a DONE replay of its current config hash:

```bash
curl -s $STRAT -X POST $BASE/api/v1/lab/versions/$V/propose
```

6. **Approve as a different user** (APPROVER). The proposer cannot approve — approving as the strategist
   returns 403 "Maker-checker: the proposer cannot approve"; approve as the APPROVER instead:

```bash
curl -s $APPR -X POST $BASE/api/v1/lab/versions/$V/approve
```

7. **Roll back** (APPROVER) to restore the previous LIVE version. Promote and rollback each write a
   GOVERNANCE ledger row:

```bash
curl -s $APPR -X POST $BASE/api/v1/lab/rollback
```
