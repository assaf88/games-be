FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /home/app
COPY . .

ENV JAVA_HOME=/usr
ENV PATH="$JAVA_HOME/bin:$PATH"

RUN ./mvnw native:compile

FROM public.ecr.aws/lambda/provided:al2023
COPY --from=build /home/app/target/GameFunctions /var/task/bootstrap
COPY --from=build /home/app/target/GameFunctions /var/runtime/bootstrap
RUN chmod 755 /var/task/bootstrap
RUN chmod 755 /var/runtime/bootstrap
CMD [ "com.assaffin.games.functions.actions.ActionHandler::handleRequest" ]
