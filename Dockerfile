FROM ubuntu:16.04
LABEL maintainer="josh@radixdlt.com"

RUN apt-get -y update && \
    apt-get -y --no-install-recommends install openjdk-8-jdk-headless bzip2 && \
    apt-get clean && \
    rm -rf rm -rf /var/lib/apt/lists/* /var/tmp/* /tmp/*

ADD build/distributions/faucet-service-1.0-SNAPSHOT.tar /tmp

WORKDIR /tmp/faucet-service-1.0-SNAPSHOT/bin
CMD ["/bin/bash", "faucet-service", "highgarden", "faucet.key", "password123"]
