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

package org.ejbca.core.protocol.cmp;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Iterator;

import junit.framework.Assert;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DEROutputStream;
import org.cesecore.core.ejb.ca.store.CertificateProfileSession;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSession;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.config.GlobalConfiguration;
import org.ejbca.config.WebConfiguration;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.ejb.ca.caadmin.CaSessionRemote;
import org.ejbca.core.ejb.config.ConfigurationSessionRemote;
import org.ejbca.core.ejb.config.GlobalConfigurationSessionRemote;
import org.ejbca.core.ejb.ra.UserAdminSessionRemote;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfileExistsException;
import org.ejbca.core.model.ca.certificateprofiles.EndUserCertificateProfile;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileExistsException;
import org.ejbca.core.protocol.FailInfo;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.InterfaceCache;
import org.ejbca.util.dn.DnComponents;
import org.ejbca.util.keystore.KeyTools;

import com.novosec.pkix.asn1.cmp.PKIMessage;

/**
 * @author tomas
 * @version $Id: CrmfRARequestTest.java 14975 2012-06-16 12:28:32Z primelars $
 */
public class CrmfRARequestTest extends CmpTestCase {

    final private static Logger log = Logger.getLogger(CrmfRARequestTest.class);

    final private static String PBEPASSWORD = "password";

    final private String issuerDN;

    final private int caid;
    final private Admin admin;
    final private X509Certificate cacert;

    private CaSessionRemote caSession = InterfaceCache.getCaSession();
    private CAAdminSessionRemote caAdminSessionRemote = InterfaceCache.getCAAdminSession();
    private ConfigurationSessionRemote configurationSession = InterfaceCache.getConfigurationSession();
    private UserAdminSessionRemote userAdminSession = InterfaceCache.getUserAdminSession();
    private EndEntityProfileSession eeProfileSession = InterfaceCache.getEndEntityProfileSession();
    private CertificateProfileSession certProfileSession = InterfaceCache.getCertificateProfileSession();
    private GlobalConfigurationSessionRemote globalConfigurationSession = InterfaceCache.getGlobalConfigurationSession();

    public CrmfRARequestTest(String arg0) throws CertificateEncodingException, CertificateException {
        super(arg0);

        admin = new Admin(Admin.TYPE_BATCHCOMMANDLINE_USER);
        // Configure CMP for this test, we allow custom certificate serial numbers
    	CertificateProfile profile = new EndUserCertificateProfile();
    	try {
    		certProfileSession.addCertificateProfile(admin, "CMPTESTPROFILE", profile);
		} catch (CertificateProfileExistsException e) {
			log.error("Could not create certificate profile.", e);
		}
        int cpId = certProfileSession.getCertificateProfileId(admin, "CMPTESTPROFILE");
        EndEntityProfile eep = new EndEntityProfile(true);
        eep.setValue(EndEntityProfile.DEFAULTCERTPROFILE,0, "" + cpId);
        eep.setValue(EndEntityProfile.AVAILCERTPROFILES,0, "" + cpId);
        eep.addField(DnComponents.COMMONNAME);
        eep.addField(DnComponents.ORGANIZATION);
        eep.addField(DnComponents.COUNTRY);
        eep.addField(DnComponents.RFC822NAME);
        eep.addField(DnComponents.UPN);
        eep.setModifyable(DnComponents.RFC822NAME, 0, true);
        eep.setUse(DnComponents.RFC822NAME, 0, false);	// Don't use field from "email" data
        try {
        	eeProfileSession.addEndEntityProfile(admin, "CMPTESTPROFILE", eep);
		} catch (EndEntityProfileExistsException e) {
			log.error("Could not create end entity profile.", e);
		}
        // Configure CMP for this test
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "ra");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWRAVERIFYPOPO, "true");
        updatePropertyOnServer(CmpConfiguration.CONFIG_RESPONSEPROTECTION, "signature");
        updatePropertyOnServer(CmpConfiguration.CONFIG_RA_AUTHENTICATIONSECRET, PBEPASSWORD);
        updatePropertyOnServer(CmpConfiguration.CONFIG_RA_ENDENTITYPROFILE, "CMPTESTPROFILE");
        updatePropertyOnServer(CmpConfiguration.CONFIG_RA_CERTIFICATEPROFILE, "CMPTESTPROFILE");
        updatePropertyOnServer(CmpConfiguration.CONFIG_RACANAME, "AdminCA1");
        updatePropertyOnServer(CmpConfiguration.CONFIG_RA_NAMEGENERATIONSCHEME, "DN");
        updatePropertyOnServer(CmpConfiguration.CONFIG_RA_NAMEGENERATIONPARAMS, "CN");
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONMODULE, CmpConfiguration.AUTHMODULE_HMAC);
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONPARAMETERS, "-");

        CryptoProviderTools.installBCProvider();
        // Try to use AdminCA1 if it exists
        final CAInfo adminca1;

        adminca1 = caAdminSessionRemote.getCAInfo(admin, "AdminCA1");

        if (adminca1 == null) {
            final Collection<Integer> caids;

            caids = caSession.getAvailableCAs(admin);

            final Iterator<Integer> iter = caids.iterator();
            int tmp = 0;
            while (iter.hasNext()) {
                tmp = iter.next().intValue();
            }
            caid = tmp;
        } else {
            caid = adminca1.getCAId();
        }
        if (caid == 0) {
            assertTrue("No active CA! Must have at least one active CA to run tests!", false);
        }
        final CAInfo cainfo;

        cainfo = caAdminSessionRemote.getCAInfo(admin, caid);

        Collection<Certificate> certs = cainfo.getCertificateChain();
        if (certs.size() > 0) {
            Iterator<Certificate> certiter = certs.iterator();
            Certificate cert = certiter.next();
            String subject = CertTools.getSubjectDN(cert);
            if (StringUtils.equals(subject, cainfo.getSubjectDN())) {
                // Make sure we have a BC certificate
                try {
                    cacert = (X509Certificate) CertTools.getCertfromByteArray(cert.getEncoded());
                } catch (Exception e) {
                    throw new Error(e);
                }
            } else {
                cacert = null;
            }
        } else {
            log.error("NO CACERT for caid " + caid);
            cacert = null;
        }
        issuerDN = cacert != null ? cacert.getIssuerDN().getName() : "CN=AdminCA1,O=EJBCA Sample,C=SE";
    }

    /**
     * @param userDN
     *            for new certificate.
     * @param keys
     *            key of the new certificate.
     * @param sFailMessage
     *            if !=null then EJBCA is expected to fail. The failure response
     *            message string is checked against this parameter.
     * @throws Exception
     */
    private void crmfHttpUserTest(String userDN, KeyPair keys, String sFailMessage, BigInteger customCertSerno) throws Exception {

        // Create a new good user

        final byte[] nonce = CmpMessageHelper.createSenderNonce();
        final byte[] transid = CmpMessageHelper.createSenderNonce();
        final int reqId;
        {
            final PKIMessage one = genCertReq(issuerDN, userDN, keys, cacert, nonce, transid, true, null, null, null, customCertSerno);
            final PKIMessage req = protectPKIMessage(one, false, PBEPASSWORD, 567);

            reqId = req.getBody().getIr().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();
            assertNotNull(req);
            final ByteArrayOutputStream bao = new ByteArrayOutputStream();
            final DEROutputStream out = new DEROutputStream(bao);
            out.writeObject(req);
            final byte[] ba = bao.toByteArray();
            // Send request and receive response
            final byte[] resp = sendCmpHttp(ba, 200);
            // do not check signing if we expect a failure (sFailMessage==null)
            checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, sFailMessage == null, null);
            if (sFailMessage == null) {
                X509Certificate cert = checkCmpCertRepMessage(userDN, cacert, resp, reqId);
                // verify if custom cert serial number was used
                if (customCertSerno != null) {
                	assertTrue(cert.getSerialNumber().toString(16)+" is not same as expected "+customCertSerno.toString(16), cert.getSerialNumber().equals(customCertSerno));
                }
            } else {
                checkCmpFailMessage(resp, sFailMessage, CmpPKIBodyConstants.ERRORMESSAGE, reqId, FailInfo.BAD_REQUEST.hashCode());
            }
        }
        {
            // Send a confirm message to the CA
            final String hash = "foo123";
            final PKIMessage con = genCertConfirm(userDN, cacert, nonce, transid, hash, reqId);
            assertNotNull(con);
            PKIMessage confirm = protectPKIMessage(con, false, PBEPASSWORD, 567);
            final ByteArrayOutputStream bao = new ByteArrayOutputStream();
            final DEROutputStream out = new DEROutputStream(bao);
            out.writeObject(confirm);
            final byte[] ba = bao.toByteArray();
            // Send request and receive response
            final byte[] resp = sendCmpHttp(ba, 200);
            checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
            checkCmpPKIConfirmMessage(userDN, cacert, resp);
        }
    }

    public void test01CrmfHttpOkUser() throws Exception {
        final CAInfo caInfo = caAdminSessionRemote.getCAInfo(admin, "AdminCA1");
        // make sure same keys for different users is prevented
        caInfo.setDoEnforceUniquePublicKeys(true);
        // make sure same DN for different users is prevented
        caInfo.setDoEnforceUniqueDistinguishedName(true);
        caAdminSessionRemote.editCA(admin, caInfo);

        final KeyPair key1 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        final KeyPair key2 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        final KeyPair key3 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        final KeyPair key4 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        final String userName1 = "cmptest1";
        final String userName2 = "cmptest2";
        final String userDN1 = "C=SE,O=PrimeKey,CN=" + userName1;
        final String userDN2 = "C=SE,O=PrimeKey,CN=" + userName2;
        String hostname=null;
        try {
        	// check that several certificates could be created for one user and one key.
        	crmfHttpUserTest(userDN1, key1, null, null);
        	crmfHttpUserTest(userDN2, key2, null, null);
        	// check that the request fails when asking for certificate for another
        	// user with same key.
        	crmfHttpUserTest(userDN2, key1, InternalResources.getInstance().getLocalizedMessage("signsession.key_exists_for_another_user", "'" + userName2 + "'",
        			"'" + userName1 + "'"), null);
        	crmfHttpUserTest(userDN1, key2, InternalResources.getInstance().getLocalizedMessage("signsession.key_exists_for_another_user", "'" + userName1 + "'",
        			"'" + userName2 + "'"), null);
        	// check that you can not issue a certificate with same DN as another
        	// user.
        	crmfHttpUserTest("CN=AdminCA1,O=EJBCA Sample,C=SE", key3, InternalResources.getInstance().getLocalizedMessage(
        			"signsession.subjectdn_exists_for_another_user", "'AdminCA1'", "'SYSTEMCA'"), null);

        	hostname = configurationSession.getProperty(WebConfiguration.CONFIG_HTTPSSERVERHOSTNAME, "localhost");

        	crmfHttpUserTest("CN=" + hostname + ",O=EJBCA Sample,C=SE", key4, InternalResources.getInstance().getLocalizedMessage(
        			"signsession.subjectdn_exists_for_another_user", "'" + hostname + "'", "'tomcat'"), null);

        } finally {
        	try {
        		userAdminSession.deleteUser(admin, userName1);
        	} catch (NotFoundException e) {}
        	try {
        		userAdminSession.deleteUser(admin, userName2);        	
        	} catch (NotFoundException e) {}
        	try {
        		userAdminSession.deleteUser(admin, "AdminCA1");
        	} catch (NotFoundException e) {}
        	try {
        		userAdminSession.deleteUser(admin, hostname);
        	} catch (NotFoundException e) {}
        }
    }

    public void test02NullKeyID() throws Exception {

        // Create a new good user

        String userDN = "CN=keyIDTestUser,C=SE";
        final KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        final byte[] nonce = CmpMessageHelper.createSenderNonce();
        final byte[] transid = CmpMessageHelper.createSenderNonce();
        final int reqId;
        
        final PKIMessage one = genCertReq(issuerDN, userDN, keys, cacert, nonce, transid, true, null, null, null, null);
        final PKIMessage req = protectPKIMessage(one, false, PBEPASSWORD, null, 567);
        Assert.assertNotNull(req);
        reqId = req.getBody().getIr().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();
        
        final ByteArrayOutputStream bao = new ByteArrayOutputStream();
        final DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        final byte[] ba = bao.toByteArray();
        // Send request and receive response
        final byte[] resp = sendCmpHttp(ba, 200);
        // do not check signing if we expect a failure (sFailMessage==null)
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, true, null);
        X509Certificate cert = checkCmpCertRepMessage(userDN, cacert, resp, reqId);
        BigInteger serialnumber = cert.getSerialNumber();
        
        // Revoke the created certificate
        //final String hash = "foo123";
        final PKIMessage con = genRevReq(issuerDN, userDN, serialnumber, cacert, nonce, transid, false);
        Assert.assertNotNull(con);
        PKIMessage revmsg = protectPKIMessage(con, false, PBEPASSWORD, null, 567);
        final ByteArrayOutputStream baorev = new ByteArrayOutputStream();
        final DEROutputStream outrev = new DEROutputStream(baorev);
        outrev.writeObject(revmsg);
        final byte[] barev = baorev.toByteArray();
        // Send request and receive response
        final byte[] resprev = sendCmpHttp(barev, 200);
        checkCmpResponseGeneral(resprev, issuerDN, userDN, cacert, nonce, transid, false, null);
        int revstatus = checkRevokeStatus(issuerDN, serialnumber);
        Assert.assertEquals("Certificate revocation failed.", RevokedCertInfo.REVOCATION_REASON_KEYCOMPROMISE, revstatus);
        
    }

    public void test03UseKeyID() throws Exception {
    	
        GlobalConfiguration gc = globalConfigurationSession.getCachedGlobalConfiguration(admin);
        boolean gcEELimitations = gc.getEnableEndEntityProfileLimitations();
        gc.setEnableEndEntityProfileLimitations(true);
        globalConfigurationSession.saveGlobalConfigurationRemote(admin, gc);

    	updatePropertyOnServer(CmpConfiguration.CONFIG_RA_ENDENTITYPROFILE, "KeyId");
    	updatePropertyOnServer(CmpConfiguration.CONFIG_RA_CERTIFICATEPROFILE, "KeyId");
    		
    	try {
    		certProfileSession.removeCertificateProfile(admin, "CMPKEYIDTESTPROFILE");
    		eeProfileSession.removeEndEntityProfile(admin, "CMPKEYIDTESTPROFILE");
    	} catch(Exception e) {}

    	// Configure CMP for this test, we allow custom certificate serial numbers
    	CertificateProfile profile = new CertificateProfile();
    	try {
    		certProfileSession.addCertificateProfile(admin, "CMPKEYIDTESTPROFILE", profile);
    	} catch (CertificateProfileExistsException e) {
    		log.error("Could not create certificate profile.", e);
    	}
    	int cpId = certProfileSession.getCertificateProfileId(admin, "CMPKEYIDTESTPROFILE");
    	
    	EndEntityProfile eep = new EndEntityProfile();
    	eep.setValue(EndEntityProfile.DEFAULTCERTPROFILE, 0, "" + cpId);
    	eep.setValue(EndEntityProfile.AVAILCERTPROFILES, 0, "" + cpId);
    	eep.setValue(EndEntityProfile.DEFAULTCA, 0, "" + caid); //CertificateProfile.ANYCA
    	eep.setValue(EndEntityProfile.AVAILCAS, 0, "" + caid);
    	eep.addField(DnComponents.ORGANIZATION);
    	eep.setRequired(DnComponents.ORGANIZATION, 0, true);
    	eep.addField(DnComponents.RFC822NAME);
    	eep.addField(DnComponents.UPN);
    	eep.setModifyable(DnComponents.RFC822NAME, 0, true);
    	eep.setUse(DnComponents.RFC822NAME, 0, false); // Don't use field from "email" data
    	
    	try {
    		eeProfileSession.addEndEntityProfile(admin, "CMPKEYIDTESTPROFILE", eep);
    	} catch (EndEntityProfileExistsException e) {
    		log.error("Could not create end entity profile.", e);
    	}
    	
    	// Create a new user that does not fulfill the end entity profile
    		
    	String userDN = "CN=keyIDTestUser,C=SE";
    	final KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
    	final byte[] nonce = CmpMessageHelper.createSenderNonce();
    	final byte[] transid = CmpMessageHelper.createSenderNonce();
    	final int reqId;
    	
    	if(userAdminSession.existsUser(admin, "keyIDTestUser")) {
    		userAdminSession.deleteUser(admin, "keyIDTestUser");
    	}
    	final PKIMessage one = genCertReq(issuerDN, userDN, keys, cacert, nonce, transid, true, null, null, null, null);
    	final PKIMessage req = protectPKIMessage(one, false, PBEPASSWORD, "CMPKEYIDTESTPROFILE", 567);
    		
    	reqId = req.getBody().getIr().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();
    	Assert.assertNotNull(req);
    	final ByteArrayOutputStream bao = new ByteArrayOutputStream();
    	final DEROutputStream out = new DEROutputStream(bao);
    	out.writeObject(req);
    	final byte[] ba = bao.toByteArray();
    	// Send request and receive response
    	final byte[] resp = sendCmpHttp(ba, 200);
    	// do not check signing if we expect a failure (sFailMessage==null)
    	checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
    	checkCmpFailMessage(resp, "Subject DN field 'ORGANIZATION' must exist.", CmpPKIBodyConstants.INITIALIZATIONRESPONSE, reqId, FailInfo.BAD_REQUEST.hashCode());
    	
    	
    	// Create a new user that fulfills the end entity profile
    		
    	userDN = "CN=keyidtest2,O=org";
    	final KeyPair keys2 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
    	final byte[] nonce2 = CmpMessageHelper.createSenderNonce();
    	final byte[] transid2 = CmpMessageHelper.createSenderNonce();
    	final int reqId2;
    	        
    	final PKIMessage one2 = genCertReq(issuerDN, userDN, keys2, cacert, nonce2, transid2, true, null, null, null, null);
    	final PKIMessage req2 = protectPKIMessage(one2, false, PBEPASSWORD, "CMPKEYIDTESTPROFILE", 567);
    	
    	reqId2 = req2.getBody().getIr().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();
    	Assert.assertNotNull(req2);
    	final ByteArrayOutputStream bao2 = new ByteArrayOutputStream();
    	final DEROutputStream out2 = new DEROutputStream(bao2);
    	out2.writeObject(req2);
    	final byte[] ba2 = bao2.toByteArray();
    	// Send request and receive response
    	final byte[] resp2 = sendCmpHttp(ba2, 200);
    	// do not check signing if we expect a failure (sFailMessage==null)
    	checkCmpResponseGeneral(resp2, issuerDN, userDN, cacert, nonce2, transid2, true, null);
    	X509Certificate cert = checkCmpCertRepMessage(userDN, cacert, resp2, reqId2);
    	BigInteger serialnumber = cert.getSerialNumber();
    	        
    	        
    	UserDataVO user = userAdminSession.findUser(admin, "keyidtest2");
    	Assert.assertEquals("Wrong certificate profile", cpId, user.getCertificateProfileId());
    	
    	// Revoke the created certificate and use keyid
    	//final String hash = "foo123";
    	final PKIMessage con = genRevReq(issuerDN, userDN, serialnumber, cacert, nonce2, transid2, false);
    	Assert.assertNotNull(con);
    	PKIMessage revmsg = protectPKIMessage(con, false, PBEPASSWORD, "CMPKEYIDTESTPROFILE", 567);
    	final ByteArrayOutputStream baorev = new ByteArrayOutputStream();
    	final DEROutputStream outrev = new DEROutputStream(baorev);
    	outrev.writeObject(revmsg);
    	final byte[] barev = baorev.toByteArray();
    	// Send request and receive response
    	final byte[] resprev = sendCmpHttp(barev, 200);
    	checkCmpResponseGeneral(resprev, issuerDN, userDN, cacert, nonce2, transid2, true, null);
    	int revstatus = checkRevokeStatus(issuerDN, serialnumber);
    	Assert.assertEquals("Certificate revocation failed.", RevokedCertInfo.REVOCATION_REASON_KEYCOMPROMISE, revstatus);
    	        
        gc.setEnableEndEntityProfileLimitations(gcEELimitations);
        globalConfigurationSession.saveGlobalConfigurationRemote(admin, gc);

    }
    
    public void testZZZCleanUp() throws Exception {
    	log.trace(">testZZZCleanUp");
        assertTrue("Unable to restore server configuration.", configurationSession.restoreConfiguration());
        // Remove test profiles
        certProfileSession.removeCertificateProfile(admin, "CMPTESTPROFILE");
        certProfileSession.removeCertificateProfile(admin, "CMPKEYIDTESTPROFILE");
        eeProfileSession.removeEndEntityProfile(admin, "CMPTESTPROFILE");
        eeProfileSession.removeEndEntityProfile(admin, "CMPKEYIDTESTPROFILE");
        try {
        	userAdminSession.deleteUser(admin, "keyidtest2");
        } catch( Throwable t) {}
        try {
        	userAdminSession.deleteUser(admin, "keyIDTestUser");
        } catch( Throwable t) {}
        log.trace("<testZZZCleanUp");
    }
}
