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

import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.*;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Optional;
import java.util.Properties;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {ProactiveSwitchInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
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

    //---------------------------------------------//

    //Needed variables
    private ApplicationId appId;

    //Packet processor
    private PacketProcessor proactiveSwitchProcessor;

    @Activate
    protected void activate() {
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
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }



    @Override
    public void someMethod() {
        log.info("Invoked");
    }


    private class ProactiveSwitchProcessor implements PacketProcessor{

        @Override
        public void process(PacketContext context) {

            InboundPacket packet = context.inPacket();
            Ethernet ethPacket = packet.parsed();
            if(ethPacket == null) return;

            //entry port from source device
            ConnectPoint srcPort = packet.receivedFrom();

            switch (EthType.EtherType.lookup(ethPacket.getEtherType())){
                case LLDP:
                    return;
                case ARP:

                    break;
                case IPV4:
                    break;
            }

        }
    }

}



