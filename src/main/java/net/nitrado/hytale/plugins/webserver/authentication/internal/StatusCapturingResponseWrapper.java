package net.nitrado.hytale.plugins.webserver.authentication.internal;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;

/**
 * Response wrapper that captures the status code and prevents the response
 * from being committed when a 401 status is set, allowing the auth filter
 * to perform a challenge redirect instead.
 */
final class StatusCapturingResponseWrapper extends HttpServletResponseWrapper {

    private int statusCode = SC_OK;
    private boolean statusCaptured = false;

    public StatusCapturingResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setStatus(int sc) {
        this.statusCode = sc;
        this.statusCaptured = true;
        // Don't call super for 401 - we'll handle it in the filter
        if (sc != SC_UNAUTHORIZED) {
            super.setStatus(sc);
        }
    }

    @Override
    public void sendError(int sc) throws IOException {
        this.statusCode = sc;
        this.statusCaptured = true;
        // Don't call super for 401 - we'll handle it in the filter
        if (sc != SC_UNAUTHORIZED) {
            super.sendError(sc);
        }
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        this.statusCode = sc;
        this.statusCaptured = true;
        // Don't call super for 401 - we'll handle it in the filter
        if (sc != SC_UNAUTHORIZED) {
            super.sendError(sc, msg);
        }
    }

    @Override
    public int getStatus() {
        return this.statusCode;
    }

    public boolean isUnauthorized() {
        return this.statusCaptured && this.statusCode == SC_UNAUTHORIZED;
    }

    /**
     * Commits the 401 status to the underlying response if no challenge was performed.
     */
    public void commitUnauthorized() throws IOException {
        if (isUnauthorized() && !isCommitted()) {
            super.sendError(SC_UNAUTHORIZED);
        }
    }
}

