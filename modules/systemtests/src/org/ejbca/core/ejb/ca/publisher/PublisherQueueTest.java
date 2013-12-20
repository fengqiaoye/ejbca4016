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

package org.ejbca.core.ejb.ca.publisher;

import static org.junit.Assert.assertTrue;

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.ejbca.config.DatabaseConfiguration;
import org.ejbca.config.InternalConfiguration;
import org.ejbca.core.ejb.config.ConfigurationSessionRemote;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.ca.publisher.CustomPublisherContainer;
import org.ejbca.core.model.ca.publisher.PublisherConst;
import org.ejbca.core.model.ca.publisher.PublisherExistsException;
import org.ejbca.core.model.ca.publisher.PublisherQueueData;
import org.ejbca.core.model.ca.publisher.PublisherQueueVolatileData;
import org.ejbca.core.model.ca.publisher.ValidationAuthorityPublisher;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.ExtendedInformation;
import org.ejbca.util.Base64;
import org.ejbca.util.CertTools;
import org.ejbca.util.InterfaceCache;

/**
 * Tests Publisher Queue Data.
 *
 * @version $Id: PublisherQueueTest.java 16302 2013-02-13 09:48:51Z anatom $
 */
public class PublisherQueueTest extends TestCase {

    private static byte[] testcert = Base64.decode(("MIICWzCCAcSgAwIBAgIIJND6Haa3NoAwDQYJKoZIhvcNAQEFBQAwLzEPMA0GA1UE"
            + "AxMGVGVzdENBMQ8wDQYDVQQKEwZBbmFUb20xCzAJBgNVBAYTAlNFMB4XDTAyMDEw"
            + "ODA5MTE1MloXDTA0MDEwODA5MjE1MlowLzEPMA0GA1UEAxMGMjUxMzQ3MQ8wDQYD"
            + "VQQKEwZBbmFUb20xCzAJBgNVBAYTAlNFMIGdMA0GCSqGSIb3DQEBAQUAA4GLADCB"
            + "hwKBgQCQ3UA+nIHECJ79S5VwI8WFLJbAByAnn1k/JEX2/a0nsc2/K3GYzHFItPjy"
            + "Bv5zUccPLbRmkdMlCD1rOcgcR9mmmjMQrbWbWp+iRg0WyCktWb/wUS8uNNuGQYQe"
            + "ACl11SAHFX+u9JUUfSppg7SpqFhSgMlvyU/FiGLVEHDchJEdGQIBEaOBgTB/MA8G"
            + "A1UdEwEB/wQFMAMBAQAwDwYDVR0PAQH/BAUDAwegADAdBgNVHQ4EFgQUyxKILxFM"
            + "MNujjNnbeFpnPgB76UYwHwYDVR0jBBgwFoAUy5k/bKQ6TtpTWhsPWFzafOFgLmsw"
            + "GwYDVR0RBBQwEoEQMjUxMzQ3QGFuYXRvbS5zZTANBgkqhkiG9w0BAQUFAAOBgQAS"
            + "5wSOJhoVJSaEGHMPw6t3e+CbnEL9Yh5GlgxVAJCmIqhoScTMiov3QpDRHOZlZ15c"
            + "UlqugRBtORuA9xnLkrdxYNCHmX6aJTfjdIW61+o/ovP0yz6ulBkqcKzopAZLirX+"
            + "XSWf2uI9miNtxYMVnbQ1KPdEAt7Za3OQR6zcS0lGKg==").getBytes());

    private static final Logger log = Logger.getLogger(PublisherQueueTest.class);
    private static final Admin admin = new Admin(Admin.TYPE_CACOMMANDLINE_USER);

    private ConfigurationSessionRemote configurationSession = InterfaceCache.getConfigurationSession();
    private PublisherSessionRemote publisherSession = InterfaceCache.getPublisherSession();
    private PublisherQueueSessionRemote publisherQueueSession = InterfaceCache.getPublisherQueueSession();
    
    public PublisherQueueTest(String name) {
        super(name);
    }

    public void setUp() throws Exception {
    }

    public void tearDown() throws Exception {
    }

    public void test01QueueData() throws Exception {
    	publisherQueueSession.addQueueData(123456, PublisherConst.PUBLISH_TYPE_CERT, "XX", null, PublisherConst.STATUS_PENDING);
    	Collection<PublisherQueueData> c = publisherQueueSession.getPendingEntriesForPublisher(12345);
    	assertEquals(0, c.size());
    	c = publisherQueueSession.getPendingEntriesForPublisher(123456);
    	assertEquals(1, c.size());
    	Iterator<PublisherQueueData> i = c.iterator();
    	PublisherQueueData d = i.next();
    	assertEquals("XX", d.getFingerprint());
    	Date lastUpdate1 = d.getLastUpdate();
    	assertNotNull(lastUpdate1);
    	assertNotNull(d.getTimeCreated());
    	assertEquals(PublisherConst.STATUS_PENDING, d.getPublishStatus());
    	assertEquals(0,d.getTryCounter());
    	assertNull(d.getVolatileData());
    	
    	String xxpk = d.getPk(); // Keep for later so we can set to success
    	
    	PublisherQueueVolatileData vd = new PublisherQueueVolatileData();
    	vd.setUsername("foo");
    	vd.setPassword("bar");
    	ExtendedInformation ei = new ExtendedInformation();
    	ei.setSubjectDirectoryAttributes("directoryAttr");
    	vd.setExtendedInformation(ei);
    	publisherQueueSession.addQueueData(123456, PublisherConst.PUBLISH_TYPE_CRL, "YY", vd, PublisherConst.STATUS_PENDING);
    	
    	c = publisherQueueSession.getPendingEntriesForPublisher(123456);
    	assertEquals(2, c.size());
    	boolean testedXX = false;
    	boolean testedYY = false;
    	i = c.iterator();
    	while (i.hasNext()) {
        	d = i.next();
        	if (d.getFingerprint().equals("XX")) {
        		assertEquals(PublisherConst.PUBLISH_TYPE_CERT, d.getPublishType());
            	assertNotNull(d.getLastUpdate());
            	assertNotNull(d.getTimeCreated());
            	assertEquals(PublisherConst.STATUS_PENDING, d.getPublishStatus());
            	assertEquals(0,d.getTryCounter());
            	testedXX = true;
        	}
        	if (d.getFingerprint().equals("YY")) {
        		assertEquals(PublisherConst.PUBLISH_TYPE_CRL, d.getPublishType());
            	assertEquals(PublisherConst.STATUS_PENDING, d.getPublishStatus());
            	assertEquals(0,d.getTryCounter());
            	PublisherQueueVolatileData v = d.getVolatileData();
            	assertEquals("bar", v.getPassword());
            	assertEquals("foo", v.getUsername());
            	ExtendedInformation e = v.getExtendedInformation();
            	assertNotNull(e);
            	assertEquals("directoryAttr", e.getSubjectDirectoryAttributes());
            	testedYY = true;
        	}
    	}
    	assertTrue(testedXX);
    	assertTrue(testedYY);
    	
    	publisherQueueSession.updateData(xxpk, PublisherConst.STATUS_SUCCESS, 4);
    	c = publisherQueueSession.getEntriesByFingerprint("XX");
    	assertEquals(1, c.size());
    	i = c.iterator();
    	d = i.next();
    	assertEquals("XX", d.getFingerprint());
    	Date lastUpdate2 = d.getLastUpdate();
    	assertTrue(lastUpdate2.after(lastUpdate1));
    	assertNotNull(d.getTimeCreated());
    	assertEquals(PublisherConst.STATUS_SUCCESS, d.getPublishStatus());
    	assertEquals(4,d.getTryCounter());    	
    }

    public void test02ExternalOCSPPublisherFail() throws Exception {
    	log.trace(">test02ExternalOCSPPublisherFail");
    	boolean ret = false;
    	try {
    		ret = false;
    		try {
    			CustomPublisherContainer publisher = new CustomPublisherContainer();
    			publisher.setClassPath(ValidationAuthorityPublisher.class.getName());
    			// We use a datasource that we know don't exist, so we know publishing will fail
    			String jndiNamePrefix = configurationSession.getProperty(InternalConfiguration.CONFIG_DATASOURCENAMEPREFIX, "");
    			log.debug("jndiNamePrefix=" + jndiNamePrefix);
    			publisher.setPropertyData("dataSource " + jndiNamePrefix + "NoExist234DS");
    			publisher.setDescription("Used in Junit Test, Remove this one");
    			publisherSession.addPublisher(admin, "TESTEXTOCSPQUEUE", publisher);
    			ret = true;
    		} catch (PublisherExistsException pee) {
    			// Do nothing
    		}        
    		assertTrue("Creating External OCSP Publisher failed", ret);
    		int id = publisherSession.getPublisherId(admin, "TESTEXTOCSPQUEUE");

    		Certificate cert = CertTools.getCertfromByteArray(testcert);
    		ArrayList<Integer> publishers = new ArrayList<Integer>();
    		publishers.add(Integer.valueOf(publisherSession.getPublisherId(admin, "TESTEXTOCSPQUEUE")));

    		ret = publisherSession.storeCertificate(admin, publishers, cert, "test05", "foo123", null, null, SecConst.CERT_ACTIVE, SecConst.CERTTYPE_ENDENTITY, -1, RevokedCertInfo.NOT_REVOKED, "foo", SecConst.CERTPROFILE_FIXED_ENDUSER, new Date().getTime(), null);
    		assertFalse("Storing certificate to external ocsp publisher should fail.", ret);

    		// Now this certificate fingerprint should be in the queue
    		Collection<PublisherQueueData> c = publisherQueueSession.getPendingEntriesForPublisher(id);
    		assertEquals(1, c.size());
    		Iterator<PublisherQueueData> i = c.iterator();
    		PublisherQueueData d = i.next();
    		assertEquals(CertTools.getFingerprintAsString(cert), d.getFingerprint());
    	} finally { 
    		Collection<PublisherQueueData> c = publisherQueueSession.getEntriesByFingerprint(CertTools.getFingerprintAsString(testcert));
    		Iterator<PublisherQueueData> i = c.iterator();
        	while (i.hasNext()) {
        		PublisherQueueData d = i.next();
        		publisherQueueSession.removeQueueData(d.getPk());
        	}
    		publisherSession.removePublisher(admin, "TESTEXTOCSPQUEUE");            
    	}
    	log.trace("<test02ExternalOCSPPublisherFail");
    }

    public void test03ExternalOCSPPublisherOk() throws Exception {
    	log.trace(">test03ExternalOCSPPublisherOk");
    	boolean ret = false;
    	try{
    		ret = false;
    		try {
    			CustomPublisherContainer publisher = new CustomPublisherContainer();
    			publisher.setClassPath(ValidationAuthorityPublisher.class.getName());
    			// We use a datasource that we know don't exist, so we know publishing will be successful
    			String jndiName = configurationSession.getProperty(InternalConfiguration.CONFIG_DATASOURCENAMEPREFIX, "")
    					+ configurationSession.getProperty(DatabaseConfiguration.CONFIG_DATASOURCENAME, "EjbcaDS");
    			log.debug("jndiName=" + jndiName);
    			publisher.setPropertyData("dataSource " + jndiName);
    			publisher.setDescription("Used in Junit Test, Remove this one");
    			publisherSession.addPublisher(admin, "TESTEXTOCSPQUEUE", publisher);
    			ret = true;
    		} catch (PublisherExistsException pee) {
    			// Do nothing
    		}        
    		assertTrue("Creating External OCSP Publisher failed", ret);
    		int id = publisherSession.getPublisherId(admin, "TESTEXTOCSPQUEUE");

    		Certificate cert = CertTools.getCertfromByteArray(testcert);
    		ArrayList<Integer> publishers = new ArrayList<Integer>();
    		publishers.add(Integer.valueOf(publisherSession.getPublisherId(admin, "TESTEXTOCSPQUEUE")));

    		ret = publisherSession.storeCertificate(admin, publishers, cert, "test05", "foo123", null, null, SecConst.CERT_ACTIVE, SecConst.CERTTYPE_ENDENTITY, -1, RevokedCertInfo.NOT_REVOKED, "foo", SecConst.CERTPROFILE_FIXED_ENDUSER, new Date().getTime(), null);
    		assertTrue("Storing certificate to external ocsp publisher should succeed.", ret);

    		// Now this certificate fingerprint should NOT be in the queue
    		Collection<PublisherQueueData> c = publisherQueueSession.getPendingEntriesForPublisher(id);
    		assertEquals(0, c.size());
    	} finally { 
    		Collection<PublisherQueueData> c = publisherQueueSession.getEntriesByFingerprint(CertTools.getFingerprintAsString(testcert));
    		Iterator<PublisherQueueData> i = c.iterator();
        	while (i.hasNext()) {
        		PublisherQueueData d = i.next();
        		publisherQueueSession.removeQueueData(d.getPk());
        	}
    		publisherSession.removePublisher(admin, "TESTEXTOCSPQUEUE");            
    	}    	
    	log.trace("<test03ExternalOCSPPublisherOk");
    }

    public void test04ExternalOCSPPublisherOnlyUseQueue() throws Exception {
    	log.trace(">test04ExternalOCSPPublisherOnlyUseQueue");
    	boolean ret = false;
    	try {
    		ret = false;
    		try {
    			ValidationAuthorityPublisher vaPublisher = new ValidationAuthorityPublisher();
    			// We use the default EjbcaDS datasource here, because it probably exists during our junit test run
    			String jndiName = configurationSession.getProperty(InternalConfiguration.CONFIG_DATASOURCENAMEPREFIX, "")
    					+ configurationSession.getProperty(DatabaseConfiguration.CONFIG_DATASOURCENAME, "EjbcaDS");
    			log.debug("jndiName=" + jndiName);
    			vaPublisher.setDataSource(jndiName);
    			vaPublisher.setDescription("Used in Junit Test, Remove this one");
    			vaPublisher.setOnlyUseQueue(true);
    			assertFalse(vaPublisher.getOnlyPublishRevoked());
    			publisherSession.addPublisher(admin, "TESTEXTOCSPQUEUE", vaPublisher);
    			ret = true;
    		} catch (PublisherExistsException pee) {
    			// Do nothing
    		}        
    		assertTrue("Creating External OCSP Publisher failed", ret);
    		int id = publisherSession.getPublisherId(admin, "TESTEXTOCSPQUEUE");

    		Certificate cert = CertTools.getCertfromByteArray(testcert);
    		ArrayList<Integer> publishers = new ArrayList<Integer>();
    		publishers.add(Integer.valueOf(publisherSession.getPublisherId(admin, "TESTEXTOCSPQUEUE")));

    		// storeCertificate should return false as we have not published to all publishers but instead only pushed to the queue
    		ret = publisherSession.storeCertificate(admin, publishers, cert, "test05", "foo123", null, null, SecConst.CERT_ACTIVE, SecConst.CERTTYPE_ENDENTITY, -1, RevokedCertInfo.NOT_REVOKED, "foo", SecConst.CERTPROFILE_FIXED_ENDUSER, new Date().getTime(), null);
    		assertFalse("Storing certificate to all external ocsp publisher should return false.", ret);

    		// Now this certificate fingerprint should be in the queue
    		Collection<PublisherQueueData> c = publisherQueueSession.getPendingEntriesForPublisher(id);
    		assertEquals(1, c.size());
    		Iterator<PublisherQueueData> i = c.iterator();
    		PublisherQueueData d = i.next();
    		assertEquals(CertTools.getFingerprintAsString(cert), d.getFingerprint());
    	} finally { 
    		Collection<PublisherQueueData> c = publisherQueueSession.getEntriesByFingerprint(CertTools.getFingerprintAsString(testcert));
    		Iterator<PublisherQueueData> i = c.iterator();
        	while (i.hasNext()) {
        		PublisherQueueData d = i.next();
        		publisherQueueSession.removeQueueData(d.getPk());
        	}
    		publisherSession.removePublisher(admin, "TESTEXTOCSPQUEUE");            
    	}
    	log.trace("<test04ExternalOCSPPublisherOnlyUseQueue");
    }

    public void test05ExternalOCSPPublisherOnlyUseQueueOnlyPublishRevoked() throws Exception {
    	log.trace(">test05ExternalOCSPPublisherOnlyUseQueueOnlyPublishRevoked");
    	boolean ret = false;
    	try {
    		ret = false;
    		try {
    			ValidationAuthorityPublisher vaPublisher = new ValidationAuthorityPublisher();
    			// We use the default EjbcaDS datasource here, because it probably exists during our junit test run
    			String jndiName = configurationSession.getProperty(InternalConfiguration.CONFIG_DATASOURCENAMEPREFIX, "")
    					+ configurationSession.getProperty(DatabaseConfiguration.CONFIG_DATASOURCENAME, "EjbcaDS");
    			log.debug("jndiName=" + jndiName);
    			vaPublisher.setDataSource(jndiName);
    			vaPublisher.setDescription("Used in Junit Test, Remove this one");
    			vaPublisher.setOnlyUseQueue(true);
    			vaPublisher.setOnlyPublishRevoked(true);
    			publisherSession.addPublisher(admin, "TESTEXTOCSPQUEUE", vaPublisher);
    			ret = true;
    		} catch (PublisherExistsException pee) {
    			// Do nothing
    		}        
    		assertTrue("Creating External OCSP Publisher failed", ret);
    		int id = publisherSession.getPublisherId(admin, "TESTEXTOCSPQUEUE");

    		Certificate cert = CertTools.getCertfromByteArray(testcert);
    		ArrayList<Integer> publishers = new ArrayList<Integer>();
    		publishers.add(Integer.valueOf(publisherSession.getPublisherId(admin, "TESTEXTOCSPQUEUE")));

    		//
    		// Publish a non revoked certificate, since this publisher only stores revoked certificates it should not show up in the queue
    		// storeCertificate should return false as we have not published to all publishers but instead only pushed to the queue
    		ret = publisherSession.storeCertificate(admin, publishers, cert, "test05", "foo123", null, null, SecConst.CERT_ACTIVE, SecConst.CERTTYPE_ENDENTITY, -1, RevokedCertInfo.NOT_REVOKED, "foo", SecConst.CERTPROFILE_FIXED_ENDUSER, new Date().getTime(), null);
            assertTrue("Storing ACTIVE certificate to external ocsp publisher the only publishes REVOKED should return true.", ret);
            // Now this certificate fingerprint should not be be in the queue, since we don't publish revoked
    		Collection<PublisherQueueData> c = publisherQueueSession.getPendingEntriesForPublisher(id);
    		assertEquals("non revoked certificate should not have been stored in queue", 0, c.size());
    		
    		//
    		// Now publish a revoked certificate, it should show up in the queue
    		ret = publisherSession.storeCertificate(admin, publishers, cert, "test05", "foo123", null, null, SecConst.CERT_REVOKED, SecConst.CERTTYPE_ENDENTITY, new Date().getTime(), RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD, "foo", SecConst.CERTPROFILE_FIXED_ENDUSER, new Date().getTime(), null);
    		assertFalse("Storing certificate to all external ocsp publisher should return false.", ret);
    		// Now this certificate fingerprint should be in the queue
    		c = publisherQueueSession.getPendingEntriesForPublisher(id);
    		assertEquals("revoked certificate should have been stored in queue", 1, c.size());
    		Iterator<PublisherQueueData> i = c.iterator();
    		PublisherQueueData d = i.next();
    		assertEquals(CertTools.getFingerprintAsString(cert), d.getFingerprint());
    		// Remove it for next test
    		publisherQueueSession.removeQueueData(d.getPk());
    		//
    		// Try some variants of revocation information, it should show up in the queue, but status must be SecConst.CERT_REVOKED at least
    		ret = publisherSession.storeCertificate(admin, publishers, cert, "test05", "foo123", null, null, SecConst.CERT_REVOKED, SecConst.CERTTYPE_ENDENTITY, -1, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD, "foo", SecConst.CERTPROFILE_FIXED_ENDUSER, new Date().getTime(), null);
    		assertFalse("Storing certificate to all external ocsp publisher should return false.", ret);
    		// Now this certificate fingerprint should be in the queue
    		c = publisherQueueSession.getPendingEntriesForPublisher(id);
    		assertEquals("revoked certificate should have been stored in queue", 1, c.size());
    		i = c.iterator();
    		d = i.next();
    		assertEquals(CertTools.getFingerprintAsString(cert), d.getFingerprint());
    		// Remove it for next test
    		publisherQueueSession.removeQueueData(d.getPk());
    		// Try some variants of revocation information, it should show up in the queue
    		ret = publisherSession.storeCertificate(admin, publishers, cert, "test05", "foo123", null, null, SecConst.CERT_REVOKED, SecConst.CERTTYPE_ENDENTITY, -1, RevokedCertInfo.NOT_REVOKED, "foo", SecConst.CERTPROFILE_FIXED_ENDUSER, new Date().getTime(), null);
    		assertFalse("Storing certificate to all external ocsp publisher should return false.", ret);
    		// Now this certificate fingerprint should be in the queue
    		c = publisherQueueSession.getPendingEntriesForPublisher(id);
    		assertEquals("revoked certificate should have been stored in queue", 1, c.size());
    		i = c.iterator();
    		d = i.next();
    		assertEquals(CertTools.getFingerprintAsString(cert), d.getFingerprint());
    		// Remove it for next test
    		publisherQueueSession.removeQueueData(d.getPk());
    		
            // If we should use the queue for only revoked certificates and
            // - status is not revoked
            // - revocation reason is not REVOCATION_REASON_REMOVEFROMCRL even if status is active
    		// This one should also should show up in the queue
    		ret = publisherSession.storeCertificate(admin, publishers, cert, "test05", "foo123", null, null, SecConst.CERT_ACTIVE, SecConst.CERTTYPE_ENDENTITY, -1, RevokedCertInfo.REVOCATION_REASON_REMOVEFROMCRL, "foo", SecConst.CERTPROFILE_FIXED_ENDUSER, new Date().getTime(), null);
    		assertFalse("Storing certificate to all external ocsp publisher should return false.", ret);
    		// Now this certificate fingerprint should be in the queue
    		c = publisherQueueSession.getPendingEntriesForPublisher(id);
            assertEquals("activated certificate (previously on hold) should have been stored in queue", 1, c.size());
    		i = c.iterator();
    		d = i.next();
    		assertEquals(CertTools.getFingerprintAsString(cert), d.getFingerprint());
    		// Remove it for next test
    		publisherQueueSession.removeQueueData(d.getPk());
    	} finally { 
    		Collection<PublisherQueueData> c = publisherQueueSession.getEntriesByFingerprint(CertTools.getFingerprintAsString(testcert));
    		Iterator<PublisherQueueData> i = c.iterator();
        	while (i.hasNext()) {
        		PublisherQueueData d = i.next();
        		publisherQueueSession.removeQueueData(d.getPk());
        	}
    		publisherSession.removePublisher(admin, "TESTEXTOCSPQUEUE");            
    	}
    	log.trace("<test05ExternalOCSPPublisherOnlyUseQueueOnlyPublishRevoked");
    }

    
    public void test06PublisherQueueCountInInterval1() throws Exception {
    	// Nothing in the queue from the beginning
    	assertEquals(0, publisherQueueSession.getPendingEntriesCountForPublisher(56789));
    	assertEquals(0, publisherQueueSession.getPendingEntriesCountForPublisherInIntervals(56789, new int[]{0}, new int[]{-1})[0]);
    	
    	// Add data
    	publisherQueueSession.addQueueData(56789, PublisherConst.PUBLISH_TYPE_CERT, "XX", null, PublisherConst.STATUS_PENDING);
    	
    	// One entry in the queue
    	assertEquals(1, publisherQueueSession.getPendingEntriesCountForPublisher(56789));
    	int[] actual = publisherQueueSession.getPendingEntriesCountForPublisherInIntervals(56789, new int[]{0}, new int[]{-1});
    	assertEquals(1, actual.length);
    	assertEquals(1, actual[0]);
    	
    	// Wait a while and then add some more data
    	try {
    		Thread.sleep(2000);
    	} catch(InterruptedException ex) {
    		fail(ex.getMessage());
    	}
    	// Another entry in the queue, atleast 1s after the first one
    	publisherQueueSession.addQueueData(56789, PublisherConst.PUBLISH_TYPE_CERT, "XX", null, PublisherConst.STATUS_PENDING);
    	 
    	actual = publisherQueueSession.getPendingEntriesCountForPublisherInIntervals(56789, new int[]{0, 1, 10}, new int[]{-1, -1, -1});
    	assertEquals(3, actual.length);
    	assertEquals(2, actual[0]); // 0s old = 2
    	assertEquals(1, actual[1]); // 1s old = 1
    	assertEquals(0, actual[2]); // 10s old = 0
    }
    
    public void test07PublisherQueueCountInInterval2() throws Exception {
    	// Nothing in the queue from the beginning
    	assertEquals(0, publisherQueueSession.getPendingEntriesCountForPublisher(456789));
    	assertEquals(0, publisherQueueSession.getPendingEntriesCountForPublisherInIntervals(456789, new int[]{0}, new int[]{-1})[0]);
    	
    	// Add data
    	publisherQueueSession.addQueueData(456789, PublisherConst.PUBLISH_TYPE_CERT, "XX", null, PublisherConst.STATUS_PENDING);
    	
    	// One entry in the queue
    	assertEquals(1, publisherQueueSession.getPendingEntriesCountForPublisher(456789));
    	int[] actual = publisherQueueSession.getPendingEntriesCountForPublisherInIntervals(456789, new int[]{0}, new int[]{-1});
    	assertEquals(1, actual.length);
    	assertEquals(1, actual[0]);
    	
    	// Wait a while and then add some more data
    	try {
    		Thread.sleep(2000);
    	} catch(InterruptedException ex) {
    		fail(ex.getMessage());
    	}
    	// Another entry in the queue, at least 1s after the first one
    	publisherQueueSession.addQueueData(456789, PublisherConst.PUBLISH_TYPE_CERT, "XX", null, PublisherConst.STATUS_PENDING);
    	 
    	actual = publisherQueueSession.getPendingEntriesCountForPublisherInIntervals(456789, new int[]{0, 1, 10, 0}, new int[]{1, 10, -1, -1}); //new int[]{0, 1, 10});
    	log.debug("actual=" + Arrays.toString(actual));
    	assertEquals(4, actual.length);
    	assertEquals(1, actual[0]); // (0, 1) s  = 1
    	assertEquals(1, actual[1]); // (1, 10) s = 1
    	assertEquals(0, actual[2]); // (10, ~) s = 0
    	assertEquals(2, actual[3]); // (0, ~) s = 2
    }
    
    public void test99CleanUp() throws Exception {
    	Collection<PublisherQueueData> c = publisherQueueSession.getEntriesByFingerprint("XX");
    	Iterator<PublisherQueueData> i = c.iterator();
    	while (i.hasNext()) {
    		PublisherQueueData d = i.next();
    		publisherQueueSession.removeQueueData(d.getPk());
    	}    
    	c = publisherQueueSession.getEntriesByFingerprint("YY");
    	i = c.iterator();
    	while (i.hasNext()) {
    		PublisherQueueData d = i.next();
    		publisherQueueSession.removeQueueData(d.getPk());
    	}    
    	c = publisherQueueSession.getEntriesByFingerprint(CertTools.getFingerprintAsString(testcert));
    	i = c.iterator();
    	while (i.hasNext()) {
    		PublisherQueueData d = i.next();
    		publisherQueueSession.removeQueueData(d.getPk());
    	}
    }
}
