package net.nitrado.hytale.plugins.webserver.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * A servlet that serves static files (CSS, JavaScript, images, etc.) from a specified directory.
 * <p>
 * Example usage:
 * <pre>
 * webServerPlugin
 *     .addServlet(this, new StaticFileServlet(Path.of("plugins/MyPlugin/static")), "/static/*")
 * </pre>
 */
public final class StaticFileServlet extends HttpServlet {

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            // Text
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "application/javascript"),
            Map.entry("mjs", "application/javascript"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("txt", "text/plain"),
            // Images
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("webp", "image/webp"),
            // Fonts
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("eot", "application/vnd.ms-fontobject"),
            // Other
            Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"),
            Map.entry("map", "application/json")
    );

    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final int BUFFER_SIZE = 8192;

    private final Path baseDirectory;
    private final String classpathBase;
    private final ClassLoader classLoader;

    /**
     * Creates a new StaticFileServlet serving files from the specified directory.
     *
     * @param baseDirectory the directory to serve files from
     */
    public StaticFileServlet(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        this.classpathBase = null;
        this.classLoader = null;
    }

    /**
     * Creates a new StaticFileServlet serving files from the classpath (e.g., resources baked into a JAR).
     *
     * @param classpathBase the base path within the classpath (e.g., "public" or "static/assets")
     * @param classLoader   the class loader to use for loading resources (typically your plugin's class loader)
     */
    public StaticFileServlet(String classpathBase, ClassLoader classLoader) {
        this(null,  classpathBase, classLoader);
    }

    public StaticFileServlet(Path baseDirectory, String classpathBase, ClassLoader classLoader) {
        if (baseDirectory != null) {
            this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        } else {
            this.baseDirectory = null;
        }

        // Normalize: remove leading/trailing slashes
        String normalized = classpathBase;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.classpathBase = normalized;
        this.classLoader = classLoader;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty()) {
            pathInfo = "/";
        }

        if (baseDirectory != null && serveFromFilesystem(pathInfo, resp)) {
            return;
        }

        if (classpathBase != null && serveFromClasspath(pathInfo, resp)) {
            return;
        }

        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private boolean serveFromClasspath(String pathInfo, HttpServletResponse resp) throws IOException {
        // Security check: prevent path traversal
        if (pathInfo.contains("..")) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return true;
        }

        // Build resource path
        String resourcePath = classpathBase + pathInfo;
        // Normalize double slashes
        resourcePath = resourcePath.replaceAll("/+", "/");

        URL resourceUrl = classLoader.getResource(resourcePath);
        if (resourceUrl == null) {
            return false;
        }

        // Determine content type from path
        String contentType = getContentType(pathInfo);
        resp.setContentType(contentType);

        // Try to get content length
        try {
            URLConnection connection = resourceUrl.openConnection();
            int contentLength = connection.getContentLength();
            if (contentLength >= 0) {
                resp.setContentLength(contentLength);
            }
        } catch (IOException e) {
            // Ignore - content length will just not be set
        }

        // Set caching headers
        setCacheHeaders(resp, pathInfo);

        // Stream the resource to the response
        try (InputStream in = classLoader.getResourceAsStream(resourcePath);
             OutputStream out = resp.getOutputStream()) {
            if (in == null) {
                return false;
            }
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return true;
    }

    private boolean serveFromFilesystem(String pathInfo, HttpServletResponse resp) throws IOException {
        // Resolve the requested file path
        Path requestedFile = baseDirectory.resolve(pathInfo.substring(1)).normalize();

        // Security check: ensure the resolved path is within the base directory
        if (!requestedFile.startsWith(baseDirectory)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return true;
        }

        // Check if file exists and is readable
        if (!Files.exists(requestedFile)) {
            return false;
        }

        if (!Files.isRegularFile(requestedFile)) {
            return false;
        }

        if (!Files.isReadable(requestedFile)) {
            return false;
        }

        // Determine content type
        String contentType = getContentType(requestedFile);
        resp.setContentType(contentType);

        // Set content length
        long fileSize = Files.size(requestedFile);
        if (fileSize <= Integer.MAX_VALUE) {
            resp.setContentLength((int) fileSize);
        } else {
            resp.setHeader("Content-Length", String.valueOf(fileSize));
        }

        // Set caching headers for static assets
        setCacheHeaders(resp, requestedFile.getFileName().toString());

        // Stream the file to the response
        try (InputStream in = Files.newInputStream(requestedFile);
             OutputStream out = resp.getOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return true;
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty()) {
            pathInfo = "/";
        }

        if (classpathBase != null) {
            // Security check: prevent path traversal
            if (pathInfo.contains("..")) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            String resourcePath = classpathBase + pathInfo;
            resourcePath = resourcePath.replaceAll("/+", "/");

            URL resourceUrl = classLoader.getResource(resourcePath);
            if (resourceUrl == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            resp.setContentType(getContentType(pathInfo));
            try {
                URLConnection connection = resourceUrl.openConnection();
                int contentLength = connection.getContentLength();
                if (contentLength >= 0) {
                    resp.setContentLength(contentLength);
                }
            } catch (IOException e) {
                // Ignore
            }
            setCacheHeaders(resp, pathInfo);
        } else {
            Path requestedFile = baseDirectory.resolve(pathInfo.substring(1)).normalize();

            if (!requestedFile.startsWith(baseDirectory)) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            if (!Files.exists(requestedFile) || !Files.isRegularFile(requestedFile)) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            resp.setContentType(getContentType(requestedFile));
            long fileSize = Files.size(requestedFile);
            if (fileSize <= Integer.MAX_VALUE) {
                resp.setContentLength((int) fileSize);
            } else {
                resp.setHeader("Content-Length", String.valueOf(fileSize));
            }
            setCacheHeaders(resp, requestedFile);
        }
    }

    private String getContentType(Path file) {
        return getContentType(file.getFileName().toString());
    }

    private String getContentType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();
            return MIME_TYPES.getOrDefault(extension, DEFAULT_MIME_TYPE);
        }
        return DEFAULT_MIME_TYPE;
    }

    private void setCacheHeaders(HttpServletResponse resp, Path file) {
        setCacheHeaders(resp, file.getFileName().toString());
    }

    private void setCacheHeaders(HttpServletResponse resp, String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();
            // Cache static assets for 1 hour by default
            if (extension.matches("css|js|mjs|png|jpg|jpeg|gif|svg|ico|webp|woff|woff2|ttf|otf|eot")) {
                resp.setHeader("Cache-Control", "public, max-age=3600");
            }
        }
    }
}


