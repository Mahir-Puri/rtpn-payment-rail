"""Participant bank simulator.

Generates credit-transfer messages and produces them onto payments.inbound,
keyed by debtor participant so all traffic from one institution stays on one
partition (ordering guarantee). A configurable fraction of messages are
deliberate duplicates, to demonstrate that the hub settles them exactly once.

Usage:
    python bank_simulator.py --count 200 --rate 20 --duplicate-pct 5
"""

import argparse
import json
import random
import time
import uuid

PARTICIPANTS = ["ALPHA_BANK", "BETA_BANK", "GAMMA_CU", "DELTA_FIN"]


def build_payment():
    debtor, creditor = random.sample(PARTICIPANTS, 2)
    return {
        "messageId": str(uuid.uuid4()),
        "endToEndId": f"E2E-{uuid.uuid4().hex[:12].upper()}",
        "debtorParticipant": debtor,
        "creditorParticipant": creditor,
        "amount": round(random.uniform(5, 5000), 2),
        "currency": "CAD",
    }


def main():
    parser = argparse.ArgumentParser(description="Generate payment traffic onto the rail")
    parser.add_argument("--bootstrap", default="localhost:9094", help="Kafka bootstrap server")
    parser.add_argument("--topic", default="payments.inbound")
    parser.add_argument("--count", type=int, default=100, help="Total messages to send")
    parser.add_argument("--rate", type=float, default=10.0, help="Messages per second")
    parser.add_argument("--duplicate-pct", type=float, default=5.0,
                        help="Percent of sends that redeliver a previous message")
    args = parser.parse_args()

    from kafka import KafkaProducer  # imported here so tests don't need a broker

    producer = KafkaProducer(
        bootstrap_servers=args.bootstrap,
        key_serializer=lambda k: k.encode("utf-8"),
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
    )

    sent = []
    interval = 1.0 / args.rate if args.rate > 0 else 0
    duplicates = 0

    for i in range(args.count):
        if sent and random.uniform(0, 100) < args.duplicate_pct:
            payment = random.choice(sent)  # deliberate redelivery
            duplicates += 1
        else:
            payment = build_payment()
            sent.append(payment)

        producer.send(args.topic, key=payment["debtorParticipant"], value=payment)
        if interval:
            time.sleep(interval)

    producer.flush()
    print(f"Sent {args.count} messages ({len(sent)} unique, {duplicates} duplicates) "
          f"to {args.topic} via {args.bootstrap}")


if __name__ == "__main__":
    main()
