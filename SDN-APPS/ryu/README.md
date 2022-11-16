# SDN Ryu

Ryu SDN Controller using Python 3.7. Built with Debian Bullseye and Alpine for reduced image size.


## Getting Started

```shell
docker pull pgmconreg/sdn_ryu:bullseye
```
```shell
docker pull pgmconreg/sdn_ryu:alpine
```

### Prerequisities


### Usage
Start container with shell (use sh with alpine instead of bash):
```shell
docker run -p 6633:6633 -p 8080:8080 -it <sdn_ryu:tag> /bin/bash
```

#### Container Parameters

TODO: launch ryu manager directly without shell

## Dockerfiles
### Dockerfile used for Debian image
```dockerfile
FROM python:3.7-bullseye
#default user, change it on docker-compose
ARG USER=ryu-user
EXPOSE 6633 8080

RUN apt update && apt upgrade -y
RUN apt install -y gcc libffi-dev libssl-dev libxml2-dev libxslt1-dev zlib1g-dev mlocate git curl make
RUN updatedb
RUN python -m pip install --upgrade pip
RUN pip install ryu networkx requests numpy WebOb eventlet==0.30.2 Routes six tinyrpc

#non root user for security reasons, change uid as desired
RUN useradd -u 1000 -ms /bin/bash ${USER} 

USER ${USER}
WORKDIR /home/${USER}
#You can mount volumes or ryu apps in this directory
RUN mkdir ryu-apps && ln -s /usr/local/lib/python3.7/site-packages/ryu/app ryu-apps
```


### Dockerfile used for Alpine image
```dockerfile
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
#You can mount volumes or ryu apps in this directory
RUN mkdir ryu-apps && ln -s /usr/local/lib/python3.7/site-packages/ryu/app ryu-apps
```

## Docker Compose
### Usage with docker-compose
```shell
docker-compose up -d 
```
### Example of docker-compose.yml

```yml
version: "3"
services:
  ryu-controller:
    container_name: ryu-controller
    image: ryu-controller
    build:
      context: .
      args:
        - USER=ryu-user #Change as desired to build Dockerfile
    environment:
      - USER="ryu-user" #Change as desired to build Dockerfile
    expose:
      - 6633
      - 8080 #for webservers of topology
    ports: #<host port>:<container port>
      - "6633:6633"
      - "8080:8080"
    volumes: #/path/on/host:/path/on/container
      - "/home/${USER}/<desired_directory>:/home/${USER}/externals" #mount desired directory into container. ${USER} references local host user, not containers user
    tty: true

  ```
<!---#### Environment Variables

* `VARIABLE_ONE` - A Description
* `ANOTHER_VAR` - More Description
* `YOU_GET_THE_IDEA` - And another

#### Volumes

* `/your/file/location` - File location

#### Useful File Locations

* `/some/special/script.sh` - List special scripts
  
* `/magic/dir` - And also directories

## Built With

* List the software v0.1.3
* And the version numbers v2.0.0
* That are in this container v0.3.2

## Find Us

* [GitHub](https://github.com/your/repository)
* [Quay.io](https://quay.io/repository/your/docker-repository)

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the 
[tags on this repository](https://github.com/your/repository/tags). 

## Authors

* **Billie Thompson** - *Initial work* - [PurpleBooth](https://github.com/PurpleBooth)

See also the list of [contributors](https://github.com/your/repository/contributors) who 
participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
--->
## Acknowledgments

<!---* People you want to thank
* If you took a bunch of code from somewhere list it here
--->
<ul>
  <li> [Ryu Pip Package](https://pypi.org/project/ryu/) </li> 
  <li> [Ryu Project](https://ryu-sdn.org/index.html)</li> 
  <li> [Faucet](https://github.com/faucetsdn/ryu)</li>
  <li> [Python 3.7 Docker images](https://hub.docker.com/_/python)</li>
</ul> 