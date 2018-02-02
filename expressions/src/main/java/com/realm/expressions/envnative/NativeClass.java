package com.realm.expressions.envnative;

import android.content.Context;
import android.support.annotation.NonNull;

/**
 * Created by usuario on 15/01/18.
 */

public class NativeClass {

    private Context context;

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public NativeClass(@NonNull Context context) {
        this.context = context;
        if(context == null){
            throw new NullPointerException("Context cannot be null in native class!");
        }
    }
}
