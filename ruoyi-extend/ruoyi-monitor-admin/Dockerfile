FROM amazoncorretto:17-alpine
LABEL maintainer="NexusERP"
RUN mkdir -p /ruoyi/monitor/logs
WORKDIR /ruoyi/monitor
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=""
EXPOSE 9090
ADD ./target/ruoyi-monitor-admin.jar ./app.jar
ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom ${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/ruoyi/monitor/logs/ -XX:+ExitOnOutOfMemoryError -jar app.jar
