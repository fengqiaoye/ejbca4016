/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.extra.db;

import java.util.ArrayList;

import javax.persistence.Persistence;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.ejbca.util.CertTools;

/**
 * Makes basic database functionality tests.
 *
 * @version $Id: MessageHomeTest.java 9435 2010-07-14 15:18:39Z mikekushner $ 
 */
public class MessageHomeTest extends TestCase {

	private static final Logger log = Logger.getLogger(MessageHomeTest.class);
	
	private static MessageHome msghome = new MessageHome(Persistence.createEntityManagerFactory("external-ra-test-notpolled"), MessageHome.MESSAGETYPE_EXTRA, true);

	public void setUp() throws Exception {
		super.setUp();
		CertTools.installBCProvider();
	}

	/**
	 * Test method for 'org.ejbca.extra.db.MessageHome.create(String, String)'
	 */
	public void test01Create() throws Exception {
		SubMessages submessages = new SubMessages(null,null,null);
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS10Request(1, "PKCS10REQ", "PKCS10"));
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS12Request(2,"PKCS12REQ",false));
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAKeyRecoveryRequest(3,"KEYRECREQ", true,null));
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS10Response());
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS12Response());
		
		msghome.create("test1", submessages);
		
		Message msg = msghome.findByMessageId("test1");
		assertTrue(msg.getMessageid().equals("test1"));
		assertTrue(msg.getType().equals(MessageHome.MESSAGETYPE_EXTRA));
		assertTrue(msg.getStatus().equals(Message.STATUS_WAITING));
		ExtRAMessagesTest.checkSubMessages(msg.getSubMessages(null,null,null).getSubMessages());
		assertTrue(msg.getCreatetime()== msg.getModifytime());
		
		//Thread.sleep(500);
		
		submessages = new SubMessages(null,null,Constants.getUserCert());
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS10Request(1, "PKCS10REQ", "PKCS10"));
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS12Request(2,"PKCS12REQ",false));
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAKeyRecoveryRequest(3,"KEYRECREQ", true,null));
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS10Response());
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS12Response());
		
		msghome.create("test2",submessages);
		
		msg = msghome.findByMessageId("test2");
		assertTrue(msg.getMessageid().equals("test2"));
		assertTrue(msg.getType().equals(MessageHome.MESSAGETYPE_EXTRA));
		assertTrue(msg.getStatus().equals(Message.STATUS_WAITING));
		ExtRAMessagesTest.checkSubMessages(msg.getSubMessages(Constants.getUserKey(),null,null).getSubMessages());
		assertTrue(msg.getCreatetime()== msg.getModifytime());   
		
		//Thread.sleep(500);       
		
		submessages = new SubMessages(Constants.getUserCert(),Constants.getUserKey(),null);
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS10Request(1, "PKCS10REQ", "PKCS10"));
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS12Request(2,"PKCS12REQ",false));
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAKeyRecoveryRequest(3,"KEYRECREQ", true,null));
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS10Response());
		submessages.addSubMessage(ExtRAMessagesTest.genExtRAPKCS12Response());
		
		ArrayList cACerts = new ArrayList();
		cACerts.add(Constants.getRootCert());
		cACerts.add(Constants.getIntermediateCert());
		
		msghome.create("test3",submessages);
		
		msg = msghome.findByMessageId("test3");
		assertTrue(msg.getMessageid().equals("test3"));
		assertTrue(msg.getType().equals(MessageHome.MESSAGETYPE_EXTRA));
		assertTrue(msg.getStatus().equals(Message.STATUS_WAITING));
		ExtRAMessagesTest.checkSubMessages(msg.getSubMessages(Constants.getUserKey(),cACerts,null).getSubMessages());
		assertTrue(msg.getCreatetime()== msg.getModifytime());   
		
		//Thread.sleep(500); 
	}

	/**
	 * Test method for 'org.ejbca.extra.db.MessageHome.update(Message)'
	 */
	public void test02Update() {
		Message msg = msghome.findByMessageId("test1");
		assertNotNull(msg);
		
		SubMessages submgs = msg.getSubMessages(null,null,null);
		assertTrue(submgs.getSubMessages().size() == 5);
		submgs.addSubMessage(ExtRAMessagesTest.genExtRAPKCS10Request(1, "PKCS10REQ", "PKCS10"));
		msg.setSubMessages(submgs);
		
		msghome.update(msg);
		
		msg = msghome.findByMessageId("test1");
		assertNotNull(msg);
		assertTrue(msg.getSubMessages(null,null,null).getSubMessages().size() == 6);		
		assertTrue(msg.getCreatetime() != msg.getModifytime());
	}

	/**
	 * Test method for 'org.ejbca.extra.db.MessageHome.findByUser(String)'
	 */
	public void test03FindByUser() {
	  Message msg = msghome.findByMessageId("test1");
	  assertNotNull(msg);
	  msg = msghome.findByMessageId("test2");
	  assertNotNull(msg);
	  msg = msghome.findByMessageId("test3");
	  assertNotNull(msg); 
	}

	/**
	 * Test method for 'org.ejbca.extra.db.MessageHome.getNextWaitingUser()'
	 */
	public void test04GetNextWaitingUser() {
		log.trace(">test04GetNextWaitingUser");
		Message msg = msghome.getNextWaitingMessage();
		
		assertEquals("msghome.getNextWaitingMessage did not return user 'test1'", "test1", msg.getMessageid());
		assertEquals("User 'test1' does not have INPROCESS status", Message.STATUS_INPROCESS, msg.getStatus());
		
		msg = msghome.findByMessageId("test1");
		assertEquals("User 'test1' does not have INPROCESS status", Message.STATUS_INPROCESS, msg.getStatus());
        msg.setStatus(Message.STATUS_PROCESSED);
		
		msg = msghome.getNextWaitingMessage();
		assertEquals("msghome.getNextWaitingMessage did not return user 'test2'", "test2", msg.getMessageid());
		log.trace("<test04GetNextWaitingUser");
	}

	/**
	 * Test method for 'org.ejbca.extra.db.MessageHome.remove(String)'
	 */
	public void test05Remove() {
		assertNotNull(msghome.findByMessageId("test1"));
		msghome.remove("test1");
		assertNull(msghome.findByMessageId("test1"));

		assertNotNull(msghome.findByMessageId("test2"));
		msghome.remove("test2");
		assertNull(msghome.findByMessageId("test2"));
		
		assertNotNull(msghome.findByMessageId("test3"));
		msghome.remove("test3");
		assertNull(msghome.findByMessageId("test3"));
		
	}
}
