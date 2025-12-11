package net.nitrado.hytale.plugins.webserver.filters;

import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.auth.HytaleUserPrincipal;

import java.io.IOException;
import java.util.UUID;

public class PermissionsRequiredFilter implements Filter {

    protected String[] permissions;
    protected boolean any = false;
    static String anonymousUserName = (new UUID(0,0)).toString();

    public PermissionsRequiredFilter(String ...permissions) {
        this(false, permissions);
    }

    public PermissionsRequiredFilter(boolean any, String ...permissions) {
        this.permissions = permissions;
        this.any = any;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        var req = (HttpServletRequest) servletRequest;
        var res = (HttpServletResponse) servletResponse;

        var user = req.getUserPrincipal();

        if (user == null) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (!(user instanceof PermissionHolder holder)) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        var isAllowed = this.any ? this.hasAnyPermission(holder) : this.hasAllPermissions(holder);
        if (!isAllowed) {
            if (isAnonymousUser(holder)) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }

            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    protected boolean isAnonymousUser(PermissionHolder holder) {
        if (!(holder instanceof HytaleUserPrincipal principal)) {
            return false;
        }

        return principal.getName().equals(anonymousUserName);
    }

    protected boolean hasAllPermissions(PermissionHolder holder) {
        for (String permission : this.permissions) {
            if (!holder.hasPermission(permission)) {
                return false;
            }
        }

        return true;
    }

    protected boolean hasAnyPermission(PermissionHolder holder) {
        if (this.permissions.length == 0) {
            return true;
        }

        for (String permission : this.permissions) {
            if (holder.hasPermission(permission)) {
                return true;
            }
        }

        return false;
    }
}
