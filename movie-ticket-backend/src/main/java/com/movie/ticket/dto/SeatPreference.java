package com.movie.ticket.dto;

import com.movie.ticket.exception.BusinessException;

import java.util.Locale;

public enum SeatPreference {
    CENTER,
    QUIET,
    EASY_ACCESS,
    COUPLE,
    ACCESSIBLE;

    public static SeatPreference from(String value) {
        if (value == null || value.isBlank()) {
            return CENTER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("不支持的选座偏好");
        }
    }
}
