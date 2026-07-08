"""End-of-window reconciliation.

Replays the immutable audit trail in MongoDB, recomputes what every
participant's net position should be, and compares it against the hub's
live settlement balances. If the two disagree by even a cent, money was
created or destroyed and the run fails loudly.

Usage:
    python reconciliation.py --mongo mongodb://localhost:27017 --api http://localhost:8080
"""

import argparse
from decimal import Decimal


def compute_net_positions(settled_records):
    """Net position per participant from SETTLED audit records.

    Credits increase the position, debits decrease it. Across all
    participants the positions must sum to zero: the system is closed.
    """
    net = {}
    for record in settled_records:
        payload = record["payload"]
        amount = Decimal(str(payload["amount"]))
        debtor = payload["debtorParticipant"]
        creditor = payload["creditorParticipant"]
        net[debtor] = net.get(debtor, Decimal("0")) - amount
        net[creditor] = net.get(creditor, Decimal("0")) + amount
    return net


def reconcile(net_positions, accounts):
    """Compare replayed net positions with live balances.

    Returns a list of (participant, expected_delta, actual_delta, ok).
    """
    results = []
    for account in accounts:
        participant = account["participantId"]
        actual_delta = Decimal(str(account["balance"])) - Decimal(str(account["openingBalance"]))
        expected_delta = net_positions.get(participant, Decimal("0"))
        results.append((participant, expected_delta, actual_delta, expected_delta == actual_delta))
    return results


def main():
    parser = argparse.ArgumentParser(description="Reconcile audit trail against ledger balances")
    parser.add_argument("--mongo", default="mongodb://localhost:27017")
    parser.add_argument("--api", default="http://localhost:8080")
    args = parser.parse_args()

    from pymongo import MongoClient
    import requests

    client = MongoClient(args.mongo)
    audit = client["rtpn_audit"]["payment_audit"]
    settled = list(audit.find({"status": "SETTLED"}))
    print(f"Replaying {len(settled)} settled payments from the audit trail")

    net = compute_net_positions(settled)
    zero_sum = sum(net.values(), Decimal("0"))
    print(f"Sum of net positions across participants: {zero_sum} (must be 0)")

    accounts = requests.get(f"{args.api}/api/v1/accounts", timeout=10).json()
    results = reconcile(net, accounts)

    all_ok = zero_sum == 0
    for participant, expected, actual, ok in results:
        all_ok = all_ok and ok
        flag = "OK  " if ok else "FAIL"
        print(f"[{flag}] {participant:<12} expected delta {expected:>12}  actual delta {actual:>12}")

    if all_ok:
        print("Reconciliation PASSED: ledger and audit trail agree to the cent.")
    else:
        print("Reconciliation FAILED: ledger and audit trail have diverged.")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
