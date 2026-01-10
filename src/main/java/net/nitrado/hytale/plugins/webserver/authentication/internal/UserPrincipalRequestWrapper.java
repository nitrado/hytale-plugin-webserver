package net.nitrado.hytale.plugins.webserver.authentication.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.security.Principal;

/**
 * Internal request wrapper that attaches a user principal to the request.
 */
final class UserPrincipalRequestWrapper extends HttpServletRequestWrapper {
    private final Principal principal;

    public UserPrincipalRequestWrapper(HttpServletRequest request, Principal principal) {
        super(request);
        this.principal = principal;
    }

    @Override
    public Principal getUserPrincipal() {
        return this.principal;
    }

    @Override
    public String getAuthType() {
        return "CUSTOM";
    }
}
