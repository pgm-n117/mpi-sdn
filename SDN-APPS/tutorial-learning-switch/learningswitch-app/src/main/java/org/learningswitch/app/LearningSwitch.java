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
package org.learningswitch.app;

import com.google.common.collect.Maps;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.*;
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

import java.util.*;

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
public class LearningSwitch implements SomeInterface {
    //Choose logger to print messages
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;


    //---RELEVANT AND NECESSARY SERVICES FOR LEARNINGSWITCH APP---//
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;


    //---------------------------------------------//


    /*
     * Defining macTables as a concurrent map allows multiple threads and packets to
     * use the map without an issue.
     */
    //macTables is a MAP of all network devices (Openflow Switches), containing all of their own <MAC, port> tables
    protected Map<DeviceId, Map<MacAddress, PortNumber>> macTables = Maps.newConcurrentMap();
    //Storing App ID on activate process
    private ApplicationId appId;

    //We need a Packet Processor:
    private PacketProcessor processor;



    /**
     * Create a variable of the SwitchPacketProcessor class using the PacketProcessor defined above.
     * Activates the app.
     *
     * Create code to add a processor
     */
    //+++Actions done when activating or deactivating app on the controller+++//
    @Activate
    protected void activate() {
        System.out.println("activate()");
        cfgService.registerProperties(getClass());
        log.info("Started");


        appId = coreService.getAppId((String)"org.learningswitch.app"); //pom.xml app name

        //!!!Create a processor and add it using packetService:
        processor = new SwitchPacketProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(3));
        //!!!

        /*
         * Restricts packet types to IPV4 and ARP by only requesting those types. Controller will only request this packets
         */
        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.empty());
    }


    /**
     * Deactivates the processor by removing it.
     *
     * Create code to remove the processor.
     */
    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
        //!!!Remove the processor
        packetService.removeProcessor(processor);
        //!!!
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }
    //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++//


    @Override
    public void someMethod() {
        log.info("Invoked");
    }




    /**
     * This class contains pseudo code that you must replace with your own code in actLikeSwitch.  Your job is to
     * send the packet out the port previously learned for the destination MAC.  If it does not exist,
     * flood the packet out (to all ports).
     */
    private class SwitchPacketProcessor implements PacketProcessor {
        /**
         * Learns the source port associated with the packet's DeviceId if it has not already been learned.
         * Calls actLikeSwitch to process and send the packet.
         * @param pc PacketContext object containing packet info
         */
        @Override
        public void process(PacketContext pc) {
            log.info(pc.toString());
            /*
             * Puts the packet's source's device Id into the map macTables if it has not previously been added.
             * (learns the output port)
             */
            ConnectPoint cp = pc.inPacket().receivedFrom();
            //If we receive a packet from a network device not known yet, we include the new network device onto the macTables map
            macTables.putIfAbsent(cp.deviceId(), Maps.newConcurrentMap());


            // This method simply floods all ports with the packet.
            //actLikeHub(pc);

            /*
             * This is the call to the actLikeSwitch method you will be creating. When
             * you are ready to test it, uncomment the line below, and comment out the
             * actLikeHub call above.
             *
             * NOTE: The perk of an actLikeSwitch method over actLikeHub is speed.
             * FlowRule allows much faster processing.
             */
            actLikeSwitch(pc);
        }

        /**
         * Example method. Floods packet out of all switch ports.
         *
         * @param pc the PacketContext object passed through from activate method
         */
        public void actLikeHub(PacketContext pc) {
            pc.treatmentBuilder().setOutput(PortNumber.FLOOD);
            pc.send();
        }

        /**
         * Ensures packet is of required type. Obtain the port number associated with the packet's source ID.
         * If this port has previously been learned (in the process method) build a flow using the packet's
         * out port, treatment, destination, and other properties.  Send the flow to the learned out port.
         * Otherwise, flood packet to all ports if out port has not been learned.
         *
         * @param pc the PacketContext object passed through from activate() method
         */
        public void actLikeSwitch(PacketContext pc) {

            /*
             * Ensures the type of packet being processed is only of type IPV4 or ARP (not LLDP or BDDP).
             * If it is not, return and do nothing with the packet. actLikeSwitch can only process
             * IPV4 and ARP packets.
             */
            Short type = pc.inPacket().parsed().getEtherType();
            if (type != Ethernet.TYPE_IPV4 &&  type != Ethernet.TYPE_ARP) {
                return;
            }

            /*
             * Learn the destination, source, and output port of the packet using a ConnectPoint and the
             * associated macTable.  If there is a known port associated with the packet's destination MAC Address,
             * the output port will not be null.
             */
            //find the packets connect point
            ConnectPoint cp = pc.inPacket().receivedFrom();
            //Creating new macTable entry for macTables:
            //Obtain the network device macTable and en
            Map<MacAddress, PortNumber> macTable = macTables.get(cp.deviceId()); //->get the added entry of the device we put earlier
            //Obtain source address from packet and add it to device macTable
            MacAddress srcMac = pc.inPacket().parsed().getSourceMAC();
            log.info("PUTTING NEW DEVICE ON " +cp.deviceId().toString() + "MAC TABLE: "+ srcMac.toString());
            macTable.put(srcMac, cp.port());

            //Obtain destination address from packet to know if we learned earlier
            MacAddress dstMac = pc.inPacket().parsed().getDestinationMAC();

            //save the outPort as a variable -> if the port is obtained from the macTable, it means we learned the destination address earlier
            PortNumber outPort = macTable.get(dstMac);


            /*
             * If port is known, set output port to the packet's learned output port and construct a
             * FlowRule using a source, destination, treatment and other properties. Send the FlowRule
             * to the designated output port.
             */
            //if outPort isn't null
            if(outPort != null){
                //to send the packet
                pc.treatmentBuilder().setOutput(outPort);
                //construct the new flowrule for specified device:
                /**
                 * Using default traffic selector, matching destination MAC addreses will get the treatment:
                 * set the output device port of the packet to the specified device port found on the macTable (outPort)
                 * This rule will be applied to the device: deviceId (device which received the inbound packet)
                 * Priority of the rule: REACTIVE, and a limited time of 60 seconds.
                 * We can also specify which app is applying a rule into a switch
                 */
                log.info("INSTALLING FLOWRULE ON" + cp.deviceId().toString());
                FlowRule fr = DefaultFlowRule.builder()
                        .withSelector(DefaultTrafficSelector.builder().matchEthDst(dstMac).build())
                        .withTreatment(DefaultTrafficTreatment.builder().setOutput(outPort).build())
                        .forDevice(cp.deviceId()).withPriority(PacketPriority.REACTIVE.priorityValue())
                        .makeTemporary(60).fromApp(appId).build();
                //apply flowrule built

                cd ..
                cdflowRuleService.applyFlowRules(fr);
                //send the packet
                pc.send();

            }else {
                /*
                 * else, the output port has not been learned yet.  Flood the packet to all ports using
                 * the actLikeHub method
                 */
                actLikeHub(pc);
            }
        }
    }
}
