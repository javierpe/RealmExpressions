package com.oxigen.storage;

import io.realm.RealmList;
import io.realm.RealmObject;

/**
 * Created by usuario on 02/02/18.
 */

public class Company extends RealmObject{
    private String name;
    private RealmList<Contact> credential;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RealmList<Contact> getCredential() {
        return credential;
    }

    public void setCredential(RealmList<Contact> credential) {
        this.credential = credential;
    }
}
