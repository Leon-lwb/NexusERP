FROM eclipse-temurin:17-jdk-alpine
LABEL maintainer="NexusERP"
RUN mkdir -p /ruoyi/snailjob/logs
WORKDIR /ruoyi/snailjob
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=""
EXPOSE 8800 17888
ADD ./target/ruoyi-snailjob-server.jar ./app.jar
ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -XX:+HeapDumpOnOutOfMemoryError -XX:+UseZGC ${JAVA_OPTS} -jar app.jar
