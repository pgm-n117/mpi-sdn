# mpi-sdn
SDN research and implementation for MPI improvements


This repo contains an ONOS applications that balances traffic between TCP applications on a software defined network topology, and also interacts with a modified version of OpenMPI. This ONOS controller receives an UDP packet for every MPI process (sent on MPI_Init phase) that execute a parallel task, and estabilishes routes before OpenMPI communications start. Balanced paths are selected in order to distribute network traffic among all available links on the topology for a given source-destination route.

Both MPI and normal TCP traffic are balanced using a link weight system. When a link is used by a new installed FlowRule, the weight of this link is increased, so new paths are selected with the less used available links. When a FlowRule is deleted (traffic has ended), the link weight is decreased.

This work was developed for the College Degree final project, and Master's Degree final project.

Currently, this project is being written in Python, for RYU controller

## OVS Config
ovs-config contains the configurations files in order to make an OpenVSwitch with physical interfaces. Currently, there are 3 configurations available:
- RPI: Raspberry Pi configuration files. Configuration of Interfaces, for making an OpenVSwitch with USB to Ethernet adapters (a total of 5 ports, 1 integrated, 4 USB-Ethernet). Also includes scripts for downloading and compiling OVS on RPI (ARM architechture), but it is available on APT. Also a startup script is included to connect to controller on startup, building the necessary bridge and all its ports, referencing the physical interfaces.

- openwrt: OpenWRT configuration files. This configuration corresponds to an old ADSL Comtrend WiFi Router, that was used as an OpenVSwitch. This uses the old syntax of network interfaces for OpenWRT.

- Banana Pi R3: OpenWRT configuration files of Banana Pi R3 with the newest network interfaces syntax. SFP ports are included in the OVS configuration. SFP ports have been tested with ONOS and RYU and work correctly.

## Requirements:

### ONOS
- Make sure Python 2 is the version that executes with python command 
    - for quick solution, a symbolic link can be used:
        ```
        ln -s <your python 2 binary location> /usr/bin/python
        ... build ONOS
        unlink /usr/bin/python
        ```
- Download Bazel with Bazelisk
- Download ONOS Release 2.7.0, for Bazel 3.2.7 (Latest versions with Bazel 6.0.0 do not work with the developed application, so better use the latest stable release)
- Build ONOS with bazel
- Set ONOS Root env variable
```

#ONOS requirements: https://wiki.onosproject.org/display/ONOS/Installing+required+tools


sudo apt install python
sudo apt install python3
sudo apt install python-pip python-dev python-setuptools
sudo apt install python3-pip python3-dev python3-setuptools
 
pip3 install --upgrade pip
pip3 install selenium


#Bazelisk:
wget https://github.com/bazelbuild/bazelisk/releases/download/v1.11.0/bazelisk-linux-amd64
chmod +x bazelisk-linux-amd64
sudo mv bazelisk-linux-amd64 /usr/local/bin/bazel

#ONOS
##Download ONOS
cd ~
wget https://github.com/opennetworkinglab/onos/archive/refs/tags/2.7.0.tar.gz
tar -xf 2.7.0.tar.gz
mv onos-2.7.0 onos
cd ~/onos
##build ONOS
bazel version
bazel build onos

##Add binaries to .bashrc
export ONOS_ROOT=~/onos
source $ONOS_ROOT/tools/dev/bash_profile

##Run ONOS on its root directory:
cd ~/onos
bazel run onos-local -- <clean> <debug>

```



#### Compile and install Onos Application
- Install Maven
    ``` 
    sudo apt install maven 
    ```
- Move to application directory (where pom.xml is stored)
- Build with Maven
    ```
    mvn compile #to check for errors in building process

    mvn clean install #compile and also generate .oar file 

    ```
- Install and activate application onto ONOS
    ```
    onos-app <ip address of the machine running the ONOS instance> install! <.oar file> #installs and activates app
    #check onos documentations for more commands

    ```
- Enjoy???








### RYU
Right now, a dockerfile and Docker Compose yml and included for downloading the necessary environment to quickly develop and test with Ryu, and Mininet as the simulated network topology. The controller python programs will be included in the future, trying to achieve the same functionalities as the ones programmed for the ONOS application, including topology management and a dashboard to observe the topology in real time, and properties of the existing devices (hosts information, link status, flowrules installed on switches ...)

A python requirements file will replace the dockerfiles pip install command, to help people build the same environment on their machine, and not only develop on docker.


### MPI
Some MPI scripts and programs are included, which are:
- Data communication programs for testing the network:
    - Matrix Multiplication using simple communication functions (MPI_Send and MPI_Recv)
    - Matrix Multiplication using collective communication functions (MPI_Scatter, MPI_Gather and MPI_Bcast)
    - MPI_Send test program
    - MPI_Bcast test program
- Scripts for debugging and extract execution information, related with Extrae tool, from BSC (Not really used for this project)
