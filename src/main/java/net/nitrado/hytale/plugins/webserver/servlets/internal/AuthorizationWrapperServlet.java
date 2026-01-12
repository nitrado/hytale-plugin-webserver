package net.nitrado.hytale.plugins.webserver.servlets.internal;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import net.nitrado.hytale.plugins.webserver.authentication.HytaleUserPrincipal;
import net.nitrado.hytale.plugins.webserver.authorization.RequirePermissions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class AuthorizationWrapperServlet extends HttpServlet {
    private static final Set<HttpServlet> initializedServlets = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>())
    );

    private final HttpServlet delegate;
    private final HytaleLogger logger;

    public AuthorizationWrapperServlet(HytaleLogger logger, HttpServlet delegate) {
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        if (initializedServlets.add(delegate)) {
            delegate.init(getServletConfig());
        }
    }

    @Override
    public void destroy() {
        if (initializedServlets.remove(delegate)) {
            delegate.destroy();
        }
        super.destroy();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        RequirePermissions[] rps = findPermissionAnnotations(delegate.getClass(), req.getMethod());
        if (rps != null && !checkPermissions(req, resp, rps)) {
            return;
        }

        delegate.service(req, resp);
    }

    private boolean checkPermissions(HttpServletRequest req, HttpServletResponse res, RequirePermissions[] rps) {
        if (rps.length == 0) {
            return true;
        }

        var user =  req.getUserPrincipal();

        if (user == null) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        if (!(user instanceof PermissionHolder holder)) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        var isAnonymous = isAnonymousUser(holder);


        for  (RequirePermissions rp : rps) {
            var isAllowed = rp.mode() == RequirePermissions.Mode.ANY ?
                      checkPermissionsAny(holder, rp.value())
                    : checkPermissionsAll(holder, rp.value());


            if (!isAllowed) {
                if (isAnonymous) {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                } else {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }

                return false;
            }
        }

        return true;
    }

    private boolean checkPermissionsAll(PermissionHolder holder, String[] permissions) {
        for (String permission : permissions) {
            if (!holder.hasPermission(permission)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkPermissionsAny(PermissionHolder holder, String[] permissions) {
        if (permissions.length == 0) {
            return true;
        }

        for (String permission : permissions) {
            if (holder.hasPermission(permission)) {
                return true;
            }

        }

        return false;
    }

    private boolean isAnonymousUser(PermissionHolder holder) {
        if (!(holder instanceof HytaleUserPrincipal principal)) {
            return false;
        }

        return principal.isAnonymous();
    }

    private RequirePermissions[] findPermissionAnnotations(Class<?> servletClass, String httpMethod) {
        String servletMethodName = toServletMethodName(httpMethod);
        if (servletMethodName == null) return null;

        try {
            // Only checks if the servlet author overrode the method (public/protected in class hierarchy)
            Method m = servletClass.getDeclaredMethod(servletMethodName, HttpServletRequest.class, HttpServletResponse.class);
            return m.getAnnotationsByType(RequirePermissions.class);
        } catch (NoSuchMethodException e) {
            this.logger.atSevere().withCause(e).log("no such method %s on class %s",  servletMethodName, servletClass.toString());
            return null;
        }
    }

    private static String toServletMethodName(String httpMethod) {
        return switch (httpMethod) {
            case "GET" -> "doGet";
            case "POST" -> "doPost";
            case "PUT" -> "doPut";
            case "DELETE" -> "doDelete";
            case "HEAD" -> "doHead";
            case "OPTIONS" -> "doOptions";
            case "TRACE" -> "doTrace";
            default -> null;
        };
    }
}

