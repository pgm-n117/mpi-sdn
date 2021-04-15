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
package org.mpicontroller.app;

import org.onlab.packet.Ethernet;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
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
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           },
            enabled = true)
public class MpiController implements SomeInterface {

    //Choose logger to print debug messages
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
    //---------------------------------------------//

    //Needed variables
    private ApplicationId appId;

    //UDP PORT WHERE SENDING NOTIFICATION MESSAGES TO NETWORK DEVICES/SDN SWITCHES
    TpPort UdpTpPort = TpPort.tpPort(44444);

    //UDP processor
    private PacketProcessor UdpProcessor;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        log.info("Started");

        //Storing app id from pom.xml
        appId = coreService.getAppId("org.mpicontroller.app");

        //adding implemented packet processors
        UdpProcessor = new UdpPacketProcessor();


        packetService.addProcessor(UdpProcessor, PacketProcessor.director(3));

        //Requesting UDP packets sent to port 44444, as they will be notifications
        packetService.requestPackets(DefaultTrafficSelector.builder().matchUdpDst(UdpTpPort).build(),
                PacketPriority.REACTIVE, appId, Optional.empty());
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");

        //Removing added packet processors
        packetService.removeProcessor(UdpProcessor);
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

    private class UdpPacketProcessor implements PacketProcessor {


        @Override
        public void process(PacketContext context) {

            //Base UDP packet
            UDP udp = new UDP();

            //Obtain which port UPD packet has arrived:
            ConnectPoint cp = context.inPacket().receivedFrom();

            log.info("TESTING 1: "+cp.ipElementId().ipAddress().getIp4Address().toString());
            //ver si se puede sacar el puerto asignado del mensaje de payload.

            //Obtain UDP packet itself
            //udp.setParent(context.inPacket().parsed().getPayload());

            //get srcIP


        }
    }
}
