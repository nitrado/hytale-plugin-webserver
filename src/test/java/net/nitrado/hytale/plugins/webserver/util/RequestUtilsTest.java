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

    // --- RFC 9110 Compliance Tests ---

    @Test
    @DisplayName("RFC 9110: Parameters before q= belong to media type")
    void parametersBeforeQBelongToMediaType() {
        // Accept: application/x.hytale.nitrado.query+json;version=1;q=0.8
        // The version=1 is part of the media type, q=0.8 is the quality
        HttpServletRequest req = mockRequest("application/x.hytale.nitrado.query+json;version=1;q=0.8", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json;version=1",
                "application/x.hytale.nitrado.query+json;version=2"));
    }

    @Test
    @DisplayName("RFC 9110: Parameters after q= are accept-extensions, not media type params")
    void parametersAfterQAreAcceptExtensions() {
        // Accept: application/json;q=0.8;level=1
        // The level=1 is an accept-extension (should be ignored), NOT part of application/json
        HttpServletRequest req = mockRequest("application/json;q=0.8;level=1", null);
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req, "application/json"));
    }

    @Test
    @DisplayName("RFC 9110: Complex Accept header with params before and after q=")
    void complexAcceptHeaderWithParamsBeforeAndAfterQ() {
        // Accept: application/x.hytale.nitrado.query+json;version=1;q=0.9;foo=bar
        // version=1 is media type param, q=0.9 is quality, foo=bar is accept-extension (ignored)
        HttpServletRequest req = mockRequest(
            "application/x.hytale.nitrado.query+json;version=1;q=0.9;foo=bar", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json;version=1",
                "application/x.hytale.nitrado.query+json;version=2"));
    }

    @Test
    @DisplayName("RFC 9110: Quality determines preference order")
    void qualityDeterminesPreferenceWithParams() {
        // version=2 has higher quality (0.9) than version=1 (0.5)
        HttpServletRequest req = mockRequest(
            "application/x.hytale.nitrado.query+json;version=1;q=0.5, " +
            "application/x.hytale.nitrado.query+json;version=2;q=0.9", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=2",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json;version=1",
                "application/x.hytale.nitrado.query+json;version=2"));
    }

    @Test
    @DisplayName("RFC 2045: Parameter names are case-insensitive")
    void parameterNamesCaseInsensitive() {
        // VERSION=1 should match version=1
        HttpServletRequest req = mockRequest("application/x.hytale.nitrado.query+json;VERSION=1", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json;version=1"));
    }

    @Test
    @DisplayName("RFC 2045: Media type and subtype are case-insensitive")
    void mediaTypeCaseInsensitive() {
        HttpServletRequest req = mockRequest("APPLICATION/JSON", null);
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req, "application/json"));
    }

    @Test
    @DisplayName("RFC 9110: Media type with parameter matches unparametrized server type (flexible)")
    void mediaTypeWithParamMatchesUnparametrizedServerType() {
        // Client requests specific version, server only supports unversioned
        // This matches because server's unversioned type is considered to accept any version
        HttpServletRequest req = mockRequest("application/x.hytale.nitrado.query+json;version=1", null);
        assertEquals("application/x.hytale.nitrado.query+json",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json"));
    }

    @Test
    @DisplayName("RFC 9110: Media type without parameter matches type with parameter via suffix")
    void mediaTypeWithoutParamMatchesViaBaseSuffix() {
        // Client accepts application/json, server has versioned custom type - suffix match
        HttpServletRequest req = mockRequest("application/json", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=1",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json;version=1"));
    }

    @Test
    @DisplayName("Multiple media types with different versions and qualities")
    void multipleVersionsWithQualities() {
        HttpServletRequest req = mockRequest(
            "application/x.hytale.nitrado.query+json;version=1;q=0.5, " +
            "application/x.hytale.nitrado.query+json;version=2;q=0.8, " +
            "application/json;q=0.3", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=2",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json;version=1",
                "application/x.hytale.nitrado.query+json;version=2",
                "application/json"));
    }

    @Test
    @DisplayName("RFC 9110: q=0 means not acceptable")
    void qualityZeroMeansNotAcceptable() {
        HttpServletRequest req = mockRequest("application/json;q=0, text/html;q=1", null);
        assertEquals("text/html",
            RequestUtils.negotiateContentType(req, "application/json", "text/html"));
    }

    @Test
    @DisplayName("RFC 9110: Only q=0 types available returns null")
    void onlyQualityZeroTypesReturnsNull() {
        HttpServletRequest req = mockRequest("application/json;q=0", null);
        assertNull(RequestUtils.negotiateContentType(req, "application/json"));
    }

    @Test
    @DisplayName("Accept header with charset parameter")
    void acceptHeaderWithCharsetParameter() {
        HttpServletRequest req = mockRequest("application/json;charset=utf-8", null);
        // Should match application/json (base type match)
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req, "application/json"));
    }

    @Test
    @DisplayName("Multiple parameters in Accept header")
    void multipleParametersInAcceptHeader() {
        HttpServletRequest req = mockRequest(
            "application/x.hytale.nitrado.query+json;version=1;format=compact;q=0.9", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=1;format=compact",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json;version=1;format=compact",
                "application/x.hytale.nitrado.query+json;version=1"));
    }

    @Test
    @DisplayName("Parameter order should not affect matching")
    void parameterOrderDoesNotAffectMatching() {
        // Client sends format=compact;version=1, server has version=1;format=compact
        HttpServletRequest req = mockRequest(
            "application/x.hytale.nitrado.query+json;format=compact;version=1", null);
        assertEquals("application/x.hytale.nitrado.query+json;version=1;format=compact",
            RequestUtils.negotiateContentType(req,
                "application/x.hytale.nitrado.query+json;version=1;format=compact"));
    }

    @ParameterizedTest
    @DisplayName("RFC compliance: quality value edge cases")
    @CsvSource({
        "application/json;q=1.0, application/json",
        "application/json;q=0.001, application/json",
        "application/json;q=0.999, application/json",
    })
    void qualityValueEdgeCases(String accept, String expected) {
        HttpServletRequest req = mockRequest(accept, null);
        assertEquals(expected, RequestUtils.negotiateContentType(req, "application/json", "text/html"));
    }

    @Test
    @DisplayName("Invalid quality value defaults to 1.0")
    void invalidQualityValueDefaultsToOne() {
        HttpServletRequest req = mockRequest("application/json;q=invalid, text/html;q=0.5", null);
        // application/json with invalid q defaults to 1.0, higher than text/html's 0.5
        assertEquals("application/json",
            RequestUtils.negotiateContentType(req, "application/json", "text/html"));
    }
}

