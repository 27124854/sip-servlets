/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package org.mobicents.servlet.sip.testsuite.b2bua;

import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;

import javax.sip.ListeningPoint;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.AllowHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.ReasonHeader;
import javax.sip.header.UserAgentHeader;

import org.apache.log4j.Logger;
import org.mobicents.servlet.sip.NetworkPortAssigner;
import org.mobicents.servlet.sip.SipServletTestCase;
import org.mobicents.servlet.sip.catalina.SipStandardManager;
import org.mobicents.servlet.sip.startup.SipContextConfig;
import org.mobicents.servlet.sip.startup.SipStandardContext;
import org.mobicents.servlet.sip.testsuite.ProtocolObjects;
import org.mobicents.servlet.sip.testsuite.TestSipListener;

/**
 * Testing B2BUA bridge between udp and tcp transports
 *
 * @author Filip Olsson
 *
 */
public class B2BUATcpUdpTest extends SipServletTestCase {

    private static transient Logger logger = Logger.getLogger(B2BUATcpUdpTest.class);

    private static final String TRANSPORT_UDP = "udp";
    private static final String TRANSPORT_TCP = "tcp";
    private static final boolean AUTODIALOG = true;
    private static final int TIMEOUT = 15000;
//	private static final int TIMEOUT = 100000000;

    TestSipListener sender;
    TestSipListener receiver;
    ProtocolObjects senderProtocolObjects;
    ProtocolObjects receiverProtocolObjects;
    SipStandardManager sipStandardManager = null;

    public B2BUATcpUdpTest(String name) {
        super(name);
        startTomcatOnStartup = false;
        autoDeployOnStartup = false;
    }

    @Override
    public void deployApplication() {
        sipStandardManager = new SipStandardManager();
        SipStandardContext context = new SipStandardContext();
        context.setDocBase(projectHome + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp");
        context.setName("sip-test-context");
        context.setPath("/sip-test");
        context.addLifecycleListener(new SipContextConfig());
        context.setManager(sipStandardManager);
        boolean success = tomcat.deployContext(
                context);
        assertTrue(success);
    }

    @Override
    protected String getDarConfigurationFile() {
        return "file:///"
                + projectHome
                + "/sip-servlets-test-suite/testsuite/src/test/resources/"
                + "org/mobicents/servlet/sip/testsuite/callcontroller/call-forwarding-b2bua-servlet-dar.properties";
    }

    @Override
    protected void setUp() throws Exception {
        containerPort = NetworkPortAssigner.retrieveNextPort();
        super.setUp();

        tomcat.addSipConnector(serverName, sipIpAddress, containerPort, ListeningPoint.TCP);
        tomcat.startTomcat();

        senderProtocolObjects = new ProtocolObjects("forward-udp-sender",
                "gov.nist", TRANSPORT_UDP, AUTODIALOG, null, null, null);
        receiverProtocolObjects = new ProtocolObjects("forward-tcp-receiver",
                "gov.nist", TRANSPORT_TCP, AUTODIALOG, null, null, null);

    }

    public void testCallForwardingCallerSendBye() throws Exception {
        int senderPort = NetworkPortAssigner.retrieveNextPort();
        sender = new TestSipListener(senderPort, containerPort, senderProtocolObjects, true);
        SipProvider senderProvider = sender.createProvider();

        int receiverPort = NetworkPortAssigner.retrieveNextPort();
        receiver = new TestSipListener(receiverPort, containerPort, receiverProtocolObjects, false);
        SipProvider receiverProvider = receiver.createProvider();

        receiverProvider.addSipListener(receiver);
        senderProvider.addSipListener(sender);

        senderProtocolObjects.start();
        receiverProtocolObjects.start();

        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));
        SipStandardContext deployApplication = deployApplication(projectHome
                + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp",
                params, null);
        sipStandardManager = (SipStandardManager) deployApplication.getSipManager();

        String fromName = "forward-tcp-sender";
        String fromSipAddress = "sip-servlets.com";
        SipURI fromAddress = senderProtocolObjects.addressFactory.createSipURI(
                fromName, fromSipAddress);

        String toSipAddress = "sip-servlets.com";
        String toUser = "forward-receiver";
        SipURI toAddress = senderProtocolObjects.addressFactory.createSipURI(
                toUser, toSipAddress);

        sender.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false, new String[]{UserAgentHeader.NAME, "extension-header", AllowHeader.NAME}, new String[]{"TestSipListener UA", "extension-sip-listener", "INVITE, CANCEL, BYE, ACK, OPTIONS"}, true);
        Thread.sleep(TIMEOUT);
        assertTrue(sender.getOkToByeReceived());
        assertTrue(receiver.getByeReceived());
        CallIdHeader receiverCallIdHeader = (CallIdHeader) receiver.getInviteRequest().getHeader(CallIdHeader.NAME);
        CallIdHeader senderCallIdHeader = (CallIdHeader) sender.getInviteRequest().getHeader(CallIdHeader.NAME);
        ListIterator<UserAgentHeader> userAgentHeaderIt = receiver.getInviteRequest().getHeaders(UserAgentHeader.NAME);
        ListIterator<AllowHeader> allowHeaderIt = receiver.getInviteRequest().getHeaders(AllowHeader.NAME);
        int i = 0;
        while (userAgentHeaderIt.hasNext()) {
            UserAgentHeader userAgentHeader = (UserAgentHeader) userAgentHeaderIt
                    .next();
            assertTrue(userAgentHeader.toString().trim().endsWith("CallForwardingB2BUASipServlet"));
            i++;
        }
        assertEquals(1, i);
        ListIterator<ContactHeader> contactHeaderIt = receiver.getInviteRequest().getHeaders(ContactHeader.NAME);
        i = 0;
        while (contactHeaderIt.hasNext()) {
            ContactHeader contactHeader = (ContactHeader) contactHeaderIt
                    .next();
            assertTrue(contactHeader.toString().trim().startsWith("Contact: \"callforwardingB2BUA\" <sip:test@" + System.getProperty("org.mobicents.testsuite.testhostaddr") + ":" + containerPort + ";q=0.1;transport=tcp;test>;test"));
            i++;
        }
        assertEquals(1, i);
        ListIterator<Header> extensionHeaderIt = receiver.getInviteRequest().getHeaders("extension-header");
        i = 0;
        while (extensionHeaderIt.hasNext()) {
            extensionHeaderIt.next();
            i++;
        }
        assertEquals(2, i);
        userAgentHeaderIt = receiver.getByeRequestReceived().getHeaders(UserAgentHeader.NAME);
        i = 0;
        while (userAgentHeaderIt.hasNext()) {
            UserAgentHeader userAgentHeader = (UserAgentHeader) userAgentHeaderIt
                    .next();
            assertTrue(userAgentHeader.toString().trim().endsWith("CallForwardingB2BUASipServlet"));
            i++;
        }
        assertEquals(1, i);
        i = 0;
        while (allowHeaderIt.hasNext()) {
            allowHeaderIt.next();
            i++;
        }
        assertEquals(5, i);
        contactHeaderIt = receiver.getByeRequestReceived().getHeaders(ContactHeader.NAME);
        i = 0;
        while (contactHeaderIt.hasNext()) {
            ContactHeader contactHeader = (ContactHeader) contactHeaderIt
                    .next();
            i++;
        }
        assertEquals(0, i);
        assertFalse(receiverCallIdHeader.getCallId().equals(senderCallIdHeader.getCallId()));
        extensionHeaderIt = receiver.getByeRequestReceived().getHeaders("extension-header");
        i = 0;
        while (extensionHeaderIt.hasNext()) {
            extensionHeaderIt.next();
            i++;
        }
        assertEquals(2, i);
    }

    public void testCallForwardingREGISTERCheckContact() throws Exception {
        int senderPort = NetworkPortAssigner.retrieveNextPort();
        sender = new TestSipListener(senderPort, containerPort, senderProtocolObjects, true);
        SipProvider senderProvider = sender.createProvider();

        int receiverPort = NetworkPortAssigner.retrieveNextPort();
        receiver = new TestSipListener(receiverPort, containerPort, receiverProtocolObjects, false);
        SipProvider receiverProvider = receiver.createProvider();

        receiverProvider.addSipListener(receiver);
        senderProvider.addSipListener(sender);

        senderProtocolObjects.start();
        receiverProtocolObjects.start();

        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));
        deployApplication(projectHome
                + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp",
                params, null);

        String fromName = "forward-tcp-sender";
        String fromSipAddress = "sip-servlets.com";
        SipURI fromAddress = senderProtocolObjects.addressFactory.createSipURI(
                fromName, fromSipAddress);

        String toSipAddress = "sip-servlets.com";
        String toUser = "forward-receiver";
        SipURI toAddress = senderProtocolObjects.addressFactory.createSipURI(
                toUser, toSipAddress);

        sender.sendSipRequest("REGISTER", fromAddress, toAddress, null, null, false, new String[]{UserAgentHeader.NAME, "extension-header"}, new String[]{"TestSipListener UA", "extension-sip-listener"}, true);
        Thread.sleep(TIMEOUT);
        ListIterator<UserAgentHeader> userAgentHeaderIt = receiver.getRegisterReceived().getHeaders(UserAgentHeader.NAME);
        int i = 0;
        while (userAgentHeaderIt.hasNext()) {
            UserAgentHeader userAgentHeader = (UserAgentHeader) userAgentHeaderIt
                    .next();
            assertTrue(userAgentHeader.toString().trim().endsWith("CallForwardingB2BUASipServlet"));
            i++;
        }
        assertEquals(1, i);
        ListIterator<ContactHeader> contactHeaderIt = receiver.getRegisterReceived().getHeaders(ContactHeader.NAME);
        assertTrue(contactHeaderIt.hasNext());
        assertTrue(contactHeaderIt.next().toString().trim().startsWith("Contact: <sip:forward-tcp-sender@" + System.getProperty("org.mobicents.testsuite.testhostaddr") + ":" + senderPort + ";transport=tcp>"));
        assertTrue(contactHeaderIt.hasNext());
        assertTrue(contactHeaderIt.next().toString().trim().startsWith("Contact: \"callforwardingB2BUA\" <sip:test@192.168.15.15:5055;q=0.1;transport=tcp;test>;test"));
        ListIterator<Header> extensionHeaderIt = receiver.getRegisterReceived().getHeaders("extension-header");
        i = 0;
        while (extensionHeaderIt.hasNext()) {
            extensionHeaderIt.next();
            i++;
        }
        assertEquals(2, i);
    }

    public void testCallForwardingCalleeSendByeTCPSender() throws Exception {
        int senderPort = NetworkPortAssigner.retrieveNextPort();
        sender = new TestSipListener(senderPort, containerPort, senderProtocolObjects, true);
        SipProvider senderProvider = sender.createProvider();

        int receiverPort = NetworkPortAssigner.retrieveNextPort();
        receiver = new TestSipListener(receiverPort, containerPort, receiverProtocolObjects, false);
        SipProvider receiverProvider = receiver.createProvider();

        receiverProvider.addSipListener(receiver);
        senderProvider.addSipListener(sender);

        senderProtocolObjects.start();
        receiverProtocolObjects.start();

        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));
        deployApplication(projectHome
                + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp",
                params, null);

        String fromName = "forward-udp-sender-tcp-sender";
        String fromSipAddress = "sip-servlets.com";
        SipURI fromAddress = senderProtocolObjects.addressFactory.createSipURI(
                fromName, fromSipAddress);

        String toSipAddress = "sip-servlets.com";
        String toUser = "receiver";
        SipURI toAddress = senderProtocolObjects.addressFactory.createSipURI(
                toUser, toSipAddress);

        receiver.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
        Thread.sleep(TIMEOUT);
        assertTrue(sender.getOkToByeReceived());
        assertTrue(receiver.getByeReceived());
        CallIdHeader receiverCallIdHeader = (CallIdHeader) sender.getInviteRequest().getHeader(CallIdHeader.NAME);
        CallIdHeader senderCallIdHeader = (CallIdHeader) receiver.getInviteRequest().getHeader(CallIdHeader.NAME);
        assertFalse(receiverCallIdHeader.getCallId().equals(senderCallIdHeader.getCallId()));
    }

    public void testCallForwardingCalleeSendBye() throws Exception {
        int senderPort = NetworkPortAssigner.retrieveNextPort();
        sender = new TestSipListener(senderPort, containerPort, senderProtocolObjects, false);
        SipProvider senderProvider = sender.createProvider();

        int receiverPort = NetworkPortAssigner.retrieveNextPort();
        receiver = new TestSipListener(receiverPort, containerPort, receiverProtocolObjects, true);
        SipProvider receiverProvider = receiver.createProvider();

        receiverProvider.addSipListener(receiver);
        senderProvider.addSipListener(sender);

        senderProtocolObjects.start();
        receiverProtocolObjects.start();

        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));
        deployApplication(projectHome
                + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp",
                params, null);

        String fromName = "forward-tcp-sender";
        String fromSipAddress = "sip-servlets.com";
        SipURI fromAddress = senderProtocolObjects.addressFactory.createSipURI(
                fromName, fromSipAddress);

        String toSipAddress = "sip-servlets.com";
        String toUser = "receiver";
        SipURI toAddress = senderProtocolObjects.addressFactory.createSipURI(
                toUser, toSipAddress);

        sender.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
        Thread.sleep(TIMEOUT);
        assertTrue(receiver.getOkToByeReceived());
        assertTrue(sender.getByeReceived());
        CallIdHeader receiverCallIdHeader = (CallIdHeader) receiver.getInviteRequest().getHeader(CallIdHeader.NAME);
        CallIdHeader senderCallIdHeader = (CallIdHeader) sender.getInviteRequest().getHeader(CallIdHeader.NAME);
        assertFalse(receiverCallIdHeader.getCallId().equals(senderCallIdHeader.getCallId()));
    }

    public void testCancelCallForwarding() throws Exception {
        int senderPort = NetworkPortAssigner.retrieveNextPort();
        sender = new TestSipListener(senderPort, containerPort, senderProtocolObjects, false);
        SipProvider senderProvider = sender.createProvider();

        int receiverPort = NetworkPortAssigner.retrieveNextPort();
        receiver = new TestSipListener(receiverPort, containerPort, receiverProtocolObjects, true);
        SipProvider receiverProvider = receiver.createProvider();

        receiverProvider.addSipListener(receiver);
        senderProvider.addSipListener(sender);

        senderProtocolObjects.start();
        receiverProtocolObjects.start();

        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));
        SipStandardContext deployApplication = deployApplication(projectHome
                + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp",
                params, null);
        sipStandardManager = (SipStandardManager) deployApplication.getSipManager();

        String fromName = "forward-tcp-sender";
        String fromSipAddress = "sip-servlets.com";
        SipURI fromAddress = senderProtocolObjects.addressFactory.createSipURI(
                fromName, fromSipAddress);

        String toSipAddress = "sip-servlets.com";
        String toUser = "receiver";
        SipURI toAddress = senderProtocolObjects.addressFactory.createSipURI(
                toUser, toSipAddress);

        receiver.setWaitForCancel(true);
        sender.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
        Thread.sleep(500);
        sender.sendCancel();
        Thread.sleep(TIMEOUT);
        assertTrue(sender.isCancelOkReceived());
        assertTrue(sender.isRequestTerminatedReceived());
        assertTrue(receiver.isCancelReceived());
        CallIdHeader receiverCallIdHeader = (CallIdHeader) receiver.getInviteRequest().getHeader(CallIdHeader.NAME);
        CallIdHeader senderCallIdHeader = (CallIdHeader) sender.getInviteRequest().getHeader(CallIdHeader.NAME);
        assertFalse(receiverCallIdHeader.getCallId().equals(senderCallIdHeader.getCallId()));
        Thread.sleep(TIMEOUT * 2);
        assertEquals(0, sipStandardManager.getActiveSipApplicationSessions());
        assertEquals(0, sipStandardManager.getActiveSipSessions());
    }

    public void testCancelCallForwarding487RequestTerminatedReasonHeader() throws Exception {
        int senderPort = NetworkPortAssigner.retrieveNextPort();
        sender = new TestSipListener(senderPort, containerPort, senderProtocolObjects, false);
        SipProvider senderProvider = sender.createProvider();

        int receiverPort = NetworkPortAssigner.retrieveNextPort();
        receiver = new TestSipListener(receiverPort, containerPort, receiverProtocolObjects, true);
        SipProvider receiverProvider = receiver.createProvider();

        receiverProvider.addSipListener(receiver);
        senderProvider.addSipListener(sender);

        senderProtocolObjects.start();
        receiverProtocolObjects.start();

        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));
        SipStandardContext deployApplication = deployApplication(projectHome
                + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp",
                params, null);
        sipStandardManager = (SipStandardManager) deployApplication.getSipManager();

        String fromName = "forward-tcp-sender";
        String fromSipAddress = "sip-servlets.com";
        SipURI fromAddress = senderProtocolObjects.addressFactory.createSipURI(
                fromName, fromSipAddress);

        String toSipAddress = "sip-servlets.com";
        String toUser = "receiver";
        SipURI toAddress = senderProtocolObjects.addressFactory.createSipURI(
                toUser, toSipAddress);

        receiver.setWaitForCancel(true);
        sender.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
        Thread.sleep(500);
        sender.sendCancel();
        Thread.sleep(TIMEOUT);
        assertTrue(sender.isCancelOkReceived());
        assertTrue(sender.isRequestTerminatedReceived());
        assertTrue(receiver.isCancelReceived());
        assertTrue(sender.getFinalResponse().getHeader(ReasonHeader.NAME).toString().contains("487 request terminated reason"));
        CallIdHeader receiverCallIdHeader = (CallIdHeader) receiver.getInviteRequest().getHeader(CallIdHeader.NAME);
        CallIdHeader senderCallIdHeader = (CallIdHeader) sender.getInviteRequest().getHeader(CallIdHeader.NAME);
        assertFalse(receiverCallIdHeader.getCallId().equals(senderCallIdHeader.getCallId()));
        Thread.sleep(TIMEOUT * 2);
        assertEquals(0, sipStandardManager.getActiveSipApplicationSessions());
        assertEquals(0, sipStandardManager.getActiveSipSessions());
    }

    @Override
    protected void tearDown() throws Exception {
        senderProtocolObjects.destroy();
        receiverProtocolObjects.destroy();
        sender = null;
        receiver = null;
        logger.info("Test completed");
        super.tearDown();
    }

}
