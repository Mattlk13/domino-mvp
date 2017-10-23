package com.progressoft.brix.domino.apt.client.processors.module.client.requests;

import com.progressoft.brix.domino.apt.commons.AbstractRegisterMethodWriter;

class RequestEntry implements AbstractRegisterMethodWriter.ItemEntry {
    protected final String request;
    protected final String presenter;

    public RequestEntry(String request, String presenter) {
        this.request = request;
        this.presenter = presenter;
    }
}
