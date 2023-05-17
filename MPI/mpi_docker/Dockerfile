#containernet adapted image for running the modified version of openmpi

FROM ubuntu:20.04 AS base

ARG USER=mpiuser
ARG USERPASS=mpiuser
ARG DEBIAN_FRONTEND=noninteractive

RUN apt update
RUN apt upgrade
RUN apt install -y gcc net-tools sshpass iproute2 iputils-ping libssl-dev git curl make g++ openssh-server openssh-client m4 autoconf automake libtool gfortran flex

#non root user for security reasons, change uid as desired
RUN useradd -rm -d /home/${USER} -s /bin/bash -g root -G sudo -u 1001 ${USER}
USER ${USER}
WORKDIR /home/${USER}
RUN git clone https://github.com/pgm-n117/ompi
WORKDIR /home/${USER}/ompi
RUN ./autogen.pl
RUN ./configure
RUN make -j
USER root
WORKDIR /home/${USER}/ompi
RUN make install
RUN ldconfig
USER ${USER}
WORKDIR /home/${USER}
RUN echo mpirun --version
RUN ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519
USER root
RUN echo "${USER}:${USERPASS}" | chpasswd
RUN apt install -y 
#RUN echo /etc/init.d/ssh start && /etc/init.d/ssh status
#RUN service ssh start

ENTRYPOINT ["/bin/bash", "-l", "-c", "/usr/sbin/sshd", "-D" ]
CMD ["/bin/bash"]
