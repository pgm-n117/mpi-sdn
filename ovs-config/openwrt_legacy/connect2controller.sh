#/bin/sh

#Usage # connect2controller <controller_IP>

ovs-ctl start

ovs-vsctl set-manager tcp:$1:6640

ovs-vsctl add-br sdn-sw1
ovs-vsctl add-port sdn-sw1 eth0.10 -- set Interface eth0.10 ofport_request=1
ovs-vsctl add-port sdn-sw1 eth0.11 -- set Interface eth0.12 ofport_request=1
ovs-vsctl add-port sdn-sw1 eth0.12 -- set Interface eth0.13 ofport_request=1

ovs-vsctl set-controller sdn-sw1 tcp:$1:6653

ovs-vsctl show
