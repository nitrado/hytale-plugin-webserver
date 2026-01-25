package net.nitrado.hytale.plugins.webserver.util;

import jakarta.servlet.http.HttpServletRequest;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.ArrayList;
import java.util.List;

public final class RequestUtils {
    private RequestUtils() {}

    /**
     * Negotiates the content type based on the request, with query parameter override enabled.
     *
     * @param req the HTTP request
     * @param supportedContentTypes the content types supported by the endpoint, in order of preference
     * @return the negotiated content type, or {@code null} if no match is found
     * @see #negotiateContentType(HttpServletRequest, boolean, String...)
     */
    public static String negotiateContentType(HttpServletRequest req, String ...supportedContentTypes) {
        return negotiateContentType(req, true, supportedContentTypes);
    }

    /**
     * Negotiates the content type based on the request's Accept header and optionally a query parameter.
     * <p>
     * When {@code allowOverrideFromQuery} is {@code true}, the {@code output} query parameter takes precedence
     * over the Accept header. The query parameter can be:
     * <ul>
     *   <li>A simple subtype (e.g., {@code ?output=json} matches {@code application/json})</li>
     *   <li>A full media type (e.g., {@code ?output=application/x.custom+json;version=1})</li>
     * </ul>
     * <p>
     * If the Accept header is used, content types are matched based on quality values. When multiple
     * content types have the same quality, the order in {@code supportedContentTypes} determines priority.
     * <p>
     * <b>Suffix matching:</b> For custom media types with suffixes (e.g., {@code application/x.custom+json}),
     * a request for the base type (e.g., {@code Accept: application/json} or {@code ?output=json}) will match
     * the suffixed type, unless the exact base type is also in the supported list.
     *
     * @param req the HTTP request
     * @param allowOverrideFromQuery if {@code true}, the {@code output} query parameter can override Accept header negotiation
     * @param supportedContentTypes the content types supported by the endpoint, in order of preference
     * @return the negotiated content type, or {@code null} if no match is found
     */
    public static String negotiateContentType(HttpServletRequest req, boolean allowOverrideFromQuery, String ...supportedContentTypes) {
        if (supportedContentTypes == null || supportedContentTypes.length == 0) {
            return null;
        }

        if (allowOverrideFromQuery) {
            String outputParam = req.getParameter("output");
            if (outputParam != null && !outputParam.isBlank()) {
                return findContentTypeByQueryParam(outputParam, supportedContentTypes);
            }
        }

        String acceptHeader = req.getHeader("Accept");
        if (acceptHeader == null || acceptHeader.isBlank() || acceptHeader.equals("*/*")) {
            // Client accepts anything, return first supported
            return supportedContentTypes[0];
        }

        return findContentTypeByAcceptHeader(supportedContentTypes, acceptHeader);
    }

    @NullableDecl
    private static String findContentTypeByAcceptHeader(String[] supportedContentTypes, String acceptHeader) {
        List<AcceptEntry> acceptEntries = parseAcceptHeader(acceptHeader);

        String bestMatch = null;
        double bestQuality = -1;
        int bestSupportedIndex = Integer.MAX_VALUE;

        for (AcceptEntry entry : acceptEntries) {
            for (int i = 0; i < supportedContentTypes.length; i++) {
                String supported = supportedContentTypes[i];
                if (matches(entry.mediaType, supported, supportedContentTypes)) {
                    // If quality is higher, or same quality but earlier in supportedContentTypes
                    if (entry.quality > bestQuality ||
                        (entry.quality == bestQuality && i < bestSupportedIndex)) {
                        bestQuality = entry.quality;
                        bestMatch = supported;
                        bestSupportedIndex = i;
                    }
                }
            }
        }
        return bestMatch;
    }

    /**
     * Finds a matching content type based on the output query parameter.
     * Matches if:
     * - The query param equals the full media type (e.g., "application/x.custom+json;version=1")
     * - The query param matches the subtype (e.g., "json" matches "application/json")
     * - The query param matches a suffix (e.g., "json" matches "application/x.custom+json" if "application/json" is not supported)
     * - The query param matches the subtype with parameters (e.g., "x.custom+json;version=1" matches "application/x.custom+json;version=1")
     */
    private static String findContentTypeByQueryParam(String queryParam, String[] supportedContentTypes) {
        String suffixMatch = null;

        for (String supported : supportedContentTypes) {
            // Exact match with full media type
            if (supported.equals(queryParam)) {
                return supported;
            }

            // Match against subtype (part after '/')
            String subtype = extractSubtype(supported);
            if (subtype.equals(queryParam)) {
                return supported;
            }

            // Match against subtype with parameters (e.g., "x.custom+json;version=1")
            String subtypeWithParams = extractSubtypeWithParams(supported);
            if (subtypeWithParams.equals(queryParam)) {
                return supported;
            }

            // Check for suffix match (e.g., "json" matches "x.custom+json")
            if (suffixMatch == null && hasSuffixSubtype(subtype, queryParam)) {
                suffixMatch = supported;
            }
        }

        // Return suffix match only if no exact subtype match was found
        return suffixMatch;
    }

    /**
     * Extracts the subtype with parameters from a media type.
     * E.g., "x.custom+json;version=1" from "application/x.custom+json;version=1".
     */
    private static String extractSubtypeWithParams(String mediaType) {
        int slashIndex = mediaType.indexOf('/');
        if (slashIndex < 0) {
            return mediaType;
        }
        return mediaType.substring(slashIndex + 1);
    }

    /**
     * Checks if the subtype has a suffix matching the query param.
     * E.g., "x.custom+json" has suffix "json".
     */
    private static boolean hasSuffixSubtype(String subtype, String queryParam) {
        return subtype.endsWith("+" + queryParam);
    }

    /**
     * Extracts the subtype from a media type (e.g., "json" from "application/json").
     * For types with parameters, extracts only the subtype portion.
     */
    private static String extractSubtype(String mediaType) {
        int slashIndex = mediaType.indexOf('/');
        if (slashIndex < 0) {
            return mediaType;
        }

        String afterSlash = mediaType.substring(slashIndex + 1);

        // Remove parameters (e.g., ";version=1")
        int semicolonIndex = afterSlash.indexOf(';');
        if (semicolonIndex >= 0) {
            afterSlash = afterSlash.substring(0, semicolonIndex);
        }

        return afterSlash;
    }

    private static List<AcceptEntry> parseAcceptHeader(String acceptHeader) {
        List<AcceptEntry> entries = new ArrayList<>();
        String[] parts = acceptHeader.split(",");

        for (String part : parts) {
            String[] tokens = part.trim().split(";");
            String mediaType = tokens[0].trim();
            double quality = 1.0;
            StringBuilder mediaTypeParams = new StringBuilder();

            for (int i = 1; i < tokens.length; i++) {
                String param = tokens[i].trim();
                if (param.startsWith("q=")) {
                    try {
                        quality = Double.parseDouble(param.substring(2).trim());
                    } catch (NumberFormatException e) {
                        quality = 1.0;
                    }
                } else {
                    // Preserve non-quality parameters as part of the media type
                    mediaTypeParams.append(";").append(param);
                }
            }

            entries.add(new AcceptEntry(mediaType + mediaTypeParams, quality));
        }

        return entries;
    }

    private static boolean matches(String acceptMediaType, String supportedMediaType, String[] allSupportedTypes) {
        if ("*/*".equals(acceptMediaType)) {
            return true;
        }

        if (acceptMediaType.endsWith("/*")) {
            String acceptType = acceptMediaType.substring(0, acceptMediaType.indexOf('/'));
            String supportedType = supportedMediaType.substring(0, supportedMediaType.indexOf('/'));
            return acceptType.equals(supportedType);
        }

        if (acceptMediaType.equals(supportedMediaType)) {
            return true;
        }

        // Compare base types (without parameters) for exact match
        String acceptBase = stripParameters(acceptMediaType);
        String supportedBase = stripParameters(supportedMediaType);
        if (acceptBase.equals(supportedBase)) {
            return true;
        }

        // Handle suffix-based matching (e.g., application/json matches application/x.custom+json)
        // Only if the exact type is not in the supported list
        return isSuffixMatch(acceptMediaType, supportedMediaType) && !containsExactType(acceptMediaType, allSupportedTypes);
    }

    /**
     * Strips parameters from a media type.
     * E.g., "application/json;charset=utf-8" becomes "application/json".
     */
    private static String stripParameters(String mediaType) {
        int semicolonIndex = mediaType.indexOf(';');
        return semicolonIndex >= 0 ? mediaType.substring(0, semicolonIndex) : mediaType;
    }

    /**
     * Checks if the supported media type has a suffix that matches the accept media type.
     * E.g., "application/json" matches "application/x.custom+json;version=1" because the suffix is "+json".
     */
    private static boolean isSuffixMatch(String acceptMediaType, String supportedMediaType) {
        int acceptSlash = acceptMediaType.indexOf('/');
        int supportedSlash = supportedMediaType.indexOf('/');
        if (acceptSlash < 0 || supportedSlash < 0) {
            return false;
        }

        String acceptType = acceptMediaType.substring(0, acceptSlash);
        String supportedType = supportedMediaType.substring(0, supportedSlash);
        if (!acceptType.equals(supportedType)) {
            return false;
        }

        String acceptSubtype = acceptMediaType.substring(acceptSlash + 1);
        String supportedSubtype = supportedMediaType.substring(supportedSlash + 1);

        // Remove parameters from supported subtype
        int semicolonIndex = supportedSubtype.indexOf(';');
        if (semicolonIndex >= 0) {
            supportedSubtype = supportedSubtype.substring(0, semicolonIndex);
        }

        // Check if supported subtype has a suffix matching the accept subtype (e.g., +json)
        return supportedSubtype.endsWith("+" + acceptSubtype);
    }

    /**
     * Checks if the exact media type (ignoring parameters) is in the supported types list.
     */
    private static boolean containsExactType(String mediaType, String[] supportedTypes) {
        for (String supported : supportedTypes) {
            String supportedBase = supported;
            int semicolonIndex = supportedBase.indexOf(';');
            if (semicolonIndex >= 0) {
                supportedBase = supportedBase.substring(0, semicolonIndex);
            }
            if (supportedBase.equals(mediaType)) {
                return true;
            }
        }
        return false;
    }

    private record AcceptEntry(String mediaType, double quality) {}
}
