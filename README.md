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
      compile 'com.github.javierpe:RealmExpressions:1.0.1'
  }
```

# Usage
1. RealmExpression instance
```
RealmExpression.init(Realm.getDefaultInstance(), context);
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
You can add environments objects

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
