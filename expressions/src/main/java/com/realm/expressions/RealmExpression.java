package com.realm.expressions;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.realm.expressions.envnative.NativeClass;
import com.realm.expressions.envnative.environment.DateNative;
import com.realm.expressions.envnative.environment.DeviceNative;
import com.realm.expressions.exceptions.EnvironmentNotFoundException;
import com.realm.expressions.exceptions.MethodNotFoundException;
import com.realm.expressions.exceptions.ParameterTypeUnsupportedException;
import com.realm.expressions.interfaces.OnEvaluationListener;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import io.rapidpro.expressions.EvaluatedTemplate;
import io.rapidpro.expressions.EvaluationContext;
import io.rapidpro.expressions.EvaluatorBuilder;
import io.rapidpro.expressions.evaluator.Evaluator;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmModel;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;

/**
 * Created by usuario on 15/01/18.
 */

public class RealmExpression {

    // TAG for Log
    private String TAG = "RealmExpreesions";
    // Crash when Realm instance is null.
    private static String instanceNullMessage = "Realm parameter cannot be null!";
    // Crash when Context is null.
    private static String contextNullMessage = "Context cannot be null!";
    // Crash when Environment is not found in current instance.
    private String environmentNotFoundException = "Environment %s not found in this instance!";

    // Current Realm instance.
    private static Realm realmInstance;
    // App Context.
    private static Context appContext;
    // All valid disposable Realm objects.
    private static HashMap<String, Object> disposableEnvironmentObejcts;
    // All valid not disposable Realm objects.
    private static HashMap<String, Object> noDisposableEnvironmentObjects;
    // All Realm Objects Mapped in init method.
    //private static ArrayList<EnvironmentClass> environmentClasses;
    private static HashMap<String, Class<? extends RealmModel>> environmentClasses;
    // All expressions of this instance.
    private LinkedHashMap<String, String> expressions;
    // Expression template compatible with RapidPro.
    private String template;
    // Native classes.
    private static HashMap<String, NativeClass> nativeClasses;
    // Evaluated expressions.
    private HashMap<String, Object> evaluatedExpressions;

    // Current Key in progress...
    private String currentKeyOnEvaluation;
    // Current Expression in progress..
    private String currentExpressionOnEvaluation;
    // Evaluation Listener
    private OnEvaluationListener listener;


    // region Regex
    private String mainMatchRP = "@REXP\\((.*)\\)";
    private String mainMatchREUSE = "@REUSE\\((.*)\\)";
    private String matchKey = "\\$(.*?)\\.";
    private String matchNative = "\\@@(.*?)\\.";
    private String matchNativeExtract = "@@(.*?)\\)";
    private String matchRapidPROWithParams = "@RP\\((.*),";
    private String matchRapidPROWithoutParams = "@RP\\((.*)\\)\\)\\)*\\)*.";
    private String matchMethodWithParams = "@(.*?)\\(.*";
    private String matchReservedWords = "#(.*?)\\(.*";
    private String matchEnvironments = "@ENV\\((.*?\\))\\)";
    private String matchREUSE = "@REUSE\\((.*?\\))\\)";
    // endregion


    // Starts with Realm instance
    public static void init(@Nonnull Realm realm, @Nonnull Context context){
        if(realm != null) {

            if (disposableEnvironmentObejcts == null) {
                disposableEnvironmentObejcts = new HashMap<>();
            }

            if(noDisposableEnvironmentObjects == null){
                noDisposableEnvironmentObjects = new HashMap<>();
            }

            if(nativeClasses == null) {
                nativeClasses = new HashMap<>();
            }

            appContext = context;
            if(context != null) {
                realmInstance = realm;
                // Realm DB mapping
                Iterator<Class<? extends RealmModel>> schemaClasses = realmInstance.getConfiguration().getRealmObjectClasses().iterator();
                //environmentClasses = new ArrayList<>();
                environmentClasses = new HashMap<>();
                while (schemaClasses.hasNext()) {
                    Class<? extends RealmModel> schema = schemaClasses.next();
                    //environmentClasses.add(new EnvironmentClass(schema.getSimpleName().toLowerCase(), schema));
                    environmentClasses.put("_" + schema.getSimpleName().toLowerCase(), schema);
                }

                loadDefaultNative();

            }else{
                throw new NullPointerException(contextNullMessage);
            }
        }else {
            throw new NullPointerException(instanceNullMessage);
        }
    }

    /**
     * Load all default native classes.
     */
    private static void loadDefaultNative(){
        nativeClasses.put("device", new DeviceNative(appContext));
        nativeClasses.put("date", new DateNative(appContext));
    }

    /**
     * Add new native class.
     * @param key
     * @param nativeClass
     */
    public static void addNewNativeClass(String key, Class<? extends NativeClass> nativeClass){

        if(nativeClass == null){
            throw new NullPointerException("Native class cannot be null!");
        }

        if(key == null){
            throw new NullPointerException("Key of NativeClass cannot be null!");
        }

        if(nativeClasses == null) {
            nativeClasses = new HashMap<>();
        }

        try {
            if(nativeClasses.containsKey(key)){
                Log.e("NativeClass", "Class " + nativeClass.getSimpleName() + " already exists in this instance...");
                return;
            }
            nativeClasses.put(key, nativeClass.newInstance());
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * RealmExpression constructor.
     * @param builder
     * @param template
     */
    public RealmExpression(Builder builder, String template){
        this.expressions = builder.expressions;
        this.template = template;
    }

    /**
     * Add new environment RealmObject to instance.
     * @param key
     * @param object
     */
    public static void addEnvironmentObject(@Nonnull String key, @Nonnull Object object, boolean isDisposable){

        if(key == null){
            throw new NullPointerException("Key of Environment cannot be null!");
        }

        else if(object == null){
            throw new NullPointerException("Environment cannot be null!");
        }

        if(isDisposable) {
            if(disposableEnvironmentObejcts.containsKey(key)){
                Log.e("Environment", "This Environment already exists in this instance!");
                return;
            }
            disposableEnvironmentObejcts.put(key, object);
        }else {
            if(noDisposableEnvironmentObjects.containsKey(key)){
                Log.e("Environment", "This Environment already exists in this instance!");
                return;
            }

            // Move RealmModel from original thread to this instance...
            if(object instanceof RealmModel){
                object = realmInstance.copyFromRealm((RealmModel) object);
            }
            noDisposableEnvironmentObjects.put(key, object);
        }
    }

    /**
     * Remove specific environment
     * @param key
     */
    public static void removeEnvironment(String key){
        noDisposableEnvironmentObjects.remove(key);
        disposableEnvironmentObejcts.remove(key);
    }

    /**
     * Remove specific Native Class
     * @param key
     */
    public static void removeNativeClass(String key){
        nativeClasses.clear();
        loadDefaultNative();
        Log.i("RealmExpreesions", key + " native class is removed");
    }

    /**
     * Important to free memory
     */
    public static void dispose(){
        if(disposableEnvironmentObejcts != null){
            disposableEnvironmentObejcts.clear();
        }
        Log.i("RealmExpreesions", "Dispose all Disposable Environment Objects...");
    }

    /**
     * Remove all Environment.
     */
    public static void disposeAll(){
        if(disposableEnvironmentObejcts != null){
            disposableEnvironmentObejcts.clear();
        }

        if(noDisposableEnvironmentObjects != null){
            noDisposableEnvironmentObjects.clear();
        }

        Log.i("RealmExpreesions", "Dispose all...");
    }


    /**
     * Evaluate all RealmExpression instance.
     */
    public void evaluate(boolean isAsync, final OnEvaluationListener listener){
        this.listener = listener;

        if(isAsync) {
            evaluationAsFlowable().doOnNext(new Consumer<Object>() {
                @Override
                public void accept(Object o) throws Exception {
                    if (listener != null) {
                        listener.onExpressionResult(currentKeyOnEvaluation, currentExpressionOnEvaluation, o);
                    }
                }
            }).doOnError(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                    throwable.printStackTrace();
                    if (listener != null) {
                        listener.onError(currentKeyOnEvaluation, currentExpressionOnEvaluation, throwable);
                    }
                }
            }).doOnComplete(new Action() {
                @Override
                public void run() throws Exception {
                   finalizeEvaluation();
                }
            }).subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread()).subscribe();
        }else {
            Iterator<String> keys = expressions.keySet().iterator();
            while (keys.hasNext()){
                String key = keys.next();
                String value = expressions.get(key);
                Object obj = evaluation(key, value);
                if (listener != null) {
                    listener.onExpressionResult(currentKeyOnEvaluation, currentExpressionOnEvaluation, obj);
                }
            }

            finalizeEvaluation();
        }
    }


    /**
     * Finalize all evaluation
     */
    private void finalizeEvaluation(){
        // Replace all evaluated expressions in template.
        if (evaluatedExpressions != null) {
            String oldTemplate = template;
            Iterator<String> evExpressionsKeys = evaluatedExpressions.keySet().iterator();
            while (evExpressionsKeys.hasNext()) {
                String evKey = evExpressionsKeys.next();
                Object evValue = evaluatedExpressions.get(evKey);
                if (evValue instanceof RealmObject) {
                    if (template.contains(evKey)) {
                        Log.e("RealmExpressions", evKey
                                + " is a RealmObject and cannot be in any template!");
                    }
                } else {
                    template = template.replace(evKey, evValue.toString());
                }
            }

            String result = evaluateRP(template);

            if (listener != null) {
                listener.onEvaluationResult(oldTemplate, result);
            }

            Log.i("RealmExpressions", "Successful template: " + result);

        } else {
            if (listener != null) {
                listener.onError(currentKeyOnEvaluation, currentExpressionOnEvaluation,
                        new IllegalStateException("Please add expressions!"));
            }
            Log.e("RealmExpressions", "Expressions not found!");
        }
    }

    /**
     * Simple evaluation in async task with Java Rx
     * @return
     */
    private Flowable<Object> evaluationAsFlowable(){

       return Flowable.create(new FlowableOnSubscribe<Object>() {
            @Override
            public void subscribe(FlowableEmitter<Object> e) throws Exception {
                if(e.isCancelled()){
                    return;
                }

                Iterator<String> keys = expressions.keySet().iterator();
                while (keys.hasNext()){
                    String key = keys.next();
                    String value = expressions.get(key);
                    e.onNext(evaluation(key, value));
                }

                e.onComplete();
            }
        }, BackpressureStrategy.BUFFER);
    }

    /**
     * Simple evaluation
     * @param key
     * @param value
     * @return
     */
    private Object evaluation(String key, String value){
        currentKeyOnEvaluation = key;
        currentExpressionOnEvaluation = value;

        // Search all @RP first and evaluate it with params.
        Matcher matcherRP = Pattern.compile(matchRapidPROWithParams).matcher(value);
        HashMap<String, Object> matchesValues = new HashMap<>();
        while (matcherRP.find()){
            String matcherKey = matcherRP.group(1);
            System.out.println("MatcherKey: " + matcherKey);
            matchesValues.put(matcherKey, evaluateRP(matcherKey));
        }

        // region Search all @RP first and evaluate it without params.
        matcherRP = Pattern.compile(matchRapidPROWithoutParams).matcher(value);
        while (matcherRP.find()){
            String matcherKey = matcherRP.group(1);
            matchesValues.put(matcherKey, evaluateRP(matcherKey));
        }

        // Replace all '@RP(...)' in 'value' with 'matchesValues'
        Iterator<String> matcherKeys = matchesValues.keySet().iterator();
        while (matcherKeys.hasNext()){
            String mKey = matcherKeys.next();
            Object val = matchesValues.get(mKey);
            value = value.replace("@RP("+ mKey +")", val.toString());
        }

        // endregion

        // region Evaluate all @@
        Matcher matcherNative = Pattern.compile(matchNativeExtract).matcher(value);
        matchesValues = new HashMap<>();
        while (matcherNative.find()){
            String matcherKey = matcherNative.group(0);
            matchesValues.put(matcherKey, resolveExpression("", null, matcherKey, ""));
        }

        // Replace
        Iterator<String> nativeKeys = matchesValues.keySet().iterator();
        while (nativeKeys.hasNext()){
            String mKey = nativeKeys.next();
            String val = matchesValues.get(mKey).toString();
            value = value.replace(mKey, val);
        }

        // endregion

        // region Evaluate all @ENV
        Matcher matcherEnv = Pattern.compile(matchEnvironments).matcher(value);
        matchesValues = new HashMap<>();

        while (matcherEnv.find()){
            String matcherKey = matcherEnv.group(0);
            String matcherValue = matcherEnv.group(1);
            matchesValues.put(matcherKey, resolveExpression("", null, matcherValue, ""));
        }

        // Replace
        Iterator<String> envKeys = matchesValues.keySet().iterator();
        while (envKeys.hasNext()){
            String mKey = envKeys.next();
            String val = matchesValues.get(mKey).toString();
            value = value.replace(mKey, val);
        }

        // endregion

        // region Evaluate all @REUSE
        if(!value.startsWith("@REUSE")) {
            Matcher matcherREUSE = Pattern.compile(matchREUSE).matcher(value);
            matchesValues = new HashMap<>();

            while (matcherREUSE.find()) {
                String matcherKey = matcherREUSE.group(0);
                String matcherValue = matcherREUSE.group(1);
                String mKey = matcherValue.split("\\.")[0];
                matcherValue = matcherValue.replace(mKey, "");
                matchesValues.put(matcherKey, resolveExpression(mKey, evaluatedExpressions.get(mKey), matcherValue, ""));
            }

            // Replace
            Iterator<String> reuseKeys = matchesValues.keySet().iterator();
            while (reuseKeys.hasNext()) {
                String mKey = reuseKeys.next();
                String val = matchesValues.get(mKey).toString();
                value = value.replace(mKey, val);
            }
        }
        // endregion

        return resolveExpression(key, null, value, "");
    }

    /**
     * Resolve any compatible expression.
     * @param key
     * @param environment
     * @param expression
     * @param params
     * @return
     */
    private Object resolveExpression(String key, Object environment, String expression, String params){
        if(expression.startsWith("@REUSE")){
            Matcher m = Pattern.compile(mainMatchREUSE).matcher(expression);
            String data = "";
            while (m.find()){
                data = m.group(1);
            }


            expression = data;
            String mKey = expression.split("\\.")[0];

            expression = expression.replace(mKey, "");

            if(evaluatedExpressions != null && evaluatedExpressions.containsKey(mKey)){
                return resolveExpression(key, evaluatedExpressions.get(mKey), expression, params);
            }else {
                if(listener != null){
                    listener.onError(currentKeyOnEvaluation, currentExpressionOnEvaluation, new IllegalStateException("Please add '"+ mKey +"' first and then evaluate expression!"));
                }
                throw new IllegalStateException("Please add '"+ mKey +"' first and then evaluate expression!");
            }

        }else if(expression.startsWith("@REXP")){
            Matcher m = Pattern.compile(mainMatchRP).matcher(expression);
            String group = "";
            while (m.find()){
                group = m.group(1);
            }
            return resolveExpression(key, null, group, params);
        }else if(expression.startsWith("$")){
            Matcher m = Pattern.compile(matchKey).matcher(expression);
            String group = "";
            String pre = "";
            while (m.find()){
                pre = m.group(0);
                group = m.group(1);
            }

            expression = expression.replace(pre, "");
            Object mEnvironment = searchEnvironment(group);

            return resolveExpression(key, mEnvironment, expression, params);
        }else if(expression.startsWith("@@")){
            // Native class
            Matcher m = Pattern.compile(matchNative).matcher(expression);
            String group = "";
            String pre = "";
            while (m.find()){
                pre = m.group(0);
                group = m.group(1);
            }

            expression = expression.replace(pre, "");
            Object mEnvironment = searchInNative(group);

            return resolveExpression(key, mEnvironment, expression, params);
        }else if(expression.startsWith("@") || expression.startsWith(".@")){
            if(expression.startsWith(".@")){
                expression = expression.substring(1);
            }
            Matcher m = Pattern.compile(matchMethodWithParams).matcher(expression);
            String group = "";
            while (m.find()){
                group = m.group(1);
                break;
            }

            // For replace I
            Matcher mD = Pattern.compile("@"+ group +"\\((.*?)\\)").matcher(expression);
            String mData = "";
            while (mD.find()){
                mData = mD.group(1);
                break;
            }

            expression = expression.replace("@" + group + "("+ mData +")", "");
            Object b = null;
            if(environment instanceof Class){ // For static classes
                b = executeMethod(null, (Class) environment, null, group, mData);
            }else if(environment == null){
                return resolveExpression(key, environment, "", "");
            }else {
                b = executeMethod(null, null, environment, group, mData);
            }

            return resolveExpression(key, b, expression, mData);

        }if(expression.startsWith(".#") || expression.startsWith("#")){ // Reserved words...
            Matcher m = Pattern.compile(matchReservedWords).matcher(expression);
            String group = "";
            while (m.find()){
                group = m.group(1);
            }

            // For replace I
            Matcher mD = Pattern.compile("#"+ group +"\\((.*?)\\)").matcher(expression);
            String mData = "";
            while (mD.find()){
                mData = mD.group(1);
                break;
            }

            // Resolve extras
            expression = expression.replace((expression.startsWith(".#") ? ".#" : "#") + group + "("+ mData +")", "");
            return resolveExpression(key, resolveReservedWord(group, environment, mData), expression, params);
        }else {

            // Validation for @@
            if(!key.isEmpty()) {
                addEvaluatedExpression(key, environment != null ? environment : "null");
            }

            if(environment != null) {
                return environment;
            }else {
                Log.e("RealmExpressions", "Not found or inaccessible object!");
                if(listener != null){
                    listener.onError(currentKeyOnEvaluation, currentExpressionOnEvaluation, new Exception("Not found or inaccessible object!"));
                }
                return "null";
            }
        }
    }


    /**
     * Resolve all reserved words.
     * @param reservedWord
     * @param environment
     * @param extra
     * @return
     */
    private Object resolveReservedWord(String reservedWord, Object environment, String extra){
        if(environment == null){
            return null;
        }

        switch (reservedWord){
            // region len
            case "len":
                // RealmResult List
                if(environment instanceof RealmResults){
                    return ((RealmResults)environment).size();
                }else if(environment instanceof String){
                    return String.valueOf(environment).length();
                }else if(environment instanceof RealmList){
                    return ((RealmList)environment).size();
                }else {
                    new MethodNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Cannot resolve '"+ reservedWord +"'");
                }
                // endregion
            // region has
            case "has":
                if(environment instanceof RealmResults){

                }else if(environment instanceof String){

                }else {
                    new MethodNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Cannot resolve '"+ reservedWord +"'");
                }
                break;
            //endregion
            // region last
            case "last":
                if(environment instanceof RealmResults){
                    return ((RealmResults)environment).get(((RealmResults)environment).size());
                }else if(environment instanceof String){
                    return environment.toString().substring(environment.toString().length());
                }else {
                    new MethodNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Cannot resolve '"+ reservedWord +"'");
                }
            // endregion
            // region first
            case "first":
                if(environment instanceof RealmResults){
                    if(((RealmResults)environment).size() > 0) {
                        return ((RealmResults) environment).first();
                    }
                }else if(environment instanceof String){
                    return environment.toString().substring(1);
                }else {
                    new MethodNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Cannot resolve '"+ reservedWord +"'");
                }
                break;
            // endregion
            // region toJSON
            case "toJSON":
                return new Gson().toJson(environment);
            // endregion
            // region between
            case "between":
                try{
                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    JSONObject data = new JSONObject(extra);

                    if(!data.has("fieldName")){
                        throw new IllegalStateException("between method needs 'fieldName' param!");
                    }

                    if(!data.has("from")){
                        throw new IllegalStateException("between method needs 'from' param!");
                    }

                    if(!data.has("to")){
                        throw new IllegalStateException("between method needs 'to' param!");
                    }

                    String fieldName = data.getString("fieldName");
                    Object from = data.get("from");
                    Object to = data.get("to");

                    if(from instanceof String && to instanceof String){
                        results = results.where().between(fieldName,
                                DateTime.parse(String.valueOf(from)).toDate(),
                                DateTime.parse(String.valueOf(to)).toDate()).findAll();
                    }else if(from instanceof Integer && to instanceof Integer){
                        results = results.where().between(fieldName,
                                Integer.parseInt(String.valueOf(from)),
                                Integer.parseInt(String.valueOf(to))).findAll();
                    }else if(from instanceof Float && to instanceof Float){
                        results = results.where().between(fieldName,
                                Float.parseFloat(String.valueOf(from)),
                                Float.parseFloat(String.valueOf(to))).findAll();
                    }else if(from instanceof Double && to instanceof Double){
                        results = results.where().between(fieldName,
                                Double.parseDouble(String.valueOf(from)),
                                Double.parseDouble(String.valueOf(to))).findAll();
                    }else {
                        throw new IllegalStateException("From and To values are incompatibles!");
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion

            // region extendedGreaterThanOrEqualTo
            case "extendedGreaterThanOrEqualTo":
                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);
                        Iterator<String> keys = extraObj.keys();
                        while (keys.hasNext()){
                            String key = keys.next();
                            Object value = extraObj.get(key);
                            if(value instanceof String){
                                try{
                                    results = results.where().greaterThanOrEqualTo(key, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }else if(value instanceof Integer){
                                results = results.where().greaterThanOrEqualTo(key, Integer.parseInt(String.valueOf(value))).findAll();
                            }else if(value instanceof Double){
                                results = results.where().greaterThanOrEqualTo(key, Double.parseDouble(String.valueOf(value))).findAll();
                            }else if(value instanceof Long){
                                results = results.where().greaterThanOrEqualTo(key, Long.parseLong(String.valueOf(value))).findAll();
                            }else if(value instanceof Float){
                                results = results.where().greaterThanOrEqualTo(key, Float.parseFloat(String.valueOf(value))).findAll();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion
            // region greaterThanOrEqualTo
            case "greaterThanOrEqualTo":
                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);

                        if(!extraObj.has("fieldName")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'fieldName' param!");
                        }

                        if(!extraObj.has("value")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'value' param!");
                        }

                        String fieldName = extraObj.getString("fieldName");
                        Object value = extraObj.get("value");

                        if(value instanceof String){
                            try{
                                results = results.where().greaterThanOrEqualTo(fieldName, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }else if(value instanceof Integer){
                            results = results.where().greaterThanOrEqualTo(fieldName, Integer.parseInt(String.valueOf(value))).findAll();
                        }else if(value instanceof Double){
                            results = results.where().greaterThanOrEqualTo(fieldName, Double.parseDouble(String.valueOf(value))).findAll();
                        }else if(value instanceof Long){
                            results = results.where().greaterThanOrEqualTo(fieldName, Long.parseLong(String.valueOf(value))).findAll();
                        }else if(value instanceof Float){
                            results = results.where().greaterThanOrEqualTo(fieldName, Float.parseFloat(String.valueOf(value))).findAll();
                        }else {
                            throw new IllegalStateException("Value "+ value +" is not valid!");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion

            // region extendedLessThanOrEqualTo
            case "extendedLessThanOrEqualTo":
                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);
                        Iterator<String> keys = extraObj.keys();
                        while (keys.hasNext()){
                            String key = keys.next();
                            Object value = extraObj.get(key);
                            if(value instanceof String){
                                try{
                                    results = results.where().lessThanOrEqualTo(key, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }else if(value instanceof Integer){
                                results = results.where().lessThanOrEqualTo(key, Integer.parseInt(String.valueOf(value))).findAll();
                            }else if(value instanceof Double){
                                results = results.where().lessThanOrEqualTo(key, Double.parseDouble(String.valueOf(value))).findAll();
                            }else if(value instanceof Long){
                                results = results.where().lessThanOrEqualTo(key, Long.parseLong(String.valueOf(value))).findAll();
                            }else if(value instanceof Float){
                                results = results.where().lessThanOrEqualTo(key, Float.parseFloat(String.valueOf(value))).findAll();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion
            // region lessThanOrEqualTo
            case "lessThanOrEqualTo":
                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);

                        if(!extraObj.has("fieldName")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'fieldName' param!");
                        }

                        if(!extraObj.has("value")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'value' param!");
                        }

                        String fieldName = extraObj.getString("fieldName");
                        Object value = extraObj.get("value");

                        if(value instanceof String){
                            try{
                                results = results.where().lessThanOrEqualTo(fieldName, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }else if(value instanceof Integer){
                            results = results.where().lessThanOrEqualTo(fieldName, Integer.parseInt(String.valueOf(value))).findAll();
                        }else if(value instanceof Double){
                            results = results.where().lessThanOrEqualTo(fieldName, Double.parseDouble(String.valueOf(value))).findAll();
                        }else if(value instanceof Long){
                            results = results.where().lessThanOrEqualTo(fieldName, Long.parseLong(String.valueOf(value))).findAll();
                        }else if(value instanceof Float){
                            results = results.where().lessThanOrEqualTo(fieldName, Float.parseFloat(String.valueOf(value))).findAll();
                        }else {
                            throw new IllegalStateException("Value "+ value +" is not valid!");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion

            // region extendedLessThan
            case "extendedLessThan":
                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);
                        Iterator<String> keys = extraObj.keys();
                        while (keys.hasNext()){
                            String key = keys.next();
                            Object value = extraObj.get(key);
                            if(value instanceof String){
                                try{
                                    results = results.where().lessThan(key, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }else if(value instanceof Integer){
                                results = results.where().lessThan(key, Integer.parseInt(String.valueOf(value))).findAll();
                            }else if(value instanceof Double){
                                results = results.where().lessThan(key, Double.parseDouble(String.valueOf(value))).findAll();
                            }else if(value instanceof Long){
                                results = results.where().lessThan(key, Long.parseLong(String.valueOf(value))).findAll();
                            }else if(value instanceof Float){
                                results = results.where().lessThan(key, Float.parseFloat(String.valueOf(value))).findAll();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion
            // region lessThan
            case "lessThan":
                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);

                        if(!extraObj.has("fieldName")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'fieldName' param!");
                        }

                        if(!extraObj.has("value")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'value' param!");
                        }

                        String fieldName = extraObj.getString("fieldName");
                        Object value = extraObj.get("value");

                        if(value instanceof String){
                            try{
                                results = results.where().lessThan(fieldName, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }else if(value instanceof Integer){
                            results = results.where().lessThan(fieldName, Integer.parseInt(String.valueOf(value))).findAll();
                        }else if(value instanceof Double){
                            results = results.where().lessThan(fieldName, Double.parseDouble(String.valueOf(value))).findAll();
                        }else if(value instanceof Long){
                            results = results.where().lessThan(fieldName, Long.parseLong(String.valueOf(value))).findAll();
                        }else if(value instanceof Float){
                            results = results.where().lessThan(fieldName, Float.parseFloat(String.valueOf(value))).findAll();
                        }else {
                            throw new IllegalStateException("Value "+ value +" is not valid!");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion

            // region extendedGreaterThan
            case "extendedGreaterThan":
                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);
                        Iterator<String> keys = extraObj.keys();
                        while (keys.hasNext()){
                            String key = keys.next();
                            Object value = extraObj.get(key);
                            if(value instanceof String){
                                try{
                                    results = results.where().greaterThan(key, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }else if(value instanceof Integer){
                                results = results.where().greaterThan(key, Integer.parseInt(String.valueOf(value))).findAll();
                            }else if(value instanceof Double){
                                results = results.where().greaterThan(key, Double.parseDouble(String.valueOf(value))).findAll();
                            }else if(value instanceof Long){
                                results = results.where().greaterThan(key, Long.parseLong(String.valueOf(value))).findAll();
                            }else if(value instanceof Float){
                                results = results.where().greaterThan(key, Float.parseFloat(String.valueOf(value))).findAll();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion
            // region greaterThan
            case "greaterThan":
                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);

                        if(!extraObj.has("fieldName")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'fieldName' param!");
                        }

                        if(!extraObj.has("value")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'value' param!");
                        }

                        String fieldName = extraObj.getString("fieldName");
                        Object value = extraObj.get("value");

                        if(value instanceof String){
                            try{
                                results = results.where().greaterThan(fieldName, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }else if(value instanceof Integer){
                            results = results.where().greaterThan(fieldName, Integer.parseInt(String.valueOf(value))).findAll();
                        }else if(value instanceof Double){
                            results = results.where().greaterThan(fieldName, Double.parseDouble(String.valueOf(value))).findAll();
                        }else if(value instanceof Long){
                            results = results.where().greaterThan(fieldName, Long.parseLong(String.valueOf(value))).findAll();
                        }else if(value instanceof Float){
                            results = results.where().greaterThan(fieldName, Float.parseFloat(String.valueOf(value))).findAll();
                        }else {
                            throw new IllegalStateException("Value "+ value +" is not valid!");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion

            // region extendedEqualTo
            case "extendedEqualTo":

                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);
                        Iterator<String> keys = extraObj.keys();
                        while (keys.hasNext()){
                            String key = keys.next();
                            Object value = extraObj.get(key);
                            if(value instanceof String){
                                try{
                                    results = results.where().equalTo(key, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                                }catch (Exception e){
                                    results = results.where().equalTo(key, String.valueOf(value)).findAll();
                                }
                            }else if(value instanceof Integer){
                                results = results.where().equalTo(key, Integer.parseInt(String.valueOf(value))).findAll();
                            }else if(value instanceof Boolean){
                                results = results.where().equalTo(key, Boolean.parseBoolean(String.valueOf(value))).findAll();
                            }else if(value instanceof Double){
                                results = results.where().equalTo(key, Double.parseDouble(String.valueOf(value))).findAll();
                            }else if(value instanceof Long){
                                results = results.where().equalTo(key, Long.parseLong(String.valueOf(value))).findAll();
                            }else if(value instanceof Float){
                                results = results.where().equalTo(key, Float.parseFloat(String.valueOf(value))).findAll();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion
            // region equalTo
            case "equalTo":
                try{

                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);

                        if(!extraObj.has("fieldName")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'fieldName' param!");
                        }

                        if(!extraObj.has("value")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'value' param!");
                        }

                        String fieldName = extraObj.getString("fieldName");
                        Object value = extraObj.get("value");

                        if(value instanceof String){
                            try{
                                results = results.where().equalTo(fieldName, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                            }catch (Exception e){
                                results = results.where().equalTo(fieldName, String.valueOf(value)).findAll();
                            }
                        }else if(value instanceof Integer){
                            results = results.where().equalTo(fieldName, Integer.parseInt(String.valueOf(value))).findAll();
                        }else if(value instanceof Boolean){
                            results = results.where().equalTo(fieldName, Boolean.parseBoolean(String.valueOf(value))).findAll();
                        }else if(value instanceof Double){
                            results = results.where().equalTo(fieldName, Double.parseDouble(String.valueOf(value))).findAll();
                        }else if(value instanceof Long){
                            results = results.where().equalTo(fieldName, Long.parseLong(String.valueOf(value))).findAll();
                        }else if(value instanceof Float){
                            results = results.where().equalTo(fieldName, Float.parseFloat(String.valueOf(value))).findAll();
                        }else {
                            throw new IllegalStateException("Value "+ value +" is not valid!");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion

            // region extendedNotEqualTo
            case "extendedNotEqualTo":

                try{
                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);
                        Iterator<String> keys = extraObj.keys();
                        while (keys.hasNext()){
                            String key = keys.next();
                            Object value = extraObj.get(key);
                            if(value instanceof String){
                                try{
                                    results = results.where().equalTo(key, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                                }catch (Exception e){
                                    results = results.where().equalTo(key, String.valueOf(value)).findAll();
                                }
                            }else if(value instanceof Integer){
                                results = results.where().notEqualTo(key, Integer.parseInt(String.valueOf(value))).findAll();
                            }else if(value instanceof Boolean){
                                results = results.where().notEqualTo(key, Boolean.parseBoolean(String.valueOf(value))).findAll();
                            }else if(value instanceof Double){
                                results = results.where().notEqualTo(key, Double.parseDouble(String.valueOf(value))).findAll();
                            }else if(value instanceof Long){
                                results = results.where().notEqualTo(key, Long.parseLong(String.valueOf(value))).findAll();
                            }else if(value instanceof Float){
                                results = results.where().notEqualTo(key, Float.parseFloat(String.valueOf(value))).findAll();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
                // endregion
            // region notEqualTo
            case "notEqualTo":
                try{
                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    try {
                        JSONObject extraObj = new JSONObject(extra);

                        if(!extraObj.has("fieldName")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'fieldName' param!");
                        }

                        if(!extraObj.has("value")){
                            throw new IllegalStateException("greaterThanOrEqualTo method needs 'value' param!");
                        }

                        String fieldName = extraObj.getString("fieldName");
                        Object value = extraObj.get("value");

                        if(value instanceof String){
                            try{
                                results = results.where().equalTo(fieldName, DateTime.parse(String.valueOf(value)).toDate()).findAll();
                            }catch (Exception e){
                                results = results.where().equalTo(fieldName, String.valueOf(value)).findAll();
                            }
                        }else if(value instanceof Integer){
                            results = results.where().notEqualTo(fieldName, Integer.parseInt(String.valueOf(value))).findAll();
                        }else if(value instanceof Boolean){
                            results = results.where().notEqualTo(fieldName, Boolean.parseBoolean(String.valueOf(value))).findAll();
                        }else if(value instanceof Double){
                            results = results.where().notEqualTo(fieldName, Double.parseDouble(String.valueOf(value))).findAll();
                        }else if(value instanceof Long){
                            results = results.where().notEqualTo(fieldName, Long.parseLong(String.valueOf(value))).findAll();
                        }else if(value instanceof Float){
                            results = results.where().notEqualTo(fieldName, Float.parseFloat(String.valueOf(value))).findAll();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion

            // region allSorted
            case "allSorted":
                try{
                    RealmResults<? extends RealmModel> results = null;

                    if(environment instanceof RealmResults){
                        results = (RealmResults)environment;
                    }else {

                        Class<? extends RealmModel> newEnvironment = (Class<? extends RealmModel>) environment;
                        results = realmInstance.where(newEnvironment).findAll();
                    }

                    JSONObject data = new JSONObject(extra);

                    if(!data.has("fieldName")){
                        throw new IllegalStateException("allSorted method receives two params...( fieldName , ASC | DESC )");
                    }


                    String fieldName = data.getString("fieldName");
                    String sortType = "DESC";
                    if(data.has("sortType")){
                        sortType = data.getString("sortType");
                    }

                    if(sortType.equals("ASC") || sortType.equals("DESC")) {
                        Sort sort = sortType.equals("ASC") ? Sort.ASCENDING : Sort.DESCENDING;
                        results = results.where().findAllSorted(fieldName, sort);
                    }else {
                        throw new IllegalStateException("Sort "+ sortType +" type is invalid. Only can be ASC or DESC!");
                    }

                    return results;
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Environment is not a instance of RealmResults!");
                }
            // endregion
            case "all":
                break;
            // region get
            case "get":

                try {
                    JSONObject object = new JSONObject(environment.toString());
                    return object.get(extra);
                } catch (JSONException e) {
                    try {
                        JSONArray array = new JSONArray(environment.toString());
                        return array.get(Integer.parseInt(environment.toString()));
                    } catch (JSONException e1) {
                    }
                }

                if(environment instanceof RealmResults){

                    RealmResults<Object> results = (RealmResults<Object>)environment;

                    try {
                        JSONObject extraObj = new JSONObject(extra);
                        Iterator<String> keys = extraObj.keys();
                        while (keys.hasNext()){
                            String key = keys.next();
                            Object value = extraObj.get(key);
                            if(value instanceof String){
                                results = results.where().equalTo(key, String.valueOf(value)).findAll();
                            }else if(value instanceof Integer){
                                results = results.where().equalTo(key, Integer.parseInt(String.valueOf(value))).findAll();
                            }else if(value instanceof Boolean){
                                results = results.where().equalTo(key, Boolean.parseBoolean(String.valueOf(value))).findAll();
                            }else if(value instanceof Double){
                                results = results.where().equalTo(key, Double.parseDouble(String.valueOf(value))).findAll();
                            }else if(value instanceof Long){
                                results = results.where().equalTo(key, Long.parseLong(String.valueOf(value))).findAll();
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    return results;
                }

            // endregion
            default:
                if(listener != null){
                    listener.onError(currentKeyOnEvaluation, currentExpressionOnEvaluation, new MethodNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Cannot resolve '"+ reservedWord +"'"));
                }

                new MethodNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Cannot resolve '"+ reservedWord +"'");
        }

        return null;
    }


    /**
     * Execute single method
     * @param realmObject
     * @param strMethod
     * @return
     */
    private Object executeMethod(RealmObject realmObject, Class mClass, Object nativeOrObject, String strMethod, String params){
        Object value = null;

        if(nativeOrObject instanceof RealmObject){
            realmObject = (RealmObject) nativeOrObject;
        }

        Method method = getMethod(realmObject, mClass != null ? mClass : nativeOrObject.getClass(), strMethod);
        if(method != null){
            Type[] parameterTypes = method.getGenericParameterTypes();
            String[] args = params.split(",");
            Object[] newArgs = new Object[parameterTypes.length];

            boolean typeNotFound = false;
            String paramNotFound = "";

            for(int i = 0; i < parameterTypes.length; i++){
                String simpleName = ((Class) parameterTypes[i]).getSimpleName();
                switch (simpleName){
                    case "String":
                        newArgs[i] = String.valueOf(args[i]);
                        break;
                    case "Integer":
                        newArgs[i] = Integer.parseInt(args[i]);
                        break;
                    case "Double":
                        newArgs[i] = Double.parseDouble(args[i]);
                        break;
                    default:
                        typeNotFound = true;
                        paramNotFound = args[i];
                        break;
                }
            }

            if(realmObject != null){
                try {
                    value = method.invoke(realmObject, newArgs);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }else if(mClass != null){
                try {
                    value = method.invoke(mClass, newArgs);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            } else if(nativeOrObject != null){
                try {
                    value = method.invoke(nativeOrObject, newArgs);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }

            if(typeNotFound){
                if(listener != null){
                    listener.onError(currentKeyOnEvaluation, currentExpressionOnEvaluation, new ParameterTypeUnsupportedException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Parameter " + paramNotFound + " is unsupported"));
                }
                new ParameterTypeUnsupportedException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Parameter " + paramNotFound + " is unsupported");
            }
        }else{
            if(listener != null){
                listener.onError(currentKeyOnEvaluation, currentExpressionOnEvaluation, new MethodNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Method '" + strMethod + "' is not found in this Class or Object!"));
            }
            new MethodNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Method '" + strMethod + "' is not found in this Class or Object!");
        }

        return value;
    }


    /**
     * Return Method by RealmObject or simple Class
     * @param realmObject
     * @param mClass
     * @param methodName
     * @return
     */
    private Method getMethod(RealmObject realmObject, Class mClass, String methodName){
        Class genericClass = null;
        if(realmObject != null){
            genericClass = realmObject.getClass();
            //methodName = "realmGet$" + methodName;
        }else if(mClass != null){ // if mClass is type of NativeClass
            genericClass = mClass;
        }

        Method[] methods = null;
        if(genericClass != null){
            methods = genericClass.getMethods();
        }

        Method method = null;
        for(Method m : methods){
            if(m.getName().equals(methodName)){
                method = m;
                break;
            }
        }

        return method;
    }
    /**
     * Add new evaluated expression.
     * @param key
     * @param value
     */
    private void addEvaluatedExpression(String key, Object value){
        if(evaluatedExpressions == null){
            evaluatedExpressions = new HashMap<>();
        }

        evaluatedExpressions.put(key, value);
    }

    /**
     * Evaluate some RapidPro expression.
     * @param expression
     * @return
     */
    private String evaluateRP(String expression){
        Evaluator evaluator = new EvaluatorBuilder().build();
        EvaluatedTemplate output = evaluator.evaluateTemplate(expression, new EvaluationContext(), false);
        return output.getOutput();
    }

    /**
     * Search in environment objects.
     * Search in environment classes.
     * @return
     */
    private Object searchEnvironment(String key){
        if(environmentClasses.containsKey(key)){
            return environmentClasses.get(key);
        }else if(disposableEnvironmentObejcts.containsKey(key)) {
            return disposableEnvironmentObejcts.get(key);
        }else if(noDisposableEnvironmentObjects.containsKey(key)) {
            return noDisposableEnvironmentObjects.get(key);
        }else {
            if(listener != null){
                listener.onError(currentKeyOnEvaluation, currentExpressionOnEvaluation, new EnvironmentNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, String.format(environmentNotFoundException, key)));
            }
            new EnvironmentNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, String.format(environmentNotFoundException, key));
            return null;
        }
    }

    /**
     * Search environment in native classes.
     * @param key
     * @return
     */
    private Object searchInNative(String key){
        if(nativeClasses.containsKey(key)){
            return nativeClasses.get(key);
        }

        if(listener != null){
            listener.onError(currentKeyOnEvaluation, currentExpressionOnEvaluation, new EnvironmentNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Native class " + key + " not found in this instance, please support this environment!"));
        }
        new EnvironmentNotFoundException(currentKeyOnEvaluation, currentExpressionOnEvaluation, "Native class " + key + " not found in this instance, please support this environment!");
        return null;
    }


    /**
     * Builder class.
     */
    public static class Builder{
        private LinkedHashMap<String, String> expressions;

        public Builder addExpression(String key, String expression){
            if(expressions == null){
                expressions = new LinkedHashMap<>();
            }

            expressions.put(key, expression);
            return this;
        }

        public RealmExpression withTemplate(String template){
            return new RealmExpression(this, template);
        }
    }
}
