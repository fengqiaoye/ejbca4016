package org.ejbca.core.model.util;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.apache.log4j.Logger;
import org.ejbca.core.ejb.ca.auth.AuthenticationSession;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSession;
import org.ejbca.core.ejb.ca.sign.SignSession;
import org.ejbca.core.ejb.keyrecovery.KeyRecoverySession;
import org.ejbca.core.ejb.ra.UserAdminSession;
import org.ejbca.core.model.keyrecovery.KeyRecoveryData;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.util.CertTools;
import org.ejbca.util.keystore.KeyTools;

/** Class that has helper methods to generate tokens for users in ejbca. 
 * Generating tokens can often depend on the ejb services (local interfaces), for example for key recovery.
 * 
 * @author Tomas Gustavsson
 * @version $Id: GenerateToken.java 9766 2010-08-31 14:20:21Z anatom $
 */
public class GenerateToken {
    private static final Logger log = Logger.getLogger(GenerateToken.class);

	private AuthenticationSession authenticationSession;
	private UserAdminSession userAdminSession;
	private CAAdminSession caAdminSession;
	private KeyRecoverySession keyRecoverySession;
	private SignSession signSession;
	
    public GenerateToken(AuthenticationSession authenticationSession, UserAdminSession useradminsession, CAAdminSession caAdminSession, KeyRecoverySession keyRecoverySession, SignSession signSession) {
    	this.authenticationSession = authenticationSession;
    	this.userAdminSession = useradminsession;
    	this.caAdminSession = caAdminSession;
    	this.keyRecoverySession = keyRecoverySession;
    	this.signSession = signSession;
    }
    
    /**
     * This method generates a new pkcs12 or jks token for a user, and key recovers the token, if the user is configured for that in EJBCA.
     * 
     * @param administrator administrator performing the action
     * @param username username in ejbca
     * @param password password for user
     * @param caid caid of the CA the user is registered for
     * @param keyspec name of ECDSA key or length of RSA and DSA keys  
     * @param keyalg AlgorithmConstants.KEYALGORITHM_RSA, AlgorithmConstants.KEYALGORITHM_DSA or AlgorithmConstants.KEYALGORITHM_ECDSA
     * @param createJKS true to create a JKS, false to create a PKCS12
     * @param loadkeys true if keys should be recovered
     * @param savekeys true if generated keys should be stored for keyrecovery
     * @param reusecertificate true if the old certificate should be reused for a recovered key
     * @param endEntityProfileId the end entity profile the user is registered for
     * @return KeyStore
     * @throws Exception if something goes wrong...
     */
    public KeyStore generateOrKeyRecoverToken(Admin administrator, String username, String password, int caid, String keyspec, 
    		String keyalg, boolean createJKS, boolean loadkeys, boolean savekeys, boolean reusecertificate, int endEntityProfileId)
    throws Exception {
    	log.trace(">generateOrKeyRecoverToken");
    	KeyRecoveryData keyData = null;
    	KeyPair rsaKeys = null;
    	if (loadkeys) {
    		log.debug("Recovering keys for user: "+ username);
            // used saved keys.
			keyData = keyRecoverySession.keyRecovery(administrator, username, endEntityProfileId);
    		if (keyData == null) {
    			throw new Exception("No key recovery data exists for user");
    		}
    		rsaKeys = keyData.getKeyPair();
    		if (reusecertificate) {
    			// TODO: Why is this only done is reusecertificate == true ??
        		log.debug("Re-using old certificate for user: "+ username);
    			keyRecoverySession.unmarkUser(administrator,username);
    		}
    	} else {
    		log.debug("Generating new keys for user: "+ username);
            // generate new keys.
    		rsaKeys = KeyTools.genKeys(keyspec, keyalg);
    	}
    	X509Certificate cert = null;
    	if ((reusecertificate) && (keyData != null)) {
    		cert = (X509Certificate) keyData.getCertificate();
    		boolean finishUser = true;
			finishUser = caAdminSession.getCAInfo(administrator,caid).getFinishUser();
    		if (finishUser) {
    			UserDataVO userdata = userAdminSession.findUser(administrator, username);
				authenticationSession.finishUser(userdata);    				
    		}
    	} else {
    		log.debug("Generating new certificate for user: "+ username);
			cert = (X509Certificate)signSession.createCertificate(administrator, username, password, rsaKeys.getPublic());
    	}
        // Make a certificate chain from the certificate and the CA-certificate
    	Certificate[] cachain = (Certificate[])signSession.getCertificateChain(administrator, caid).toArray(new Certificate[0]);
        // Verify CA-certificate
    	Certificate rootcert = cachain[cachain.length - 1];
    	if (CertTools.isSelfSigned(rootcert)) {
    		try {
    			rootcert.verify(rootcert.getPublicKey());
    		} catch (GeneralSecurityException se) {
    			throw new Exception("RootCA certificate does not verify, issuerDN: "+CertTools.getIssuerDN(rootcert)+", subjectDN: "+CertTools.getSubjectDN(rootcert));
    		}
    	} else {
    		throw new Exception("RootCA certificate not self-signed, issuerDN: "+CertTools.getIssuerDN(rootcert)+", subjectDN: "+CertTools.getSubjectDN(rootcert));
    	}
        // Verify that the user-certificate is signed by our CA
    	Certificate cacert = cachain[0];
    	try {
    		cert.verify(cacert.getPublicKey());
    	} catch (GeneralSecurityException se) {
    		throw new Exception("Generated certificate does not verify using CA-certificate, issuerDN: "+CertTools.getIssuerDN(cert)+", subjectDN: "+CertTools.getSubjectDN(cert)+
    				"caIssuerDN: "+CertTools.getIssuerDN(cacert)+", caSubjectDN: "+CertTools.getSubjectDN(cacert));
    	}
    	if (savekeys) {
            // Save generated keys to database.
    		log.debug("Saving generated keys for recovery for user: "+ username);
			keyRecoverySession.addKeyRecoveryData(administrator, cert, username, rsaKeys);
    	}
        //  Use CN if as alias in the keystore, if CN is not present use username
    	String alias = CertTools.getPartFromDN(CertTools.getSubjectDN(cert), "CN");
    	if (alias == null) {
    		alias = username;
    	}
        // Store keys and certificates in keystore.
    	KeyStore ks = null;
    	if (createJKS) {
    		log.debug("Generating JKS for user: "+ username);
    		ks = KeyTools.createJKS(alias, rsaKeys.getPrivate(), password, cert, cachain);
    	} else {
    		log.debug("Generating PKCS12 for user: "+ username);
    		ks = KeyTools.createP12(alias, rsaKeys.getPrivate(), cert, cachain);
    	}
    	log.trace("<generateOrKeyRecoverToken");
    	return ks;
    }
}
