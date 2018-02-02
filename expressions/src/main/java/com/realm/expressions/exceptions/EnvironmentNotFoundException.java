package com.realm.expressions.exceptions;

/**
 * Created by usuario on 16/01/18.
 */

public class EnvironmentNotFoundException extends RealmExpressionException{
    public EnvironmentNotFoundException(String key, String expression, String message) {
        super(key, expression, message);
    }
}
