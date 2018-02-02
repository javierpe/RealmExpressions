package com.realm.expressions.exceptions;

/**
 * Created by usuario on 30/01/18.
 */

public class RealmExpressionException extends Throwable{

    private String key;
    private String expression;
    private String message;

    public RealmExpressionException(String key, String expression, String message) {
        super(message);
        this.key = key;
        this.expression = expression;
        this.message = message;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
