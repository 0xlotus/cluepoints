
FROM openjdk:11

RUN apt-get update
RUN apt-get install -y maven

COPY . crypto-staging

WORKDIR ./crypto-staging
RUN mvn clean assembly:assembly
RUN cp ./target/crypto-parent-*-dist.tar.gz /

WORKDIR /
RUN tar -xzf crypto-parent-*-dist.tar.gz
RUN rm crypto-parent-*-dist.tar.gz
RUN rm -rf ./crypto-staging

EXPOSE 8080