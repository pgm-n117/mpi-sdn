FROM python:3.7-alpine
#default user, change it on docker-compose
ARG USER=ryu-user
EXPOSE 6633 8080

RUN apk --no-cache add gcc libffi-dev libxml2-dev mlocate git curl make musl-dev linux-headers g++
RUN updatedb
RUN python -m pip install --upgrade pip setuptools wheel
RUN pip install ryu networkx requests numpy WebOb eventlet==0.30.2 Routes six tinyrpc
#non root user for security reasons, change uid as desired
RUN addgroup -S ryu-group && adduser -u 1000 -s /bin/bash -S ${USER} -G ryu-group 
USER ${USER}
WORKDIR /home/${USER}
RUN mkdir ryu-apps && ln -s /usr/local/lib/python3.7/site-packages/ryu/app ryu-apps
