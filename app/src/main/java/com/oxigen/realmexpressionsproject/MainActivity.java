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

    }
}
