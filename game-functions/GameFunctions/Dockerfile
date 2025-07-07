FROM ghcr.io/graalvm/native-image-community:21 AS build

WORKDIR /app
COPY target/GameFunctions-*.jar app.jar

RUN native-image \
  -H:Name=app \
  -H:Class=com.assaffin.games.functions.actions.ActionHandler \
  --no-fallback \
  --enable-url-protocols=http,https \
  --initialize-at-run-time=software.amazon.awssdk \
  --initialize-at-run-time=com.fasterxml.jackson \
  --initialize-at-build-time=io.netty.util.internal.logging.InternalLoggerFactory,io.netty.channel.AbstractChannel \
  --report-unsupported-elements-at-runtime \
  --strict-image-heap \
  -jar app.jar

FROM public.ecr.aws/lambda/provided:al2023

COPY --from=build /app/app ${LAMBDA_RUNTIME_DIR}/bootstrap

CMD [ "bootstrap" ]
