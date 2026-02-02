package com.rssai.exception;

/**
 * 用户已存在异常
 * 用于处理用户名或邮箱已被注册的情况
 */
public class UserAlreadyExistsException extends RuntimeException {
    
    private final String field;
    private final String value;
    
    public UserAlreadyExistsException(String field, String value) {
        super(String.format("%s '%s' 已被注册", field, value));
        this.field = field;
        this.value = value;
    }
    
    public UserAlreadyExistsException(String message) {
        super(message);
        this.field = null;
        this.value = null;
    }
    
    public String getField() {
        return field;
    }
    
    public String getValue() {
        return value;
    }
}
