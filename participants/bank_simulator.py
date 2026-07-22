"""Participant bank simulator — emits ISO 20022 pacs.008 XML messages.

Each message is a minimal but structurally valid FIToFICstmrCdtTrf envelope.
The namespace is urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08 (pacs.008 version 8).

Usage:
    python3 bank_simulator.py --count 200 --rate 20 --duplicate-pct 5
"""

import argparse
import random
import time
import uuid

PARTICIPANTS = ["ALPHA_BANK", "BETA_BANK", "GAMMA_CU", "DELTA_FIN"]

PACS008_NS = "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08"


def build_pacs008(message_id=None):
    """Build a minimal pacs.008 FIToFICstmrCdtTrf XML string."""
    if message_id is None:
        message_id = str(uuid.uuid4())
    debtor, creditor = random.sample(PARTICIPANTS, 2)
    amount = round(random.uniform(5, 5000), 2)
    e2e_id = f"E2E-{uuid.uuid4().hex[:12].upper()}"

    xml = f"""<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="{PACS008_NS}">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>{message_id}</MsgId>
      <CreDtTm>{time.strftime('%Y-%m-%dT%H:%M:%S')}</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>CLRG</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <EndToEndId>{e2e_id}</EndToEndId>
      </PmtId>
      <IntrBkSttlmAmt Ccy="CAD">{amount:.2f}</IntrBkSttlmAmt>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>{debtor}</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>{creditor}</BICFI>
        </FinInstnId>
      </CdtrAgt>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>"""
    return message_id, debtor, xml


def main():
    parser = argparse.ArgumentParser(description="Generate pacs.008 payment traffic")
    parser.add_argument("--bootstrap", default="localhost:9094")
    parser.add_argument("--topic", default="payments.inbound")
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--rate", type=float, default=10.0)
    parser.add_argument("--duplicate-pct", type=float, default=5.0)
    args = parser.parse_args()

    from kafka import KafkaProducer

    producer = KafkaProducer(
        bootstrap_servers=args.bootstrap,
        key_serializer=lambda k: k.encode("utf-8"),
        value_serializer=lambda v: v.encode("utf-8"),
    )

    sent = []
    interval = 1.0 / args.rate if args.rate > 0 else 0
    duplicates = 0

    for i in range(args.count):
        if sent and random.uniform(0, 100) < args.duplicate_pct:
            msg_id, debtor, xml = random.choice(sent)
            duplicates += 1
        else:
            msg_id, debtor, xml = build_pacs008()
            sent.append((msg_id, debtor, xml))

        producer.send(args.topic, key=debtor, value=xml)
        if interval:
            time.sleep(interval)

    producer.flush()
    print(f"Sent {args.count} pacs.008 messages "
          f"({len(sent)} unique, {duplicates} duplicates) "
          f"to {args.topic} via {args.bootstrap}")


if __name__ == "__main__":
    main()