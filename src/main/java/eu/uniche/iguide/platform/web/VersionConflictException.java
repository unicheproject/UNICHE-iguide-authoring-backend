package eu.uniche.iguide.platform.web;

import org.springframework.http.HttpStatus;

/**
 * The {@code version} in the request is not the current one: someone else saved first. The body carries
 * {@code currentVersion} so the client can reload ({@code 06} section 16, {@code 07} section 15).
 */
public class VersionConflictException extends ApiException {

    private final Long currentVersion;

    public VersionConflictException(String message, Long currentVersion) {
        super(message);
        this.currentVersion = currentVersion;
    }

    public Long currentVersion() {
        return currentVersion;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String type() {
        return "VERSION_CONFLICT";
    }
}
