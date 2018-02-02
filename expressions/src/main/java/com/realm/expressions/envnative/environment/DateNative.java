package com.realm.expressions.envnative.environment;

import android.content.Context;
import android.support.annotation.NonNull;

import com.realm.expressions.envnative.NativeClass;

import org.joda.time.DateTime;

/**
 * Created by usuario on 23/01/18.
 */

public class DateNative extends NativeClass{
    public DateNative(@NonNull Context context) {
        super(context);
    }

    /**
     * Return date with some format.
     * @param format
     * @return
     */
    public String now(String format){
        if(format != null && !format.isEmpty()){
            return DateTime.now().toString(format);
        }
        return DateTime.now().toString();
    }

    /**
     * Return simple date.
     * @return
     */
    public String now(){
        return DateTime.now().toString();
    }

    /**
     * Compare dates.
     * @param date1
     * @param date2
     * @return
     */
    public int compare(String date1, String date2){
        DateTime d1 = DateTime.parse(date1).withTimeAtStartOfDay();
        DateTime d2 = DateTime.parse(date2).withTimeAtStartOfDay();
        return d1.compareTo(d2);
    }

}
