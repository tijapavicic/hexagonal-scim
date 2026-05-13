package com.example.user.core;

public class DuplicateAccountException extends RuntimeException {
    public DuplicateAccountException(String message) {
        super(message);
    }
}

