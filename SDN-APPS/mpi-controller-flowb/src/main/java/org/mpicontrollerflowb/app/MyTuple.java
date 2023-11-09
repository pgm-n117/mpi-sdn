package org.mpicontrollerflowb.app;

import org.onlab.packet.Ip4Address;

//Tuple of objects, used to store pair IpAddress and Port
public class MyTuple {

    private Ip4Address ipAddress;
    private int port;

    public MyTuple(Ip4Address ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public Ip4Address getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setIpAddress(Ip4Address ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return "MyTuple{" + "ipAddress=" + ipAddress + ", port=" + port + '}';
    }

    //Override equals method to compare two objects
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MyTuple other = (MyTuple) obj;
        if (this.ipAddress != other.ipAddress && (this.ipAddress == null || !this.ipAddress.equals(other.ipAddress))) {
            return false;
        }
        if (this.port != other.port) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + (this.ipAddress != null ? this.ipAddress.hashCode() : 0);
        hash = 97 * hash + this.port;
        return hash;
    }
}
