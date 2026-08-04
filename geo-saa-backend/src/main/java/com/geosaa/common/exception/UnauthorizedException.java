package com.geosaa.common.exception;

import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException {

    private final int code;

    public UnauthorizedException(String message) {
        super(message);
        this.code = 401;
    }

    public UnauthorizedException(int code, String message) {
        super(message);
        this.code = code;
    }
}