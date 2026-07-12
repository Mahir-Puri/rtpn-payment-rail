import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from tools import build_search_query, clamp_limit, shape_audit_record, shape_stats


def test_build_search_query_empty():
    assert build_search_query() == {}


def test_build_search_query_status_is_uppercased():
    assert build_search_query(status="settled") == {"status": "SETTLED"}


def test_build_search_query_participant_matches_either_side():
    q = build_search_query(participant="alpha_bank")
    assert q == {"$or": [
        {"payload.debtorParticipant": "ALPHA_BANK"},
        {"payload.creditorParticipant": "ALPHA_BANK"},
    ]}


def test_build_search_query_combined():
    q = build_search_query(status="REJECTED_RISK", participant="BETA_BANK")
    assert q["status"] == "REJECTED_RISK"
    assert "$or" in q


def test_clamp_limit_bounds_and_garbage():
    assert clamp_limit(20) == 20
    assert clamp_limit(9999) == 50
    assert clamp_limit(0) == 1
    assert clamp_limit("not a number") == 20
    assert clamp_limit(None) == 20


def test_shape_audit_record_strips_mongo_internals():
    record = {
        "_id": "should-not-appear",
        "messageId": "MSG-1",
        "status": "SETTLED",
        "reason": None,
        "processedAt": "2026-07-11T10:00:00Z",
        "payload": {
            "debtorParticipant": "ALPHA_BANK",
            "creditorParticipant": "BETA_BANK",
            "amount": 100.0,
            "currency": "CAD",
        },
    }
    shaped = shape_audit_record(record)
    assert "_id" not in shaped
    assert shaped["messageId"] == "MSG-1"
    assert shaped["debtorParticipant"] == "ALPHA_BANK"
    assert shaped["amount"] == 100.0


def test_shape_audit_record_handles_missing_payload():
    shaped = shape_audit_record({"messageId": "MSG-2", "status": "REJECTED_VALIDATION"})
    assert shaped["messageId"] == "MSG-2"
    assert shaped["debtorParticipant"] is None


def test_shape_stats():
    stats = shape_stats(
        status_counts=[{"_id": "SETTLED", "count": 101}, {"_id": "REJECTED_RISK", "count": 7}],
        rejection_reasons=[{"_id": "velocity limit exceeded", "count": 7}],
        top_debtors=[{"_id": "ALPHA_BANK", "totalAmount": 12736.42, "count": 40}],
    )
    assert stats["messagesByStatus"]["SETTLED"] == 101
    assert stats["topRejectionReasons"][0]["reason"] == "velocity limit exceeded"
    assert stats["topDebtorsBySettledVolume"][0]["participant"] == "ALPHA_BANK"
