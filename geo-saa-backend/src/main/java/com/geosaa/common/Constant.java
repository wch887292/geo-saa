package com.geosaa.common;

public interface Constant {

    String TOKEN_HEADER = "Authorization";
    String TOKEN_PREFIX = "Bearer ";
    String TOKEN_ATTRIBUTE = "token";

    String ROLE_ADMIN = "ADMIN";
    String ROLE_USER = "USER";

    String CACHE_KEY_PREFIX = "geo:";

    Integer TASK_STATUS_PENDING = 0;
    Integer TASK_STATUS_PROCESSING = 1;
    Integer TASK_STATUS_COMPLETED = 2;
    Integer TASK_STATUS_FAILED = 3;
}