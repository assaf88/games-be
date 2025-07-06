FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /home/app

# Copy pom.xml first to cache dependencies
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Download dependencies (this layer will be cached)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

ENV JAVA_HOME=/usr
ENV PATH="$JAVA_HOME/bin:$PATH"

RUN ./mvnw native:compile
RUN ls -la target/

FROM public.ecr.aws/lambda/provided:al2023
COPY --from=build /home/app/target/GameFunctions /var/runtime/bootstrap
RUN chmod 755 /var/runtime/bootstrap
RUN ls -la /var/runtime/
CMD [ "bootstrap" ]
