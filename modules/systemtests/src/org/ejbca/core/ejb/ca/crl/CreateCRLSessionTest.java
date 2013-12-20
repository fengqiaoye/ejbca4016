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

package org.ejbca.core.ejb.ca.crl;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import javax.ejb.EJBException;
import javax.persistence.PersistenceException;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuingDistributionPoint;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.cesecore.core.ejb.ca.crl.CrlCreateSessionRemote;
import org.cesecore.core.ejb.ca.crl.CrlSessionRemote;
import org.cesecore.core.ejb.ca.store.CertificateProfileSessionRemote;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.config.WebConfiguration;
import org.ejbca.core.ejb.ca.CaTestCase;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.ejb.ca.caadmin.CaSessionRemote;
import org.ejbca.core.ejb.ca.sign.SignSessionRemote;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionRemote;
import org.ejbca.core.ejb.ra.UserAdminSessionRemote;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.caadmin.CA;
import org.ejbca.core.model.ca.caadmin.CADoesntExistsException;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.caadmin.X509CAInfo;
import org.ejbca.core.model.ca.catoken.CATokenOfflineException;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfileExistsException;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.ca.store.CertificateInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.ExtendedInformation;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileExistsException;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.InterfaceCache;
import org.ejbca.util.cert.CrlExtensions;
import org.ejbca.util.keystore.KeyTools;

/**
 * Tests CRL session (agentrunner and certificatesession).
 * 
 * @version $Id: CreateCRLSessionTest.java 11526 2011-03-16 12:03:24Z netmackan $
 */
public class CreateCRLSessionTest extends CaTestCase {

    private final static Logger log = Logger.getLogger(CreateCRLSessionTest.class);
    private final static Admin admin = new Admin(Admin.TYPE_CACOMMANDLINE_USER);

    private static int caid;
    private static CA ca;
    private static final String TESTUSERNAME = "TestCreateCRLSessionUser";
    private static final String TESTPROFILE = "TestCreateCRLSessionProfile";

    private CAAdminSessionRemote caAdminSession = InterfaceCache.getCAAdminSession();
    private CaSessionRemote caSession = InterfaceCache.getCaSession();
    private CertificateStoreSessionRemote certificateStoreSession = InterfaceCache.getCertificateStoreSession();
    private CertificateProfileSessionRemote certificateProfileSession = InterfaceCache.getCertificateProfileSession();
    private CrlSessionRemote crlSession = InterfaceCache.getCrlSession();
    private CrlCreateSessionRemote crlCreateSession = InterfaceCache.getCrlStoreSession();
    private EndEntityProfileSessionRemote endEntityProfileSession = InterfaceCache.getEndEntityProfileSession();
    private SignSessionRemote signSession = InterfaceCache.getSignSession();
    private UserAdminSessionRemote userAdminSession = InterfaceCache.getUserAdminSession();

    /**
     * Creates a new TestCreateCRLSession object.
     * 
     * @param name
     *            name
     */
    public CreateCRLSessionTest(String name) throws Exception {
        super(name);
        CryptoProviderTools.installBCProviderIfNotAvailable();
        assertTrue("Could not create TestCA.", createTestCA());
        CAInfo inforsa = caAdminSession.getCAInfo(admin, "TEST");
        assertTrue("No active RSA CA! Must have at least one active CA to run tests!", inforsa != null);
        caid = inforsa.getCAId();
        ca = caSession.getCA(admin, caid);
    }

    public void setUp() throws Exception {
    }

    public void tearDown() throws Exception {
    }

    /**
     * creates new crl
     * 
     * @throws Exception
     *             error
     */
    public void test01CreateNewCRL() throws Exception {
        log.trace(">test01CreateNewCRL()");
        crlCreateSession.run(admin, ca);
        log.trace("<test01CreateNewCRL()");
    }

    /**
     * gets last crl
     * 
     * @throws Exception
     *             error
     */
    public void test02LastCRL() throws Exception {
        log.trace(">test02LastCRL()");
        // Get number of last CRL
        int number = crlSession.getLastCRLNumber(admin, ca.getSubjectDN(), false);
        log.debug("Last CRLNumber = " + number);
        byte[] crl = crlSession.getLastCRL(admin, ca.getSubjectDN(), false);
        assertNotNull("Could not get CRL", crl);
        X509CRL x509crl = CertTools.getCRLfromByteArray(crl);
        BigInteger num = CrlExtensions.getCrlNumber(x509crl);
        // Create a new CRL again to see that the number increases
        crlCreateSession.run(admin, ca);
        int number1 = crlSession.getLastCRLNumber(admin, ca.getSubjectDN(), false);
        assertEquals(number + 1, number1);
        byte[] crl1 = crlSession.getLastCRL(admin, ca.getSubjectDN(), false);
        X509CRL x509crl1 = CertTools.getCRLfromByteArray(crl1);
        BigInteger num1 = CrlExtensions.getCrlNumber(x509crl1);
        assertEquals(num.intValue() + 1, num1.intValue());
        log.trace("<test02LastCRL()");
    }

    /**
     * check revoked certificates
     * 
     * @throws Exception
     *             error
     */
    public void test03CheckNumberofRevokedCerts() throws Exception {
        log.trace(">test03CheckNumberofRevokedCerts()");

        // Get number of last CRL
        Collection<RevokedCertInfo> revfp = certificateStoreSession.listRevokedCertInfo(admin, ca.getSubjectDN(), -1);
        log.debug("Number of revoked certificates=" + revfp.size());
        byte[] crl = crlSession.getLastCRL(admin, ca.getSubjectDN(), false);
        assertNotNull("Could not get CRL", crl);

        X509CRL x509crl = CertTools.getCRLfromByteArray(crl);
        Set<? extends X509CRLEntry> revset = x509crl.getRevokedCertificates();
        int revsize = 0;

        if (revset != null) {
            revsize = revset.size();
            assertEquals(revfp.size(), revsize);
        }
        log.trace("<test03CheckNumberofRevokedCerts()");
    }

    /**
     * Test revocation and reactivation of certificates
     * 
     * @throws Exception
     *             error
     */
    public void test04RevokeAndUnrevoke() throws Exception {
        log.trace(">test04RevokeAndUnrevoke()");

        // Make user that we know...
        boolean userExists = false;
        final String userDN = "C=SE,O=AnaTom,CN=foo";
        try {
            userAdminSession.addUser(admin, "foo", "foo123", userDN, null, "foo@anatom.se", false, SecConst.EMPTY_ENDENTITYPROFILE,
                    SecConst.CERTPROFILE_FIXED_ENDUSER, SecConst.USER_ENDUSER, SecConst.TOKEN_SOFT_PEM, 0, caid);
            log.debug("created user: foo, foo123, C=SE, O=AnaTom, CN=foo");
        } catch (EJBException e) {
        	if (e.getCause() instanceof PersistenceException) {
        		userExists = true;
        	}
        }
        if (userExists) {
            log.info("User foo already exists, resetting status.");
            UserDataVO userdata = new UserDataVO("foo", userDN, caid, null, "foo@anatom.se", SecConst.USER_ENDUSER, SecConst.EMPTY_ENDENTITYPROFILE,
                    SecConst.CERTPROFILE_FIXED_ENDUSER, SecConst.TOKEN_SOFT_PEM, 0, null);
            userdata.setStatus(UserDataConstants.STATUS_NEW);
            userdata.setPassword("foo123");
            userAdminSession.changeUser(admin, userdata, false);
            log.debug("Reset status to NEW");
        }
        KeyPair keys = genKeys();

        // user that we know exists...
        X509Certificate cert = (X509Certificate) signSession.createCertificate(admin, "foo", "foo123", keys.getPublic());
        assertNotNull("Misslyckades skapa cert", cert);
        log.debug("Cert=" + cert.toString());

        // Create a new CRL again...
        crlCreateSession.run(admin, ca);
        // Check that our newly signed certificate is not present in a new CRL
        byte[] crl = crlSession.getLastCRL(admin, ca.getSubjectDN(), false);
        assertNotNull("Could not get CRL", crl);
        X509CRL x509crl = CertTools.getCRLfromByteArray(crl);
        Set<? extends X509CRLEntry> revset = x509crl.getRevokedCertificates();
        if (revset != null) {
            Iterator<? extends X509CRLEntry> iter = revset.iterator();
            while (iter.hasNext()) {
                X509CRLEntry ce = iter.next();
                assertTrue(ce.getSerialNumber().compareTo(cert.getSerialNumber()) != 0);
            }
        } // If no revoked certificates exist at all, this test passed...

        certificateStoreSession.revokeCertificate(admin, cert, null, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD, userDN);
        // Create a new CRL again...
        crlCreateSession.run(admin, ca);
        // Check that our newly signed certificate IS present in a new CRL
        crl = crlSession.getLastCRL(admin, ca.getSubjectDN(), false);
        assertNotNull("Could not get CRL", crl);
        x509crl = CertTools.getCRLfromByteArray(crl);
        revset = x509crl.getRevokedCertificates();
        assertNotNull(revset);
        Iterator<? extends X509CRLEntry> iter = revset.iterator();
        boolean found = false;
        while (iter.hasNext()) {
            X509CRLEntry ce = iter.next();
            if (ce.getSerialNumber().compareTo(cert.getSerialNumber()) == 0) {
                found = true;
                // TODO: verify the reason code
            }
        }
        assertTrue("Certificate with serial " + cert.getSerialNumber().toString(16) + " not revoked", found);

        // Unrevoke the certificate that we just revoked
        certificateStoreSession.revokeCertificate(admin, cert, null, RevokedCertInfo.NOT_REVOKED, userDN);
        // Create a new CRL again...
        crlCreateSession.run(admin, ca);
        // Check that our newly signed certificate IS NOT present in the new
        // CRL.
        crl = crlSession.getLastCRL(admin, ca.getSubjectDN(), false);
        assertNotNull("Could not get CRL", crl);
        x509crl = CertTools.getCRLfromByteArray(crl);
        revset = x509crl.getRevokedCertificates();
        if (revset != null) {
            iter = revset.iterator();
            found = false;
            while (iter.hasNext()) {
                X509CRLEntry ce = iter.next();
                if (ce.getSerialNumber().compareTo(cert.getSerialNumber()) == 0) {
                    found = true;
                }
            }
            assertFalse(found);
        } // If no revoked certificates exist at all, this test passed...

        certificateStoreSession.revokeCertificate(admin, cert, null, RevokedCertInfo.REVOCATION_REASON_CACOMPROMISE, userDN);
        assertTrue("Failed to revoke certificate!", certificateStoreSession.isRevoked(CertTools.getIssuerDN(cert), CertTools.getSerialNumber(cert)));
        // Create a new CRL again...
        crlCreateSession.run(admin, ca);
        // Check that our newly signed certificate IS present in a new CRL
        crl = crlSession.getLastCRL(admin, ca.getSubjectDN(), false);
        assertNotNull("Could not get CRL", crl);
        x509crl = CertTools.getCRLfromByteArray(crl);
        revset = x509crl.getRevokedCertificates();
        iter = revset.iterator();
        found = false;
        while (iter.hasNext()) {
            X509CRLEntry ce = (X509CRLEntry) iter.next();
            if (ce.getSerialNumber().compareTo(cert.getSerialNumber()) == 0) {
                found = true;
                // TODO: verify the reason code
            }
        }
        assertTrue(found);

        certificateStoreSession.revokeCertificate(admin, cert, null, RevokedCertInfo.NOT_REVOKED, userDN);
        assertTrue("Was able to re-activate permanently revoked certificate!", certificateStoreSession.isRevoked(CertTools.getIssuerDN(cert), CertTools.getSerialNumber(cert)));
        // Create a new CRL again...
        crlCreateSession.run(admin, ca);
        // Check that our newly signed certificate is present in the new CRL,
        // because the revocation reason
        // was not CERTIFICATE_HOLD, we can only un-revoke certificates that are
        // on hold.
        crl = crlSession.getLastCRL(admin, ca.getSubjectDN(), false);
        assertNotNull("Could not get CRL", crl);
        x509crl = CertTools.getCRLfromByteArray(crl);
        revset = x509crl.getRevokedCertificates();
        iter = revset.iterator();
        found = false;
        while (iter.hasNext()) {
            X509CRLEntry ce = (X509CRLEntry) iter.next();
            if (ce.getSerialNumber().compareTo(cert.getSerialNumber()) == 0) {
                found = true;
            }
        }
        assertTrue(found);
        log.trace("<test04RevokeAndUnrevoke()");
    }

    /**
     * Test Overflow of CRL Period
     * 
     * @throws Exception
     *             error
     */
    public void test05CRLPeriodOverflow() throws Exception {
        log.trace(">test05CRLPeriodOverflow()");
        // Fetch CAInfo and save CRLPeriod
        CAInfo cainfo = ca.getCAInfo();
        long tempCRLPeriod = cainfo.getCRLPeriod();
        try {
            // Create a user that Should be revoked
            boolean userExists = false;
            final String userDN = "CN=" + TESTUSERNAME;
            try {
                int certprofileid = 0;
                // add a Certificate Profile with overridable validity
                try {
                    CertificateProfile certProfile = new CertificateProfile();
                    certProfile.setAllowValidityOverride(true);
                    certificateProfileSession.addCertificateProfile(admin, TESTPROFILE, certProfile);
                } catch (CertificateProfileExistsException cpeee) {
                }
                certprofileid = certificateProfileSession.getCertificateProfileId(admin, TESTPROFILE);
                assertTrue(certprofileid != 0);
                // add End Entity Profile with validity limitations
                EndEntityProfile profile;
                try {
                    endEntityProfileSession.removeEndEntityProfile(admin, TESTPROFILE);
                    profile = new EndEntityProfile();
                    profile.setUse(EndEntityProfile.ENDTIME, 0, true);
                    profile.setUse(EndEntityProfile.CLEARTEXTPASSWORD, 0, false);
                    profile.setValue(EndEntityProfile.CLEARTEXTPASSWORD, 0, EndEntityProfile.FALSE);
                    profile.setValue(EndEntityProfile.AVAILCAS, 0, Integer.valueOf(caid).toString());
                    profile.setUse(EndEntityProfile.STARTTIME, 0, true);
                    profile.setValue(EndEntityProfile.AVAILCERTPROFILES, 0, Integer.valueOf(certprofileid).toString());
                    profile.setValue(EndEntityProfile.DEFAULTCERTPROFILE, 0, Integer.valueOf(certprofileid).toString());
                    endEntityProfileSession.addEndEntityProfile(admin, TESTPROFILE, profile);
                } catch (EndEntityProfileExistsException pee) {
                }
                // Create a new user
                ExtendedInformation ei = new ExtendedInformation();
                ei.setCustomData(EndEntityProfile.STARTTIME, "0:00:00");
                ei.setCustomData(EndEntityProfile.ENDTIME, "0:00:50");
                UserDataVO userdata = new UserDataVO(TESTUSERNAME, userDN, caid, "", "foo@bar.se", UserDataConstants.STATUS_NEW, SecConst.USER_ENDUSER,
                        endEntityProfileSession.getEndEntityProfileId(admin, TESTPROFILE), certprofileid, new Date(), new Date(), SecConst.TOKEN_SOFT_PEM, 0, ei);
                userdata.setPassword("foo123");
                try {
                    userAdminSession.revokeAndDeleteUser(admin, TESTUSERNAME, RevokedCertInfo.REVOCATION_REASON_KEYCOMPROMISE);
                } catch (NotFoundException nfe) {
                }
                userAdminSession.addUser(admin, userdata, false);
                log.debug("created user");
            } catch (EJBException e) {
            	if (e.getCause() instanceof PersistenceException) {
            		userExists = true;
            	}
            }
            if (userExists) {
                log.info("User testCRLPeriod already exists, resetting status.");
                userAdminSession.setUserStatus(admin, TESTUSERNAME, UserDataConstants.STATUS_NEW);
                log.debug("Reset status to NEW");
            }
            KeyPair keys = genKeys();
            // user that we know exists...
            X509Certificate cert = (X509Certificate) signSession.createCertificate(admin, TESTUSERNAME, "foo123", keys.getPublic());
            assertNotNull("Failed to create certificate", cert);
            log.debug("Cert=" + cert.toString());
            // Revoke the user
            certificateStoreSession.revokeCertificate(admin, cert, null, RevokedCertInfo.REVOCATION_REASON_KEYCOMPROMISE, userDN);
            // Change CRLPeriod
            cainfo.setCRLPeriod(Long.MAX_VALUE);
            caAdminSession.editCA(admin, cainfo);
            ca = caSession.getCA(admin, caid);
            // Create new CRL's
            crlCreateSession.run(admin, ca);
            // Verify that status is not archived
            CertificateInfo certinfo = certificateStoreSession.getCertificateInfo(admin, CertTools.getFingerprintAsString(cert));
            assertFalse("Non Expired Revoked Certificate was archived", certinfo.getStatus() == SecConst.CERT_ARCHIVED);
        } finally {
            // Restore CRL Period
            cainfo.setCRLPeriod(tempCRLPeriod);
            caAdminSession.editCA(admin, cainfo);
            ca = caSession.getCA(admin, caid);
            // Delete and revoke User
            userAdminSession.revokeAndDeleteUser(admin, TESTUSERNAME, RevokedCertInfo.REVOCATION_REASON_KEYCOMPROMISE);
            // Delete end entity profile

            certificateProfileSession.removeCertificateProfile(admin, TESTPROFILE);

            // Delete certificate profile
            try {
                endEntityProfileSession.removeEndEntityProfile(admin, TESTPROFILE);
            } catch (Exception e) {
                log.error("Could not remove End Entity Profile");
            }
        }
    }

    /**
     * Tests the extension CRL Distribution Point on CRLs
     * 
     * @throws Exception
     *             error
     */
    public void test06CRLDistPointOnCRL() throws Exception {
        log.trace(">test06CRLDistPointOnCRL()");

        final String cdpURL = "http://www.ejbca.org/foo/bar.crl";
        X509CAInfo cainfo = (X509CAInfo) ca.getCAInfo();
        X509CRL x509crl;
        byte[] cdpDER;

        cainfo.setUseCrlDistributionPointOnCrl(true);
        cainfo.setDefaultCRLDistPoint(cdpURL);
        caAdminSession.editCA(admin, cainfo);
        ca = caSession.getCA(admin, caid);
        crlCreateSession.run(admin, ca);
        x509crl = CertTools.getCRLfromByteArray(crlSession.getLastCRL(admin, cainfo.getSubjectDN(), false));
        cdpDER = x509crl.getExtensionValue(X509Extensions.IssuingDistributionPoint.getId());
        assertNotNull("CRL has no distribution points", cdpDER);

        ASN1InputStream aIn = new ASN1InputStream(new ByteArrayInputStream(cdpDER));
        ASN1OctetString octs = (ASN1OctetString) aIn.readObject();
        aIn = new ASN1InputStream(new ByteArrayInputStream(octs.getOctets()));
        IssuingDistributionPoint cdp = new IssuingDistributionPoint((ASN1Sequence) aIn.readObject());
        DistributionPointName distpoint = cdp.getDistributionPoint();

        assertEquals("CRL distribution point is different", cdpURL, ((DERIA5String) ((GeneralNames) distpoint.getName()).getNames()[0].getName()).getString());

        cainfo.setUseCrlDistributionPointOnCrl(false);
        cainfo.setDefaultCRLDistPoint("");
        caAdminSession.editCA(admin, cainfo);
        ca = caSession.getCA(admin, caid);
        crlCreateSession.run(admin, ca);
        x509crl = CertTools.getCRLfromByteArray(crlSession.getLastCRL(admin, cainfo.getSubjectDN(), false));
        assertNull("CRL has distribution points", x509crl.getExtensionValue(X509Extensions.CRLDistributionPoints.getId()));

        log.trace("<test06CRLDistPointOnCRL()");
    }

    /**
     * Tests the extension Freshest CRL DP.
     * 
     * @throws Exception
     *             in case of error.
     */
    public void test07CRLFreshestCRL() throws Exception {
        log.trace(">test07CRLFreshestCRL()");

        final String cdpURL = "http://www.ejbca.org/foo/bar.crl";
        final String freshestCdpURL = "http://www.ejbca.org/foo/delta.crl";
        X509CAInfo cainfo = (X509CAInfo) caAdminSession.getCAInfo(admin, caid);
        X509CRL x509crl;
        byte[] cFreshestDpDER;

        cainfo.setUseCrlDistributionPointOnCrl(true);
        cainfo.setDefaultCRLDistPoint(cdpURL);
        cainfo.setCADefinedFreshestCRL(freshestCdpURL);
        caAdminSession.editCA(admin, cainfo);
        ca = caSession.getCA(admin, caid);
        crlCreateSession.run(admin, ca);
        x509crl = CertTools.getCRLfromByteArray(crlSession.getLastCRL(admin, cainfo.getSubjectDN(), false));
        cFreshestDpDER = x509crl.getExtensionValue(X509Extensions.FreshestCRL.getId());
        assertNotNull("CRL has no Freshest Distribution Point", cFreshestDpDER);

        ASN1InputStream aIn = new ASN1InputStream(new ByteArrayInputStream(cFreshestDpDER));
        ASN1OctetString octs = (ASN1OctetString) aIn.readObject();
        aIn = new ASN1InputStream(new ByteArrayInputStream(octs.getOctets()));
        CRLDistPoint cdp = new CRLDistPoint((ASN1Sequence) aIn.readObject());
        DistributionPoint[] distpoints = cdp.getDistributionPoints();

        assertEquals("More CRL Freshest distributions points than expected", 1, distpoints.length);
        assertEquals("Freshest CRL distribution point is different", freshestCdpURL, ((DERIA5String) ((GeneralNames) distpoints[0].getDistributionPoint()
                .getName()).getNames()[0].getName()).getString());

        log.trace("<test07CRLFreshestCRL()");
    }

    public void test08TestCRLStore() throws Exception {
        log.trace(">test08TestCRLStore()");
        final String HTTP_PORT = InterfaceCache.getConfigurationSession().getProperty(WebConfiguration.CONFIG_HTTPSERVERPUBHTTP, "8080");
    	final String result = ValidationAuthorityTst.testCRLStore(ca, this.crlSession, HTTP_PORT);
    	assertNull(result, result);
        log.trace("<test08TestCRLStore()");
    }

    public void test09CrlGenerateForAll() throws Exception {
        log.trace(">test09CrlGenerateForAll()");
        final X509CAInfo cainfo = (X509CAInfo) caAdminSession.getCAInfo(admin, caid);
        cainfo.setCRLIssueInterval(1);	// Issue very often..
        cainfo.setDeltaCRLPeriod(1);	// Issue very often..
        caAdminSession.editCA(admin, cainfo);
        ca = caSession.getCA(admin, caid);
        Thread.sleep(1000);
        final X509CRL x509crl = CertTools.getCRLfromByteArray(crlSession.getLastCRL(admin, cainfo.getSubjectDN(), false));
        crlCreateSession.createCRLs(admin);
        final X509CRL x509crlAfter = CertTools.getCRLfromByteArray(crlSession.getLastCRL(admin, cainfo.getSubjectDN(), false));
        assertTrue("Did not generate a newer CRL.", x509crlAfter.getThisUpdate().after(x509crl.getThisUpdate()));
        final X509CRL x509deltaCrl = CertTools.getCRLfromByteArray(crlSession.getLastCRL(admin, cainfo.getSubjectDN(), true));
        crlCreateSession.createDeltaCRLs(admin);
        final X509CRL x509deltaCrlAfter = CertTools.getCRLfromByteArray(crlSession.getLastCRL(admin, cainfo.getSubjectDN(), true));
        assertTrue("Did not generate a newer Delta CRL.", x509deltaCrlAfter.getThisUpdate().after(x509deltaCrl.getThisUpdate()));
        // Try a similar thing when we specify which CA IDs to generate CRLs for
        // Compare CRL numbers instead of Dates, since these CRLs might have been generated the same second as the last ones
        final Collection<Integer> caids = new ArrayList<Integer>();
        caids.add(Integer.valueOf(caid));
        crlCreateSession.createCRLs(admin, caids, 2);
        final X509CRL x509crlAfter2 = CertTools.getCRLfromByteArray(crlSession.getLastCRL(admin, cainfo.getSubjectDN(), false));
        assertTrue("Did not generate a newer CRL.", CrlExtensions.getCrlNumber(x509crlAfter2).intValue() > CrlExtensions.getCrlNumber(x509crlAfter).intValue());
        crlCreateSession.createDeltaCRLs(admin, caids, 2);
        final X509CRL x509deltaCrlAfter2 = CertTools.getCRLfromByteArray(crlSession.getLastCRL(admin, cainfo.getSubjectDN(), true));
        assertTrue("Did not generate a newer Delta CRL.", CrlExtensions.getCrlNumber(x509deltaCrlAfter2).intValue() > CrlExtensions.getCrlNumber(x509deltaCrlAfter).intValue());
        log.trace("<test09CrlGenerateForAll()");
    }
    
    /** Test error handling when we request info that does not exist. */
    public void test10CrlSessionErrorHandling() {
    	log.trace(">test10CrlSessionErrorHandling()");
    	assertNull("crlSession.getLastCRL returned a CRL for nonexsting CA", crlSession.getLastCRL(admin, "CN=notexsting", false));
    	assertNull("crlSession.getLastCRL returned a DeltaCRL for nonexsting CA", crlSession.getLastCRL(admin, "CN=notexsting", true));
    	assertNull("crlSession.getLastCRLInfo returned CRLInfo for nonexsting CA", crlSession.getLastCRLInfo(admin, "CN=notexsting", false));
    	assertNull("crlSession.getLastCRLInfo returned Delta CRLInfo for nonexsting CA", crlSession.getLastCRLInfo(admin, "CN=notexsting", true));
    	assertNull("crlSession.getCRLInfo returned CRLInfo for nonexsting CRL fingerprint", crlSession.getCRLInfo(admin, "tooshortfp"));
    	log.trace("<test10CrlSessionErrorHandling()");
    }

    /** Test error handling during CRL creation. */
    public void test11CrlCreateSessionErrorHandling() throws CADoesntExistsException {
    	log.trace(">test11CrlCreateSessionErrorHandling()");
    	CA ca = caSession.getCA(admin, getTestCAId());
    	ca.setStatus(SecConst.CA_OFFLINE);
    	try {
			crlCreateSession.createCRL(admin, ca, null, 0);
			assertTrue("Trying to generate a CRL for CA with status CA_OFFLINE did not throw the CATokenOfflineException.", false);
		} catch (CATokenOfflineException e) {
			// Expected
		}
    	log.trace("<test11CrlCreateSessionErrorHandling()");
    }

    public void test99CleanUp() throws Exception {
        log.trace(">test99CleanUp()");
        removeTestCA();
        log.trace("<test99CleanUp()");
    }

    // 
    // Helper methods
    //

    /**
     * Generates a RSA key pair.
     * 
     * @return KeyPair the generated key pair
     * 
     * @throws Exception
     *             if en error occurs...
     */
    private static KeyPair genKeys() throws Exception {
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA", "BC");
        keygen.initialize(512);
        log.debug("Generating keys, please wait...");
        KeyPair rsaKeys = keygen.generateKeyPair();
        log.debug("Generated " + rsaKeys.getPrivate().getAlgorithm() + " keys with length" + KeyTools.getKeyLength(rsaKeys.getPublic()));

        return rsaKeys;
    } // genKeys

}
