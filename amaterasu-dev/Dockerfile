FROM docker:29.9.2-dind-alpine3.24

RUN apk add --no-cache openssh-client openssh-server bash

RUN adduser -D -g '' amaterasu
RUN echo "amaterasu:amaterasu" | chpasswd

RUN ssh-keygen -A
# Expose the SSH port
EXPOSE 22

# Start SSH service
CMD ["/usr/sbin/sshd", "-D"]