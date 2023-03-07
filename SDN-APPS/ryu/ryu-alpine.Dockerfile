FROM python:3.7-alpine
#default user, change it on docker-compose
ARG USER=ryu-user
EXPOSE 6633 8080 8050

RUN apk update
RUN apk --no-cache add gcc libffi-dev libxml2-dev mlocate git curl make musl-dev linux-headers g++ bzip2-dev libpng-dev libjpeg qhull-dev libxslt-dev libgcc openssl-dev jpeg-dev zlib-dev freetype-dev lcms2-dev openjpeg-dev tiff-dev tk-dev tcl-dev geos-dev graphviz graphviz-dev

RUN updatedb
RUN python -m pip install --upgrade pip setuptools wheel
RUN pip install --config-settings system_qhull=true cython pyhull pillow ryu networkx requests numpy WebOb eventlet==0.30.2 Routes six tinyrpc graphviz matplotlib==3.3.4 bokeh==2.4.3 pandas==1.3.5 pydot GEOS shapely pygraphviz dash plotly dash-cytoscape
#non root user for security reasons, change uid as desired
RUN addgroup -S ryu-group && adduser -u 1000 -s /bin/bash -S ${USER} -G ryu-group 
USER ${USER}
WORKDIR /home/${USER}
RUN mkdir ryu-apps && ln -s /usr/local/lib/python3.7/site-packages/ryu/app ryu-apps
