package com.realm.expressions.envnative.environment;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.realm.expressions.envnative.NativeClass;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Created by usuario on 15/01/18.
 */

public class DeviceNative extends NativeClass {


    private final Pattern PATTERN_EMAIL = Pattern.compile("^[A-Za-z0-9._]{1,16}+@{1}+[a-z]{1,7}\\.[a-z]{1,3}$");
    private final Pattern PATTERN_IP = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");


    public DeviceNative(Context context) {
        super(context);
    }


    /**
     * Create new UUID
     * @return
     */
    public String randomUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Return phone IMEI
     *
     * @return
     */
    public String imei() {
        TelephonyManager tm = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "";
        }

        if (tm != null) {
            return tm.getDeviceId();
        } else {
            return "";
        }
    }

    /**
     * Return the currency value for money
     * @param val
     * @return
     */
    public static String convertToDefaultCurrency(double val) {
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(val);
    }

    /**
     * Convert value to specific locale.
     * @param val
     * @param language
     * @param country
     * @return
     */
    public static String convertCurrency(double val, String language, String country) {
        return NumberFormat.getCurrencyInstance(new Locale(language, country)).format(val);
    }

    /**
     * Validate if String is an email.
     * @param email
     * @return
     */
    public boolean isEmail(final String email) {
        return PATTERN_EMAIL.matcher(email).matches();
    }

    /**
     * Validate if String is an IP.
     * @param ip
     * @return
     */
    public boolean isIP(final String ip) {
        return PATTERN_IP.matcher(ip).matches();
    }

    /**
     * Get OS Version
     * @return
     */
    public String androidVersion() {
        return Build.VERSION.RELEASE;
    }

    /**
     * Return string to uppercase
     * @param s
     * @return
     */
    public String toUpperCase(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }

        return s.toUpperCase();
    }

    /**
     * Return string to lowercase
     * @param s
     * @return
     */
    public String toLowerCase(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }

        return s.toLowerCase();
    }

    /**
     * Get app version
     * @return
     */
    public String appVersion() {
        String version = "";
        PackageManager manager = getContext().getPackageManager();
        PackageInfo info = null;

        try {
            info = manager.getPackageInfo(getContext().getPackageName(), 0);
            version = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return version;
    }

    /**
     * Return last location
     * @return
     */
    public String lastLocation() {
        LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("RealmExpressions", "Please support for ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION permissions!");
            return "";
        }

        Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if(lastKnownLocation == null){
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if(lastKnownLocation == null){
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }

        if(lastKnownLocation == null){
            return "";
        }else {
            return String.valueOf(lastKnownLocation.getLatitude() + ", " + lastKnownLocation.getLongitude());
        }
    }

}
