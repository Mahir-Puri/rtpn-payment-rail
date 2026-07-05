package ca.rtpn.hub.model;

public record ClearingResult(ClearingStatus status, String reason) {

    public static ClearingResult settled() {
        return new ClearingResult(ClearingStatus.SETTLED, null);
    }

    public static ClearingResult of(ClearingStatus status, String reason) {
        return new ClearingResult(status, reason);
    }
}
