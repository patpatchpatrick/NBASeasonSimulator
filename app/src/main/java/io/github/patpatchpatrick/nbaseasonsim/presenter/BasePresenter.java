package io.github.patpatchpatrick.nbaseasonsim.presenter;

import android.content.ContentResolver;

public abstract class BasePresenter<V> {

    protected V view;

    protected BasePresenter(V view){
        this.view = view;
    }

    protected BasePresenter(){ }

    protected void setView(V view) {
        this.view = view;
    }


}
