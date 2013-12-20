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


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.ejb.ObjectNotFoundException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.ReasonFlags;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.asn1.x509.X509Name;
import org.cesecore.core.ejb.authorization.AdminEntitySessionRemote;
import org.cesecore.core.ejb.authorization.AdminGroupSessionRemote;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.config.ConfigurationHolder;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.JndiHelper;
import org.ejbca.core.ejb.authorization.AuthorizationSession;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.ejb.ca.caadmin.CaSessionRemote;
import org.ejbca.core.ejb.ca.sign.SignSessionRemote;
import org.ejbca.core.ejb.ca.store.CertificateStoreSession;
import org.ejbca.core.ejb.config.ConfigurationSessionRemote;
import org.ejbca.core.ejb.ra.UserAdminSessionRemote;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.authorization.AdminEntity;
import org.ejbca.core.model.authorization.AdminGroup;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ca.caadmin.CADoesntExistsException;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.ui.cli.batch.BatchMakeP12;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.InterfaceCache;
import org.ejbca.util.keystore.KeyTools;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.novosec.pkix.asn1.cmp.CertOrEncCert;
import com.novosec.pkix.asn1.cmp.CertRepMessage;
import com.novosec.pkix.asn1.cmp.CertResponse;
import com.novosec.pkix.asn1.cmp.CertifiedKeyPair;
import com.novosec.pkix.asn1.cmp.PKIBody;
import com.novosec.pkix.asn1.cmp.PKIHeader;
import com.novosec.pkix.asn1.cmp.PKIMessage;
import com.novosec.pkix.asn1.cmp.PKIStatusInfo;
import com.novosec.pkix.asn1.crmf.AttributeTypeAndValue;
import com.novosec.pkix.asn1.crmf.CRMFObjectIdentifiers;
import com.novosec.pkix.asn1.crmf.CertReqMessages;
import com.novosec.pkix.asn1.crmf.CertReqMsg;
import com.novosec.pkix.asn1.crmf.CertRequest;
import com.novosec.pkix.asn1.crmf.CertTemplate;
import com.novosec.pkix.asn1.crmf.OptionalValidity;
import com.novosec.pkix.asn1.crmf.POPOSigningKey;
import com.novosec.pkix.asn1.crmf.ProofOfPossession;

/**
 * This will test the different cmp authentication modules.
 * 
 * @version $Id: CrmfKeyUpdateTest.java 15009 2012-06-18 12:49:30Z primelars $
 *
 */
@SuppressWarnings("unused")
public class CrmfKeyUpdateTest extends CmpTestCase {

    
    private static final Logger log = Logger.getLogger(CrmfKeyUpdateTest.class);

    private static Admin admin;
    
    private String username;
    private String userDN;
    private String issuerDN;
    private byte[] nonce;
    private byte[] transid;
    private int caid;
    private Certificate cacert;
    
    private CaSessionRemote caSession = InterfaceCache.getCaSession();
    private CAAdminSessionRemote caAdminSession = InterfaceCache.getCAAdminSession();
    private UserAdminSessionRemote userAdminSession = InterfaceCache.getUserAdminSession();
    private SignSessionRemote signSession = InterfaceCache.getSignSession();
    private ConfigurationSessionRemote confSession = JndiHelper.getRemoteSession(ConfigurationSessionRemote.class); //InterfaceCache.getConfigurationSession();
    private CertificateStoreSession certStoreSession = InterfaceCache.getCertificateStoreSession();
    private AdminGroupSessionRemote adminGroupSession = InterfaceCache.getAdminGroupSession();
    private AdminEntitySessionRemote adminEntitySession = InterfaceCache.getAdminEntitySession();
    private AuthorizationSession authorizationSession = InterfaceCache.getAuthorizationSession();
    
    public CrmfKeyUpdateTest(String arg0) {
        super(arg0);

        admin = new Admin(Admin.TYPE_BATCHCOMMANDLINE_USER);

        username = "certRenewalUser";
        userDN = "CN="+username+",O=PrimeKey Solutions AB,C=SE";
        issuerDN = "CN=AdminCA1,O=EJBCA Sample,C=SE";
        nonce = CmpMessageHelper.createSenderNonce();
        transid = CmpMessageHelper.createSenderNonce();


        CryptoProviderTools.installBCProvider();
        try {
            setCAID();
            assertFalse("caid if 0", caid==0);
            setCaCert();
            assertNotNull("cacert is null", cacert);
        } catch (CADoesntExistsException e) {
            log.error("Failed to find CA. " + e.getLocalizedMessage());
        } catch (AuthorizationDeniedException e) {
            log.error("Failed to find CA. " + e.getLocalizedMessage());
        }
        
        // Initialize config in here
        ConfigurationHolder.instance();
        
        confSession.backupConfiguration();
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_RA_ENDENTITYPROFILE, "EMPTY");
        updatePropertyOnServer(CmpConfiguration.CONFIG_RA_CERTIFICATEPROFILE, "ENDUSER");
        updatePropertyOnServer(CmpConfiguration.CONFIG_RACANAME, "AdminCA1");
    }

    
    /**
     * A "Happy Path" test. Sends a KeyUpdateRequest and receives a new certificate.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Set cmp.checkadminauthorization to 'false'
     * - Pre-configurations: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configurations: Sets cmp.allowupdatewithsamekey to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attached cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Obtains the certificate from the response
     * 		- Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test01KeyUpdateRequestOK() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test01KeyUpdateRequestOK");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "normal");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "false");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true");
        assertTrue("The CMP Authentication module was not configured correctly.", confSession.verifyProperty(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true"));
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWUPDATEWITHSAMEKEY, "true");

        
        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        
        
        PKIMessage req = genRenewalReq(keys, false, null, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();
        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);      
        req.getHeader().setSenderKID(new DEROctetString(nonce));
        addExtraCert(req, certificate);
        signPKIMessage(req, keys);
        assertNotNull(req);
        //******************************************''''''
        final Signature sig = Signature.getInstance(req.getHeader().getProtectionAlg().getObjectId().getId(), "BC");
        sig.initVerify(certificate.getPublicKey());
        sig.update(req.getProtectedBytes());
        boolean verified = sig.verify(req.getProtection().getBytes());
        assertTrue("Signing the message failed.", verified);
        //***************************************************
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, true, null);
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);
        assertTrue("The new certificate's keys are incorrect.", cert.getPublicKey().equals(keys.getPublic()));
        
        if(log.isTraceEnabled()) {
            log.trace("<test01KeyUpdateRequestOK");
        }

    }

    /**
     * Sends a KeyUpdateRequest for a certificate that belongs to an end entity whose status is not NEW and the configurations is 
     * NOT to allow changing the end entity status automatically. A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Set cmp.checkadminauthorization to 'false'
     * - Pre-configurations: Sets cmp.allowautomaticrenewal to 'false' and tests that the resetting of configuration has worked.
     * - Pre-configurations: Sets cmp.allowupdatewithsamekey to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attached cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Parse the response and make sure that the parsing did not result in a 'null'
     * 		- Check that the CMP response message tag number is '23', indicating a CMP error message
     * 		- Check that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test02AutomaticUpdateNotAllowed() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test02AutomaticUpdateNotAllowed");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "normal");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "false");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "false");
        assertTrue("The CMP Authentication module was not configured correctly.", confSession.verifyProperty(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "false"));
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWUPDATEWITHSAMEKEY, "true");

        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);
        
        PKIMessage req = genRenewalReq(keys, false, null, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);      
        req.getHeader().setSenderKID(new DEROctetString(nonce));
        addExtraCert(req, certificate);
        signPKIMessage(req, keys);
        assertNotNull(req);
        //******************************************''''''
        final Signature sig = Signature.getInstance(req.getHeader().getProtectionAlg().getObjectId().getId(), "BC");
        sig.initVerify(certificate.getPublicKey());
        sig.update(req.getProtectedBytes());
        boolean verified = sig.verify(req.getProtection().getBytes());
        assertTrue("Signing the message failed.", verified);
        //***************************************************
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
        
        PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(resp)).readObject());
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getTagNo());
        final String errMsg = body.getError().getPKIStatus().getStatusString().getString(0).getString();
        final String expectedErrMsg = "Got request with status GENERATED (40), NEW, FAILED or INPROCESS required: " + username + ".";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test02AutomaticUpdateNotAllowed");
        }

    }

    /**
     * Sends a KeyUpdateRequest concerning a revoked certificate. A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Set cmp.checkadminauthorization to 'false'
     * - Pre-configurations: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configurations: Sets cmp.allowupdatewithsamekey to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Revokes cert and tests that the revocation was performed successfully
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attached cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Parse the response and make sure that the parsing did not result in a 'null'
     * 		- Check that the CMP response message tag number is '23', indicating a CMP error message
     * 		- Check that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test03UpdateRevokedCert() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test03UpdateRevokedCert");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "normal");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "false");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true");
        assertTrue("The CMP Authentication module was not configured correctly.", confSession.verifyProperty(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true"));
        
        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        certStoreSession.revokeCertificate(admin, certificate, null, RevokedCertInfo.REVOCATION_REASON_CESSATIONOFOPERATION, userDN);
        assertTrue("Failed to revoke the test certificate", certStoreSession.isRevoked(CertTools.getIssuerDN(certificate), CertTools.getSerialNumber(certificate)));

        
        
        PKIMessage req = genRenewalReq(keys, false, null, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);      
        req.getHeader().setSenderKID(new DEROctetString(nonce));
        addExtraCert(req, certificate);
        signPKIMessage(req, keys);
        assertNotNull(req);
        //******************************************''''''
        final Signature sig = Signature.getInstance(req.getHeader().getProtectionAlg().getObjectId().getId(), "BC");
        sig.initVerify(certificate.getPublicKey());
        sig.update(req.getProtectedBytes());
        boolean verified = sig.verify(req.getProtection().getBytes());
        assertTrue("Signing the message failed.", verified);
        //***************************************************
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
        
        PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(resp)).readObject());
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getTagNo());
        final String errMsg = body.getError().getPKIStatus().getStatusString().getString(0).getString();
        final String expectedErrMsg = "The End Entity certificate attached to the PKIMessage in the extraCert field is revoked.";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test03UpdateRevokedCert");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest concerning a certificate that does not exist in the database. A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Set cmp.checkadminauthorization to 'false'
     * - Pre-configurations: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configurations: Sets cmp.allowupdatewithsamekey to 'true'
     * - Generates a self-signed certificate, fakecert
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using fakecert and attaches fakecert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Parse the response and make sure that the parsing did not result in a 'null'
     * 		- Check that the CMP response message tag number is '23', indicating a CMP error message
     * 		- Check that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test04UpdateKeyWithFakeCert() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test04UpdateKeyWithFakeCert");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "normal");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "false");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true");
        assertTrue("The CMP Authentication module was not configured correctly.", confSession.verifyProperty(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true"));
        
        //--------------- create the user and issue his first certificate -----------------
        final String fakeUserDN = "CN=fakeuser,C=SE";
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate fakeCert = null;
        fakeCert = CertTools.genSelfCert(fakeUserDN, 30, null, keys.getPrivate(), keys.getPublic(),
                    AlgorithmConstants.SIGALG_SHA1_WITH_RSA, false);
        assertNotNull("Failed to create a test certificate", fakeCert);
        
        PKIMessage req = genRenewalReq(keys, false, null, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);      
        req.getHeader().setSenderKID(new DEROctetString(nonce));
        addExtraCert(req, fakeCert);
        signPKIMessage(req, keys);
        assertNotNull(req);
        //***************************************************
        final Signature sig = Signature.getInstance(req.getHeader().getProtectionAlg().getObjectId().getId(), "BC");
        sig.initVerify(fakeCert.getPublicKey());
        sig.update(req.getProtectedBytes());
        boolean verified = sig.verify(req.getProtection().getBytes());
        assertTrue("Signing the message failed.", verified);
        //***************************************************
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
        
        PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(resp)).readObject());
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getTagNo());
        final String errMsg = body.getError().getPKIStatus().getStatusString().getString(0).getString();
        final String expectedErrMsg = "The End Entity certificate attached to the PKIMessage in the extraCert field could not be found in the database.";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test04UpdateKeyWithFakeCert");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest using the same old keys and the configurations is NOT to allow the use of the same key. 
     * A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Set cmp.checkadminauthorization to 'false'
     * - Pre-configurations: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configurations: Sets cmp.allowupdatewithsamekey to 'false'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attached cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Parse the response and make sure that the parsing did not result in a 'null'
     * 		- Check that the CMP response message tag number is '23', indicating a CMP error message
     * 		- Check that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test05UpdateWithSameKeyNotAllowed() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test07UpdateWithSameKeyNotAllowed");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "normal");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "false");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true");
        assertTrue("The CMP Authentication module was not configured correctly.", confSession.verifyProperty(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true"));
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWUPDATEWITHSAMEKEY, "false");

        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        
        
        PKIMessage req = genRenewalReq(keys, false, null, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);      
        req.getHeader().setSenderKID(new DEROctetString(nonce));
        addExtraCert(req, certificate);
        signPKIMessage(req, keys);
        assertNotNull(req);
        //******************************************''''''
        final Signature sig = Signature.getInstance(req.getHeader().getProtectionAlg().getObjectId().getId(), "BC");
        sig.initVerify(certificate.getPublicKey());
        sig.update(req.getProtectedBytes());
        boolean verified = sig.verify(req.getProtection().getBytes());
        assertTrue("Signing the message failed.", verified);
        //***************************************************
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
        
        PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(resp)).readObject());
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getTagNo());
        final String errMsg = body.getError().getPKIStatus().getStatusString().getString(0).getString();
        final String expectedErrMsg = "Invalid key. The public key in the KeyUpdateRequest is the same as the public key in the existing end entity certiticate";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test07UpdateWithSameKeyNotAllowed");
        }

    }

    /**
     * Sends a KeyUpdateRequest with a different key and the configurations is NOT to allow the use of the same keys. 
     * Successful operation is expected and a new certificate is received.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Set cmp.checkadminauthorization to 'false'
     * - Pre-configurations: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configurations: Sets cmp.allowupdatewithsamekey to 'false'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attached cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Obtains the certificate from the response
     * 		- Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test06UpdateWithDifferentKey() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test08UpdateWithDifferentKey");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "normal");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "false");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true");
        assertTrue("The CMP Authentication module was not configured correctly.", confSession.verifyProperty(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true"));
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWUPDATEWITHSAMEKEY, "false");

        
        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        
        KeyPair newkeys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        PKIMessage req = genRenewalReq(newkeys, false, null, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();
        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);      
        req.getHeader().setSenderKID(new DEROctetString(nonce));
        addExtraCert(req, certificate);
        signPKIMessage(req, keys);
        assertNotNull(req);
        //******************************************''''''
        final Signature sig = Signature.getInstance(req.getHeader().getProtectionAlg().getObjectId().getId(), "BC");
        sig.initVerify(certificate.getPublicKey());
        sig.update(req.getProtectedBytes());
        boolean verified = sig.verify(req.getProtection().getBytes());
        assertTrue("Signing the message failed.", verified);
        //***************************************************
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, true, null);
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);
        assertTrue("The new certificate's keys are incorrect.", cert.getPublicKey().equals(newkeys.getPublic()));
        assertFalse("The new certificate's keys are the same as the old certificate's keys.", cert.getPublicKey().equals(keys.getPublic()));
        
        if(log.isTraceEnabled()) {
            log.trace("<test08UpdateWithDifferentKey");
        }

    }
   
    /**
     * Sends a KeyUpdateRequest in RA mode. 
     * Successful operation is expected and a new certificate is received.
     * 
     * - Pre-configuration: Sets the operational mode to RA mode (cmp.raoperationalmode=ra)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'EndEntityCertificate'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'AdminCA1'
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Obtains the certificate from the response
     * 		- Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test07RAMode() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test09RAMode()");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "ra");
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONMODULE, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONPARAMETERS, "AdminCA1");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "true");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true");

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        PKIMessage req = genRenewalReq(keys, false, userDN, issuerDN);
        assertNotNull("Failed to generate a CMP renewal request", req);
        int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);
        req.getHeader().setSenderKID(new DEROctetString("CMPTESTPROFILE".getBytes()));
        
		createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
		KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
		Certificate admCert = signSession.createCertificate(admin, "cmpTestAdmin", "foo123", admkeys.getPublic());
		Admin adm = new Admin(admCert, "cmpTestAdmin", "cmpTestAdmin@primekey.se");
		setupAccessRights(adm);
		addExtraCert(req, admCert);
		signPKIMessage(req, admkeys);
		assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, true, null);
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);


        if(log.isTraceEnabled()) {
            log.trace("<test09RAMode()");
        }

    }

    /**
     * Sends a KeyUpdateRequest in RA mode and the request sender is not an authorized administrator. 
     * A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'EndEntityCertificate'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'AdminCA1'
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Parse the response and make sure that the parsing did not result in a 'null'
     * 		- Check that the CMP response message tag number is '23', indicating a CMP error message
     * 		- Check that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test8RAModeNonAdmin() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test10RAModeNonAdmin()");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "ra");
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONMODULE, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONPARAMETERS, "AdminCA1");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "true");

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        PKIMessage req = genRenewalReq(keys, false, userDN, issuerDN);
        assertNotNull("Failed to generate a CMP renewal request", req);
        int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);
        req.getHeader().setSenderKID(new DEROctetString("CMPTESTPROFILE".getBytes()));
        
        addExtraCert(req, certificate);
        signPKIMessage(req, keys);
        assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
        
        PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(resp)).readObject());
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getTagNo());
        final String errMsg = body.getError().getPKIStatus().getStatusString().getString(0).getString();
        final String expectedErrMsg = "\"" + userDN + "\" is not an authorized administrator.";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test10RAModeNonAdmin()");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest in RA mode without filling the 'issuerDN' field in the request. 
     * Successful operation is expected and a new certificate is received.
     * 
     * - Pre-configuration: Sets the operational mode to RA mode (cmp.raoperationalmode=ra)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'EndEntityCertificate'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'AdminCA1'
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Obtains the certificate from the response
     * 		- Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test9RANoIssuer() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test11RANoIssuer()");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "ra");
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONMODULE, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONPARAMETERS, "AdminCA1");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "true");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true");

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        PKIMessage req = genRenewalReq(keys, false, userDN, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);
        req.getHeader().setSenderKID(new DEROctetString("CMPTESTPROFILE".getBytes()));
        
		createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
		KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
		Certificate admCert = signSession.createCertificate(admin, "cmpTestAdmin", "foo123", admkeys.getPublic());
		Admin adm = new Admin(admCert, "cmpTestAdmin", "cmpTestAdmin@primekey.se");
		setupAccessRights(adm);
		addExtraCert(req, admCert);
		signPKIMessage(req, admkeys);
		assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);
        
        if(log.isTraceEnabled()) {
            log.trace("<test11RANoIssuer()");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest in RA mode with neither subjectDN nor issuerDN are set in the request. 
     * A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'EndEntityCertificate'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'AdminCA1'
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Parse the response and make sure that the parsing did not result in a 'null'
     * 		- Check that the CMP response message tag number is '23', indicating a CMP error message
     * 		- Check that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test10RANoIssuerNoSubjectDN() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test12RANoIssuerNoSubjetDN()");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "ra");
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONMODULE, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONPARAMETERS, "AdminCA1");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "true");

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        PKIMessage req = genRenewalReq(keys, false, null, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);
        req.getHeader().setSenderKID(new DEROctetString("CMPTESTPROFILE".getBytes()));
        
		createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
		KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
		Certificate admCert = signSession.createCertificate(admin, "cmpTestAdmin", "foo123", admkeys.getPublic());
		Admin adm = new Admin(admCert, "cmpTestAdmin", "cmpTestAdmin@primekey.se");
		setupAccessRights(adm);
		addExtraCert(req, admCert);
		signPKIMessage(req, admkeys);
		assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
        
        PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(resp)).readObject());
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getTagNo());
        final String errMsg = body.getError().getPKIStatus().getStatusString().getString(0).getString();
        final String expectedErrMsg = "Cannot find a SubjectDN in the request";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test12RANoIssuerNoSubjectDN()");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest in RA mode when there are more than one authentication module configured. 
     * Successful operation is expected and a new certificate is received.
     * 
     * - Pre-configuration: Sets the operational mode to RA mode (cmp.raoperationalmode=ra)
     * - Pre-configuration: Sets the cmp.authenticationmodule to "HMAC;DnPartPwd;EndEntityCertificate"
     * - Pre-configuration: Sets the cmp.authenticationparameters to "-;OU;AdminCA1"
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Obtains the certificate from the response
     * 		- Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test11RAMultipleAuthenticationModules() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test13RAMultipleAuthenticationModules");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "ra");
        String authmodules = CmpConfiguration.AUTHMODULE_HMAC + ";" + CmpConfiguration.AUTHMODULE_DN_PART_PWD + ";" + CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE;
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONMODULE, authmodules);
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONPARAMETERS, "-;OU;AdminCA1");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "true");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true");

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        PKIMessage req = genRenewalReq(keys, false, userDN, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);
        req.getHeader().setSenderKID(new DEROctetString("CMPTESTPROFILE".getBytes()));
        
		createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
		KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
		Certificate admCert = signSession.createCertificate(admin, "cmpTestAdmin", "foo123", admkeys.getPublic());
		Admin adm = new Admin(admCert, "cmpTestAdmin", "cmpTestAdmin@primekey.se");
		setupAccessRights(adm);
		addExtraCert(req, admCert);
		signPKIMessage(req, admkeys);
		assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);
        
        if(log.isTraceEnabled()) {
            log.trace("<test13RAMultipleAuthenticationModules()");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest in RA mode when the authentication module is NOT set to 'EndEntityCertificate'. 
     * Successful operation is expected and a new certificate is received.
     * 
     * - Pre-configuration: Sets the operational mode to RA mode (cmp.raoperationalmode=ra)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'HMAC'
     * - Pre-configuration: Sets the cmp.authenticationparameters to '-'
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Obtains the certificate from the response
     * 		- Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test12RANoCA() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test14RANoCA()");
        }
        
        updatePropertyOnServer(CmpConfiguration.CONFIG_OPERATIONMODE, "ra");
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONMODULE, CmpConfiguration.AUTHMODULE_DN_PART_PWD);
        updatePropertyOnServer(CmpConfiguration.CONFIG_AUTHENTICATIONPARAMETERS, "OU");
        updatePropertyOnServer(CmpConfiguration.CONFIG_CHECKADMINAUTHORIZATION, "true");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWAUTOMATICKEYUPDATE, "true");
        updatePropertyOnServer(CmpConfiguration.CONFIG_ALLOWUPDATEWITHSAMEKEY, "true");

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        PKIMessage req = genRenewalReq(keys, false, userDN, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        req.getHeader().setProtectionAlg(pAlg);
        req.getHeader().setSenderKID(new DEROctetString("CMPTESTPROFILE".getBytes()));
        
		createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
		KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
		Certificate admCert = signSession.createCertificate(admin, "cmpTestAdmin", "foo123", admkeys.getPublic());
		Admin adm = new Admin(admCert, "cmpTestAdmin", "cmpTestAdmin@primekey.se");
		setupAccessRights(adm);
		addExtraCert(req, admCert);
		signPKIMessage(req, admkeys);
		assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null);
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);

        if(log.isTraceEnabled()) {
            log.trace("<test14RANoCA()");
        }

    }

    public void setUp() throws Exception {
    	super.setUp();
    }
    
    public void tearDown() throws Exception {

        super.tearDown();
        
        try {
            userAdminSession.revokeAndDeleteUser(admin, username, ReasonFlags.unused);
            userAdminSession.revokeAndDeleteUser(admin, "fakeuser", ReasonFlags.unused);

        } catch(Exception e){}
        
        boolean cleanUpOk = true;
        if (!confSession.restoreConfiguration()) {
            cleanUpOk = false;
        }
        assertTrue("Unable to clean up properly.", cleanUpOk);
    }
    
    
    private void setCAID() throws CADoesntExistsException, AuthorizationDeniedException {
        // Try to use AdminCA1 if it exists
        CAInfo adminca1 = caAdminSession.getCAInfo(admin, "AdminCA1");

        if (adminca1 == null) {
            final Collection<Integer> caids;

            caids = caSession.getAvailableCAs(admin);
            final Iterator<Integer> iter = caids.iterator();
            int tmp = 0;
            while (iter.hasNext()) {
                tmp = iter.next().intValue();
                if(tmp != 0)    break;
            }
            caid = tmp;
        } else {
            caid = adminca1.getCAId();
        }
        if (caid == 0) {
            assertTrue("No active CA! Must have at least one active CA to run tests!", false);
        }
 
    }
    
    private void setCaCert() throws CADoesntExistsException, AuthorizationDeniedException {
        final CAInfo cainfo;

        cainfo = caAdminSession.getCAInfo(admin, caid);

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
    }
    
    private void addExtraCert(PKIMessage msg, Certificate cert) throws CertificateEncodingException, IOException{
        ByteArrayInputStream    bIn = new ByteArrayInputStream(cert.getEncoded());
        ASN1InputStream         dIn = new ASN1InputStream(bIn);
        ASN1Sequence extraCertSeq = (ASN1Sequence)dIn.readObject();
        X509CertificateStructure extraCert = new X509CertificateStructure(ASN1Sequence.getInstance(extraCertSeq));
        msg.addExtraCert(extraCert);
    }
    
    private void signPKIMessage(PKIMessage msg, KeyPair keys) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
        final Signature sig = Signature.getInstance(PKCSObjectIdentifiers.sha1WithRSAEncryption.getId(), "BC");
        sig.initSign(keys.getPrivate());
        sig.update(msg.getProtectedBytes());
        byte[] eeSignature = sig.sign();            
        msg.setProtection(new DERBitString(eeSignature));   
    }
    
    private UserDataVO createUser(String username, String subjectDN, String password) throws AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, 
                WaitingForApprovalException, EjbcaException, Exception {

        UserDataVO user = new UserDataVO(username, subjectDN, caid, null, username+"@primekey.se", SecConst.USER_ENDUSER, SecConst.EMPTY_ENDENTITYPROFILE,
        SecConst.CERTPROFILE_FIXED_ENDUSER, SecConst.TOKEN_SOFT_PEM, 0, null);
        user.setPassword(password);
        try {
            //userAdminSession. addUser(ADMIN, user, true);
            userAdminSession.addUser(admin, username, password, subjectDN, "rfc822name=" + username + "@primekey.se", username + "@primekey.se",
                    true, SecConst.EMPTY_ENDENTITYPROFILE, SecConst.CERTPROFILE_FIXED_ENDUSER, SecConst.USER_ENDUSER, SecConst.TOKEN_SOFT_PEM, 0,
                    caid);
            log.debug("created user: " + username);
        } catch (Exception e) {
            log.debug("User " + username + " already exists. Setting the user status to NEW");
            userAdminSession.changeUser(admin, user, true);
            userAdminSession.setUserStatus(admin, username, UserDataConstants.STATUS_NEW);
            log.debug("Reset status to NEW");
        }

        return user;

    }
    
    private PKIMessage genRenewalReq(KeyPair keys, boolean raVerifiedPopo, String reqSubjectDN, String reqIssuerDN) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, 
                        InvalidKeyException, SignatureException {
        
        CertTemplate myCertTemplate = new CertTemplate();
        
        OptionalValidity myOptionalValidity = new OptionalValidity();
        org.bouncycastle.asn1.x509.Time nb = new org.bouncycastle.asn1.x509.Time(new DERGeneralizedTime("20030211002120Z"));
        org.bouncycastle.asn1.x509.Time na = new org.bouncycastle.asn1.x509.Time(new Date());
        myOptionalValidity.setNotBefore(nb);
        myOptionalValidity.setNotAfter(na);
        myCertTemplate.setValidity(myOptionalValidity);
        
        if(reqSubjectDN != null) {
        	myCertTemplate.setSubject(new X509Name(reqSubjectDN));
        }
        
        if(reqIssuerDN != null) {
        	myCertTemplate.setIssuer(new X509Name(reqIssuerDN));
        }
        
        byte[] bytes = keys.getPublic().getEncoded();
        ByteArrayInputStream bIn = new ByteArrayInputStream(bytes);
        ASN1InputStream dIn = new ASN1InputStream(bIn);
        SubjectPublicKeyInfo keyInfo = new SubjectPublicKeyInfo((ASN1Sequence) dIn.readObject());
        myCertTemplate.setPublicKey(keyInfo);

        CertRequest myCertRequest = new CertRequest(new DERInteger(4), myCertTemplate);
        // myCertRequest.addControls(new
        // AttributeTypeAndValue(CRMFObjectIdentifiers.regInfo_utf8Pairs, new
        // DERInteger(12345)));
        CertReqMsg myCertReqMsg = new CertReqMsg(myCertRequest);

        // POPO
        /*
         * PKMACValue myPKMACValue = new PKMACValue( new AlgorithmIdentifier(new
         * DERObjectIdentifier("8.2.1.2.3.4"), new DERBitString(new byte[] { 8,
         * 1, 1, 2 })), new DERBitString(new byte[] { 12, 29, 37, 43 }));
         * 
         * POPOPrivKey myPOPOPrivKey = new POPOPrivKey(new DERBitString(new
         * byte[] { 44 }), 2); //take choice pos tag 2
         * 
         * POPOSigningKeyInput myPOPOSigningKeyInput = new POPOSigningKeyInput(
         * myPKMACValue, new SubjectPublicKeyInfo( new AlgorithmIdentifier(new
         * DERObjectIdentifier("9.3.3.9.2.2"), new DERBitString(new byte[] { 2,
         * 9, 7, 3 })), new byte[] { 7, 7, 7, 4, 5, 6, 7, 7, 7 }));
         */
        ProofOfPossession myProofOfPossession = null;
        if (raVerifiedPopo) {
            // raVerified POPO (meaning there is no POPO)
            myProofOfPossession = new ProofOfPossession(new DERNull(), 0);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DEROutputStream mout = new DEROutputStream(baos);
            mout.writeObject(myCertRequest);
            mout.close();
            byte[] popoProtectionBytes = baos.toByteArray();
            Signature sig = Signature.getInstance(PKCSObjectIdentifiers.sha1WithRSAEncryption.getId(), "BC");
            sig.initSign(keys.getPrivate());
            sig.update(popoProtectionBytes);

            DERBitString bs = new DERBitString(sig.sign());

            POPOSigningKey myPOPOSigningKey = new POPOSigningKey(new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption), bs);
            // myPOPOSigningKey.setPoposkInput( myPOPOSigningKeyInput );
            myProofOfPossession = new ProofOfPossession(myPOPOSigningKey, 1);
        }

        myCertReqMsg.setPop(myProofOfPossession);
        // myCertReqMsg.addRegInfo(new AttributeTypeAndValue(new
        // DERObjectIdentifier("1.3.6.2.2.2.2.3.1"), new
        // DERInteger(1122334455)));
        AttributeTypeAndValue av = new AttributeTypeAndValue(CRMFObjectIdentifiers.regCtrl_regToken, new DERUTF8String("foo123"));
        myCertReqMsg.addRegInfo(av);

        CertReqMessages myCertReqMessages = new CertReqMessages(myCertReqMsg);
        // myCertReqMessages.addCertReqMsg(myCertReqMsg);

        // log.debug("CAcert subject name: "+cacert.getSubjectDN().getName());
        PKIHeader myPKIHeader = new PKIHeader(new DERInteger(2), new GeneralName(new X509Name(userDN)), new GeneralName(new X509Name(
                ((X509Certificate) cacert).getSubjectDN().getName())));
        myPKIHeader.setMessageTime(new DERGeneralizedTime(new Date()));
        // senderNonce
        myPKIHeader.setSenderNonce(new DEROctetString(nonce));
        // TransactionId
        myPKIHeader.setTransactionID(new DEROctetString(transid));
        // myPKIHeader.setRecipNonce(new DEROctetString(new
        // String("RecipNonce").getBytes()));
        // PKIFreeText myPKIFreeText = new PKIFreeText(new
        // DERUTF8String("hello"));
        // myPKIFreeText.addString(new DERUTF8String("free text string"));
        // myPKIHeader.setFreeText(myPKIFreeText);

        PKIBody myPKIBody = new PKIBody(myCertReqMessages, 7); // Key Update Request
        PKIMessage myPKIMessage = new PKIMessage(myPKIHeader, myPKIBody);
        
        return myPKIMessage;

    }
    
    private X509Certificate checkKurCertRepMessage(String userDN, Certificate cacert, byte[] retMsg, int requestId) throws IOException,
                        CertificateException {
        // Parse response message
        PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(retMsg)).readObject());
        assertNotNull(respObject);

        PKIBody body = respObject.getBody();
        int tag = body.getTagNo();
        assertEquals(8, tag);
        CertRepMessage c = body.getKup();
        assertNotNull(c);
        CertResponse resp = c.getResponse(0);
        assertNotNull(resp);
        assertEquals(resp.getCertReqId().getValue().intValue(), requestId);
        PKIStatusInfo info = resp.getStatus();
        assertNotNull(info);
        assertEquals(0, info.getStatus().getValue().intValue());
        CertifiedKeyPair kp = resp.getCertifiedKeyPair();
        assertNotNull(kp);
        CertOrEncCert cc = kp.getCertOrEncCert();
        assertNotNull(cc);
        X509CertificateStructure struct = cc.getCertificate();
        assertNotNull(struct);
        checkDN(userDN, struct.getSubject());
        assertEquals(CertTools.stringToBCDNString(struct.getIssuer().toString()), CertTools.getSubjectDN(cacert));
        return (X509Certificate) CertTools.getCertfromByteArray(struct.getEncoded());
    }
    
    private void setupAccessRights(Admin adm) throws Exception {
        
    	boolean adminExists = false;
    	AdminGroup admingroup = adminGroupSession.getAdminGroup(adm, AdminGroup.TEMPSUPERADMINGROUP);
    	Iterator<AdminEntity> iter = admingroup.getAdminEntities().iterator();
    	while (iter.hasNext()) {
    		AdminEntity adminEntity = iter.next();
    		if (adminEntity.getMatchValue().equals(adm.getUsername())) {
    			adminExists = true;
            }
    	}

    	if (!adminExists) {
    		List<AdminEntity> list = new ArrayList<AdminEntity>();
    		list.add(new AdminEntity(AdminEntity.WITH_COMMONNAME, AdminEntity.TYPE_EQUALCASE, adm.getUsername(), caid));
    		adminEntitySession.addAdminEntities(adm, AdminGroup.TEMPSUPERADMINGROUP, list);
    		authorizationSession.forceRuleUpdate(adm);
    	}
    	
    	BatchMakeP12 batch = new BatchMakeP12();
    	batch.setMainStoreDir("p12");
    	batch.createAllNew();
    }

}
