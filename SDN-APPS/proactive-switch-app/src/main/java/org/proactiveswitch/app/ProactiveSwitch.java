/*
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
package org.proactiveswitch.app;

import org.onlab.packet.*;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {ProactiveSwitchInterface.class},
           property = {
               "someProperty=Some Default String Value"}
           )
public class ProactiveSwitch implements ProactiveSwitchInterface {
    
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
    private PacketProcessor proactiveSwitchProcessor;

    @Activate
    protected void activate() {

        try{
            log.info("PROACTIVE-SWITCH -- ACTIVATING");

            cfgService.registerProperties(getClass());
            log.info("Started");

            //Obtain app id
            appId = coreService.getAppId("org.proactiveswitch.app");

            //Procesador de paquetes
            proactiveSwitchProcessor = new ProactiveSwitchProcessor();
            packetService.addProcessor(proactiveSwitchProcessor, PacketProcessor.director(3));

            //Request Packets - obtain first packages at the beginning.
            //Packets can be obtained from edge devices only, as only them will have hosts connected
            edgePortService.getEdgePoints().forEach(connectPoint -> {
                log.info("EDGE DEVICE: "+ connectPoint.deviceId());

                //ARP
                packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.of(connectPoint.deviceId()));
                //IPV4
                packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.of(connectPoint.deviceId()));


            });

        }catch(Exception ex){
            log.info("------------ ACTIVATE ERROR ------------" + ex.toString());
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

        });

        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(proactiveSwitchProcessor);
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

    private class ProactiveSwitchProcessor implements PacketProcessor{

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

                        //Destination device connection pointt
                        ConnectPoint dstConnectionPoint;
                        //Mac address of destination host
                        ///MacAddress dstMac = null;

                        //Get host from target ip address at ARP REQUEST packet
                        Set<Host> hosts = hostService.getHostsByIp(targetIpAddress);
                        //If hosts found on the network, send it the ARP REQUEST packet
                        if(!hosts.isEmpty()){
                            for (Host host : hosts){
                                if(host.mac() != null){ //ARP Request done over broadcast (FF:FF:FF:FF), nothing else to compare
                                    ///dstMac = host.mac();        //mac address of the host
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
                            //Mac address of destination host
                            ///MacAddress dstMAC = null;

                            //Get host from target ip address at ARP REPLAY packet
                            Set<Host> hosts = hostService.getHostsByIp(targetIpAddress);
                            if(!hosts.isEmpty()){
                                for(Host host : hosts){
                                    //If target host is found and equals eth packet destination MAC (it should)
                                    if(host.mac().equals(ethPacket.getDestinationMAC())){
                                         ///dstMAC = host.mac();
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
                    if(protocol == IPv4.PROTOCOL_TCP){
                        TCP tcpHeader = (TCP) ipHeader.getPayload();
                        srcIpPort = tcpHeader.getSourcePort();
                        dstIpPort = tcpHeader.getDestinationPort();

                    }else if(protocol == IPv4.PROTOCOL_UDP){
                        UDP udpHeader = (UDP) ipHeader.getPayload();
                        srcIpPort = udpHeader.getSourcePort();
                        dstIpPort = udpHeader.getDestinationPort();
                    }

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
                    log.info("Default - Received packet based on protocol: "+EthType.EtherType.lookup(ethPacket.getEtherType()));
                    return;
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
                log.info("      SOURCE AND DESTINATION UNDER SAME NETWORK DEVICE");
                //Source and destination hosts are on different network device ports
                if(!InputDevicePort.equals(OutputDevicePort)){
                    //Install flowrule setting route on same device:
                    installPathFlowRule(dstHost.location(), protocol, srcIp, srcIpPort, dstIp, dstIpPort);
                    //Reverse path
                    installPathFlowRule(context.inPacket().receivedFrom(), protocol, dstIp, dstIpPort, srcIp, srcIpPort);

                }
                return;
            }
            log.info("      SOURCE AND DESTINATION ON DIFFERENT NETWORK DEVICES");
            //Source and destination hosts are under different network devices
            Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(), InputDeviceId, OutputDeviceId);
            Set<Path> reversePaths = topologyService.getPaths(topologyService.currentTopology(), OutputDeviceId, InputDeviceId);

            Path path = selectPaths(paths, InputDevicePort);
            Path reversePath = selectPaths(reversePaths, OutputDevicePort);
            if(path != null && reversePath != null){
                log.info("FOUND PATHS FOR HOSTS: "+srcIp.toString()+" - "+dstIp.toString());
                log.info(path.toString());
                log.info(reversePath.toString());
                //Install flowrules on each network device involved on the path. Installing for both initial and reverse paths.

                path.links().forEach(l -> {
                    installPathFlowRule(l.src(), protocol, srcIp, srcIpPort, dstIp, dstIpPort);
                });
                //Install flowrule on last device (redirect to host)
                installPathFlowRule(dstHost.location(), OutputDevicePort, dstMac, protocol, srcIp, srcIpPort, dstIp, dstIpPort);

                reversePath.links().forEach(l -> {
                    installPathFlowRule(l.src(), protocol, dstIp, dstIpPort, srcIp, srcIpPort);
                });
                //Install flowrule on last device of reverse path
                installPathFlowRule(context.inPacket().receivedFrom(), InputDevicePort, dstMac, protocol, dstIp, dstIpPort, srcIp, srcIpPort);

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


        //Install path flowrule -> return id of flowrule installed?
        private void installPathFlowRule(ConnectPoint dstConnectionPoint, byte protocol, Ip4Address srcIp, int srcIpPort,
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
            flowRuleService.applyFlowRules(flowrule.build());
            return;
        }


        //Install path flowrule for specific device output port and destination mac. -> return id of flowrule installed?
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
            //Wait for flowrule to activate
            try { Thread.sleep(100); } catch (InterruptedException ignored) { }
            context.treatmentBuilder().setOutput(PortNumber.TABLE);
            context.send();
        }
    }

}



