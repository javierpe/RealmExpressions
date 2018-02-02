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
    }

    /**
     * Test
     */
    private void testRealmExpressions(){
        // Init instance.
        RealmExpression.init(Realm.getDefaultInstance(), this);
        // Build with default objects and native classes.
        //RealmExpression.addEnvironmentObject("user", KUser.getCurrentUser(), false);


        // Build...
        RealmExpression engine = new RealmExpression.Builder()
                //.addExpression("B1", "@REXP($user.@getFirstName())")
                //.addExpression("B1", "@@device.@lastLocation()")
                //.addExpression("B1", "@REXP($flow.@isAvailableToday())")
                //.addExpression("B2", "@REXP($_kevent.@byDate(18-01-18).#len())")
                //.addExpression("B1", "@@device.@imei()")
                //.addExpression("B2", "@@device.@randomUUID()")
                //.addExpression("B1", "@@date.@now()")
                //.addExpression("B1", "@REXP($_kevent.@byDate(@@date.@now()).#len())")
                //.addExpression("B1", "@REXP($_kevent.@byDate(@@date.@now()).#get({'UUID' : 750dd9a1-f972-4dcc-91a8-a84f623e6b45}).#first().@getServerID())")
                //.addExpression("B1", "@REXP($_kevent.#extendedEqualTo({'UUID' : 750dd9a1-f972-4dcc-91a8-a84f623e6b45}).#first().@getServerID())")
                //.addExpression("B1", "@REXP($_kevent.#equalTo({'fieldName' : UUID, 'value':750dd9a1-f972-4dcc-91a8-a84f623e6b45}).#first().@getServerID())")
                //.addExpression("B1", "@REXP($_kevent.#allSorted('fieldName' : serverID, 'sortType':ASC}).#first().@getServerID())")
                //.addExpression("B1", "@REXP($_kevent.@byDate(@@date.@now()).#allSorted(serverID, DESC).#first().@getServerID())")
                //.addExpression("B1", "@REXP($_kevent.@byDate(@@date.@now()).#allSorted({'fieldName':serverID, 'sortType':DESC}).#first().@getServerID())")
                //.addExpression("B1", "@REXP($_kevent.@byDate(@@date.@now()).#allSorted({'fieldName':serverID, 'sortType':DESC}).#first().@getClient().@getName())")
                //.addExpression("B1", "@REXP($_kevent.@byDate(@@date.@now()).#allSorted({'fieldName':serverID, 'sortType':DESC}).#first().@getClient())")
                //.addExpression("B2", "@REUSE(B1.@getName())")
                //.addExpression("B3", "@REUSE(B1.@getServerID())")
                //.addExpression("BX", "@REXP($_kclient.#extendedEqualTo({'userOwner.serverID':@ENV($user.@getServerID()), 'serverID':@REUSE(B1.@getServerID())}).@getProducts())")
                //.addExpression("BX", "@REXP($_kclient.#extendedEqualTo({'userOwner.serverID':@ENV($user.@getServerID()), 'serverID':@REUSE(B1.@getServerID())}).#first().@getProducts().#len())")
                .withTemplate("NAME: B1");


        // Start evaluation... and return Object...True..RealmResult...Date...etc
        engine.evaluateAsync(new OnEvaluationListener() {
            @Override
            public void onEvaluationResult(String template, Object result) {
                System.out.println("Template: " + template);
                System.out.println("Result: " + result);
                System.out.println("* * * * * * * * * * *");
            }

            @Override
            public void onExpressionResult(String key, String expression, Object result) {
                System.out.println("Key: " + key);
                System.out.println("Expression: " + expression);
                System.out.println("Result: " + result);
                System.out.println("= = = = = = = = = = = =");
            }

            @Override
            public void onError(String key, String expression, Throwable throwable) {

            }
        });
    }
}
