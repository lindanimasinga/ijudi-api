package io.curiousoft.izinga.ordermanagement;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.serverless.proxy.spring.SpringBootProxyHandlerBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamLambdaHandler implements RequestStreamHandler {
    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
            LambdaContainerHandler.getContainerConfig().setInitializationTimeout(120_000);
             try {
//                 SpringBootLambdaContainerHandler.getAwsProxyHandler(DailySchedulerApp::class.java)
                // For applications that take longer than 10 seconds to start, use the async builder:
                 handler = new SpringBootProxyHandlerBuilder<AwsProxyRequest>()
                        .springBootApplication(IjudiApplication.class)
                        .defaultProxy()
                        .asyncInit()
                        .buildAndInitialize();
            } catch (ContainerInitializationException e) {
                // if we fail here. We re-throw the exception to force another cold start
                e.printStackTrace();
                throw new RuntimeException("Could not initialize Spring Boot application", e);
            }
    }

    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        handler.proxyStream(inputStream, outputStream, context);
    }
}