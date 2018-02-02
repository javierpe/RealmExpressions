package com.realm.expressions.interfaces;

/**
 * Created by usuario on 30/01/18.
 */

public interface OnEvaluationListener {
    void onEvaluationResult(String template, Object result);
    void onExpressionResult(String key, String expression, Object result);
    void onError(String key, String expression, Throwable throwable);
}
