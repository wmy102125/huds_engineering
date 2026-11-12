package com.hugo.huds.common.util;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checks and converts the JSON fields of the diagnostic form (for example the secondary symptoms of a case).
 *
 * <p>The content of these fields is defined by the front end, so the server only checks the JSON type
 * (array or object) and the size, then stores the text as it is.</p>
 */
@Component
public class JsonFieldSupport {

    /**
     * Largest accepted size of a JSON field, in characters of the compact text.
     * The database limit is 20000 characters of the text that PostgreSQL stores, which adds a space after
     * every colon and comma. 14000 compact characters stay below 20000 even in the worst case.
     */
    public static final int MAX_COMPACT_LENGTH = 14000;

    private final ObjectMapper objectMapper;

    public JsonFieldSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Checks that the value is a JSON array within the size limit and returns its compact text.
     *
     * @param value     the value from the request, or null
     * @param fieldName the name of the field, used in the error message
     * @return the compact JSON text; {@code []} when the value is null
     * @throws ApplicationException with {@code INVALID_PAYLOAD} if the value is not an array or is too large
     */
    public String arrayText(JsonNode value, String fieldName) {
        return text(value, fieldName, true);
    }

    /**
     * Checks that the value is a JSON object within the size limit and returns its compact text.
     *
     * @param value     the value from the request, or null
     * @param fieldName the name of the field, used in the error message
     * @return the compact JSON text; {@code {}} when the value is null
     * @throws ApplicationException with {@code INVALID_PAYLOAD} if the value is not an object or is too large
     */
    public String objectText(JsonNode value, String fieldName) {
        return text(value, fieldName, false);
    }

    /**
     * Turns the JSON text of a column into a JSON value for a response.
     *
     * @param json         the text read from the database, or null
     * @param arrayDefault true when an empty value should become {@code []}, false for {@code {}}
     * @return the parsed value
     */
    public JsonNode read(String json, boolean arrayDefault) {
        if (json == null || json.isBlank()) {
            return arrayDefault ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
        }
        return objectMapper.readTree(json);
    }

    private String text(JsonNode value, String fieldName, boolean expectArray) {
        if (value == null || value.isNull()) {
            return expectArray ? "[]" : "{}";
        }
        boolean rightType = expectArray ? value.isArray() : value.isObject();
        if (!rightType) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    fieldName + " must be a JSON " + (expectArray ? "array" : "object") + ".");
        }
        String compact = value.toString();
        if (compact.length() > MAX_COMPACT_LENGTH) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    fieldName + " is too large (more than " + MAX_COMPACT_LENGTH + " characters).");
        }
        return compact;
    }
}
