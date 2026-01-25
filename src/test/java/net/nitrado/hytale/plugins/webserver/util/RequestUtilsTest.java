package net.nitrado.hytale.plugins.webserver.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestUtilsTest {

    private HttpServletRequest mockRequest(String acceptHeader, String outputParam) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Accept")).thenReturn(acceptHeader);
        when(req.getParameter("output")).thenReturn(outputParam);
        return req;
    }

    @Test
    @DisplayName("Returns null when no supported content types provided")
    void returnsNullWhenNoSupportedTypes() {
        HttpServletRequest req = mockRequest("application/json", null);
        assertNull(RequestUtils.negotiateContentType(req));
        assertNull(RequestUtils.negotiateContentType(req, (String[]) null));
        assertNull(RequestUtils.negotiateContentType(req, new String[0]));
    }

    @Test
    @DisplayName("Returns first supported type when Accept is */*")
    void returnsFirstSupportedWhenAcceptAll() {
        HttpServletRequest req = mockRequest("*/*", null);
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req, "application/json", "text/html"));
    }

    @Test
    @DisplayName("Returns first supported type when no Accept header")
    void returnsFirstSupportedWhenNoAcceptHeader() {
        HttpServletRequest req = mockRequest(null, null);
        assertEquals("text/html",
            RequestUtils.negotiateContentType(req, "text/html", "application/json"));
    }

    @Test
    @DisplayName("Exact match takes priority")
    void exactMatchPriority() {
        HttpServletRequest req = mockRequest("application/json", null);
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req, "text/html", "application/json"));
    }

    @Test
    @DisplayName("Quality values are respected")
    void qualityValuesRespected() {
        HttpServletRequest req = mockRequest("text/html;q=0.5, application/json;q=0.9", null);
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req, "text/html", "application/json"));
    }

    @Test
    @DisplayName("Supported order is tiebreaker for same quality")
    void supportedOrderTiebreaker() {
        HttpServletRequest req = mockRequest("text/html, application/json", null);
        assertEquals("text/html",
            RequestUtils.negotiateContentType(req, "text/html", "application/json"));
    }

    @Test
    @DisplayName("Query parameter overrides Accept header")
    void queryParamOverridesAccept() {
        HttpServletRequest req = mockRequest("text/html", "json");
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req, "text/html", "application/json"));
    }

    @Test
    @DisplayName("Query param override can be disabled")
    void queryParamOverrideCanBeDisabled() {
        HttpServletRequest req = mockRequest("text/html", "json");
        assertEquals("text/html",
            RequestUtils.negotiateContentType(req, false, "text/html", "application/json"));
    }

    @Test
    @DisplayName("Query param matches full media type")
    void queryParamMatchesFullMediaType() {
        HttpServletRequest req = mockRequest(null, "application/json");
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req, "text/html", "application/json"));
    }

    // --- Tests for complex media types like application/x.hytale.nitrado.query+json;version=1 ---

    @Test
    @DisplayName("Suffix match: json matches application/x.hytale.nitrado.query+json;version=1")
    void suffixMatchWithVersionedCustomType() {
        HttpServletRequest req = mockRequest("application/json", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req, "application/x.hytale.nitrado.query+json;version=1"));
    }

    @Test
    @DisplayName("Query param json matches custom type with suffix")
    void queryParamJsonMatchesCustomSuffix() {
        HttpServletRequest req = mockRequest(null, "json");
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req, "application/x.hytale.nitrado.query+json;version=1"));
    }

    @Test
    @DisplayName("Exact match on custom type preferred over suffix match")
    void exactMatchPreferredOverSuffix() {
        HttpServletRequest req = mockRequest("application/json", null);
        // When both base and suffixed are supported, exact match wins
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json;version=1",
                "application/json"));
    }

    @Test
    @DisplayName("Wildcard type matches any subtype")
    void wildcardTypeMatches() {
        HttpServletRequest req = mockRequest("application/*", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req, "application/x.hytale.nitrado.query+json;version=1"));
    }

    @Test
    @DisplayName("Query param with full custom type matches exactly")
    void queryParamFullCustomType() {
        HttpServletRequest req = mockRequest(null, "application/x.hytale.nitrado.query+json;version=1");
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req,
                "application/json",
                "application/x.hytale.nitrado.query+json;version=1"));
    }

    @Test
    @DisplayName("Custom type with xml suffix")
    void customTypeWithXmlSuffix() {
        HttpServletRequest req = mockRequest("application/xml", null);
        assertEquals("application/x.hytale.nitrado.query+xml;version=1",
            RequestUtils.negotiateContentType(req, "application/x.hytale.nitrado.query+xml;version=1"));
    }

    @Test
    @DisplayName("Query param xml matches custom xml suffix type")
    void queryParamXmlMatchesCustomXmlSuffix() {
        HttpServletRequest req = mockRequest(null, "xml");
        assertEquals("application/x.hytale.nitrado.query+xml;version=1",
            RequestUtils.negotiateContentType(req, "application/x.hytale.nitrado.query+xml;version=1"));
    }

    @Test
    @DisplayName("Multiple custom types with same suffix - supported order wins")
    void multipleCustomTypesSameSuffix() {
        HttpServletRequest req = mockRequest("application/json", null);
        assertEquals("application/x.hytale.nitrado.v1+json",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.v1+json",
                "application/x.hytale.nitrado.v2+json"));
    }

    @Test
    @DisplayName("Accept header with custom type matches exactly")
    void acceptHeaderCustomTypeExactMatch() {
        HttpServletRequest req = mockRequest("application/x.hytale.nitrado.query+json;version=1", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req,
                "application/json",
                "application/x.hytale.nitrado.query+json;version=1"));
    }

    @ParameterizedTest
    @DisplayName("Various suffix matching scenarios")
    @CsvSource({
        "application/json, application/vnd.api+json, application/vnd.api+json",
        "application/xml, application/atom+xml, application/atom+xml",
        "application/json, application/hal+json, application/hal+json",
    })
    void suffixMatchingScenarios(String accept, String supported, String expected) {
        HttpServletRequest req = mockRequest(accept, null);
        assertEquals(expected, RequestUtils.negotiateContentType(req, supported));
    }

    @Test
    @DisplayName("No match returns null")
    void noMatchReturnsNull() {
        HttpServletRequest req = mockRequest("text/plain", null);
        assertNull(RequestUtils.negotiateContentType(req, "application/json", "text/html"));
    }

    @Test
    @DisplayName("Query param subtype match for custom type")
    void queryParamSubtypeMatchCustomType() {
        HttpServletRequest req = mockRequest(null, "x.hytale.nitrado.query+json;version=1");
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req, "application/x.hytale.nitrado.query+json;version=1"));
    }
}

