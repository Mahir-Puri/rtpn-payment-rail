package ca.rtpn.hub.kafka;

import ca.rtpn.hub.model.PaymentMessage;
import ca.rtpn.hub.service.ClearingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;

/**
 * Consumes pacs.008 (FIToFICstmrCdtTrf) XML messages from payments.inbound.
 * XPath extraction is namespace-aware but uses local-name() matching so the
 * consumer is tolerant of namespace prefix variations across senders — a
 * common headache in real ISO 20022 integrations. Unparseable messages are
 * logged and skipped; they carry no valid messageId to settle or reject on.
 */
@Service
public class PaymentInboundConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentInboundConsumer.class);

    private final ClearingService clearingService;
    private final DocumentBuilderFactory dbf;
    private final XPathFactory xpf;

    public PaymentInboundConsumer(ClearingService clearingService) {
        this.clearingService = clearingService;
        this.dbf = DocumentBuilderFactory.newInstance();
        this.dbf.setNamespaceAware(true);
        this.xpf = XPathFactory.newInstance();
    }

    @KafkaListener(topics = "${rtpn.topics.inbound}")
    public void onMessage(String xml) {
        PaymentMessage msg;
        try {
            msg = parsePacs008(xml);
        } catch (Exception e) {
            log.warn("Discarding unparseable pacs.008 message: {}", e.getMessage());
            return;
        }
        clearingService.process(msg);
    }

    /**
     * Parses a pacs.008 XML envelope into our internal PaymentMessage model.
     *
     * Relevant pacs.008 paths (simplified):
     * MsgId -> GrpHdr/MsgId
     * EndToEndId -> CdtTrfTxInf/PmtId/EndToEndId
     * Debtor -> CdtTrfTxInf/DbtrAgt/FinInstnId/BICFI (we use as participant id)
     * Creditor -> CdtTrfTxInf/CdtrAgt/FinInstnId/BICFI
     * Amount -> CdtTrfTxInf/IntrBkSttlmAmt
     * Currency -> CdtTrfTxInf/IntrBkSttlmAmt/@Ccy
     */
    private PaymentMessage parsePacs008(String xml) throws Exception {
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(xml)));
        XPath xpath = xpf.newXPath();

        String msgId = xpathText(xpath, doc, "//*[local-name()='MsgId']");
        String e2eId = xpathText(xpath, doc, "//*[local-name()='EndToEndId']");
        String debtor = xpathText(xpath, doc,
                "//*[local-name()='DbtrAgt']//*[local-name()='BICFI']");
        String creditor = xpathText(xpath, doc,
                "//*[local-name()='CdtrAgt']//*[local-name()='BICFI']");

        NodeList amtNode = (NodeList) xpath.evaluate(
                "//*[local-name()='IntrBkSttlmAmt']", doc, XPathConstants.NODESET);
        if (amtNode.getLength() == 0)
            throw new IllegalArgumentException("missing IntrBkSttlmAmt");

        String amtText = amtNode.item(0).getTextContent().trim();
        String currency = amtNode.item(0).getAttributes()
                .getNamedItem("Ccy") != null
                        ? amtNode.item(0).getAttributes().getNamedItem("Ccy").getTextContent()
                        : "CAD";

        return new PaymentMessage(msgId, e2eId, debtor, creditor,
                new BigDecimal(amtText), currency);
    }

    private String xpathText(XPath xpath, Document doc, String expr) throws Exception {
        String val = xpath.evaluate(expr, doc);
        if (val == null || val.isBlank())
            throw new IllegalArgumentException("missing field at: " + expr);
        return val.trim();
    }
}