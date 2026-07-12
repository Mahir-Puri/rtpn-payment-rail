"""Read-only tools the copilot can call.

Design rule for the whole AI layer: it never touches the settlement path.
Every tool here reads the audit trail or the hub's REST API. Nothing can
create, modify, or replay a payment.
"""

MAX_SEARCH_LIMIT = 50

# ---------------------------------------------------------------------------
# Pure query-building and shaping functions (unit-tested, no I/O)
# ---------------------------------------------------------------------------

STATUS_COUNTS_PIPELINE = [
    {"$group": {"_id": "$status", "count": {"$sum": 1}}},
    {"$sort": {"count": -1}},
]

REJECTION_REASONS_PIPELINE = [
    {"$match": {"status": {"$ne": "SETTLED"}}},
    {"$group": {"_id": "$reason", "count": {"$sum": 1}}},
    {"$sort": {"count": -1}},
    {"$limit": 5},
]

TOP_DEBTORS_PIPELINE = [
    {"$match": {"status": "SETTLED"}},
    {"$group": {
        "_id": "$payload.debtorParticipant",
        "totalAmount": {"$sum": "$payload.amount"},
        "count": {"$sum": 1},
    }},
    {"$sort": {"totalAmount": -1}},
    {"$limit": 5},
]


def build_search_query(status=None, participant=None):
    """Translate tool arguments into a Mongo query over the audit trail."""
    query = {}
    if status:
        query["status"] = status.upper()
    if participant:
        p = participant.upper()
        query["$or"] = [
            {"payload.debtorParticipant": p},
            {"payload.creditorParticipant": p},
        ]
    return query


def clamp_limit(limit):
    try:
        limit = int(limit)
    except (TypeError, ValueError):
        return 20
    return max(1, min(limit, MAX_SEARCH_LIMIT))


def shape_audit_record(record):
    """Strip Mongo internals; keep only what the model should see."""
    payload = record.get("payload", {}) or {}
    return {
        "messageId": record.get("messageId"),
        "status": record.get("status"),
        "reason": record.get("reason"),
        "debtorParticipant": payload.get("debtorParticipant"),
        "creditorParticipant": payload.get("creditorParticipant"),
        "amount": payload.get("amount"),
        "currency": payload.get("currency"),
        "processedAt": str(record.get("processedAt")),
    }


def shape_stats(status_counts, rejection_reasons, top_debtors):
    return {
        "messagesByStatus": {row["_id"]: row["count"] for row in status_counts},
        "topRejectionReasons": [
            {"reason": row["_id"], "count": row["count"]} for row in rejection_reasons
        ],
        "topDebtorsBySettledVolume": [
            {"participant": row["_id"], "totalAmount": row["totalAmount"], "payments": row["count"]}
            for row in top_debtors
        ],
    }


# ---------------------------------------------------------------------------
# The tool executor (I/O lives here)
# ---------------------------------------------------------------------------

class RailTools:
    """Executes tool calls against Mongo and the hub REST API. Read-only."""

    def __init__(self, mongo_uri, hub_api):
        from pymongo import MongoClient  # lazy: unit tests never need a broker
        self._audit = MongoClient(mongo_uri)["rtpn_audit"]["payment_audit"]
        self._hub_api = hub_api.rstrip("/")

    def execute(self, name, arguments):
        handlers = {
            "get_payment": self.get_payment,
            "search_payments": self.search_payments,
            "get_accounts": self.get_accounts,
            "get_rail_stats": self.get_rail_stats,
        }
        handler = handlers.get(name)
        if handler is None:
            return {"error": f"unknown tool: {name}"}
        try:
            return handler(**(arguments or {}))
        except Exception as e:  # surface the failure to the model, don't crash
            return {"error": f"{type(e).__name__}: {e}"}

    def get_payment(self, message_id):
        records = [shape_audit_record(r) for r in self._audit.find({"messageId": message_id})]
        if not records:
            return {"found": False, "messageId": message_id}
        return {"found": True, "auditRecords": records}

    def search_payments(self, status=None, participant=None, limit=20):
        query = build_search_query(status, participant)
        cursor = (self._audit.find(query)
                  .sort("processedAt", -1)
                  .limit(clamp_limit(limit)))
        return {"results": [shape_audit_record(r) for r in cursor]}

    def get_accounts(self):
        import requests
        resp = requests.get(f"{self._hub_api}/api/v1/accounts", timeout=10)
        resp.raise_for_status()
        return {"accounts": resp.json()}

    def get_rail_stats(self):
        return shape_stats(
            list(self._audit.aggregate(STATUS_COUNTS_PIPELINE)),
            list(self._audit.aggregate(REJECTION_REASONS_PIPELINE)),
            list(self._audit.aggregate(TOP_DEBTORS_PIPELINE)),
        )
