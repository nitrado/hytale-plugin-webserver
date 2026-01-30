# Content Type Negotiation

Nitrado:WebServer provides utilities for HTTP content negotiation, allowing your endpoints to serve multiple response formats (e.g., HTML and JSON) and to support API versioning.

## Overview

Content negotiation lets clients specify their preferred response format via the `Accept` header. This is useful for:

- Serving HTML to browsers and JSON to API clients from the same endpoint
- Supporting multiple API versions without breaking existing consumers
- Graceful deprecation of older API versions

## Using RequestUtils.negotiateContentType

The `RequestUtils.negotiateContentType()` method handles content negotiation based on the request's `Accept` header:

```java
import net.nitrado.hytale.plugins.webserver.util.RequestUtils;

public class MyServlet extends HttpServlet {

    private static final String JSON_V1 = "application/x.hytale.myplugin.resource+json;version=1";
    private static final String TEXT_HTML = "text/html";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var contentType = RequestUtils.negotiateContentType(
                req,
                JSON_V1,
                TEXT_HTML
        );

        if (contentType == null) {
            resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
            return;
        }

        switch (contentType) {
            case JSON_V1 -> handleJsonV1(req, resp);
            case TEXT_HTML -> handleHtml(req, resp);
        }
    }

    private void handleJsonV1(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType(JSON_V1);
        resp.setCharacterEncoding("UTF-8");
        // ... write JSON response
    }

    private void handleHtml(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType(TEXT_HTML);
        resp.setCharacterEncoding("UTF-8");
        // ... render HTML template
    }
}
```

### Method Signature

```java
public static String negotiateContentType(HttpServletRequest req, String... supportedContentTypes)
public static String negotiateContentType(HttpServletRequest req, boolean allowOverrideFromQuery, String... supportedContentTypes)
```

**Parameters:**
- `req` – The HTTP request
- `allowOverrideFromQuery` – If `true` (default), the `?output=` query parameter can override Accept header negotiation
- `supportedContentTypes` – Content types your endpoint supports, **in order of preference**

**Returns:** The negotiated content type, or `null` if no match is found.

### Matching Behavior

| Accept Header      | Supported Types                              | Result                                     |
|--------------------|----------------------------------------------|--------------------------------------------|
| `*/*` or missing   | `["application/json", "text/html"]`          | `application/json` (first supported)       |
| `text/html`        | `["application/json", "text/html"]`          | `text/html`                                |
| `application/json` | `["application/x.custom+json", "text/html"]` | `application/x.custom+json` (suffix match) |
| `text/plain`       | `["application/json", "text/html"]`          | `null` (no match)                          |

### Query Parameter Override

Clients can override content negotiation using the `?output=` query parameter:

```
GET /MyOrg/MyPlugin/resource?output=json
GET /MyOrg/MyPlugin/resource?output=application/x.hytale.myplugin.resource+json;version=1
```

This is useful for debugging, showing JSON responses in the browser, or when clients cannot set custom `Accept` headers.

## API Versioning with custom Media Types

Use custom media types with version parameters to support API versioning:

```
application/x.hytale.<group>.<plugin>.<resource>+json;version=<n>
```

### Example

```java
private static final String JSON_V1 = "application/x.hytale.myorg.myplugin.users+json;version=1";
private static final String JSON_V2 = "application/x.hytale.myorg.myplugin.users+json;version=2";
private static final String TEXT_HTML = "text/html";

@Override
protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // List newer versions first - they take priority at equal quality
    var contentType = RequestUtils.negotiateContentType(
            req,
            JSON_V2,
            JSON_V1,
            TEXT_HTML
    );

    if (contentType == null) {
        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
        return;
    }

    switch (contentType) {
        case JSON_V2 -> handleJsonV2(req, resp);
        case JSON_V1 -> handleJsonV1(req, resp);
        case TEXT_HTML -> handleHtml(req, resp);
    }
}
```

### Version Priority

When a client sends `Accept: application/json` (without a specific version):

1. The suffix `+json` matches all JSON versions
2. At equal quality, **the first listed supported type wins**
3. List newer versions first to serve them by default

### Backwards Compatibility

Clients can request multiple versions for backwards compatibility with older plugin versions:

```http
Accept: application/x.hytale.myorg.myplugin.users+json;version=2, application/x.hytale.myorg.myplugin.users+json;version=1;q=0.9
```

This prefers v2 but accepts v1 if v2 is unavailable.

## Deprecating API Versions

When deprecating an older API version, set the `Deprecation` header to signal clients:

```java
private void handleJsonV1(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setHeader("Deprecation", "true");
    resp.setContentType(JSON_V1);
    // ... write response
}
```

Document the deprecation in your API and give consumers time to migrate before removing the version.

## Complete Example

See [Nitrado:Query](https://github.com/nitrado/hytale-plugin-query) for a complete implementation serving both HTML and JSON with versioned content types.

```java
public class QueryServlet extends TemplateServlet {

    private static final String JSON_V1 = "application/x.hytale.nitrado.query+json;version=1";
    private static final String TEXT_HTML = "text/html";

    @Override
    @RequirePermissions(
            mode = RequirePermissions.Mode.ANY,
            value = { /* ... */ }
    )
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var contentType = RequestUtils.negotiateContentType(req, JSON_V1, TEXT_HTML);

        if (contentType == null) {
            resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
            return;
        }

        switch (contentType) {
            case JSON_V1 -> handleJsonV1(req, resp);
            case TEXT_HTML -> handleHtml(req, resp);
        }
    }

    private void handleHtml(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType(TEXT_HTML);
        resp.setCharacterEncoding("UTF-8");
        // Render HTML template
        this.renderTemplate(req, resp, "nitrado.query", vars);
    }

    private void handleJsonV1(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType(JSON_V1);
        resp.setCharacterEncoding("UTF-8");
        
        var doc = (new Document()).append("someKey", "someValue");
        // Write JSON response
        resp.getWriter().println(doc.toJson());
    }
}
```

