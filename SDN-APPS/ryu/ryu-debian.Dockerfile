FROM python:3.7-bullseye
#default user, change it on docker-compose
ARG USER=ryu-user
EXPOSE 6633 8080

RUN apt update && apt upgrade -y
RUN apt install -y gcc libffi-dev libssl-dev libxml2-dev libxslt1-dev zlib1g-dev mlocate git curl make libpng-dev libjpeg-dev
RUN updatedb
RUN python -m pip install --upgrade pip setuptools wheel
RUN pip install ryu networkx requests numpy WebOb eventlet==0.30.2 Routes six tinyrpc graphviz matplotlib bokeh==2.4.3 pandas==1.3.5

#non root user for security reasons, change uid as desired
RUN useradd -u 1000 -ms /bin/bash ${USER} 
USER ${USER}
WORKDIR /home/${USER}
RUN mkdir ryu-apps && ln -s /usr/local/lib/python3.7/site-packages/ryu/app ryu-apps
