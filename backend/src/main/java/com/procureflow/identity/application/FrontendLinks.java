package com.procureflow.identity.application;

import java.net.URI;

/**
 * Frontend base URL shared by every mailed link (invite, verification,
 * password reset). Validated once here instead of per service: only absolute
 * http(s) URLs pass, so a misconfigured {@code app.frontend.url} fails fast
 * at mail time instead of shipping broken or misleading links. Trailing
 * slashes are stripped so path joins never double them.
 */
public final class FrontendLinks {

    private FrontendLinks() {
    }

    public static String base(String frontendUrl) {
        URI base;
        try {
            base = URI.create(frontendUrl == null ? "" : frontendUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.frontend.url is misconfigured", e);
        }
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null) {
            throw new IllegalStateException("app.frontend.url must be an absolute http(s) URL");
        }
        return base.toString().replaceAll("/+$", "");
    }
}
