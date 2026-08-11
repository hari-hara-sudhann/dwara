package me.hari.dwara.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseObject<T> {
    private Boolean success;
    private String message;
    private T response;

    public static <T> ResponseObject<T> success(String message, T response) {
        return new ResponseObject<>(true, message, response);
    }

    public static <T> ResponseObject<T> failure(String message) {
        return new ResponseObject<>(false, message, null);
    }
}
