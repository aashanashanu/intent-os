package com.intentos.validation;

public record PolicyViolation(String type, String item, String expected, String actual) {
}