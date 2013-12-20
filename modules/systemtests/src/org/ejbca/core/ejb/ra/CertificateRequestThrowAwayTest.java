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

package org.ejbca.core.ejb.ra;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Random;

import javax.ejb.CreateException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.RemoveException;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.ca.CaTestCase;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionRemote;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.core.protocol.PKCS10RequestMessage;
import org.ejbca.core.protocol.X509ResponseMessage;
import org.ejbca.util.Base64;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.InterfaceCache;
import org.ejbca.util.keystore.KeyTools;

/**
 * Test the combined function for editing and requesting a keystore/certificate
 * in a single transaction.
 * 
 * These tests will verify that the CA settings
 * - useCertReqHistory     (Store copy of UserData at the time of certificate issuance.)
 * - useUserStorage        (Store current UserData.)
 * - useCertificateStorage (Store issued certificates and related information.)
 * works as intended.
 * 
 * Certificate issuance should work with any combination of the settings from CMP in RA mode
 * and EJBCA WS. The CMP/WS entry points in will be tested, but at the used EJB entry points:
 * - (Local)CertificateRequestSessionRemote.processCertReq(Admin, UserDataVO, String, int, String, int)
 * - (Local)CertificateRequestSessionRemote.processCertReq(Admin, UserDataVO, IRequestMessage, Class)
 *
 * Since CrmfRequestMessages are a bit more complicated to create, the much simpler PKCS10 Request
 * messages will be used. 
 *
 * Test methods are assumed to run in sequential order, to save CA generation and profile setup time.
 * 
 * @version $Id: CertificateRequestThrowAwayTest.java 11526 2011-03-16 12:03:24Z netmackan $
 */
public class CertificateRequestThrowAwayTest extends CaTestCase {

	private static final Logger LOG = Logger.getLogger(CertificateRequestThrowAwayTest.class);
	private static final Admin admin = new Admin(Admin.TYPE_CACOMMANDLINE_USER);
	private static final Random random= new SecureRandom();

	private static final String TESTCA_NAME = "ThrowAwayTestCA";
	
	private CAAdminSessionRemote caAdminSession = InterfaceCache.getCAAdminSession();
    private CertificateRequestSessionRemote certificateRequestSession = InterfaceCache.getCertficateRequestSession();
    private CertificateStoreSessionRemote certificateStoreSession = InterfaceCache.getCertificateStoreSession();
    private UserAdminSessionRemote userAdminSession = InterfaceCache.getUserAdminSession();

	public void test000Setup() {
		LOG.trace(">test000Setup");
		CryptoProviderTools.installBCProviderIfNotAvailable();
		super.createTestCA(TESTCA_NAME);	// Create test CA
		assertCAConfig(true, true, true);
		LOG.trace("<test000Setup");
	}
	
	public void testCAConfigurationsWithIRequestMessage() throws Exception {
		LOG.trace(">testCAConfigurationsWithIRequestMessage");
		// Run through all possible configurations of what to store in the database
		for (int i=0; i<=7; i++) {
			generateCertificatePkcs10((i&1)>0, (i&2)>0, (i&4)>0, false);
		}
		LOG.trace("<testCAConfigurationsWithIRequestMessage");
	}
	
	public void testCAConfigurationsWithStringRequest() throws Exception {
		LOG.trace(">testCAConfigurationsWithStringRequest");
		// Run through all possible configurations of what to store in the database
		for (int i=0; i<=7; i++) {
			generateCertificatePkcs10((i&1)>0, (i&2)>0, (i&4)>0, true);
		}
		LOG.trace("<testCAConfigurationsWithStringRequest");
	}
	
	public void testZZZTearDown() {
		LOG.trace(">testZZZTearDown");
		assertTrue("Clean up failed!", super.removeTestCA(TESTCA_NAME));
		LOG.trace("<testZZZTearDown");
	}

	/** Reconfigure CA, process a certificate request and assert that the right things were stored in the database. */
	private void generateCertificatePkcs10(boolean useCertReqHistory, boolean useUserStorage, boolean useCertificateStorage, boolean raw) throws AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, EjbcaException, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException, InvalidAlgorithmParameterException, CertificateEncodingException, CertificateException, IOException, RemoveException, InvalidKeySpecException, ObjectNotFoundException, CreateException {
		LOG.trace(">generateCertificatePkcs10");
		LOG.info("useCertReqHistory=" + useCertReqHistory + " useUserStorage=" + useUserStorage + " useCertificateStorage=" + useCertificateStorage);
		reconfigureCA(useCertReqHistory, useUserStorage, useCertificateStorage);
		UserDataVO userData = getNewUserData();
		Certificate certificate = doPkcs10Request(userData, raw);
		assertNotNull("No certificate returned from PKCS#10 request.", certificate);
		assertEquals("UserData was or wasn't available in database.", useUserStorage, userDataExists(userData));
		assertEquals("Certificate Request History was or wasn't available in database.", useCertReqHistory, certificateRequestHistoryExists(certificate));
		assertEquals("Certificate was or wasn't available in database.", useCertificateStorage, certificateExists(certificate));
		// Clean up what we can
		if (useUserStorage) {
			userAdminSession.deleteUser(admin, userData.getUsername());
		}
		if (useCertReqHistory) {
			certificateStoreSession.removeCertReqHistoryData(admin, CertTools.getFingerprintAsString(certificate));
		}
		LOG.trace("<generateCertificatePkcs10");
	}
	
	/** Assert that the CA is configured to store things as expected. */
	private void assertCAConfig(boolean useCertReqHistory, boolean useUserStorage, boolean useCertificateStorage) {
		CAInfo caInfo = caAdminSession.getCAInfo(admin, TESTCA_NAME);
		assertEquals("CA has wrong useCertReqHistory setting: ", useCertReqHistory, caInfo.isUseCertReqHistory());
		assertEquals("CA has wrong useUserStorage setting: ", useUserStorage, caInfo.isUseUserStorage());
		assertEquals("CA has wrong useCertificateStorage setting: ", useCertificateStorage, caInfo.isUseCertificateStorage());
	}

	/** Change CA configuration for what to store and assert that the changes were made. */
	private void reconfigureCA(boolean useCertReqHistory, boolean useUserStorage, boolean useCertificateStorage) throws AuthorizationDeniedException {
		CAInfo caInfo = caAdminSession.getCAInfo(admin, TESTCA_NAME);
		caInfo.setUseCertReqHistory(useCertReqHistory);
		caInfo.setUseUserStorage(useUserStorage);
		caInfo.setUseCertificateStorage(useCertificateStorage);
		assertEquals("CAInfo did not store useCertReqHistory setting correctly: ", useCertReqHistory, caInfo.isUseCertReqHistory());
		assertEquals("CAInfo did not store useUserStorage setting correctly: ", useUserStorage, caInfo.isUseUserStorage());
		assertEquals("CAInfo did not store useCertificateStorage setting correctly: ", useCertificateStorage, caInfo.isUseCertificateStorage());
		caAdminSession.editCA(admin, caInfo);
		assertCAConfig(useCertReqHistory, useUserStorage, useCertificateStorage);
	}
	
	private UserDataVO getNewUserData() {
		String username = "throwAwayTest-" + random.nextInt();
		String password = "foo123";
		UserDataVO userData = new UserDataVO(username, "CN="+username, super.getTestCAId(TESTCA_NAME), null, null, UserDataConstants.STATUS_NEW, SecConst.USER_ENDUSER,
				SecConst.EMPTY_ENDENTITYPROFILE, SecConst.CERTPROFILE_FIXED_ENDUSER, null, null, SecConst.TOKEN_SOFT_BROWSERGEN, 0, null);
		userData.setPassword(password);
		return userData;
	}
	
	/**
	 * Generate a new keypair and PKCS#10 request and request a new certificate in a single transaction.
	 * @param raw true if an encoded request should be sent, false if an EJBCA PKCS10RequestMessage should be used.
	 */
	private Certificate doPkcs10Request(UserDataVO userData, boolean raw) throws AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, EjbcaException, NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException, SignatureException, CertificateEncodingException, CertificateException, IOException, InvalidKeySpecException, ObjectNotFoundException, CreateException {
		Certificate ret;
		KeyPair rsakeys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);	// Use short keys, since this will be done many times
		byte[] rawPkcs10req = new PKCS10CertificationRequest("SHA1WithRSA", CertTools.stringToBcX509Name("CN=ignored"), rsakeys.getPublic(), new DERSet(), rsakeys.getPrivate()).getEncoded();
		if (raw) {
			ret = CertTools.getCertfromByteArray(certificateRequestSession.processCertReq(admin, userData, new String(Base64.encode(rawPkcs10req)), SecConst.CERT_REQ_TYPE_PKCS10, null, SecConst.CERT_RES_TYPE_CERTIFICATE));
		} else {
			PKCS10RequestMessage pkcs10req = new PKCS10RequestMessage(rawPkcs10req);
			pkcs10req.setUsername(userData.getUsername());
			pkcs10req.setPassword(userData.getPassword());
			ret = ((X509ResponseMessage) certificateRequestSession.processCertReq(admin, userData, pkcs10req, org.ejbca.core.protocol.X509ResponseMessage.class)).getCertificate();
		}
		return ret;

	}

	private boolean userDataExists(UserDataVO userData) {
		return userAdminSession.existsUser(admin, userData.getUsername());
	}

	private boolean certificateRequestHistoryExists(Certificate certificate) {
		return certificateStoreSession.getCertReqHistory(admin, CertTools.getSerialNumber(certificate), CertTools.getIssuerDN(certificate)) != null;
	}

	private boolean certificateExists(Certificate certificate) {
		return certificateStoreSession.getCertificateInfo(admin, CertTools.getFingerprintAsString(certificate)) != null;
	}
}
