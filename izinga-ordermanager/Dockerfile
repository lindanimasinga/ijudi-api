FROM public.ecr.aws/lambda/java:17
# Copy function code and runtime dependencies from Maven layout
COPY target/izinga-ordermanager-1.2.0.jar ${LAMBDA_TASK_ROOT}/lib/
CMD [ "io.curiousoft.izinga.ordermanagement.StreamLambdaHandler::handleRequest" ]