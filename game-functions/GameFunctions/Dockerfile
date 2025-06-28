#FROM ghcr.io/graalvm/native-image-community:21 AS build
#WORKDIR /home/app
#
## 1. Copy only the files needed for dependency resolution
#COPY pom.xml ./
#COPY mvnw ./
#COPY .mvn .mvn
#
## 2. Download dependencies (this layer is cached unless pom.xml changes)
##RUN ./mvnw dependency:go-offline
#
## 3. Now copy the rest of your source code
#COPY . .
#
#ENV JAVA_HOME=/usr
#ENV PATH="$JAVA_HOME/bin:$PATH"
#
## 4. Build the native image
#RUN ./mvnw native:compile
##RUN ./mvnw package -Dpackaging=native-image
#
#FROM public.ecr.aws/lambda/provided:al2
#COPY --from=build /home/app/target/GameFunctions /var/task/bootstrap
##COPY --from=build /home/app/target/GameFunctions-1.0.jar /var/task/bootstrap
#RUN chmod 755 /var/task/bootstrap
#CMD [ "bootstrap" ]




#RUN ./mvnw.cmd -Pnative package


FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /home/app
COPY . .

ENV JAVA_HOME=/usr
ENV PATH="$JAVA_HOME/bin:$PATH"

#RUN ls -l /usr/bin/java
#RUN ls -l /usr/local/bin/java
#RUN ls -l /opt
#RUN ls -l /usr/lib/jvm
#RUN ls -l /bin/java
RUN ./mvnw native:compile
#RUN find target

#RUN ls -l /home/app/target

#FROM public.ecr.aws/lambda/provided:al2
FROM public.ecr.aws/lambda/provided:al2023
COPY --from=build /home/app/target/GameFunctions /var/task/bootstrap
COPY --from=build /home/app/target/GameFunctions /var/runtime/bootstrap
RUN chmod 755 /var/task/bootstrap




#RUN cp /var/task/bootstrap /var/runtime/bootstrap
RUN chmod 755 /var/runtime/bootstrap
CMD [ "com.assaffin.games.functions.actions.ActionHandler::handleRequest" ]


#COPY --from=build /home/app/target/GameFunctions /var/runtime/bootstrap
#RUN chmod 755 /var/runtime/bootstrap

#WORKDIR /
#COPY bootstrap /bootstrap
#RUN chmod +x bootstrap
#ENTRYPOINT ["/bootstrap"]



#RUN native-image --version

#ENV JAVA_HOME=/opt/graalvm
#ENV PATH="$JAVA_HOME/bin:$PATH"
#
#RUN java -version

#tools:
#RUN which java
#RUN ls -l /opt
#RUN ls -l /usr/lib/jvm

#USER root
#RUN apk update && apk install -y findutils
#RUN find / -name java
