package com.progressoft.brix.domino.apt.client.processors.module.client.presenters;

import com.progressoft.brix.domino.apt.commons.AbstractRegisterMethodWriter;

class PresenterEntry implements AbstractRegisterMethodWriter.ItemEntry{
    protected final String name;

    PresenterEntry(String name) {
        this.name = name;
    }
}
