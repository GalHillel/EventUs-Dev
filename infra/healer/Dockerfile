FROM alpine:3.20

ARG KUBECTL_VERSION=v1.33.13

RUN apk add --no-cache python3 bash ca-certificates curl \
 && curl -fsSLo /usr/local/bin/kubectl \
      "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
 && chmod 0755 /usr/local/bin/kubectl \
 && apk del curl

WORKDIR /opt/healer

COPY healer.py rollback.sh ./
RUN chmod 0755 rollback.sh

RUN addgroup -S healer && adduser -S healer -G healer
USER healer

ENTRYPOINT ["python3", "/opt/healer/healer.py"]
