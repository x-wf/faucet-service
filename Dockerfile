FROM ubuntu:16.04
LABEL maintainer="josh@radixdlt.com"

RUN apt-get -y update && \
    apt-get -y --no-install-recommends install openjdk-8-jdk-headless bzip2 && \
    apt-get clean && \
    rm -rf rm -rf /var/lib/apt/lists/* /var/tmp/* /tmp/*

ADD build/distributions/faucet-service-*.tar /opt
RUN mv /opt/faucet-service-* /opt/faucet-service && \
    mkdir /opt/faucet-service/etc && \
    chown -R nobody:nogroup /opt/faucet-service

VOLUME /opt/faucet-service/etc

WORKDIR /opt/faucet-service
ENTRYPOINT ["./bin/faucet-service"]

USER nobody:nogroup
