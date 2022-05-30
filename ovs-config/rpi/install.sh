#/bin/bash


#If using Ubuntu, Debian, Raspbian... better to install from repos: sudo apt install openvswitch-switch
#Else, compile from repo. Use version 2.10.7, apparently it works with /etc/network/interfaces automatically
#Necessary to use systemctl services, automatically enabled if using apt


git clone https://github.com/openvswitch/ovs.git &&



cd ovs && git checkout v2.7.0 && #version used in this project

sudo ./boot.sh && ./configure &&

sudo make && sudo make install

#set scripts in /usr/local/share/openvswitch/scripts
#start service with sudo ./ovs-ctl start

#or set system service with
#sudo systemctl enable openvswitch-switch.service
#sudo systemctl start openvswitch-switch.service





