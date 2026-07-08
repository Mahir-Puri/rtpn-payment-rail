from decimal import Decimal

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from reconciliation import compute_net_positions, reconcile


def audit_record(debtor, creditor, amount):
    return {
        "status": "SETTLED",
        "payload": {
            "debtorParticipant": debtor,
            "creditorParticipant": creditor,
            "amount": amount,
        },
    }


def test_net_positions_are_zero_sum():
    records = [
        audit_record("ALPHA_BANK", "BETA_BANK", 100.00),
        audit_record("BETA_BANK", "GAMMA_CU", 40.50),
        audit_record("GAMMA_CU", "ALPHA_BANK", 10.25),
    ]
    net = compute_net_positions(records)
    assert sum(net.values(), Decimal("0")) == Decimal("0")


def test_net_positions_per_participant():
    records = [
        audit_record("ALPHA_BANK", "BETA_BANK", 100.00),
        audit_record("BETA_BANK", "ALPHA_BANK", 30.00),
    ]
    net = compute_net_positions(records)
    assert net["ALPHA_BANK"] == Decimal("-70.00")
    assert net["BETA_BANK"] == Decimal("70.00")


def test_reconcile_passes_when_ledger_matches():
    net = {"ALPHA_BANK": Decimal("-70.00"), "BETA_BANK": Decimal("70.00")}
    accounts = [
        {"participantId": "ALPHA_BANK", "balance": 930.00, "openingBalance": 1000.00},
        {"participantId": "BETA_BANK", "balance": 570.00, "openingBalance": 500.00},
    ]
    results = reconcile(net, accounts)
    assert all(ok for _, _, _, ok in results)


def test_reconcile_fails_when_money_leaks():
    net = {"ALPHA_BANK": Decimal("-70.00"), "BETA_BANK": Decimal("70.00")}
    accounts = [
        {"participantId": "ALPHA_BANK", "balance": 930.00, "openingBalance": 1000.00},
        {"participantId": "BETA_BANK", "balance": 571.00, "openingBalance": 500.00},  # off by $1
    ]
    results = reconcile(net, accounts)
    failures = [p for p, _, _, ok in results if not ok]
    assert failures == ["BETA_BANK"]


def test_untouched_participant_reconciles_to_zero_delta():
    net = {}
    accounts = [{"participantId": "DELTA_FIN", "balance": 250000.00, "openingBalance": 250000.00}]
    results = reconcile(net, accounts)
    assert results[0][3] is True
