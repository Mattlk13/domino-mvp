package org.dominokit.domino.apt.server;

import org.dominokit.domino.api.server.resource.Handler;
import org.dominokit.domino.api.server.resource.RequestHandler;
import org.dominokit.domino.api.server.context.ExecutionContext;
import org.dominokit.domino.api.shared.request.ResponseBean;

@Handler(value = "somePath")
public class FirstHandler implements RequestHandler<FirstRequest, ResponseBean> {
    @Override
    public void handleRequest(ExecutionContext<FirstRequest, ResponseBean> executionContext) {
    }

}
