package com.geosaa.modules.common;

import lombok.Data;

@Data
public class TaskProgress {
    private String taskId;
    private int current;
    private int total;
    private int percentage;
    private String status;
    private String message;
    private String result;
}