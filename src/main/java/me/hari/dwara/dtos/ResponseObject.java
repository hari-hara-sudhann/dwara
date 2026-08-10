package me.hari.dwara.dtos;

import lombok.Data;

@Data
public class ResponseObject<T> {
    private Boolean success;
    private String message;
    private T response;
}
