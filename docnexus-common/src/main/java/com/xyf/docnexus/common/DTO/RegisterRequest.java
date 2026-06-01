package com.xyf.docnexus.common.DTO;

import lombok.Data;

@Data
public class RegisterRequest {

    private String username;

    private String password;

    private String confirmPassword;

    private String email;

    private String phone;

    private String role;
}