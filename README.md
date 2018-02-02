# RealmExpressions
[![](https://jitpack.io/v/javierpe/RealmExpressions.svg)](https://jitpack.io/#javierpe/RealmExpressions)

Build strings expressions and transform it to Realm objects.

# Installation
1. Add it in your root build.gradle at the end of repositories
```
  allprojects {
      repositories {
        ...
        maven { url 'https://jitpack.io' }
      }
    }
```

2. Add the dependency
```
  dependencies {
      compile 'com.github.javierpe:RealmExpressions:1.0.2'
  }
```

# Usage
1. RealmExpression instance
```
RealmExpression.init(context);
```
2. Build engine
```
 RealmExpression engine = new RealmExpression.Builder().addExpression(KEY, EXPRESSION);
```
3. Add template (Optional)
```
 RealmExpression engine = new RealmExpression.Builder()
                 .addExpression(KEY, EXPRESSION)
                 .withTemplate("Result: KEY");
```
# Evaluation
1. Synchronous evaluation
```
Object result = engine.evaluateSync();
```
2. Asynchronous evaluation
```
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
        // Some error in expression..
    }
});
```
# Environments
You can add environments objects or Realm models so you can use them later
```
RealmExpression.addEnvironmentObject("user", User.getCurrentUser(), isDisposable);
```
Example: ```engine.addExpression("A", "user.@getLastName()");```

```isDisposable``` allows you to discard the object when necessary. 

# Native classes
A native class is not a RealmObejct and can be accessed from whatever expression. RealmExpression has default native classes
#### DateNative accessed from ```@@date```.
##### Methods
Access | Return | Params | Description | Example 
------------ | ------------- | ------------- | ------------- | -------------
```.@now()``` | String date | Not support | Get the current date | ```@@date.@now()```

#### DeviceNative accessed from ```@@device```.
##### Methods
Access | Return | Params | Description | Example 
------------ | ------------- | ------------- | ------------- | -------------
```.@randomUUID()``` | String UUID | Not support | Get random UUID | ```@@device.@randomUUID()```
```.@imei()``` | String IMEI | Not support | Get random UUID (Permission required since Android 6.0 Marshmallow) | ```@@device.@imei()```
```.@androidVersion()``` | String Android version | Not support | Get Android Version from ```Build.VERSION.RELEASE``` | ```@@device.@androidVersion()```
```.@appVersion()``` | String app version | Not support | Get app version name | ```@@device.@appVersion()```
```.@lastLocation()``` | String location | Not support | Get last know location with format ```19.0460714,-98.2267667``` | ```@@device.@lastLocation()```

## Create native class
You can create a native class extends by NativeClass and access it by you custom key. To add this class it is necessary call to ```RealmExpression.addNewNativeClass(KEY, NativeClass);```

# Others instance methods
Code | Description
------------ | -------------
```RealmExpression.dispose();``` | Discard all no disposable environment objects
```RealmExpression.disposeAll()``` | Remove all environment objects
```RealmExpression.removeEnvironment(KEY);``` | Removes an specific environment
```RealmExpression.removeNativeClass(KEY);``` | Removes an specific NativeClass

# Please start now! :grimacing:
### Write an expression
First you should know this, an expression starts with ```@REXP(...)```


# Love build this #

# License

```
Copyright 2018 javierpe

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
