package com.hugo.huds.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author meiyan wang
 * @date 2026-10-16 5:31 p.m.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse {

    private Boolean success;
    private String message;

    public static CommonResponse buildSuccessResponse() {
        return new CommonResponse(true, null);
    }

    public static CommonResponse buildFailureResponse(String message) {
        return new CommonResponse(false, message);
    }

}
