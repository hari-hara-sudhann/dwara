package me.hari.dwara.dtos.parkingsession;

import java.util.List;

/** One page of the authenticated user's sessions, newest first. */
public record SessionPageDto(
        List<SessionDto> sessions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
