package org.dominokit.domino.api.server.resteasy;

import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.impl.BodyHandlerImpl;
import io.vertx.ext.web.impl.FileUploadImpl;
import io.vertx.reactivex.core.Context;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerResponse;
import org.dominokit.domino.api.server.AppGlobals;
import org.dominokit.domino.api.server.PluginContext;
import org.dominokit.domino.api.server.spi.Plugin;
import org.jboss.resteasy.core.ResteasyHttpServletRequestWrapper;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.plugins.server.embedded.SecurityDomain;
import org.jboss.resteasy.plugins.server.vertx.RequestDispatcher;
import org.jboss.resteasy.plugins.server.vertx.VertxHttpRequest;
import org.jboss.resteasy.plugins.server.vertx.VertxHttpResponse;
import org.jboss.resteasy.plugins.server.vertx.VertxUtil;
import org.jboss.resteasy.plugins.server.vertx.i18n.LogMessages;
import org.jboss.resteasy.plugins.server.vertx.i18n.Messages;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VertxPluginRequestHandler implements BodyHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VertxPluginRequestHandler.class);
    private final Vertx vertx;
    private final RequestDispatcher dispatcher;
    private final String servletMappingPrefix;
    private AppGlobals appGlobals;

    private static final io.vertx.core.logging.Logger log = io.vertx.core.logging.LoggerFactory.getLogger(BodyHandlerImpl.class);

    private static final String BODY_HANDLED = "__body-handled";

    private long bodyLimit = DEFAULT_BODY_LIMIT;
    private boolean handleFileUploads;
    private String uploadsDir;
    private boolean mergeFormAttributes = DEFAULT_MERGE_FORM_ATTRIBUTES;
    private boolean deleteUploadedFilesOnEnd = DEFAULT_DELETE_UPLOADED_FILES_ON_END;
    private boolean isPreallocateBodyBuffer = DEFAULT_PREALLOCATE_BODY_BUFFER;
    private static final int DEFAULT_INITIAL_BODY_BUFFER_SIZE = 1024; //bytes

    public VertxPluginRequestHandler(PluginContext context, ResteasyDeployment deployment, String servletMappingPrefix, SecurityDomain domain, List<Plugin> plugins) {
        this.vertx = context.getRxVertx();
        this.dispatcher = new PluginRequestDispatcher((SynchronousDispatcher) deployment.getDispatcher(), deployment.getProviderFactory(), domain, plugins);
        this.servletMappingPrefix = servletMappingPrefix;
        appGlobals = AppGlobals.get();
    }

    public VertxPluginRequestHandler(PluginContext context, ResteasyDeployment deployment, String servletMappingPrefix, List<Plugin> plugins) {
        this(context, deployment, servletMappingPrefix, null, plugins);
    }

    public VertxPluginRequestHandler(PluginContext context, ResteasyDeployment deployment, List<Plugin> plugins) {
        this(context, deployment, "", plugins);
    }


    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        io.vertx.reactivex.core.http.HttpServerRequest rxRequest = io.vertx.reactivex.core.http.HttpServerRequest.newInstance(request);
        if (request.headers().contains(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET, true)) {
            context.next();
            return;
        }
        // we need to keep state since we can be called again on reroute
        Boolean handled = context.get(BODY_HANDLED);
        if (handled == null || !handled) {
            long contentLength = isPreallocateBodyBuffer ? parseContentLengthHeader(request) : -1;
            BHandler handler = new BHandler(context, contentLength);
            request.handler(handler);
            request.endHandler(v -> {
                Context ctx = vertx.getOrCreateContext();
                ResteasyUriInfo uriInfo = VertxUtil.extractUriInfo(rxRequest.getDelegate(), servletMappingPrefix);
                ResteasyHttpHeaders headers = VertxUtil.extractHttpHeaders(rxRequest.getDelegate());
                HttpServerResponse response = rxRequest.response();
                VertxHttpResponse vertxResponse = new VertxHttpResponseWithWorkaround(response.getDelegate(), dispatcher.getProviderFactory(), request.method());
                VertxHttpRequest vertxRequest = new VertxHttpRequest(ctx.getDelegate(), headers, uriInfo, request.rawMethod(), dispatcher.getDispatcher(), vertxResponse, false);
                if (handler.body.length() > 0) {
                    ByteBufInputStream in = new ByteBufInputStream(handler.body.getByteBuf());
                    vertxRequest.setInputStream(in);
                }

                try {
                    AppGlobals.set(appGlobals);
                    appGlobals.injectGlobals();
                    dispatcher.service(ctx.getDelegate(), rxRequest.getDelegate(), response.getDelegate(), vertxRequest, vertxResponse, true);
                } catch (Failure failure) {
                    vertxResponse.setStatus(failure.getErrorCode());
                    LogMessages.LOGGER.error(Messages.MESSAGES.unexpected(), failure.getCause());
                } catch (Exception ex) {
                    vertxResponse.setStatus(401);
                    LogMessages.LOGGER.error(Messages.MESSAGES.unexpected(), ex);
                } finally {
                    AppGlobals.set(null);
                }
                if (!vertxRequest.getAsyncContext().isSuspended()) {
                    try {
                        vertxResponse.finish();
                    } catch (IOException e) {
                        LOGGER.error("Could not finish response!", e);
                    }
                }
                handler.end();
            });
            context.put(BODY_HANDLED, true);


        } else {
            // on reroute we need to re-merge the form params if that was desired
            if (mergeFormAttributes && request.isExpectMultipart()) {
                request.params().addAll(request.formAttributes());
            }
        }


    }

    @Override
    public BodyHandler setHandleFileUploads(boolean handleFileUploads) {
        this.handleFileUploads = handleFileUploads;
        return this;
    }

    @Override
    public BodyHandler setBodyLimit(long bodyLimit) {
        this.bodyLimit = bodyLimit;
        return this;
    }

    @Override
    public BodyHandler setUploadsDirectory(String uploadsDirectory) {
        this.uploadsDir = uploadsDirectory;
        return this;
    }

    @Override
    public BodyHandler setMergeFormAttributes(boolean mergeFormAttributes) {
        this.mergeFormAttributes = mergeFormAttributes;
        return this;
    }

    @Override
    public BodyHandler setDeleteUploadedFilesOnEnd(boolean deleteUploadedFilesOnEnd) {
        this.deleteUploadedFilesOnEnd = deleteUploadedFilesOnEnd;
        return this;
    }

    @Override
    public BodyHandler setPreallocateBodyBuffer(boolean isPreallocateBodyBuffer) {
        this.isPreallocateBodyBuffer = isPreallocateBodyBuffer;
        return this;
    }

    private long parseContentLengthHeader(io.vertx.core.http.HttpServerRequest request) {
        String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if(contentLength == null || contentLength.isEmpty()) {
            return -1;
        }
        try {
            long parsedContentLength = Long.parseLong(contentLength);
            return  parsedContentLength < 0 ? null : parsedContentLength;
        }
        catch (NumberFormatException ex) {
            return -1;
        }
    }

    private class BHandler implements Handler<Buffer> {
        private static final int MAX_PREALLOCATED_BODY_BUFFER_BYTES = 65535;

        io.vertx.ext.web.RoutingContext context;
        Buffer body;
        boolean failed;
        AtomicInteger uploadCount = new AtomicInteger();
        AtomicBoolean cleanup = new AtomicBoolean(false);
        boolean ended;
        long uploadSize = 0L;
        final boolean isMultipart;
        final boolean isUrlEncoded;

        public BHandler(io.vertx.ext.web.RoutingContext context, long contentLength) {
            this.context = context;
            Set<FileUpload> fileUploads = context.fileUploads();

            final String contentType = context.request().getHeader(HttpHeaders.CONTENT_TYPE);
            if (contentType == null) {
                isMultipart = false;
                isUrlEncoded = false;
            } else {
                final String lowerCaseContentType = contentType.toLowerCase();
                isMultipart = lowerCaseContentType.startsWith(HttpHeaderValues.MULTIPART_FORM_DATA.toString());
                isUrlEncoded = lowerCaseContentType.startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString());
            }

            initBodyBuffer(contentLength);

            if (isMultipart || isUrlEncoded) {
                context.request().setExpectMultipart(true);
                if (handleFileUploads) {
                    makeUploadDir(context.vertx().fileSystem());
                }
                context.request().uploadHandler(upload -> {
                    if (bodyLimit != -1 && upload.isSizeAvailable()) {
                        // we can try to abort even before the upload starts
                        long size = uploadSize + upload.size();
                        if (size > bodyLimit) {
                            failed = true;
                            context.fail(413);
                            return;
                        }
                    }
                    if (handleFileUploads) {
                        // we actually upload to a file with a generated filename
                        uploadCount.incrementAndGet();
                        String uploadedFileName = new File(uploadsDir, UUID.randomUUID().toString()).getPath();
                        upload.streamToFileSystem(uploadedFileName);
                        FileUploadImpl fileUpload = new FileUploadImpl(uploadedFileName, upload);
                        fileUploads.add(fileUpload);
                        upload.exceptionHandler(t -> {
                            deleteFileUploads();
                            context.fail(t);
                        });
                        upload.endHandler(v -> uploadEnded());
                    }
                });
            }

            context.request().exceptionHandler(t -> {
                deleteFileUploads();
                context.fail(t);
            });
        }

        private void initBodyBuffer(long contentLength) {
            int initialBodyBufferSize;
            if(contentLength < 0) {
                initialBodyBufferSize = DEFAULT_INITIAL_BODY_BUFFER_SIZE;
            }
            else if(contentLength > MAX_PREALLOCATED_BODY_BUFFER_BYTES) {
                initialBodyBufferSize = MAX_PREALLOCATED_BODY_BUFFER_BYTES;
            }
            else {
                initialBodyBufferSize = (int) contentLength;
            }

            if(bodyLimit != -1) {
                initialBodyBufferSize = (int)Math.min(initialBodyBufferSize, bodyLimit);
            }

            this.body = Buffer.buffer(initialBodyBufferSize);
        }

        private void makeUploadDir(FileSystem fileSystem) {
            if (!fileSystem.existsBlocking(uploadsDir)) {
                fileSystem.mkdirsBlocking(uploadsDir);
            }
        }

        @Override
        public void handle(Buffer buff) {
            if (failed) {
                return;
            }
            uploadSize += buff.length();
            if (bodyLimit != -1 && uploadSize > bodyLimit) {
                failed = true;
                context.fail(413);
                // enqueue a delete for the error uploads
                context.vertx().runOnContext(v -> deleteFileUploads());
            } else {
                // multipart requests will not end up in the request body
                // url encoded should also not, however jQuery by default
                // post in urlencoded even if the payload is something else
                if (!isMultipart /* && !isUrlEncoded */) {
                    body.appendBuffer(buff);
                }
            }
        }

        void uploadEnded() {
            int count = uploadCount.decrementAndGet();
            // only if parsing is done and count is 0 then all files have been processed
            if (ended && count == 0) {
                doEnd();
            }
        }

        void end() {
            // this marks the end of body parsing, calling doEnd should
            // only be possible from this moment onwards
            ended = true;

            // only if parsing is done and count is 0 then all files have been processed
            if (uploadCount.get() == 0) {
                doEnd();
            }
        }

        void doEnd() {

            if (failed) {
                deleteFileUploads();
                return;
            }

            if (deleteUploadedFilesOnEnd) {
                context.addBodyEndHandler(x -> deleteFileUploads());
            }

            HttpServerRequest req = context.request();
            if (mergeFormAttributes && req.isExpectMultipart()) {
                req.params().addAll(req.formAttributes());
            }
            context.setBody(body);
            context.next();
        }

        private void deleteFileUploads() {
            if (cleanup.compareAndSet(false, true) && handleFileUploads) {
                for (FileUpload fileUpload : context.fileUploads()) {
                    FileSystem fileSystem = context.vertx().fileSystem();
                    String uploadedFileName = fileUpload.uploadedFileName();
                    fileSystem.exists(uploadedFileName, existResult -> {
                        if (existResult.failed()) {
                            log.warn("Could not detect if uploaded file exists, not deleting: " + uploadedFileName, existResult.cause());
                        } else if (existResult.result()) {
                            fileSystem.delete(uploadedFileName, deleteResult -> {
                                if (deleteResult.failed()) {
                                    log.warn("Delete of uploaded file failed: " + uploadedFileName, deleteResult.cause());
                                }
                            });
                        }
                    });
                }
            }
        }
    }


}