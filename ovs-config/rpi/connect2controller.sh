#!/bin/bash

#Script to connect an OpenvSwitch configured in Raspberry to a controller with
#usage: $ connnet2controller <controller_IP>

##Configure bridge to support OpenFlow1.3
sudo ovs-vsctl set bridge brd0 protocols=OpenFlow13
##Connect brigde to controller with IP $1
sudo ovs-vsctl set-controller brd0 tcp:$1:6653
