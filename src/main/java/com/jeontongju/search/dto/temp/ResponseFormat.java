package com.jeontongju.search.dto.temp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 domain : all
 detail : Rest 통신시 사용되는 Format Dto
 method :
 comment :
 */
@Getter
@Builder
@AllArgsConstructor
public class ResponseFormat<T> {

    private Integer code;
    private String message;
    private String detail;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String failure;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T data;
}