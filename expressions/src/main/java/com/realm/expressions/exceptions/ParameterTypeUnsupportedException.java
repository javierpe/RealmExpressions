package com.realm.expressions.exceptions;

/**
 * Created by usuario on 18/01/18.
 */

public class ParameterTypeUnsupportedException extends RealmExpressionException{
    public ParameterTypeUnsupportedException(String key, String expression, String message) {
        super(key, expression, message);
    }
}
