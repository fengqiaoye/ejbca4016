package org.ejbca.core.ejb.ca.sign;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.persistence.PersistenceException;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.cesecore.core.ejb.ca.store.CertificateProfileSessionRemote;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.ca.CaTestCase;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.ejb.ca.caadmin.CaSessionRemote;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionRemote;
import org.ejbca.core.ejb.ra.CertificateRequestSessionRemote;
import org.ejbca.core.ejb.ra.UserAdminSessionRemote;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ca.certificateprofiles.EndUserCertificateProfile;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.ExtendedInformation;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileExistsException;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.core.protocol.IResponseMessage;
import org.ejbca.core.protocol.PKCS10RequestMessage;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.InterfaceCache;
import org.ejbca.util.keystore.KeyTools;

/**
 * 
 * @version $Id: CustomCertSerialnumberTest.java 11010 2010-12-29 17:40:11Z jeklund $
 */
public class CustomCertSerialnumberTest extends CaTestCase {

	private static final Logger log = Logger.getLogger(CustomCertSerialnumberTest.class);

	private final Admin admin = new Admin(Admin.TYPE_BATCHCOMMANDLINE_USER);
	private static int rsacaid = 0;

	int fooCertProfileId;
	int fooEEProfileId;

	private CAAdminSessionRemote caAdminSession = InterfaceCache.getCAAdminSession();
	private CaSessionRemote caSession = InterfaceCache.getCaSession();
	private CertificateStoreSessionRemote certificateStoreSession = InterfaceCache.getCertificateStoreSession();
	private CertificateRequestSessionRemote certificateRequestSession = InterfaceCache.getCertficateRequestSession();
	private CertificateProfileSessionRemote certificateProfileSession = InterfaceCache.getCertificateProfileSession();
	private EndEntityProfileSessionRemote endEntityProfileSession = InterfaceCache.getEndEntityProfileSession();
	private UserAdminSessionRemote userAdminSession = InterfaceCache.getUserAdminSession();

	public CustomCertSerialnumberTest(String name) throws Exception {
		super(name);

		CryptoProviderTools.installBCProvider();

		assertTrue("Could not create TestCA.", createTestCA());
		CAInfo inforsa = caAdminSession.getCAInfo(admin, "TEST");
		assertTrue("No active RSA CA! Must have at least one active CA to run tests!", inforsa != null);
		rsacaid = inforsa.getCAId();
	}

	public void setUp() throws Exception {

		certificateProfileSession.removeCertificateProfile(admin,"FOOCERTPROFILE");
		endEntityProfileSession.removeEndEntityProfile(admin, "FOOEEPROFILE");

		final EndUserCertificateProfile certprof = new EndUserCertificateProfile();
		certprof.setAllowKeyUsageOverride(true);
		certprof.setAllowCertSerialNumberOverride(true);
		certificateProfileSession.addCertificateProfile(admin, "FOOCERTPROFILE", certprof);
		fooCertProfileId = certificateProfileSession.getCertificateProfileId(admin,"FOOCERTPROFILE");

		final EndEntityProfile profile = new EndEntityProfile(true);
		profile.setValue(EndEntityProfile.DEFAULTCERTPROFILE, 0, Integer.toString(fooCertProfileId));
		profile.setValue(EndEntityProfile.AVAILCERTPROFILES,0,Integer.toString(fooCertProfileId));
		profile.setValue(EndEntityProfile.AVAILKEYSTORE, 0, Integer.toString(SecConst.TOKEN_SOFT_BROWSERGEN));
		assertTrue(profile.getUse(EndEntityProfile.CERTSERIALNR, 0));
		endEntityProfileSession.addEndEntityProfile(admin, "FOOEEPROFILE", profile);
		fooEEProfileId = endEntityProfileSession.getEndEntityProfileId(admin, "FOOEEPROFILE");
	}    

	public void tearDown() throws Exception {
		try {
			userAdminSession.deleteUser(admin, "foo");
			log.debug("deleted user: foo");
		} catch (Exception e) {}
		try {
			userAdminSession.deleteUser(admin, "foo2");
			log.debug("deleted user: foo2");
		} catch (Exception e) {}
		try {
			userAdminSession.deleteUser(admin, "foo3");
			log.debug("deleted user: foo3");
		} catch (Exception e) {}

		certificateStoreSession.revokeAllCertByCA(admin, caSession.getCA(admin, rsacaid).getSubjectDN(), RevokedCertInfo.REVOCATION_REASON_UNSPECIFIED);
	}


	// Create certificate request for user: foo with cert serialnumber=1234567890
	public void test01CreateCertWithCustomSN() throws EndEntityProfileExistsException, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException, IOException, PersistenceException, AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, EjbcaException, ClassNotFoundException, CertificateEncodingException, CertificateException, WaitingForApprovalException, InvalidAlgorithmParameterException {
		log.trace(">test01CreateCustomCert()");

		KeyPair rsakeys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);    	    
		BigInteger serno = SernoGenerator.instance().getSerno();
		log.debug("serno: " + serno);

		PKCS10CertificationRequest req = new PKCS10CertificationRequest("SHA1WithRSA",
				CertTools.stringToBcX509Name("C=SE, O=AnaTom, CN=foo"), rsakeys.getPublic(), new DERSet(),
				rsakeys.getPrivate());

		PKCS10RequestMessage p10 = new PKCS10RequestMessage(req);
		p10.setUsername("foo");
		p10.setPassword("foo123");

		UserDataVO user = new UserDataVO("foo", "C=SE,O=AnaTom,CN=foo", rsacaid, null, "foo@anatom.se", SecConst.USER_ENDUSER, fooEEProfileId, fooCertProfileId,
				SecConst.TOKEN_SOFT_BROWSERGEN, 0, null);
		user.setPassword("foo123");
		ExtendedInformation ei = new ExtendedInformation();
		ei.setCertificateSerialNumber(serno);
		user.setExtendedinformation(ei);
		IResponseMessage resp = certificateRequestSession.processCertReq(admin, user, p10, org.ejbca.core.protocol.X509ResponseMessage.class);

		X509Certificate cert = (X509Certificate) CertTools.getCertfromByteArray(resp.getResponseMessage());
		assertNotNull("Failed to create certificate", cert);
		log.debug("Cert=" + cert.toString());
		log.debug("foo certificate serialnumber: " + cert.getSerialNumber()); 
		assertTrue(cert.getSerialNumber().compareTo(serno) == 0);

		log.trace("<test01CreateCustomCert()");

	}


	// Create certificate request for user: foo2 with random cert serialnumber
	public void test02CreateCertWithRandomSN() throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException, IOException, PersistenceException, AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, EjbcaException, ClassNotFoundException, CertificateEncodingException, CertificateException, InvalidAlgorithmParameterException {

		log.trace(">test02CreateCertWithRandomSN()");

		KeyPair rsakeys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
		BigInteger serno = ((X509Certificate) certificateStoreSession.findCertificatesByUsername(admin, "foo").iterator().next()).getSerialNumber();
		log.debug("foo serno: " + serno);

		PKCS10CertificationRequest req = new PKCS10CertificationRequest("SHA1WithRSA",
				CertTools.stringToBcX509Name("C=SE, O=AnaTom, CN=foo2"), rsakeys.getPublic(), new DERSet(),
				rsakeys.getPrivate());

		PKCS10RequestMessage p10 = new PKCS10RequestMessage(req);
		p10.setUsername("foo2");
		p10.setPassword("foo123");

		UserDataVO user = new UserDataVO("foo2", "C=SE,O=AnaTom,CN=foo2", rsacaid, null, "foo@anatom.se", SecConst.USER_ENDUSER, fooEEProfileId, fooCertProfileId,
				SecConst.TOKEN_SOFT_BROWSERGEN, 0, null);
		user.setPassword("foo123");


		IResponseMessage resp = certificateRequestSession.processCertReq(admin, user, p10, org.ejbca.core.protocol.X509ResponseMessage.class);

		X509Certificate cert = (X509Certificate) CertTools.getCertfromByteArray(resp.getResponseMessage());
		assertNotNull("Failed to create certificate", cert);
		log.debug("Cert=" + cert.toString());
		log.debug("foo2 certificate serialnumber: " + cert.getSerialNumber()); 
		assertTrue(cert.getSerialNumber().compareTo(serno) != 0);

		log.trace("<test02CreateCertWithRandomSN()");
	}


	// Create certificate request for user: foo3 with cert serialnumber=1234567890 (the same as cert serialnumber of user foo)
	public void test03CreateCertWithDublicateSN() throws EndEntityProfileExistsException, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException, IOException, PersistenceException, AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, ClassNotFoundException, CertificateEncodingException, CertificateException, WaitingForApprovalException, InvalidAlgorithmParameterException {
		log.trace(">test03CreateCertWithDublicateSN()");

		KeyPair rsakeys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
		BigInteger serno = ((X509Certificate) certificateStoreSession.findCertificatesByUsername(admin, "foo").iterator().next()).getSerialNumber();
		log.debug("foo serno: " + serno);

		PKCS10CertificationRequest req = new PKCS10CertificationRequest("SHA1WithRSA",
				CertTools.stringToBcX509Name("C=SE, O=AnaTom, CN=foo3"), rsakeys.getPublic(), new DERSet(),
				rsakeys.getPrivate());

		PKCS10RequestMessage p10 = new PKCS10RequestMessage(req);
		p10.setUsername("foo3");
		p10.setPassword("foo123");

		UserDataVO user = new UserDataVO("foo3", "C=SE,O=AnaTom,CN=foo3", rsacaid, null, "foo@anatom.se", SecConst.USER_ENDUSER, fooEEProfileId, fooCertProfileId,
				SecConst.TOKEN_SOFT_BROWSERGEN, 0, null);
		user.setPassword("foo123");
		ExtendedInformation ei = new ExtendedInformation();
		ei.setCertificateSerialNumber(serno);
		user.setExtendedinformation(ei);

		IResponseMessage resp = null;
		try {
			resp = certificateRequestSession.processCertReq(admin, user, p10, org.ejbca.core.protocol.X509ResponseMessage.class);
		} catch (EjbcaException e) {
			log.debug(e.getMessage());
			assertTrue("Unexpected exception.", e.getMessage().startsWith("There is already a certificate stored in 'CertificateData' with the serial number"));
		}
		assertNull(resp);
	}

	public void test04CreateCertWithCustomSNNotAllowed() throws EndEntityProfileExistsException, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException, IOException, PersistenceException, AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, EjbcaException, ClassNotFoundException, CertificateEncodingException, CertificateException, WaitingForApprovalException, InvalidAlgorithmParameterException {
		log.trace(">test04CreateCertWithCustomSNNotAllowed()");

		KeyPair rsakeys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);    	    
		BigInteger serno = SernoGenerator.instance().getSerno();
		log.debug("serno: " + serno);

		PKCS10CertificationRequest req = new PKCS10CertificationRequest("SHA1WithRSA",
				CertTools.stringToBcX509Name("C=SE, O=AnaTom, CN=foo"), rsakeys.getPublic(), new DERSet(),
				rsakeys.getPrivate());

		PKCS10RequestMessage p10 = new PKCS10RequestMessage(req.getEncoded());
		p10.setUsername("foo");
		p10.setPassword("foo123");

		CertificateProfile fooCertProfile = certificateProfileSession.getCertificateProfile(admin, "FOOCERTPROFILE");
		fooCertProfile.setAllowCertSerialNumberOverride(false);
		certificateProfileSession.changeCertificateProfile(admin, "FOOCERTPROFILE", fooCertProfile);

		UserDataVO user = new UserDataVO("foo", "C=SE,O=AnaTom,CN=foo", rsacaid, null, "foo@anatom.se", SecConst.USER_ENDUSER, fooEEProfileId, fooCertProfileId,
				SecConst.TOKEN_SOFT_BROWSERGEN, 0, null);
		user.setPassword("foo123");
		ExtendedInformation ei = new ExtendedInformation();
		ei.setCertificateSerialNumber(serno);
		user.setExtendedinformation(ei);
		try {
			certificateRequestSession.processCertReq(admin, user, p10, org.ejbca.core.protocol.X509ResponseMessage.class);
			assertTrue("This method should throw exception", false);
		} catch (EjbcaException e) {
			assertTrue(e.getMessage().contains("not allowing certificate serial number override"));
		}
		log.trace("<test04CreateCertWithCustomSNNotAllowed()");
	}

}