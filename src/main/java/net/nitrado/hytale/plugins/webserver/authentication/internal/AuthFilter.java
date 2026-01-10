package net.nitrado.hytale.plugins.webserver.authentication.internal;


import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.authentication.AuthProvider;
import net.nitrado.hytale.plugins.webserver.authentication.HytaleUserPrincipal;

import java.io.IOException;
import java.util.UUID;

/**
 * Internal filter that processes authentication for incoming requests.
 * <p>
 * <strong>Internal API:</strong> This class is not intended for use by consumer plugins.
 * </p>
 */
public final class AuthFilter implements Filter {

    private final AuthProvider[] authProviders;

    public AuthFilter(AuthProvider... authProviders) {
        this.authProviders = authProviders;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        for  (AuthProvider authProvider : authProviders) {
            var result = authProvider.authenticate(req, res);

            switch (result.type()) {
                case AuthProvider.AuthResultType.NONE:
                    continue;

                case AuthProvider.AuthResultType.SUCCESS:
                    var wrapped = new UserPrincipalRequestWrapper(req, result.principal());
                    filterChain.doFilter(wrapped, response);
                    return;

                case AuthProvider.AuthResultType.FAILURE:
                    res.setStatus(401);
                    return;

                case AuthProvider.AuthResultType.CHALLENGE:
                    return;
            }
        }

        // We are not authenticated, so we map to the anonymous user
        var wrapped = new UserPrincipalRequestWrapper(req, new HytaleUserPrincipal(new UUID(0,0)));
        filterChain.doFilter(wrapped, response);

        if (res.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            for (AuthProvider authProvider : authProviders) {
                if (authProvider.challenge(req, res)) {
                    return;
                }
            }
        }
    }
}

