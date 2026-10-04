package com.uprera.estates.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uprera.estates.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Posts to social platforms via Buffer's free-tier API
 * (https://developers.buffer.com — GraphQL, one endpoint, Bearer auth with
 * a personal access token generated at https://publish.buffer.com/settings/api,
 * no OAuth app registration needed for a single-account integration like
 * this one). Chosen over Ayrshare (dropped its free plan in 2026, now
 * $149+/mo) specifically because Buffer's free plan includes real API
 * access — see SOCIAL_CONTENT_PIPELINE.md §8 in agentic_ai_workflow for the
 * comparison.
 *
 * Free plan caps at 3 connected channels; this product uses Instagram,
 * Facebook and LinkedIn (X deliberately dropped — see §8).
 *
 * UNVERIFIED, flagged deliberately: the exact GraphQL shape for attaching
 * an image (the {@code media} field below) is a best-effort guess built
 * from Buffer's public docs, which didn't show a complete image-post
 * example as of 2026-09-27. A wrong field name fails loudly with a clear
 * GraphQL validation error (not a silent wrong post), so this is safe to
 * have gotten wrong — but it needs a real access token to actually verify
 * and fix. Do that before relying on the image attachment working.
 */
@Component
public class BufferClient {

    private static final Logger log = LoggerFactory.getLogger(BufferClient.class);
    private static final URI ENDPOINT = URI.create("https://api.buffer.com");

    /** Keys AppProperties.bufferChannelIds and SocialPostController's `platform` param share. */
    public static final Set<String> SUPPORTED_PLATFORMS = Set.of("instagram", "facebook", "linkedin");

    public record PostResult(boolean posted, String postId, String reason) {
        static PostResult success(String postId) {
            return new PostResult(true, postId, null);
        }

        static PostResult notPosted(String reason) {
            return new PostResult(false, null, reason);
        }
    }

    private final String accessToken;
    private final Map<String, String> channelIds;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1) // see FrontendRevalidator's note on why
            .build();

    public BufferClient(AppProperties props, ObjectMapper mapper) {
        this.accessToken = props.bufferAccessToken();
        this.channelIds = props.bufferChannelIds() == null ? Map.of() : props.bufferChannelIds();
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(accessToken) && !channelIds.isEmpty();
    }

    public PostResult createPost(String platform, String text, String imageUrl) {
        return createPost(platform, text, imageUrl, null);
    }

    /**
     * Posts to one platform. {@code videoUrl} (Instagram only) makes it a reel:
     * the video goes in as the asset and the post type becomes {@code reel}.
     * When set, {@code imageUrl} is ignored.
     */
    public PostResult createPost(String platform, String text, String imageUrl, String videoUrl) {
        if (!StringUtils.hasText(accessToken)) {
            return PostResult.notPosted("Buffer not configured (app.buffer-access-token unset)");
        }
        String channelId = channelIds.get(platform);
        if (channelId == null) {
            return PostResult.notPosted("No Buffer channel configured for platform \"" + platform + "\" (app.buffer-channel-ids)");
        }

        // Verified live against Buffer's real schema via introspection
        // (2026-10-02, __type(name: "CreatePostInput")/"AssetInput"/
        // "ImageAssetInput"): `assets: [AssetInput!]!` defaults to `[]`
        // (confirmed via introspection's `defaultValue`, and by the fact our
        // earlier text-only test succeeded without ever passing it) — each
        // entry is `{ image: { url } }`, where `ImageAssetInput.url` is the
        // only required field. An earlier draft of this code tried to gate
        // the field with a GraphQL `@include` directive on an input-object
        // value, which is invalid GraphQL (that directive only applies to
        // field selections/fragments) — not repeated here; emptiness is
        // expressed as an empty list, a valid value, not a conditional field.
        boolean isReel = StringUtils.hasText(videoUrl);
        if (isReel && !"instagram".equals(platform)) {
            return PostResult.notPosted("Reels are only wired up for instagram (got \"" + platform + "\")");
        }
        // Video assets: confirmed via introspection on 2026-10-04 — AssetInput has
        // `video: VideoAssetInput`, and VideoAssetInput's only required field is `url`.
        List<Object> assets = isReel
                ? List.of(Map.of("video", Map.of("url", videoUrl)))
                : StringUtils.hasText(imageUrl)
                        ? List.of(Map.of("image", Map.of("url", imageUrl)))
                        : List.of();

        // Verified live against Buffer's real schema via introspection
        // (2026-10-02, __type(name: "FacebookPostMetadataInput")/"PostTypeFacebook"):
        // Facebook requires metadata.facebook.type (post|reel|story) — a
        // real "Facebook posts require a type" error surfaced this, not
        // guessed from docs. LinkedIn's metadata input has no required
        // fields (introspected the same way), so omitting it is fine.
        //
        // Instagram (added 2026-10-02 once its channel id arrived): its
        // metadata requires `type` (PostType! — introspected enum values
        // include carousel/event/post/reel/short/story/thread/etc.; `post`
        // is the standard single-image/caption feed post) AND
        // `shouldShareToFeed` (Boolean!, no introspected default) — `true`
        // for a normal feed post, same as `type: post`.
        Map<String, Object> metadata = switch (platform) {
            case "facebook" -> Map.of("facebook", Map.of("type", "post"));
            // A reel is PostType `reel` (introspected 2026-10-04); it still shares to the feed.
            case "instagram" -> Map.of("instagram", Map.of("type", isReel ? "reel" : "post", "shouldShareToFeed", true));
            default -> null;
        };

        String query = """
                mutation CreatePost($text: String!, $channelId: ChannelId!, $metadata: PostInputMetaData, $assets: [AssetInput!]!) {
                  createPost(input: {
                    text: $text,
                    channelId: $channelId,
                    schedulingType: automatic,
                    mode: addToQueue,
                    metadata: $metadata,
                    assets: $assets
                  }) {
                    ... on PostActionSuccess { post { id } }
                    ... on MutationError { message }
                  }
                }
                """;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("text", text);
        variables.put("channelId", channelId);
        variables.put("metadata", metadata);
        variables.put("assets", assets);

        Map<String, Object> body = Map.of("query", query, "variables", variables);

        try {
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                log.warn("Buffer API returned {} for platform \"{}\": {}", response.statusCode(), platform, response.body());
                return PostResult.notPosted("Buffer API HTTP " + response.statusCode());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode errors = root.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                String message = errors.get(0).path("message").asText("unknown GraphQL error");
                log.warn("Buffer GraphQL error for platform \"{}\": {}", platform, message);
                return PostResult.notPosted("Buffer: " + message);
            }

            JsonNode result = root.path("data").path("createPost");
            JsonNode mutationMessage = result.path("message");
            if (!mutationMessage.isMissingNode()) {
                return PostResult.notPosted("Buffer: " + mutationMessage.asText());
            }

            String postId = result.path("post").path("id").asText(null);
            if (postId == null) {
                log.warn("Buffer createPost response had neither a post id nor an error for platform \"{}\": {}", platform, response.body());
                return PostResult.notPosted("Buffer: unrecognized response shape — see backend log");
            }
            return PostResult.success(postId);
        } catch (Exception e) {
            log.warn("Buffer createPost call failed for platform \"{}\"", platform, e);
            return PostResult.notPosted("Buffer call failed: " + e.getMessage());
        }
    }
}
