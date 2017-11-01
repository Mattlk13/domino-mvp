package com.progressoft.brix.domino.api.server.endpoint;

import java.util.function.Supplier;

@FunctionalInterface
public interface EndpointsRegistry {

    void registerEndpoint(String path, Supplier<?> factory);
}
