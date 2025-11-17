package com.bookticket.payment_service.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPrincipal implements Serializable {
    private  Long userId;
    private  String username;
}
