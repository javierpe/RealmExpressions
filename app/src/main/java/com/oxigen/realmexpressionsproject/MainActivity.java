package com.oxigen.realmexpressionsproject;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.realm.expressions.RealmExpression;
import com.realm.expressions.interfaces.OnEvaluationListener;

import io.realm.Realm;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Realm.init(this);
        new RealmExpression.Builder().withTemplate("").evaluateAsync(new OnEvaluationListener() {
            @Override
            public void onEvaluationResult(String template, Object result) {

            }

            @Override
            public void onExpressionResult(String key, String expression, Object result) {

            }

            @Override
            public void onError(String key, String expression, Throwable throwable) {

            }
        });
    }
}
