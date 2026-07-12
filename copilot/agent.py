"""The tool-use loop: the model plans, calls read-only tools, and answers
with citations to real audit records. Advisory layer only — by construction
there is no tool that can move money."""

import json
import os

import anthropic

SYSTEM_PROMPT = """You are "Ask the Rail", an operations copilot for a real-time \
payments clearing and settlement system (RTPN).

Rules:
- Answer ONLY from tool results. Never invent payment data, balances, or statistics.
- Cite concrete evidence: message IDs, statuses, amounts, participant names.
- Participants are identified by codes like ALPHA_BANK, BETA_BANK, GAMMA_CU, DELTA_FIN.
- Amounts are CAD. Statuses: SETTLED, DUPLICATE, REJECTED_VALIDATION, REJECTED_RISK, \
REJECTED_INSUFFICIENT_FUNDS.
- You are read-only and advisory. You cannot create, modify, retry, or reverse payments. \
If asked to, explain that settlement actions are outside your scope by design.
- If the data does not answer the question, say exactly that.
- Keep answers short and operational: an on-call engineer is reading them."""

TOOLS = [
    {
        "name": "get_payment",
        "description": "Fetch the full audit history for one payment by its message ID.",
        "input_schema": {
            "type": "object",
            "properties": {
                "message_id": {"type": "string", "description": "The payment's messageId"},
            },
            "required": ["message_id"],
        },
    },
    {
        "name": "search_payments",
        "description": ("Search recent payments in the audit trail. Filter by status "
                        "(e.g. SETTLED, REJECTED_RISK) and/or participant code "
                        "(matches debtor or creditor). Returns newest first."),
        "input_schema": {
            "type": "object",
            "properties": {
                "status": {"type": "string"},
                "participant": {"type": "string"},
                "limit": {"type": "integer", "description": "Max results, capped at 50"},
            },
        },
    },
    {
        "name": "get_accounts",
        "description": "Current settlement account balances for every participant, from the hub API.",
        "input_schema": {"type": "object", "properties": {}},
    },
    {
        "name": "get_rail_stats",
        "description": ("Aggregate statistics from the audit trail: message counts by status, "
                        "top rejection reasons, top debtors by settled volume."),
        "input_schema": {"type": "object", "properties": {}},
    },
]

MAX_TURNS = 8


def run_agent(question, tool_executor):
    """Run the question through a bounded tool-use loop and return the answer."""
    client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY from the environment
    model = os.getenv("COPILOT_MODEL", "claude-sonnet-4-6")

    messages = [{"role": "user", "content": question}]

    for _ in range(MAX_TURNS):
        response = client.messages.create(
            model=model,
            max_tokens=1024,
            system=SYSTEM_PROMPT,
            tools=TOOLS,
            messages=messages,
        )

        if response.stop_reason != "tool_use":
            return "".join(b.text for b in response.content if b.type == "text")

        messages.append({"role": "assistant", "content": response.content})
        tool_results = []
        for block in response.content:
            if block.type == "tool_use":
                output = tool_executor(block.name, block.input)
                tool_results.append({
                    "type": "tool_result",
                    "tool_use_id": block.id,
                    "content": json.dumps(output, default=str),
                })
        messages.append({"role": "user", "content": tool_results})

    return "I hit my tool-call budget before finishing. Try a narrower question."
