package dev.releaseguard;

import org.springframework.http.HttpStatus;

final class ServiceFault extends RuntimeException {
    private final HttpStatus status;

    ServiceFault(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus status() {
        return status;
    }

    static ServiceFault unavailable(String message) {
        return new ServiceFault(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    static ServiceFault conflict(String message) {
        return new ServiceFault(HttpStatus.CONFLICT, message);
    }
}
