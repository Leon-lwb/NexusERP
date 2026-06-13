FROM eclipse-temurin:17-jdk-alpine
LABEL maintainer="NexusERP"
RUN mkdir -p /ruoyi/server/logs /ruoyi/server/temp
WORKDIR /ruoyi/server
ENV SERVER_PORT=8080 SNAIL_PORT=28080 LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=""
EXPOSE ${SERVER_PORT} ${SNAIL_PORT}
ADD ./target/ruoyi-admin.jar ./app.jar
ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${SERVER_PORT} -Dsnail-job.port=${SNAIL_PORT} ${JAVA_OPTS} -jar app.jar
