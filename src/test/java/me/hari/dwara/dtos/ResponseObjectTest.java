package me.hari.dwara.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseObjectTest {

    @Test
    void serialisesThePayloadUnderTheSpecNameData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode node = mapper.readTree(
                mapper.writeValueAsString(ResponseObject.success("ok", "value")));

        assertTrue(node.get("success").asBoolean());
        assertEquals("ok", node.get("message").asText());
        assertEquals("value", node.get("data").asText());
        assertFalse(node.has("response"), "spec §5 names the payload field data");
    }
}
