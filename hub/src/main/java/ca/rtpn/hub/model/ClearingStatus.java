package ca.rtpn.hub.model;

public enum ClearingStatus {
    SETTLED,
    DUPLICATE,
    REJECTED_VALIDATION,
    REJECTED_RISK,
    REJECTED_INSUFFICIENT_FUNDS
}
