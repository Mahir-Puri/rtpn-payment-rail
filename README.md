# RTPN — Real-Time Payments Network Simulator

![CI](https://github.com/Mahir-Puri/rtpn-payment-rail/actions/workflows/ci.yml/badge.svg)

A working model of a real-time clearing and settlement rail, in the spirit of
Canada's Real-Time Rail (RTR) and Interac e-Transfer infrastructure.
Participant financial institutions exchange ISO 20022 pacs.008 XML messages
over Kafka; a central clearing hub validates them, screens them for risk, and
settles them with finality on a double-entry ledger — exactly once, even under
redelivery and concurrency.

## Why this exists

Every payment you send between Canadian financial institutions passes through
infrastructure like this: a message rail, a clearing layer, settlement
accounts at a central hub, and a reconciliation process that proves no money
was created or destroyed. This project models that whole loop end to end,
small enough to read in an afternoon but built around the same invariants
production rails care about.

## Architecture

```
                    payments.inbound — pacs.008 XML, keyed by debtor BICFI
  Python bank simulator ──────────────────────────────────────┐
  (ALPHA_BANK, BETA_BANK,                                      ▼
   GAMMA_CU, DELTA_FIN)                          ┌──────────────────────────┐
                                                 │  Clearing Hub (Spring)   │
                                                 │                          │
                                                 │  1. Validation           │
  REST clients ────── POST /api/v1/payments ────▶│  2. Idempotency check    │
                                                 │  3. Risk (velocity)      │
                                                 │  4. RTGS settlement      │
                                                 └────┬──────────┬──────────┘
                                                      │          │
                              ┌───────────────────────┘          └──────────────┐
                              ▼                                                 ▼
                     PostgreSQL (ACID)                                  MongoDB (append-only)
                  settlement_accounts                                     payment_audit
                  ledger_entries (double-entry)                       every message + outcome
                  processed_messages (idempotency)
                  outbox_events (transactional outbox)
                              │                                                 │
                              ▼                                                 ▼
              payments.cleared / payments.rejected              Python reconciliation job
              (exactly-once via outbox poller)                  replays audit vs. balances
```

## Ask the Rail — AI Operations Copilot

A natural-language interface over the clearing hub, built as a separate service in `copilot/`.

Ask questions an on-call engineer would ask:

- _"how many payments were rejected and what were the top reasons?"_
- _"which participant is running lowest on liquidity right now?"_
- _"show me ALPHA_BANK's recent rejected payments"_

Claude plans its own tool calls against the audit trail and hub API, then
answers with cited message IDs and real numbers. The copilot has four
read-only tools: single payment lookup, audit trail search, live balance
retrieval, and Mongo aggregation stats.

**Design rule:** the AI is strictly advisory and never touches the settlement
path. There is no tool that can create, retry, reverse, or modify a payment.
Clearing stays deterministic and auditable; the LLM sits outside the path of
money movement entirely.

### Running the copilot

Requires the main stack running (postgres, mongo, kafka, hub) and an
Anthropic API key from [console.anthropic.com](https://console.anthropic.com).

```bash
cd copilot
pip3 install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...
uvicorn app:app --port 8090
```

Open **http://localhost:8090** for the demo page, or hit the API directly:

```bash
curl -s -X POST localhost:8090/ask \
  -H 'Content-Type: application/json' \
  -d '{"question": "which participant has the lowest settlement balance right now?"}'
```

See [`copilot/README.md`](copilot/README.md) for full documentation.

## Design decisions

**PostgreSQL for settlement, and why not Mongo.** Settlement is the one place
where ACID is non-negotiable: the debit, the credit, and the idempotency
record must commit atomically or not at all. Each payment settles inside a
single database transaction (real-time gross settlement — every payment is
final individually, no netting window).

**Double-entry with a cached balance.** `ledger_entries` is the append-only
source of truth; `settlement_accounts.balance` is a cached materialization
updated in the same transaction. The reconciliation job exists to prove the
two never diverge.

**Idempotency at the database level.** The primary key on
`processed_messages.message_id` is the exactly-once guarantee. Two threads
racing on the same redelivered message cannot both commit — the loser gets a
constraint violation, which the clearing layer absorbs as a silent duplicate.
Application-level "have I seen this?" checks are a fast path, not the
guarantee.

**Deadlock-free locking.** Settlement locks both account rows with
`SELECT ... FOR UPDATE`, always in lexicographic participant-id order
regardless of payment direction. A→B and B→A payments processed concurrently
can therefore never deadlock.

**Kafka partitioning by debtor.** `payments.inbound` is keyed by the debtor
participant's BICFI, so all traffic from one institution lands on one
partition and is processed in order. That makes liquidity behaviour and the
velocity risk check deterministic per debtor. Outcome topics are keyed by
messageId.

**Transactional outbox for exactly-once delivery.** Outcome events are written
to an `outbox_events` table in the same Postgres transaction as the
settlement, then a background poller delivers them to Kafka and marks them
published. A crash between commit and publish is safe — the poller retries on
restart. This closes the gap where a process death between the DB commit and
the Kafka send would silently drop an outcome event.

**ISO 20022 pacs.008 XML on the inbound rail.** The bank simulator emits
structurally valid FIToFICstmrCdtTrf envelopes; the hub parses them with
namespace-tolerant XPath extraction. Using local-name() matching instead of
namespace-prefixed XPath means the consumer is tolerant of prefix variations
across senders — a common real-world integration headache.

**MongoDB for the audit trail.** Audit records are write-once,
schema-flexible (real ISO 20022 payloads vary widely by message type), and
queried by pattern rather than joined — a document store fits. Rejections
are audited too; a rail that only remembers successes is not auditable.

**Reconciliation as a first-class feature.** `reconciliation.py` replays
every SETTLED audit record, recomputes each participant's net position,
checks the positions sum to zero (closed system), and compares against live
balances via the REST API. Any divergence fails the run.

## Running it

Requires Docker, Java 17 + Maven, Python 3.10+.

```bash
# 1. Infrastructure — Postgres seeds itself with participant accounts
docker compose up -d postgres mongo kafka

# 2. The hub
cd hub && mvn spring-boot:run

# 3. Generate pacs.008 traffic
cd participants
pip3 install -r requirements.txt
python3 bank_simulator.py --count 200 --rate 20 --duplicate-pct 5

# 4. Watch outcome events
docker compose exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payments.cleared --from-beginning

# 5. Check balances
curl -s localhost:8080/api/v1/accounts | python3 -m json.tool

# 6. Prove the books balance
python3 reconciliation.py
```

The simulator deliberately redelivers ~5% of messages. The reconciliation
still passes because duplicates settle exactly once.

### Submitting a payment over REST

```bash
curl -s -X POST localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -d '{
        "messageId": "demo-001",
        "endToEndId": "E2E-DEMO",
        "debtorParticipant": "ALPHA_BANK",
        "creditorParticipant": "GAMMA_CU",
        "amount": 125.50,
        "currency": "CAD"
      }'
# -> {"messageId":"demo-001","status":"SETTLED","reason":null}

# Send it again — exactly-once in action:
# -> {"messageId":"demo-001","status":"DUPLICATE", ...}

curl -s localhost:8080/api/v1/payments/demo-001
```

## API

| Method | Path                               | Purpose                                   |
| ------ | ---------------------------------- | ----------------------------------------- |
| POST   | `/api/v1/payments`                 | Submit a payment (same pipeline as Kafka) |
| GET    | `/api/v1/payments/{messageId}`     | Terminal status of a message              |
| GET    | `/api/v1/accounts`                 | All settlement account balances           |
| GET    | `/api/v1/accounts/{participantId}` | One participant's settlement account      |

## Statuses

`SETTLED` · `DUPLICATE` · `REJECTED_VALIDATION` · `REJECTED_RISK` ·
`REJECTED_INSUFFICIENT_FUNDS`

## Tests

```bash
cd hub && mvn test        # JUnit: validation rules, double-entry invariants,
                          # liquidity rejection, lock ordering
cd participants && pytest  # reconciliation math: zero-sum property,
                          # per-participant nets, divergence detection
cd copilot && pytest      # tool logic: query building, record shaping,
                          # limit clamping, stats aggregation
```

## Honest limitations

- One currency (CAD) and gross settlement only; no netting cycles, no FX.
- Risk screening is a single velocity rule — a seam, not a fraud engine.
- Single Kafka broker; the outbox poller provides exactly-once delivery
  but a multi-broker setup would need additional coordination.
- Active-active multi-region architecture is out of scope — the real RTR's
  coordination problem under strict latency SLAs is a different class of
  engineering entirely.

Each of these is a deliberate scope cut, and each is a natural next step.

## License

Copyright (c) 2026 Mahir Puri. All rights reserved.
