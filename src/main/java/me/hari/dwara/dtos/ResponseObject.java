package me.hari.dwara.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseObject<T> {
    private Boolean success;
    private String message;

    // Spec §5 calls the payload field `data`. Keep the Java-side name
    // (`response`) used throughout the services and tests, but serialise it
    // under the documented wire name so app clients and the mobile types agree.
    @JsonProperty("data")
    private T response;

    public static <T> ResponseObject<T> success(String message, T response) {
        return new ResponseObject<>(true, message, response);
    }

    public static <T> ResponseObject<T> failure(String message) {
        return new ResponseObject<>(false, message, null);
    }
}
