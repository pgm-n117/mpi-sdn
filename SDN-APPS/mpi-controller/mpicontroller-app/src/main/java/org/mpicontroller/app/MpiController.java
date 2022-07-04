/*
 *
 *
 * * * * * * TODO: ELIMINAR LA PARTE QUE SOBRA, SOLAMENTE GESTIONAR LO DE LAS RUTAS DE MPI * * * * * *
 *
 *
 * Copyright 2021-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mpicontroller.app;

import org.onlab.packet.*;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.GroupId;
import org.onosproject.net.*;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.ByteBuffer.*;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {MpiController.class},
           property = {
               "someProperty=Some Default String Value"}
           )
public class MpiController implements MpiControllerInterface{
    
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;



    //---RELEVANT AND NECESSARY SERVICES FOR APP---//
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;




    //---------------------------------------------//

    //Needed variables
    private ApplicationId appId;

    //Packet processor
    private PacketProcessor mpiControllerProcessor;

    private final FlowRuleListener flowRuleListener = new flowRuleEventListener(); //Class at end of file

    /*This structures can be improved, maybe using an MPI task identifier, so we can register every MPI task launched
      in a cluster. For now, this is all, and it works.
     */
    //HashMap Structure: <Ip Address of endpoint, <TCP Port of process, Topology/Device/Swtich Port Number>>
    private ConcurrentMap<IpAddress, Map<Integer, PortNumber>> MPIEndpoints = new ConcurrentHashMap<IpAddress, Map<Integer, PortNumber>>();

    private ConcurrentMap<FlowId, Link> ActiveFlowrules = new ConcurrentHashMap<>(); //Active flowrules (id) and the link its using
    private ConcurrentMap<Link, Double> ActiveLinks = new ConcurrentHashMap<>(); //Links and their usage by active paths (times used by a flowrule) -> for load balancing
    private ConcurrentMap<Link, Double> ActiveMPILinks = new ConcurrentHashMap<>(); //Links and their usage by active paths (times used by a flowrule) -> for load balancing

    private Object FlowRuleMutex =  new Object();
    private Object MPILinksMutex =  new Object();
    private Object LinksMutex =  new Object();


    @Activate
    protected void activate() {

        try{
            log.info("MPI-CONTROLLER -- ACTIVATING");

            cfgService.registerProperties(getClass());
            log.info("Started");

            //Obtain app id
            appId = coreService.getAppId("org.mpicontroller.app");

            //Procesador de paquetes
            mpiControllerProcessor = new mpiControllerProcessor();
            packetService.addProcessor(mpiControllerProcessor, PacketProcessor.director(3));

            //Request Packets - obtain first packages at the beginning.
            //Packets can be obtained from edge devices only, as only them will have hosts connected
            edgePortService.getEdgePoints().forEach(connectPoint -> {
                log.info("EDGE DEVICE: "+ connectPoint.deviceId());
                packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.of(connectPoint.deviceId()));
                //IPV4
                packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.of(connectPoint.deviceId()));
                //To catch all MPI Init UDP messages
                //packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).matchUdpDst(TpPort.tpPort(7777)).build(), PacketPriority.HIGH, appId, Optional.of(connectPoint.deviceId()));

            });

            flowRuleService.addListener(flowRuleListener);

        }catch(Exception ex){
            log.info("------------ERROR EN ACTIVATE------------" + ex.toString());
        }
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");

        //Remove everything initialized on activate
        edgePortService.getEdgePoints().forEach(connectPoint -> {
            //ARP
            packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.of(connectPoint.deviceId()));
            //IPV4
            packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.of(connectPoint.deviceId()));
            //packetService.cancelPackets(DefaultTrafficSelector.builder().matchUdpDst(TpPort.tpPort(7777)).build(), PacketPriority.HIGH, appId, Optional.of(connectPoint.deviceId()));
        });

        flowRuleService.removeFlowRulesById(appId);
        flowRuleService.removeListener(flowRuleListener);

        packetService.removeProcessor(mpiControllerProcessor);
    }

    @Modified
    public void modified(ComponentContext context) {
        /**
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
         */

        log.info("Reconfigured");
    }



    //@Override
    /*public void someMethod() {
        log.info("Invoked");
    }*/

    private class mpiControllerProcessor implements PacketProcessor{


        @Override
        public void process(PacketContext context) {

            InboundPacket packet = context.inPacket();
            Ethernet ethPacket = packet.parsed();
            if(ethPacket == null) return;

            //entry port from source device
            ///ConnectPoint srcConnectionPoint = packet.receivedFrom();

            switch (EthType.EtherType.lookup(ethPacket.getEtherType())){
                case LLDP:
                    return;
                case ARP:
                    //log.info("ARP Packet Received");
                    //Ethernet Payload can be an ARP or IP packet
                    ARP arpPacket = (ARP) ethPacket.getPayload();
                    //Obtain Ip address of the target if it is an ARP REQUEST packet
                    Ip4Address targetIpAddress = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());

                    if(arpPacket.getOpCode() == ARP.OP_REQUEST){

                        //Destination device connection point
                        ConnectPoint dstConnectionPoint;


                        //Get host from target ip address at ARP REQUEST packet
                        Set<Host> hosts = hostService.getHostsByIp(targetIpAddress);
                        //If hosts found on the network, send it the ARP REQUEST packet
                        if(!hosts.isEmpty()){
                            for (Host host : hosts){
                                if(host.mac() != null){ //ARP Request done over broadcast (FF:FF:FF:FF), nothing else to compare

                                    dstConnectionPoint = host.location();  //where is connected the host

                                    //Set up treatment: build it with output port on destination point
                                    TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                                            .setOutput(dstConnectionPoint.port());

                                    //Set packet service: new OutBound packet, with destination point, treatment
                                    // and the received packet
                                    packetService.emit(new DefaultOutboundPacket(
                                            dstConnectionPoint.deviceId(),
                                            treatment.build(),
                                            context.inPacket().unparsed()));
                                    break;
                                }
                            }
                        }

                        //If no hosts found: dstMac will be null -> destination hosts could be inactive
                        return;

                    }else{
                        if(arpPacket.getOpCode() == ARP.OP_REPLY){
                            //An ARP REQUEST has been received previously,
                            // so destination host of ARP REPLY (source of REQUEST) must be active

                            //Destination device connection point
                            ConnectPoint dstConnectionPoint;


                            //Get host from target ip address at ARP REPLAY packet
                            Set<Host> hosts = hostService.getHostsByIp(targetIpAddress);
                            if(!hosts.isEmpty()){
                                for(Host host : hosts){
                                    //If target host is found and equals eth packet destination MAC (it should)
                                    if(host.mac().equals(ethPacket.getDestinationMAC())){

                                         dstConnectionPoint = host.location();

                                        //Set up treatment: build it with output por on destination point
                                        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                                                .setOutput(dstConnectionPoint.port());

                                        //Set packet service
                                        packetService.emit(new DefaultOutboundPacket(
                                                dstConnectionPoint.deviceId(),
                                                treatment.build(),
                                                context.inPacket().unparsed()));
                                        break;
                                    }
                                }
                            }

                            //If no hosts found, source of initial ARP REQUEST could be down
                            return;
                        }
                    }

                    break;


                case IPV4:
                    //Get ethernet payload which is ipv4
                    IPv4 ipHeader = (IPv4) ethPacket.getPayload();

                    //In case of error:
                    if(ipHeader == null) return;

                    //Get destination host ipv4 address
                    Ip4Address srcIpAddress = Ip4Address.valueOf(ipHeader.getSourceAddress());
                    Ip4Address dstIpAddress = Ip4Address.valueOf(ipHeader.getDestinationAddress());
                    int srcIpPort = 0;
                    int dstIpPort = 0;


                    //TCP or UDP
                    byte protocol = ipHeader.getProtocol();

                    //TCP TREATMENT
                    if(protocol == IPv4.PROTOCOL_TCP){
                        TCP tcpHeader = (TCP) ipHeader.getPayload();
                        srcIpPort = tcpHeader.getSourcePort();
                        dstIpPort = tcpHeader.getDestinationPort();

                    }

                    //UDP TREATMENT
                    else if(protocol == IPv4.PROTOCOL_UDP){
                        UDP udpHeader = (UDP) ipHeader.getPayload();

                        srcIpPort = udpHeader.getSourcePort();
                        dstIpPort = udpHeader.getDestinationPort();

                        //IF UDP MESSAGE IS FROM MPI HOST
                        if(dstIpAddress.equals(Ip4Address.valueOf("10.0.0.1")) & (dstIpPort == 7777)){ //ip address of controller or special ip to identify
                            //log.info("      MPI UDP PACKET DESTINATION ADDRESS: "+dstIpAddress.toString()+":"+dstIpPort);
                            //log.info("      PEER INFO: "+(udpHeader.getPayload()));
                            byte[] rawMessage = ((byte[]) udpHeader.getPayload().serialize());

                            //IP and port are sent from MPI peer using an array of 2 integers. Now we have to extract from payload
                            ByteBuffer message = wrap(rawMessage);
                            message.order(ByteOrder.LITTLE_ENDIAN);
                            IntBuffer buffer = message.asIntBuffer();
                            int[] PeerAddr = new int[2];

                            buffer.get(PeerAddr);

                            int peerPort = Integer.reverseBytes(PeerAddr[1]);
                            Ip4Address peerIpAddress = Ip4Address.valueOf(PeerAddr[0]);

                            try{
                                log.info("      MPI ENDPOINT ADDR INFO: "+ Ip4Address.valueOf(PeerAddr[0]) + ":" + Integer.reverseBytes(PeerAddr[1]));
                            }catch(Exception ex){
                                log.error(ex.toString());
                            }

                            //Store MPI endpoint. This is not really necessary if removing mpi endpoints is not implemented
                            if(MPIEndpoints.containsKey(peerIpAddress)){
                                //If endpoint is already registered, set new endpoint tcp port of new MPI process
                                MPIEndpoints.get(peerIpAddress).putIfAbsent(peerPort, context.inPacket().receivedFrom().port());
                            }else{
                                ConcurrentHashMap<Integer, PortNumber> MPIPeer = new ConcurrentHashMap<Integer, PortNumber>();
                                MPIPeer.put(peerPort, context.inPacket().receivedFrom().port());

                                MPIEndpoints.putIfAbsent(peerIpAddress, MPIPeer);
                            }

                            //Set paths between every existing host (sourceHost)  and the endpoint (dstHost)
                            /*Unfortunately, have to iterate a lot because methods return iterable objects that are void
                              if a host is not found on the network
                            */
                            for(Host sourceHost : hostService.getHosts()){
                                //if host up and found, set path flowrules on network devices
                                for(Host dstHost : hostService.getHostsByIp(peerIpAddress)) { //Return a list of possible hosts with same ip, in case host is not reachable, list is void
                                    Ip4Address srcIp;
                                    if (!sourceHost.ipAddresses().isEmpty()) { //It should obtain only 1 IP address
                                        for (IpAddress IP : sourceHost.ipAddresses()) {

                                            srcIp = IP.getIp4Address();

                                            if(srcIp.getIp4Address() != peerIpAddress.getIp4Address()) {

                                                try {
                                                    setMPIPath(context, sourceHost, dstHost, IPv4.PROTOCOL_TCP, srcIp, peerIpAddress, peerPort);
                                                    //log.info("*********NEW MPI USED LINKS********* :\n "+ Arrays.asList(ActiveLinks));
                                                    log.info("*********NEW MPI PATH*********" + srcIp + " -> " + peerIpAddress + ":" + peerPort);
                                                } catch (Exception e) {
                                                    log.error(e.toString());
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            //Once paths and flowrules are created, drop UDP notification
                            context.treatmentBuilder().drop();
                            break;
                        }

                    }

                    //Actions for common TCP or UDP traffic
                    //Locate destination host and set a path:
                    for(Host host : hostService.getHostsByIp(dstIpAddress)){
                        //if host up and found, set path flowrules on network devices

                        try {
                            setPath(context, host, protocol, srcIpAddress, srcIpPort,
                                    host.mac(), dstIpAddress, dstIpPort);

                        } catch (Exception e) {
                            log.error(e.toString());
                            e.printStackTrace();
                        }

                        //Packet to table: send packet to network device which came from. Will be redirected using the installed flowrule.
                        packetToTable(context);
                    }

                    break;

                default:
                    //log.info("Default - Received packet based on protocol: "+EthType.EtherType.lookup(ethPacket.getEtherType()));
                    return;
            }

        }

        private void setMPIPath(PacketContext context, Host sourceHost, Host dstHost, byte protocol, Ip4Address srcIp, Ip4Address dstIpAddress, int dstIpPort) {


            //Source and destination devices and ports
            DeviceId InputDeviceId = sourceHost.location().deviceId();
            PortNumber InputDevicePort = sourceHost.location().port();

            DeviceId OutputDeviceId = dstHost.location().deviceId();
            PortNumber OutputDevicePort = dstHost.location().port();

            //Source and destination hosts are under same network device
            if(InputDeviceId.equals(OutputDeviceId)){
                //log.info("      SOURCE AND DESTINATION UNDER SAME NETWORK DEVICE");
                //Source and destination hosts are on different network device ports
                if(!InputDevicePort.equals(OutputDevicePort)){
                    //Install flowrule setting route on same device:
                    installMPIPathFlowRule(dstHost.location(), protocol, srcIp, dstIpAddress, dstIpPort, false);
                    //Reverse path
                    installMPIPathFlowRule(sourceHost.location(), protocol, dstIpAddress, srcIp, dstIpPort, true);

                }
                return;
            }

            //log.info("      SOURCE AND DESTINATION ON DIFFERENT NETWORK DEVICES");
            //Source and destination hosts are under different network devices
            Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(), InputDeviceId, OutputDeviceId);
            //Set<Path> reversePaths = topologyService.getPaths(topologyService.currentTopology(), OutputDeviceId, InputDeviceId);

            //Select possible paths. Used links in paths are anotated in ActiveLinks
            Path path = selectMPIPaths(paths, InputDevicePort);
            //Path reversePath = selectMPIPaths(reversePaths, OutputDevicePort, true);



            if(path != null){
                //log.info("FOUND PATHS FOR HOSTS: "+srcIp.toString()+" - "+dstIpAddress.toString());
                //log.info(path.toString());
                //log.info(reversePath.toString());
                //Install flowrules on each network device involved on the path. Installing for both initial and reverse paths.

                path.links().forEach(l -> {

                    FlowId flowruleId = installMPIPathFlowRule(l.src(), protocol, srcIp, dstIpAddress, dstIpPort, false);
                    if(flowruleId != null) { //If path and flowrule installation success
                        synchronized (FlowRuleMutex) {
                            ActiveFlowrules.putIfAbsent(flowruleId, l);
                        }
                        synchronized (MPILinksMutex){
                            ActiveMPILinks.merge(l, 1.0, Double::sum);
                        }

                    }

                    FlowId reverseFlowruleId = installMPIPathFlowRule(l.dst(), protocol, dstIpAddress, srcIp, dstIpPort, true);
                    if(reverseFlowruleId != null) { //If path and flowrule installation success
                        synchronized (FlowRuleMutex) {
                            ActiveFlowrules.putIfAbsent(reverseFlowruleId, l);
                        }
                        synchronized (MPILinksMutex){
                            ActiveMPILinks.merge(l, 1.0, Double::sum);
                        }
                    }

                });
                //Install flowrule on last device (redirect to host)
                installMPIPathFlowRule(dstHost.location(), protocol, srcIp, dstIpAddress, dstIpPort, false);


                //Install flowrule on last device of reverse path
                installMPIPathFlowRule(sourceHost.location(), protocol, dstIpAddress, srcIp, dstIpPort, true);

                log.info("*********NEW MPI PATHS********* :\n "+ Arrays.asList(ActiveMPILinks));
                return;
            }
            else{

                //bad things
                try {
                    throw new Exception("Not found paths for hosts: "+srcIp.toString()+" - "+dstIpAddress.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }

        //Set a new path on network device from packet source to destination
        private void setPath(PacketContext context, Host dstHost, byte protocol, /*MacAddress srcMac,*/
                             Ip4Address srcIp, int srcIpPort, MacAddress dstMac, Ip4Address dstIp, int dstIpPort) throws Exception {


            //Source and destination devices and ports
            DeviceId InputDeviceId = context.inPacket().receivedFrom().deviceId();
            PortNumber InputDevicePort = context.inPacket().receivedFrom().port();
            DeviceId OutputDeviceId = dstHost.location().deviceId();
            PortNumber OutputDevicePort = dstHost.location().port();

            //Source and destination hosts are under same network device
            if(InputDeviceId.equals(OutputDeviceId)){
                //log.info("      SOURCE AND DESTINATION UNDER SAME NETWORK DEVICE");
                //Source and destination hosts are on different network device ports
                if(!InputDevicePort.equals(OutputDevicePort)){
                    //Install flowrule setting route on same device:
                    installPathFlowRule(dstHost.location(), protocol, srcIp, srcIpPort, dstIp, dstIpPort);
                    //Reverse path
                    installPathFlowRule(context.inPacket().receivedFrom(), protocol, dstIp, dstIpPort, srcIp, srcIpPort);

                }
                return;
            }
            //log.info("      SOURCE AND DESTINATION ON DIFFERENT NETWORK DEVICES");
            //Source and destination hosts are under different network devices
            Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(), InputDeviceId, OutputDeviceId);
            //Set<Path> reversePaths = topologyService.getPaths(topologyService.currentTopology(), OutputDeviceId, InputDeviceId);

            Path path = selectBalancedPaths(paths, InputDevicePort);
            //Path reversePath = selectBalancedPaths(reversePaths, OutputDevicePort, true);

            if(path != null){
                //log.info("FOUND PATHS FOR HOSTS: "+srcIp.toString()+" - "+dstIp.toString());
                //log.info(path.toString());
                //log.info(reversePath.toString());
                //Install flowrules on each network device involved on the path. Installing for both initial and reverse paths.

                path.links().forEach(l -> {
                    FlowId flowruleId = installPathFlowRule(l.src(), protocol, srcIp, srcIpPort, dstIp, dstIpPort);
                    if(flowruleId != null) { //If path and flowrule installation success
                        synchronized (FlowRuleMutex) {
                            ActiveFlowrules.putIfAbsent(flowruleId, l);
                        }
                        synchronized (LinksMutex){
                            ActiveLinks.merge(l, 1.0, Double::sum );
                        }
                    }

                    //Reverse path
                    FlowId reverseFlowruleId = installPathFlowRule(l.dst(), protocol, dstIp, dstIpPort, srcIp, srcIpPort);
                    if(reverseFlowruleId != null) { //If path and flowrule installation success
                        synchronized (FlowRuleMutex) {
                            ActiveFlowrules.putIfAbsent(reverseFlowruleId, l);
                        }
                        synchronized (LinksMutex){
                            ActiveLinks.merge(l, 1.0, Double::sum );
                        }
                    }
                });

                //Install flowrule on last device (redirect to host)
                installPathFlowRule(dstHost.location(), OutputDevicePort, dstMac, protocol, srcIp, srcIpPort, dstIp, dstIpPort);

                /*reversePath.links().forEach(l -> {
                    FlowId flowruleId = installPathFlowRule(l.src(), protocol, dstIp, dstIpPort, srcIp, srcIpPort);
                    if(flowruleId != null) { //If path and flowrule installation success
                        synchronized (mutex) {
                            ActiveReverseLinks.merge(l, 1.0, Double::sum);
                            ActiveFlowrules.putIfAbsent(flowruleId, l);

                        }
                    }

                });*/
                //Install flowrule on last device of reverse path
                installPathFlowRule(context.inPacket().receivedFrom(), InputDevicePort, dstMac, protocol, dstIp, dstIpPort, srcIp, srcIpPort);

                //log.info("*********NEW PATHS********* :\n "+ Arrays.asList(ActiveLinks));

                return;
            }
            else{

                //bad things
                throw new Exception("Not found paths for hosts: "+srcIp.toString()+" - "+dstIp.toString());
            }


        }

        //Select a path which first jump does not match with input port (possible cicle)
        private Path selectPaths(Set<Path> paths, PortNumber inputDevicePort) {
            Path auxPath = null;
            for(Path p : paths){
                auxPath = p;
                if(!p.src().port().equals(inputDevicePort)) return p;

            }

            return auxPath;
        }

        //Select a path which first jump does not match with input port (possible cicle)
        private Path selectBalancedPaths(Set<Path> paths, PortNumber inputDevicePort) {
            Path defPath = null;
            Path auxPath = null;

            double pathScore=Double.MAX_VALUE;


            for(Path p : paths){
                double auxScore=0;
                auxPath = p;

                if(!p.src().port().equals(inputDevicePort)){
                    //For each link in the path, if links are not used, or are the less used, we took that path (load balancing)
                    for(Link link : p.links()){
                        //Build temp path with links weigths
                        synchronized (LinksMutex) {
                            auxScore += ActiveLinks.getOrDefault(link, 0.0);
                        }

                    }
                    //If links are less used, path score will be lower
                    if(auxScore < pathScore) {
                        defPath = auxPath;
                        pathScore = auxScore;
                    }
                }
            }

            if(defPath == null){
                return auxPath;
            }else{
                return defPath;
            }

        }

        //Select a path which first jump does not match with input port (possible cicle)
        private Path selectMPIPaths(Set<Path> paths, PortNumber inputDevicePort) {
            Path defPath = null;
            Path auxPath = null;

            double pathScore=Double.MAX_VALUE;

            for(Path p : paths){

                int auxScore=0;
                auxPath = p;

                if(!p.src().port().equals(inputDevicePort)){
                    //For each link in the path, if links are not used, or are the less used, we took that path (load balancing)
                    for(Link link : p.links()){
                        //Build temp path with links weigths
                        synchronized (MPILinksMutex){
                            auxScore += ActiveMPILinks.getOrDefault(link, 0.0);
                        }
                    }
                    //If links are less used, path score will be lower
                    if(auxScore < pathScore) {
                        defPath = auxPath;
                        pathScore = auxScore;
                    }
                }
            }

            if(defPath == null){
                return auxPath;
            }else{
                return defPath;
            }

        }




        //Install path flowrule on devices from MPI paths. We know host dst ip port, but not src ip port
        private FlowId installMPIPathFlowRule(ConnectPoint dstConnectionPoint, byte protocol, Ip4Address srcIp,
                                         Ip4Address dstIp, int dstIpPort, boolean reverse) {

            TrafficSelector.Builder selector;
            //Matching rule

            selector = DefaultTrafficSelector.builder().
                    matchEthType(Ethernet.TYPE_IPV4).
                    matchIPSrc(srcIp.toIpPrefix()).
                    matchIPDst(dstIp.toIpPrefix()).
                    matchIPProtocol(protocol);

            if(reverse){ //Reverse path, when packets come from destination (source tcp port == dstIpPort)
                if(protocol == IPv4.PROTOCOL_TCP){
                        selector.matchTcpSrc(TpPort.tpPort(dstIpPort));
                    }else if(protocol == IPv4.PROTOCOL_UDP){
                        selector.matchUdpSrc(TpPort.tpPort(dstIpPort));
                    }
                }else{ //Standard path, when packets go from source to destination (dst tcp port == dstIpPort)
                    if(protocol == IPv4.PROTOCOL_TCP){
                        selector.matchTcpDst(TpPort.tpPort(dstIpPort));
                    }else if(protocol == IPv4.PROTOCOL_UDP){
                        selector.matchUdpDst(TpPort.tpPort(dstIpPort));
                    }
                }


            //Treatment rule
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setOutput(dstConnectionPoint.port());

            //FlowRule
            FlowRule.Builder flowrule = DefaultFlowRule.builder().
                    withSelector(selector.build()).
                    withTreatment(treatment.build()).
                    fromApp(appId).
                    forDevice(dstConnectionPoint.deviceId()).
                    withPriority(PacketPriority.HIGH.priorityValue()). //Higher priority to force use this flowrule
                    makeTemporary(200);

            /*200 seconds to ensure that flows still active during compute times. i.e: matrix product
              In theory, the rules should be fixed until MPI job is finished, but this is not implemented yet
             * */

            FlowRule installFlowrule = flowrule.build();
                //Apply rule - test this:
                flowRuleService.applyFlowRules(flowrule.build());
                return installFlowrule.id();
        }

        //Install path flowrule for specific device output -> for links from paths
        private FlowId installPathFlowRule(ConnectPoint dstConnectionPoint, byte protocol, Ip4Address srcIp, int srcIpPort,
                                         Ip4Address dstIp, int dstIpPort) {


            //Matching rule
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder().
                    matchEthType(Ethernet.TYPE_IPV4).
                    matchIPSrc(srcIp.toIpPrefix()).
                    matchIPDst(dstIp.toIpPrefix()).
                    matchIPProtocol(protocol);
            if(protocol == IPv4.PROTOCOL_TCP){
                selector.matchTcpSrc(TpPort.tpPort(srcIpPort)).
                        matchTcpDst(TpPort.tpPort(dstIpPort));
            }else if(protocol == IPv4.PROTOCOL_UDP){
                selector.matchUdpSrc(TpPort.tpPort(srcIpPort)).
                        matchUdpDst(TpPort.tpPort(dstIpPort));
            }

            //Treatment rule
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setOutput(dstConnectionPoint.port());

            //FlowRule
            FlowRule.Builder flowrule = DefaultFlowRule.builder().
                    withSelector(selector.build()).
                    withTreatment(treatment.build()).
                    fromApp(appId).
                    forDevice(dstConnectionPoint.deviceId()).
                    withPriority(40000).
                    makeTemporary(10);


            //Apply rule - test this:
            FlowRule installFlowrule = flowrule.build();
            //Apply rule - test this:
            flowRuleService.applyFlowRules(flowrule.build());
            return installFlowrule.id();
        }


        //Install path flowrule for specific device output port and destination mac -> for initial or destination devices
        private void installPathFlowRule(ConnectPoint dstConnectionPoint, PortNumber outputPort, MacAddress dstMac, byte protocol,
                                                      Ip4Address srcIp, int srcIpPort, Ip4Address dstIp, int dstIpPort) {


            //Matching rule
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder().
                    matchEthType(Ethernet.TYPE_IPV4).
                    matchIPSrc(srcIp.toIpPrefix()).
                    matchIPDst(dstIp.toIpPrefix()).
                    matchIPProtocol(protocol);
            if(protocol == IPv4.PROTOCOL_TCP){
                selector.matchTcpSrc(TpPort.tpPort(srcIpPort)).
                        matchTcpDst(TpPort.tpPort(dstIpPort));
            }else if(protocol == IPv4.PROTOCOL_UDP){
                selector.matchUdpSrc(TpPort.tpPort(srcIpPort)).
                        matchUdpDst(TpPort.tpPort(dstIpPort));
            }

            //Treatment rule
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setOutput(outputPort).setEthDst(dstMac);

            //FlowRule
            FlowRule.Builder flowrule = DefaultFlowRule.builder().
                    withSelector(selector.build()).
                    withTreatment(treatment.build()).
                    fromApp(appId).
                    forDevice(dstConnectionPoint.deviceId()).
                    withPriority(40000).
                    makeTemporary(10);


            //Apply rule - test this:
            flowRuleService.applyFlowRules(flowrule.build());
            return;
        }

        //Send the packet to the table which came from. The new flowrule should take care of it.

        private void packetToTable(PacketContext context) {
            //Wait for flowrule to activate (miliseconds)
            try { Thread.sleep(100); } catch (InterruptedException ignored) { }
            context.treatmentBuilder().setOutput(PortNumber.TABLE);
            context.send();
        }



        //TODO -> remove flowrules that route traffic from mpi endpoints. Handle with another UDP message?
        private void removeMPIEndpoint(){

        }


    }

    private class flowRuleEventListener implements FlowRuleListener{

        @Override
        public void event(FlowRuleEvent event) {
            FlowRule flowrule = event.subject(); //Flowrule of event

            if(flowrule.appId() == appId.id()){
                switch (event.type()){
                    //case RULE_ADDED:
                    //    log.info("NEW FLOWRULE ADDED: "+ flowrule.id());
                    //    break;
                    case RULE_REMOVED:
                        log.info("FLOWRULE REMOVED: "+ flowrule.id());
                        //Remove flowrules and the links they are using
                        Link usedLink;

                        synchronized (FlowRuleMutex) {
                            usedLink = ActiveFlowrules.getOrDefault(flowrule.id(), null);
                        }

                        if(usedLink != null){

                            ActiveFlowrules.remove(flowrule.id());

                            /*TODO -> Reverse paths alter path weigths, and more execution time are required to verify
                                which paths are direct or reverse, so reverse paths are not balanced as they do not
                                handle data transfer in this use case, only tcp control replies (acks). Reverse paths
                                are simply set up by using selected path and applying rules to destination devices
                                of each link instead of source devices.
                                This works because the first message of a TCP connection is from client to server trying
                                to connect, and this is the firs message of the conversation captured by the controller.
                                In this use case, client always sends data to server, as client is always identified as
                                sender, and server is always identified as receiver.
                            */
                            //Decrease usage of links when the flowrules that use them expire
                            synchronized (LinksMutex) {
                                ActiveLinks.computeIfPresent(usedLink, (key, val) -> val > 0.0 ? val - 1.0 : 0.0);
                            }
                            synchronized (MPILinksMutex) {
                                ActiveMPILinks.computeIfPresent(usedLink, (key, val) -> val > 0.0 ? val - 1.0 : 0.0);
                            }


                            /*If all flowrules are removed, reset counters. Sometimes counters are not fully decremented
                              (maybe due to concurrency issues)

                              This should only happen when an mpi job is finished, using the MPI Init UDP message of MPI
                              implementation on the repo. On every other case, this happens when the timer of every flowrule
                              expires
                            */

                            log.info("***********PENDING APP FLOWRULES***********"+flowRuleService.getFlowRulesByGroupId(appId, flowrule.groupId().id().shortValue()).spliterator().getExactSizeIfKnown());
                            if(flowRuleService.getFlowRulesByGroupId(appId, flowrule.groupId().id().shortValue()).spliterator().getExactSizeIfKnown() == 0) {
                                ActiveFlowrules = new ConcurrentHashMap<>();
                                ActiveLinks = new ConcurrentHashMap<>();
                                ActiveMPILinks = new ConcurrentHashMap<>();
                                log.info("*********RESET LINKS********* :\n " + Arrays.asList(ActiveLinks));
                                log.info("*********RESET MPI LINKS********* :\n " + Arrays.asList(ActiveMPILinks));
                            }
                        }


                        //log.info("*********USED LINKS********* :\n "+ Arrays.asList(ActiveLinks));

                        break;

                    default: break;

                }

            }

        }
    }


}



