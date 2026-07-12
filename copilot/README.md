# Ask the Rail — AI operations copilot

Natural-language interface over the RTPN clearing hub. Ask questions like an
on-call engineer would — "why was payment X rejected?", "which participant is
burning through liquidity?" — and get answers grounded in real audit records
and live balances, with message IDs cited.

## Where the AI does and does not live

The copilot is a strictly advisory, **read-only** layer. It has four tools:
look up one payment's audit history, search recent payments, read settlement
balances, and pull aggregate statistics. There is deliberately no tool that
can create, retry, reverse, or modify a payment. Clearing and settlement stay
deterministic and auditable; the LLM never sits in the path of money movement.

## How it works

```
question -> FastAPI /ask -> Claude (tool-use loop, max 8 turns)
                              |-- get_payment        (Mongo audit trail)
                              |-- search_payments    (Mongo audit trail)
                              |-- get_accounts       (hub REST API)
                              '-- get_rail_stats     (Mongo aggregation pipelines)
                           -> grounded answer with cited message IDs
```

The model plans its own tool calls: "which bank has the least liquidity"
becomes a get_accounts call; "why do payments keep failing" becomes
get_rail_stats plus a targeted search_payments. Answers come only from tool
results — the system prompt forbids inventing data, and every tool result is
the model's sole source.

## Running it

Requires the main stack up (postgres, mongo, kafka, hub) and an Anthropic
API key from console.anthropic.com.

```bash
cd copilot
pip3 install -r requirements.txt

export ANTHROPIC_API_KEY=sk-ant-...      # your key
uvicorn app:app --port 8090
```

Open http://localhost:8090 for the demo page, or use the API directly:

```bash
curl -s -X POST localhost:8090/ask \
  -H 'Content-Type: application/json' \
  -d '{"question": "how many payments were rejected today and what were the top reasons?"}'
```

## Tests

```bash
pytest          # pure functions: query building, limit clamping, record shaping
```

## Configuration

| Env var             | Default                   | Purpose                       |
| ------------------- | ------------------------- | ----------------------------- |
| `ANTHROPIC_API_KEY` | (required)                | Claude API access             |
| `MONGO_URI`         | mongodb://localhost:27017 | Audit trail                   |
| `HUB_API`           | http://localhost:8080     | Hub REST API                  |
| `COPILOT_MODEL`     | claude-sonnet-4-6         | Model used for the agent loop |
