# OVS Config
This directory contains the configurations files in order to make an OpenVSwitch with physical interfaces. Currently, there are 3 configurations available:
- RPI: Raspberry Pi configuration files. Configuration of Interfaces, for making an OpenVSwitch with USB to Ethernet adapters (a total of 5 ports, 1 integrated, 4 USB-Ethernet). Also includes scripts for downloading and compiling OVS on RPI (ARM architechture), but it is available on APT. Also a startup script is included to connect to controller on startup, building the necessary bridge and all its ports, referencing the physical interfaces.

- openwrt: OpenWRT configuration files. This configuration corresponds to an old ADSL Comtrend WiFi Router, that was used as an OpenVSwitch. This uses the old syntax of network interfaces for OpenWRT.
    - connect2controller.sh: Script executed on startup to connect to SDN controller
    - network: Interfaces configuration using old openwrt syntax, as this router uses an old openwrt build. It uses port 0 as a management port to connect to the network and the controller. Ports 1 2 and 3 are used as the SDN switch ports.

- Banana Pi R3: OpenWRT configuration files of Banana Pi R3 with the newest network interfaces syntax. SFP ports are included in the OVS configuration. SFP ports have been tested with ONOS and RYU and work correctly.
    - openwrt_package_requirements: Installation of OpenVSwitch and other packages to manage and monitor the OpenWRT switch. It is necessary to configure network to connect to internet and download package, so a valid default gateway and dns server are needed.
    - network: Interfaces configuration using new openwrt syntax. It uses WAN port as a management port to connect to the network and the controller. Ports LAN1, LAN2, LAN3, LAN4, ETH1 and SFP2 are used as the SDN switch ports.
    - ovs_startup: Script to configure and launch OpenVSwitch using the mentioned interfaces.

