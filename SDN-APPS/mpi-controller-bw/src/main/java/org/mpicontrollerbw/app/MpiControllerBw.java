/*
 *
 *
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
package org.mpicontrollerbw.app;


import org.apache.commons.lang.exception.ExceptionUtils;
import org.onlab.graph.DefaultEdgeWeigher;
import org.onlab.graph.ScalarWeight;
import org.onlab.graph.Weight;
import org.onlab.metrics.MetricsService;
import org.onlab.packet.*;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.*;
import org.onosproject.net.statistic.PollInterval;
import org.onosproject.net.statistic.PortStatisticsService;
import org.onosproject.net.statistic.StatisticService;
import org.onosproject.net.topology.*;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.device.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static java.nio.ByteBuffer.*;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {MpiControllerBw.class},
           property = {
               "someProperty=Some Default String Value"}
           )
public class MpiControllerBw implements MpiControllerBwInterface {

    public final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Some configurable property.
     */
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

    //new services for BW balancer
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StatisticService statisticService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PortStatisticsService portStatisticsService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MetricsService metricsService;

    protected BwWeigher bwWeigher;




    //---------------------------------------------//

    //Needed variables
    private ApplicationId appId;

    //Packet processor
    private PacketProcessor mpiControllerProcessor;

    private final FlowRuleListener flowRuleListener = new flowRuleEventListener(); //Class at end of file

    /*This structures can be improved, maybe using an MPI task identifier, so we can register every MPI task launched
      in a cluster. For now, this is all, and it works.
     */
    //HashMap Structure: <Ip Address of endpoint, <TCP Port of process, Topology/Device/Switch Port Number>>
    private ConcurrentMap<IpAddress, Map<Integer, PortNumber>> MPIEndpoints = new ConcurrentHashMap<IpAddress, Map<Integer, PortNumber>>();
    private Object MPIEndpointsMutex = new Object();


    //HashMap Structure: <FlowId, Link>:
    // Contains the active flowrules Ids and the links associated
    private ConcurrentMap<FlowId, Link> ActiveFlowrules = new ConcurrentHashMap<>(); //Active flowrules (id) and the link its using
    private Object FlowRuleMutex = new Object();


    //HashMap Structure: <MPI Endpoint Pair(IpAddress-Port), Map<FlowId, Link>>:
    // Contains the active flowrules for every MPI Endpoint Pair and the links they are using

    private ConcurrentMap<MyTuple, HashMap<FlowRule, Link>> ActiveMPIFlowrules = new ConcurrentHashMap<>(); //Active flowrules (id) and the link its using
    private Object MPIFlowRuleMutex = new Object();


    private ConcurrentMap<Link, Double> ActiveLinks = new ConcurrentHashMap<>(); //Links and their usage by active paths (times used by a flowrule) -> for load balancing
    private Object LinksMutex = new Object();


    private ConcurrentMap<Link, Double> ActiveMPILinks = new ConcurrentHashMap<>(); //Links and their usage by active paths (times used by a flowrule) -> for load balancing
    private Object MPILinksMutex = new Object();

    private ConcurrentMap<MyTuple, ArrayList<FlowRule>> ActiveMPIEndpoints = new ConcurrentHashMap<>();
    private Object MPIEndpointsMutex2 = new Object();




    //Address to notify MPI tasks
    private Ip4Address MPI_CONTROLLER_IP = Ip4Address.valueOf("10.0.0.1");
    private int MPI_CONTROLLER_NET_MASK = 24;
    private IpPrefix MPI_CONTROLLER_NET = IpPrefix.valueOf(MPI_CONTROLLER_IP, MPI_CONTROLLER_NET_MASK);
    private MacAddress MPI_CONTROLLER_MAC = MacAddress.valueOf("00:00:00:00:00:01");


    @Activate
    protected void activate() {

        try {
            log.info("MPI-CONTROLLER -- ACTIVATING");

            cfgService.registerProperties(getClass());
            log.info("Started");

            //Obtain app id
            appId = coreService.getAppId("org.mpicontrollerbw.app");

            //Packet Processor
            mpiControllerProcessor = new mpiControllerProcessor();
            packetService.addProcessor(mpiControllerProcessor, PacketProcessor.director(3));

            //Request Packets - obtain first packages at the beginning.
            //Packets can be obtained from edge devices only, as only them will have hosts connected
            edgePortService.getEdgePoints().forEach(connectPoint -> {
                log.info("EDGE DEVICE: " + connectPoint.deviceId());
                packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.of(connectPoint.deviceId()));
                //IPV4
                packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.of(connectPoint.deviceId()));
                //To catch all MPI Init UDP messages
                //packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).matchUdpDst(TpPort.tpPort(7777)).build(), PacketPriority.HIGH, appId, Optional.of(connectPoint.deviceId()));

            });

            flowRuleService.addListener(flowRuleListener);

            bwWeigher = new BwWeigher( 0.05, 0.001);

        } catch (Exception ex) {
            log.info("------------ERROR EN ACTIVATE------------" + ex);
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

    private class mpiControllerProcessor implements PacketProcessor {


        @Override
        public void process(PacketContext context) {

            InboundPacket packet = context.inPacket();
            Ethernet ethPacket = packet.parsed();
            if (ethPacket == null) return;

            //entry port from source device
            ///ConnectPoint srcConnectionPoint = packet.receivedFrom();

            switch (EthType.EtherType.lookup(ethPacket.getEtherType())) {
                case LLDP:
                    return;
                case ARP:
                    //log.info("ARP Packet Received");
                    //Ethernet Payload can be an ARP or IP packet
                    ARP arpPacket = (ARP) ethPacket.getPayload();


                    //Obtain Ip address of the source device
                    Ip4Address sourceIpAddress = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());
                    //Obtain Ip address of the target if it is an ARP REQUEST packet
                    Ip4Address targetIpAddress = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());

                    //ARP REQUEST
                    if (arpPacket.getOpCode() == ARP.OP_REQUEST) {

                        //If the target ip address is the ip address of the controller, send an ARP REPLY
                        if (targetIpAddress.getIp4Address().equals(MPI_CONTROLLER_IP.getIp4Address())) {
                            log.info(" ---- ARP REQUEST FOR MPI CONTROLLER");
                            //Send ARP REPLY to the source device
                            ConnectPoint srcConnectionPoint = packet.receivedFrom();
                            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                                    .setOutput(srcConnectionPoint.port());

                            Ethernet arpReply = ARP.buildArpReply(MPI_CONTROLLER_IP, MPI_CONTROLLER_MAC, packet.parsed());

                            packetService.emit(new DefaultOutboundPacket(
                                    srcConnectionPoint.deviceId(),
                                    treatment.build(),
                                    ByteBuffer.wrap(arpReply.serialize())));
                        }


                        //Destination device connection point
                        ConnectPoint dstConnectionPoint;


                        //Get host from target ip address at ARP REQUEST packet
                        Set<Host> hosts = hostService.getHostsByIp(targetIpAddress);
                        //If hosts found on the network, send it the ARP REQUEST packet
                        if (!hosts.isEmpty()) {
                            for (Host host : hosts) {
                                if (host.mac() != null) { //ARP Request done over broadcast (FF:FF:FF:FF), nothing else to compare

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

                    } else {
                        if (arpPacket.getOpCode() == ARP.OP_REPLY) {
                            //An ARP REQUEST has been received previously,
                            // so destination host of ARP REPLY (source of REQUEST) must be active

                            //Destination device connection point
                            ConnectPoint dstConnectionPoint;


                            //Get host from target ip address at ARP REPLAY packet
                            Set<Host> hosts = hostService.getHostsByIp(targetIpAddress);
                            if (!hosts.isEmpty()) {
                                for (Host host : hosts) {
                                    //If target host is found and equals eth packet destination MAC (it should)
                                    if (host.mac().equals(ethPacket.getDestinationMAC())) {

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
                    if (ipHeader == null) return;

                    //Get destination host ipv4 address
                    Ip4Address srcIpAddress = Ip4Address.valueOf(ipHeader.getSourceAddress());
                    Ip4Address dstIpAddress = Ip4Address.valueOf(ipHeader.getDestinationAddress());
                    int srcIpPort = 0;
                    int dstIpPort = 0;


                    //TCP or UDP
                    byte protocol = ipHeader.getProtocol();

                    //TCP TREATMENT
                    if (protocol == IPv4.PROTOCOL_TCP) {
                        TCP tcpHeader = (TCP) ipHeader.getPayload();
                        srcIpPort = tcpHeader.getSourcePort();
                        dstIpPort = tcpHeader.getDestinationPort();

                        //If TCP message is sent to an MPI host
                        synchronized (MPIEndpoints) {
                            if (MPIEndpoints.containsKey(dstIpAddress)) {
                                if (MPIEndpoints.get(dstIpAddress).containsKey(dstIpPort)) {
                                    //Install balanced flow rule
                                    Host srcHost = hostService.getHostsByIp(srcIpAddress).iterator().next();
                                    Host dstHost = hostService.getHostsByIp(dstIpAddress).iterator().next();

                                    try {
                                        setMPIPath(context, srcHost, dstHost, IPv4.PROTOCOL_TCP, srcIpAddress, dstIpAddress, srcIpPort, dstIpPort);
                                        //log.info("*********NEW MPI USED LINKS********* :\n "+ Arrays.asList(ActiveLinks));
                                        log.info("*********NEW MPI BTL PATH*********" + srcIpAddress + ":" + srcIpPort + " -> " + dstIpAddress + ":" + dstIpPort);
                                    } catch (Exception e) {
                                        log.error(e.toString());
                                        e.printStackTrace();
                                    }
                                    return;
                                }
                            }
                        }

                    }

                    //UDP TREATMENT
                    else if (protocol == IPv4.PROTOCOL_UDP) {
                        UDP udpHeader = (UDP) ipHeader.getPayload();

                        srcIpPort = udpHeader.getSourcePort();
                        dstIpPort = udpHeader.getDestinationPort();

                        //IF UDP MESSAGE IS FROM MPI HOST
                        if (dstIpAddress.equals(MPI_CONTROLLER_IP) & (dstIpPort == 7777)) { //ip address of controller or special ip to identify
                            //log.info("      MPI UDP PACKET DESTINATION ADDRESS: "+dstIpAddress.toString()+":"+dstIpPort);
                            //log.info("      PEER INFO: "+(udpHeader.getPayload()));
                            byte[] rawMessage = ((byte[]) udpHeader.getPayload().serialize());

                            //IP and port are sent from MPI peer using an array of 2 integers. Now we have to extract from payload
                            ByteBuffer message = wrap(rawMessage);
                            message.order(ByteOrder.LITTLE_ENDIAN);
                            IntBuffer buffer = message.asIntBuffer();
                            int[] PeerAddr = new int[3];

                            buffer.get(PeerAddr);

                            int peerPort = Integer.reverseBytes(PeerAddr[1]);
                            Ip4Address peerIpAddress = Ip4Address.valueOf(PeerAddr[0]);

                            if (Integer.reverseBytes(PeerAddr[2]) == 0) {
                                //Remove MPI endpoint
                                synchronized (MPIEndpoints) {
                                    if (MPIEndpoints.containsKey(peerIpAddress)) {
                                        MPIEndpoints.get(peerIpAddress).remove(peerPort);

                                        if (MPIEndpoints.get(peerIpAddress).isEmpty()) {
                                            MPIEndpoints.remove(peerIpAddress);
                                        }
                                    }
                                }
                                log.info(" ***** MPI ENDPOINT REMOVED: " + peerIpAddress + ":" + peerPort);
                                //Uninstall MPI Flows of an endpoint on every switch
                                uninstallMPIFlow(peerIpAddress, peerPort);

                                //Remove registered flows
                                unregisterFlow(peerIpAddress, peerPort, true);

                                context.treatmentBuilder().drop();
                                return;

                            } else if (Integer.reverseBytes(PeerAddr[2]) == 1) {

                                //Add MPI endpoint
                                //Store MPI endpoint. This is not really necessary if removing mpi endpoints is not implemented
                                synchronized (MPIEndpointsMutex) {
                                    if (MPIEndpoints.containsKey(peerIpAddress)) {
                                        //If endpoint is already registered, set new endpoint tcp port of new MPI process
                                        MPIEndpoints.get(peerIpAddress).putIfAbsent(peerPort, context.inPacket().receivedFrom().port());
                                    } else {
                                        ConcurrentHashMap<Integer, PortNumber> MPIPeer = new ConcurrentHashMap<Integer, PortNumber>();
                                        MPIPeer.put(peerPort, context.inPacket().receivedFrom().port());

                                        MPIEndpoints.putIfAbsent(peerIpAddress, MPIPeer);
                                    }
                                }
                                log.info(" ***** MPI ENDPOINT ADDED: " + peerIpAddress + ":" + peerPort);
                                //Install specific flows to capture first MPI packets on every edge switch
                                edgePortService.getEdgePoints().forEach(connectPoint -> {
                                    FlowRule.Builder endpointFlowRule = DefaultFlowRule.builder().
                                            withSelector(DefaultTrafficSelector.builder().
                                                    matchEthType(Ethernet.TYPE_IPV4).
                                                    matchIPProtocol(IPv4.PROTOCOL_TCP).
                                                    matchIPDst(peerIpAddress.toIpPrefix()).
                                                    matchTcpDst(TpPort.tpPort(peerPort)).
                                                    build()).
                                            withTreatment(DefaultTrafficTreatment.builder().setOutput(PortNumber.CONTROLLER).build()).
                                            fromApp(appId).
                                            forDevice(connectPoint.deviceId()).
                                            withPriority(PacketPriority.CONTROL.priorityValue()).
                                            makePermanent();
                                    FlowRule installFlowrule = endpointFlowRule.build();
                                    flowRuleService.applyFlowRules(installFlowrule);

                                    synchronized (MPIEndpointsMutex2){
                                        if(ActiveMPIEndpoints.containsKey(new MyTuple(peerIpAddress, peerPort))) {
                                            ActiveMPIEndpoints.get(new MyTuple(peerIpAddress, peerPort)).add(installFlowrule);
                                        }else {
                                            ArrayList<FlowRule> flows = new ArrayList<>();
                                            flows.add(installFlowrule);
                                            ActiveMPIEndpoints.put(new MyTuple(peerIpAddress, peerPort), flows);
                                        }



                                    }
                                });

                            }

                            try {

                                if (!MPI_CONTROLLER_NET.contains(IpPrefix.valueOf(peerIpAddress, MPI_CONTROLLER_NET_MASK))) {
                                    log.info(" ***** EXCLUDING MPI ENDPOINT: " + peerIpAddress + ", NOT IN MPI CONTROLLER NETWORK");
                                    return;
                                } else {
                                    log.info(" ***** NEW MPI ENDPOINT: " + peerIpAddress + ":" + peerPort);
                                    //log.info("      MPI ENDPOINT ADDR INFO: "+ Ip4Address.valueOf(PeerAddr[0]) + ":" + Integer.reverseBytes(PeerAddr[1]));
                                }
                            } catch (Exception ex) {
                                log.error(ex.toString());
                            }


                            //Once paths and flowrules are created, drop UDP notification
                            context.treatmentBuilder().drop();
                            break;
                        }

                    }

                    //Actions for common TCP or UDP traffic
                    //Locate destination host and set a path:
                    for (Host host : hostService.getHostsByIp(dstIpAddress)) {
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
            }

        }


        private void setMPIPath(PacketContext context, Host sourceHost, Host dstHost, byte protocol, Ip4Address srcIp, Ip4Address dstIpAddress, int srcIpPort, int dstIpPort) {


            //Source and destination devices and ports
            DeviceId InputDeviceId = sourceHost.location().deviceId();
            PortNumber InputDevicePort = sourceHost.location().port();

            DeviceId OutputDeviceId = dstHost.location().deviceId();
            PortNumber OutputDevicePort = dstHost.location().port();

            //Source and destination hosts are under same network device
            if (InputDeviceId.equals(OutputDeviceId)) {
                //log.info("      SOURCE AND DESTINATION UNDER SAME NETWORK DEVICE");
                //Source and destination hosts are on different network device ports
                if (!InputDevicePort.equals(OutputDevicePort)) {
                    //Install flowrule setting route on same device. Local flowrules are not necessary to be registered nor balanced:
                    FlowRule LocalMPIFlow = installMPIPathFlowRule(dstHost.location(), protocol, srcIp, dstIpAddress, srcIpPort, dstIpPort, false);
                    registerFlow(dstIpAddress, dstIpPort, LocalMPIFlow, null, true);

                    //Reverse path
                    FlowRule LocalMPIReverseFlow = installMPIPathFlowRule(sourceHost.location(), protocol, dstIpAddress, srcIp, srcIpPort, dstIpPort, true);
                    registerFlow(dstIpAddress, dstIpPort, LocalMPIReverseFlow, null, true);
                }
                return;
            }

            //log.info("      SOURCE AND DESTINATION ON DIFFERENT NETWORK DEVICES");
            //Source and destination hosts are under different network devices
            Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(), InputDeviceId, OutputDeviceId);
            //Set<Path> reversePaths = topologyService.getPaths(topologyService.currentTopology(), OutputDeviceId, InputDeviceId);


            //Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(), InputDeviceId, OutputDeviceId, bwWeigher);

            //Select possible paths. Used links in paths are annotated in ActiveLinks
            Path path = selectBWBalancedPaths(paths, InputDevicePort, true);
            //Path reversePath = selectMPIPaths(reversePaths, OutputDevicePort, true);


            if (path != null) {
                //log.info("FOUND PATHS FOR HOSTS: "+srcIp.toString()+" - "+dstIpAddress.toString());
                //log.info(path.toString());
                //log.info(reversePath.toString());
                //Install flowrules on each network device involved on the path. Installing for both initial and reverse paths.

                path.links().forEach(l -> {

                    FlowRule MPIFlow = installMPIPathFlowRule(l.src(), protocol, srcIp, dstIpAddress, srcIpPort, dstIpPort, false);
                    registerFlow(dstIpAddress, dstIpPort, MPIFlow, l, true);

                    FlowRule MPIReverseFlow = installMPIPathFlowRule(l.dst(), protocol, dstIpAddress, srcIp, srcIpPort, dstIpPort, true);
                    registerFlow(dstIpAddress, dstIpPort, MPIReverseFlow, l, true);
                });


                //Install flowrule on last device (redirect to host). Last mile flowrules are not necessary to be registered nor balanced:
                FlowRule finalFlow = installMPIPathFlowRule(dstHost.location(), protocol, srcIp, dstIpAddress, srcIpPort, dstIpPort, false);
                registerFlow(dstIpAddress, dstIpPort, finalFlow, null, true);


                //Install flowrule on last device of reverse path
                FlowRule finalReverseFlow = installMPIPathFlowRule(sourceHost.location(), protocol, dstIpAddress, srcIp, srcIpPort, dstIpPort, true);
                registerFlow(dstIpAddress, dstIpPort, finalReverseFlow, null, true);


                log.info("*********NEW MPI PATHS********* :\n " + Arrays.asList(ActiveMPILinks));

            } else {

                //bad things
                try {
                    throw new Exception("Not found paths for hosts: " + srcIp.toString() + " - " + dstIpAddress.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }


        private void registerFlow(Ip4Address dstIpAddress, int dstPort, FlowRule flowrule, Link l, boolean MPI) {
            if(MPI) {
                synchronized (MPIFlowRuleMutex) {
                    MyTuple Endpoint = new MyTuple(dstIpAddress, dstPort);
                    if (!ActiveMPIFlowrules.containsKey(Endpoint)) {
                        ActiveMPIFlowrules.putIfAbsent(Endpoint, new HashMap<>());
                    }
                    ActiveMPIFlowrules.get(Endpoint).putIfAbsent(flowrule, l);

                }

                if (l != null) {
                    synchronized (MPILinksMutex) {
                        ActiveMPILinks.merge(l, 1.0, Double::sum);
                    }
                }

            }else{
                synchronized (FlowRuleMutex) {
                    ActiveFlowrules.putIfAbsent(flowrule.id(), l);
                }
                synchronized (LinksMutex) {
                    ActiveLinks.merge(l, 1.0, Double::sum);
                }

            }
        }

        private void unregisterFlow(Ip4Address dstIpAddress, int dstPort, boolean MPI) {
            //Remove flowrule and update links usage
            Collection usedLinks = new ArrayList<Link>();

            if(MPI) {
                synchronized (MPIFlowRuleMutex) {
                    if (ActiveMPIFlowrules.containsKey(new MyTuple(dstIpAddress, dstPort))) {
                        //Only removes the registered entries, not uninstalls the flowrules
                        usedLinks = ActiveMPIFlowrules.get(new MyTuple(dstIpAddress, dstPort)).values();
                        ActiveMPIFlowrules.remove(new MyTuple(dstIpAddress, dstPort));
                    }

                }
                synchronized (MPILinksMutex) {
                    for (Object usedLink : usedLinks) {
                        if (usedLink != null)
                            ActiveMPILinks.computeIfPresent((Link) usedLink, (key, val) -> val > 0.0 ? val - 1.0 : 0.0);
                    }
                }
            }//else{
                //Done on flowRemoved event when timeout is reached
            //}

        }

        //This method not really uninstalls the flow entries on the network devices, but updates them with a timeout, to let the MPI program finnish and close connections
        private void uninstallMPIFlow(Ip4Address peerIpAddress, int peerPort) {
            synchronized (MPIFlowRuleMutex) {
                if (ActiveMPIFlowrules.containsKey(new MyTuple(peerIpAddress, peerPort))) {
                    ActiveMPIFlowrules.get(new MyTuple(peerIpAddress, peerPort)).keySet().forEach(flowRule -> {
                        //First, install flowrule with same fields but with new timeout
                        FlowRule.Builder flow = DefaultFlowRule.builder().withSelector(flowRule.selector()).
                                withTreatment(flowRule.treatment()).
                                fromApp(appId).
                                forDevice(flowRule.deviceId()).
                                withPriority(flowRule.priority()).
                                makeTemporary(100); //Timeout to let the MPI program finnish and close connections
                        flowRuleService.applyFlowRules(flow.build());
                        //Last, remove old flowrule
                        flowRuleService.removeFlowRules(flowRule);

                    });
                }
            }
            synchronized (MPIEndpointsMutex2) {
                if(ActiveMPIEndpoints.containsKey(new MyTuple(peerIpAddress, peerPort))){
                    ArrayList<FlowRule> flows = ActiveMPIEndpoints.get(new MyTuple(peerIpAddress, peerPort));

                    for (FlowRule flow : flows) {
                        flowRuleService.removeFlowRules(flow);
                    }
                    ActiveMPIEndpoints.remove(new MyTuple(peerIpAddress, peerPort));
                }
            }
        }



        //Set a new path on network device from packet source to destination
        private void setPath(PacketContext context, Host dstHost, byte protocol, /*MacAddress srcMac,*/
                             Ip4Address srcIp, int srcIpPort, MacAddress dstMac, Ip4Address dstIp, int dstIpPort) throws Exception {

            try {
                //Source and destination devices and ports
                DeviceId InputDeviceId = context.inPacket().receivedFrom().deviceId();
                PortNumber InputDevicePort = context.inPacket().receivedFrom().port();
                DeviceId OutputDeviceId = dstHost.location().deviceId();
                PortNumber OutputDevicePort = dstHost.location().port();

                //Source and destination hosts are under same network device
                if (InputDeviceId.equals(OutputDeviceId)) {
                    //log.info("      SOURCE AND DESTINATION UNDER SAME NETWORK DEVICE");
                    //Source and destination hosts are on different network device ports
                    if (!InputDevicePort.equals(OutputDevicePort)) {
                        //Install flowrule setting route on same device:
                        installPathFlowRule(dstHost.location(), protocol, srcIp, srcIpPort, dstIp, dstIpPort);
                        //Reverse path
                        installPathFlowRule(context.inPacket().receivedFrom(), protocol, dstIp, dstIpPort, srcIp, srcIpPort);

                    }
                    return;
                }
                //log.info("      SOURCE AND DESTINATION ON DIFFERENT NETWORK DEVICES");
                //Source and destination hosts are under different network devices
                //Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(), InputDeviceId, OutputDeviceId);

                Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(), InputDeviceId, OutputDeviceId, bwWeigher);
                log.info("AVAILABLE PATHS: "+paths.size());
                //Set<Path> reversePaths = topologyService.getPaths(topologyService.currentTopology(), OutputDeviceId, InputDeviceId);

                //Path path = selectBWBalancedPaths(paths, InputDevicePort, false);
                Path path = null;
                if (paths != null) {
                    path = paths.iterator().next();
                }
                //Path reversePath = selectBalancedPaths(reversePaths, OutputDevicePort, true);

                if (path != null) {
                    //log.info("FOUND PATHS FOR HOSTS: "+srcIp.toString()+" - "+dstIp.toString());
                    //log.info(path.toString());
                    //log.info(reversePath.toString());
                    //Install flowrules on each network device involved on the path. Installing for both initial and reverse paths.

                    path.links().forEach(l -> {
                        FlowRule flowrule = installPathFlowRule(l.src(), protocol, srcIp, srcIpPort, dstIp, dstIpPort);
                        if (flowrule != null) { //If path and flowrule installation success
                            registerFlow(dstIp, dstIpPort, flowrule, l, false);

                        }

                        //Reverse path
                        FlowRule reverseFlowrule = installPathFlowRule(l.dst(), protocol, dstIp, dstIpPort, srcIp, srcIpPort);
                        if (reverseFlowrule != null) { //If path and flowrule installation success
                            registerFlow(dstIp, dstIpPort, reverseFlowrule, l, false);
                        }
                    });

                    //Install flowrule on last device (redirect to host)
                    installPathFlowRule(dstHost.location(), OutputDevicePort, dstMac, protocol, srcIp, srcIpPort, dstIp, dstIpPort);

                    //Install flowrule on last device of reverse path
                    installPathFlowRule(context.inPacket().receivedFrom(), InputDevicePort, dstMac, protocol, dstIp, dstIpPort, srcIp, srcIpPort);


                } else {
                    //bad things
                    throw new Exception("Not found paths for hosts: " + srcIp.toString() + " - " + dstIp.toString());
                }

            }catch (Exception e){
                log.info("ERROR ON SET PATH "+ExceptionUtils.getStackTrace(e));
            }
        }

        //Select a path which first jump does not match with input port (to avoid possible cycle)
        private Path selectPaths(Set<Path> paths, PortNumber inputDevicePort) {
            Path auxPath = null;
            for (Path p : paths) {
                auxPath = p;
                if (!p.src().port().equals(inputDevicePort)) return p;

            }

            return auxPath;
        }

        //Select a path which first jump does not match with input port (to avoid possible cycle). Balanced paths version
        private Path selectBWBalancedPaths(Set<Path> paths, PortNumber inputDevicePort, boolean MPI) {
            Path defPath = null;
            Path auxPath = null;

            double pathScore = Double.MAX_VALUE;


            for (Path p : paths) {
                double auxScore = 0;
                auxPath = p;

                if (!p.src().port().equals(inputDevicePort)) {
                    //For each link in the path, if links are not used, or are the less used, we took that path (load balancing)
                    for (Link link : p.links()) {
                        //PollInterval    pollInterval = .value("portPollInterval");
                        //total port speed (megabits per second):
                        long maxLink = deviceService.getPort(link.src()).portSpeed();


                        //port used bandwidth in Bytes per second
                        //portStatisticsService.load(link.src(), PortStatisticsService.MetricType.BYTES).rate;

                        //Build temp path with links weights
                        if (MPI) {
                            synchronized (MPILinksMutex) {
                                auxScore += portStatisticsService.load(link.src(), PortStatisticsService.MetricType.BYTES).rate()*8/maxLink;
                            }
                        }else {
                            synchronized (LinksMutex) {
                                auxScore += portStatisticsService.load(link.src(), PortStatisticsService.MetricType.BYTES).rate()*8/maxLink;
                            }
                        }

                    }

                    auxScore = auxScore / p.links().size();
                    //If links are less used, path score will be lower
                    if (auxScore < pathScore) {
                        defPath = auxPath;
                        pathScore = auxScore;
                    }
                }
            }

            if (defPath == null) {
                return auxPath;
            } else {
                return defPath;
            }

        }

        private Path selectBalancedPaths(Set<Path> paths, PortNumber inputDevicePort, boolean MPI) {
            Path defPath = null;
            Path auxPath = null;

            double pathScore = Double.MAX_VALUE;


            for (Path p : paths) {
                double auxScore = 0;
                auxPath = p;

                if (!p.src().port().equals(inputDevicePort)) {
                    //For each link in the path, if links are not used, or are the less used, we took that path (load balancing)
                    for (Link link : p.links()) {
                        //Build temp path with links weights
                        if (MPI) {
                            synchronized (MPILinksMutex) {
                                auxScore += ActiveMPILinks.getOrDefault(link, 0.0);
                            }
                        }else {
                            synchronized (LinksMutex) {
                                auxScore += ActiveLinks.getOrDefault(link, 0.0);
                            }
                        }

                    }
                    //If links are less used, path score will be lower
                    if (auxScore < pathScore) {
                        defPath = auxPath;
                        pathScore = auxScore;
                    }
                }
            }

            if (defPath == null) {
                return auxPath;
            } else {
                return defPath;
            }


        }


        //Install path flowrule on devices from MPI paths.
        private FlowRule installMPIPathFlowRule(ConnectPoint dstConnectionPoint, byte protocol, Ip4Address srcIp,
                                                Ip4Address dstIp, int srcIpPort, int dstIpPort, boolean reverse) {

            TrafficSelector.Builder selector;
            //Matching rule

            selector = DefaultTrafficSelector.builder().
                    matchEthType(Ethernet.TYPE_IPV4).
                    matchIPSrc(srcIp.toIpPrefix()).
                    matchIPDst(dstIp.toIpPrefix()).
                    matchIPProtocol(protocol);

            if (reverse) { //Reverse path, when packets come from destination (source tcp port == dstIpPort)
                if (protocol == IPv4.PROTOCOL_TCP) {
                    selector.matchTcpSrc(TpPort.tpPort(dstIpPort)).matchTcpDst(TpPort.tpPort(srcIpPort));
                } else if (protocol == IPv4.PROTOCOL_UDP) {
                    selector.matchUdpSrc(TpPort.tpPort(dstIpPort)).matchUdpDst(TpPort.tpPort(srcIpPort));
                }
            } else { //Standard path, when packets go from source to destination (dst tcp port == dstIpPort)
                if (protocol == IPv4.PROTOCOL_TCP) {
                    selector.matchTcpDst(TpPort.tpPort(dstIpPort)).matchTcpSrc(TpPort.tpPort(srcIpPort));
                } else if (protocol == IPv4.PROTOCOL_UDP) {
                    selector.matchUdpDst(TpPort.tpPort(dstIpPort)).matchUdpSrc(TpPort.tpPort(srcIpPort));
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
                            makePermanent();

            FlowRule installFlowrule = flowrule.build();
            //Apply rule - test this:
            flowRuleService.applyFlowRules(flowrule.build());
            return installFlowrule;
        }

        //Install path flowrule for specific device output -> for links from paths
        private FlowRule installPathFlowRule(ConnectPoint dstConnectionPoint, byte protocol, Ip4Address srcIp, int srcIpPort,
                                           Ip4Address dstIp, int dstIpPort) {


            //Matching rule
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder().
                    matchEthType(Ethernet.TYPE_IPV4).
                    matchIPSrc(srcIp.toIpPrefix()).
                    matchIPDst(dstIp.toIpPrefix()).
                    matchIPProtocol(protocol);
            if (protocol == IPv4.PROTOCOL_TCP) {
                selector.matchTcpSrc(TpPort.tpPort(srcIpPort)).
                        matchTcpDst(TpPort.tpPort(dstIpPort));
            } else if (protocol == IPv4.PROTOCOL_UDP) {
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
                    withPriority(PacketPriority.MEDIUM.priorityValue()).
                    makeTemporary(10);


            //Apply rule - test this:
            FlowRule installFlowrule = flowrule.build();
            //Apply rule - test this:
            flowRuleService.applyFlowRules(flowrule.build());
            return installFlowrule;
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
            if (protocol == IPv4.PROTOCOL_TCP) {
                selector.matchTcpSrc(TpPort.tpPort(srcIpPort)).
                        matchTcpDst(TpPort.tpPort(dstIpPort));
            } else if (protocol == IPv4.PROTOCOL_UDP) {
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
                    withPriority(PacketPriority.MEDIUM.priorityValue()).
                    makeTemporary(10);


            //Apply rule - test this:
            flowRuleService.applyFlowRules(flowrule.build());
            return;
        }

        //Send the packet to the table which came from. The new flowrule should take care of it.

        private void packetToTable(PacketContext context) {
            //Wait for flowrule to activate (miliseconds)
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            context.treatmentBuilder().setOutput(PortNumber.TABLE);
            context.send();

        }


    }


    private class flowRuleEventListener implements FlowRuleListener {

        @Override
        public void event(FlowRuleEvent event) {
            FlowRule flowrule = event.subject(); //Flowrule of event

            if (flowrule.appId() == appId.id()) {
                switch (event.type()) {
                    //case RULE_ADDED:
                    //    log.info("NEW FLOWRULE ADDED: "+ flowrule.id());
                    //    break;
                    case RULE_REMOVED:
                        log.info("FLOWRULE REMOVED: " + flowrule.id());
                        //Remove flowrules and the links they are using
                        Link usedLink;

                        synchronized (FlowRuleMutex) {
                            //Only decrement usage of flowrules not used by and MPI endpoint
                            // Search on ActiveFlowrules for the flowrule that was removed (not on ActiveMPIFlowrules)
                            // This flowrules expire when the timeout is reached
                            // MPI Flowrules are updated/decremented when UDP MPI endpoint message of finalize is received
                            usedLink = ActiveFlowrules.getOrDefault(flowrule.id(), null);
                        }

                        if(usedLink != null){

                            ActiveFlowrules.remove(flowrule.id());

                            /*  Reverse paths alter path weights, and more execution time are required to verify
                                which paths are direct or reverse, so reverse paths are not balanced as they usually do not
                                handle data transfer in this use case, only tcp control replies (acks). Reverse paths
                                are simply set up by using selected path and applying rules to destination devices
                                of each link instead of source devices.
                                This works because the first message of a TCP connection is from client to server trying
                                to connect, and this is the firs message of the conversation captured by the controller.
                                In this use case, client always sends data to server, as client is always identified as
                                sender, and server is always identified as receiver.

                                To use balanced reverse paths, simply use selectBalancedPath() switching src and dst,
                                instead of using the reverse path.
                            */

                            //Decrease usage of links when the flowrules that use them expire
                            synchronized (LinksMutex) {
                                ActiveLinks.computeIfPresent(usedLink, (key, val) -> val > 0.0 ? val - 1.0 : 0.0);
                            }

                            /*If all flowrules are removed, reset counters. Sometimes counters are not fully decremented
                              (maybe due to concurrency issues)
                            */
//
                            log.info("***********PENDING APP FLOWRULES***********"+flowRuleService.getFlowRulesByGroupId(appId, flowrule.groupId().id().shortValue()).spliterator().getExactSizeIfKnown());
                            if(flowRuleService.getFlowRulesByGroupId(appId, flowrule.groupId().id().shortValue()).spliterator().getExactSizeIfKnown() == 0) {
                                ActiveFlowrules = new ConcurrentHashMap<>();
                                ActiveLinks = new ConcurrentHashMap<>();
//                                ActiveMPILinks = new ConcurrentHashMap<>();
                                log.info("*********RESET LINKS********* :\n " + Arrays.asList(ActiveLinks));
//                                log.info("*********RESET MPI LINKS********* :\n " + Arrays.asList(ActiveMPILinks));
                            }
                        }
                        //log.info("*********USED LINKS********* :\n "+ Arrays.asList(ActiveLinks));
                        break;
                    default:
                        break;

                }

            }

        }
    }



    public class BwWeigher extends DefaultEdgeWeigher<TopologyVertex, TopologyEdge> implements LinkWeigher {
        Double Bandwidth_Limit;
        Double Sameness_Limit;

        /* constructor
        Params:
        Mpi_Controller: The MpiControllerBw object that is used to access the ONOS services
        Bandwidth_Limit: The bandwidth limit in percentage of the link capacity: e.g. 0.5 means 50% of the link capacity
        Sameness_Limit: Value to compare bandwidth occupancy. If the difference between two values is less than Same_Limit, it is considered the same value
        */
        public BwWeigher(Double Bandwidth_Limit, Double Sameness_Limit) {
            this.Bandwidth_Limit = Bandwidth_Limit;
            this.Sameness_Limit = Sameness_Limit;

        }


        @Override
        public Weight weight(TopologyEdge edge) {

            try {

                String v = edge.link().annotations().value(AnnotationKeys.METRIC);
                log.info("METRIC: " + v);
                statisticService.load(edge.link());
                double maxLink = (double) deviceService.getPort(edge.link().src()).portSpeed()* Math.pow(10.0,6); //Megabits per second to bit per second
                double bwRate = (double) portStatisticsService.load(edge.link().src(), PortStatisticsService.MetricType.BYTES).rate() * 8.0; //bytes per second to bits per second
                double occupancy = bwRate / maxLink;


                log.info("Link: " + edge.link().toString() + " has ingress bandwidth: " +
                        bwRate + " bps, Max Link Speed: " + maxLink + "bps, occupancy: " + occupancy);

                //Weight linkWeight = new ScalarWeight(HOP_WEIGHT_VALUE + bwRate / maxLink);


                //If link is used over the bandwidth limit, return non-viable weight
                if(occupancy > Bandwidth_Limit - this.Sameness_Limit){
                    return ScalarWeight.toWeight(Double.POSITIVE_INFINITY);
                }

                if(occupancy <= 0.0 + this.Sameness_Limit){
                    return ScalarWeight.toWeight(1.0);
                }


                //Weight linkWeight = new ScalarWeight(1 - bwRate / maxLink);
                //log.info("Link weight: " + linkWeight.toString());
                return ScalarWeight.toWeight(bwRate / maxLink);
                //return linkWeight;

            } catch (Exception e){
                log.error("Error on BWWeigher:"+ ExceptionUtils.getStackTrace(e));
                e.printStackTrace();
            }


            return ScalarWeight.toWeight(Double.POSITIVE_INFINITY);
        }

        @Override
        public Weight getInitialWeight() {
            return ScalarWeight.toWeight(0.0);
        }

    }



}



