#/bin/bash

git clone https://github.com/openvswitch/ovs.git &&



cd ovs && git checkout v2.7.0 && #version used in this project

sudo ./boot.sh && ./configure &&

sudo make && sudo make install

#set scripts in /usr/local/share/openvswitch/scripts
#start service with sudo ./ovs-ctl start

#or set system service with
#sudo systemctl enable openvswitch.switch
#sudo systemctl start openvswitch-switch





