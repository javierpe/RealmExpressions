package com.realm.expressions.exceptions;

/**
 * Created by usuario on 19/01/18.
 */

public class MethodNotFoundException extends RealmExpressionException{
    public MethodNotFoundException(String key, String expression, String message) {
        super(key, expression, message);
    }
}
