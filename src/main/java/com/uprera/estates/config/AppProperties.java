package com.uprera.estates.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String cdnBaseUrl,
        List<String> corsOrigins,
        // Both optional — see FrontendRevalidator. Unset in an environment that
        // doesn't have a paired real_estate_frontend deploy to notify.
        String frontendUrl,
        String revalidateSecret,
        // All optional — see BufferClient. Unset means the content pipeline's
        // social-post step is skipped (graceful no-op), not an error.
        String bufferAccessToken,
        // Buffer "channel" id per platform key (instagram/facebook/linkedin —
        // see BufferClient.SUPPORTED_PLATFORMS), from https://publish.buffer.com.
        Map<String, String> bufferChannelIds,
        // Optional — see UnsplashClient. Unset means Pipeline B's social/image
        // step just posts without an image rather than failing.
        String unsplashAccessKey
) {}
