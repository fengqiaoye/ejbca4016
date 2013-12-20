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
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.ocsp.BasicOCSPResponse;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.cms.CMSSignedGenerator;
import org.bouncycastle.ocsp.BasicOCSPResp;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.ca.SignRequestException;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.protocol.FailInfo;
import org.ejbca.core.protocol.IResponseMessage;
import org.ejbca.core.protocol.ResponseStatus;
import org.ejbca.util.Base64;
import org.ejbca.util.CertTools;

import com.novosec.pkix.asn1.cmp.CMPObjectIdentifiers;
import com.novosec.pkix.asn1.cmp.CertRepMessage;
import com.novosec.pkix.asn1.cmp.CertResponse;
import com.novosec.pkix.asn1.cmp.PKIBody;
import com.novosec.pkix.asn1.cmp.PKIHeader;
import com.novosec.pkix.asn1.cmp.PKIMessage;
import com.novosec.pkix.asn1.cmp.PKIStatusInfo;
import com.novosec.pkix.asn1.crmf.PBMParameter;

/**
 * Helper class to create different standard parts of CMP messages
 * 
 * @author tomas
 * @version $Id: CmpMessageHelper.java 12569 2011-09-14 10:21:15Z anatom $
 */
public class CmpMessageHelper {
	private static final Logger LOG = Logger.getLogger(CmpMessageHelper.class);
    private static final InternalResources INTRES = InternalResources.getInstance();

	private static final String CMP_ERRORGENERAL = "cmp.errorgeneral";

	public static PKIHeader createPKIHeader(final GeneralName sender, final GeneralName recipient, final String senderNonce, final String recipientNonce, final String transactionId) {
		final PKIHeader myPKIHeader =
			new PKIHeader(
					new DERInteger(2),
					sender,
					recipient);
		myPKIHeader.setMessageTime(new DERGeneralizedTime(new Date()));
		if (senderNonce != null) {
			myPKIHeader.setSenderNonce(new DEROctetString(Base64.decode(senderNonce.getBytes())));					
		}
		if (recipientNonce != null) {
			myPKIHeader.setRecipNonce(new DEROctetString(Base64.decode(recipientNonce.getBytes())));
		}
		if (transactionId != null) {
			myPKIHeader.setTransactionID(new DEROctetString(Base64.decode(transactionId.getBytes())));
		}
		return myPKIHeader;
	}

    public static byte[] signPKIMessage(final PKIMessage myPKIMessage, final X509Certificate signCert, final PrivateKey signKey, final String digestAlg, final String provider) throws InvalidKeyException, NoSuchProviderException, NoSuchAlgorithmException, SecurityException, SignatureException, IOException, CertificateEncodingException {
    	if (LOG.isTraceEnabled()) {
    		LOG.trace(">signPKIMessage()");
    	}
    	final X509CertificateStructure signStruct = X509CertificateStructure.getInstance(new ASN1InputStream(new ByteArrayInputStream(signCert.getEncoded())).readObject());
		CmpMessageHelper.buildCertBasedPKIProtection( myPKIMessage, signStruct, signKey, digestAlg, provider);
    	if (LOG.isTraceEnabled()) {
    		LOG.trace("<signPKIMessage()");
    	}
		// Return response as byte array 
		return CmpMessageHelper.pkiMessageToByteArray(myPKIMessage);
    }
    
	public static void buildCertBasedPKIProtection(final PKIMessage pKIMessage, final X509CertificateStructure cert, final PrivateKey key, final String digestAlg, final String provider )
	throws NoSuchProviderException, NoSuchAlgorithmException, SecurityException, SignatureException, InvalidKeyException
	{
		// Select which signature algorithm we should use for the response, based on the digest algorithm.
		DERObjectIdentifier oid = PKCSObjectIdentifiers.sha1WithRSAEncryption;
		if (digestAlg.equals(CMSSignedGenerator.DIGEST_SHA256)) {
			oid = PKCSObjectIdentifiers.sha256WithRSAEncryption;			
		}
		if (digestAlg.equals(CMSSignedGenerator.DIGEST_MD5)) {
			oid = PKCSObjectIdentifiers.md5WithRSAEncryption;			
		}
    	if (LOG.isDebugEnabled()) {
    		LOG.debug("Selected signature alg oid: "+oid.getId());
    	}
    	// According to PKCS#1 AlgorithmIdentifier for RSA-PKCS#1 has null Parameters, this means a DER Null (asn.1 encoding of null), not Java null.
    	// For the RSA signature algorithms specified above RFC3447 states "...the parameters MUST be present and MUST be NULL."
		pKIMessage.getHeader().setProtectionAlg(new AlgorithmIdentifier(oid, new DERNull()));
		// Most PKCS#11 providers don't like to be fed an OID as signature algorithm, so 
		// we use BC classes to translate it into a signature algorithm name instead
		final String sigAlg = new BasicOCSPResp(new BasicOCSPResponse(null, new AlgorithmIdentifier(oid), null, null)).getSignatureAlgName();
    	if (LOG.isDebugEnabled()) {
    		LOG.debug("Signing CMP message with signature alg: "+sigAlg);
    	}
    	final Signature sig = Signature.getInstance(sigAlg , provider );
		sig.initSign(key);
		sig.update( pKIMessage.getProtectedBytes() );
		
		pKIMessage.setProtection( new DERBitString(sig.sign()) );
		pKIMessage.addExtraCert( cert );
	}
	
	/** verifies signature protection on CMP PKI messages
	 *  
	 * @param pKIMessage the CMP message to verify signature on, if protected by signature protection
	 * @param pubKey the public key used to verify the signature
	 * @return true if verification is ok or false if verification fails
	 * @throws NoSuchAlgorithmException message is signed by an unknown algorithm
	 * @throws NoSuchProviderException the BouncyCastle (BC) provider is not installed
	 * @throws InvalidKeyException pubKey is not valid for signature verification
	 * @throws SignatureException if the passed-in signature is improperly encoded or of the wrong type, if this signature algorithm is unable to process the input data provided, etc.
	 */
	public static boolean verifyCertBasedPKIProtection(final PKIMessage pKIMessage, final PublicKey pubKey) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
		final AlgorithmIdentifier sigAlg = pKIMessage.getHeader().getProtectionAlg();
		if (LOG.isDebugEnabled()) {
			LOG.debug("Verifying signature with algorithm: "+sigAlg.getObjectId().getId());
		}
		final Signature sig = Signature.getInstance(sigAlg.getObjectId().getId(), "BC");
		sig.initVerify(pubKey);
		sig.update(pKIMessage.getProtectedBytes());
		final boolean result = sig.verify(pKIMessage.getProtection().getBytes());
		if (LOG.isDebugEnabled()) {
			LOG.debug("Verification result: "+result);
		}
		return result;
	}
	
	public static byte[] protectPKIMessageWithPBE(final PKIMessage msg, final String keyId, final String raSecret, final String digestAlgId, final String macAlgId, final int iterationCount) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, IOException {
    	if (LOG.isTraceEnabled()) {
    		LOG.trace(">protectPKIMessageWithPBE()");
    	}
		// Create the PasswordBased protection of the message
    	final PKIHeader head = msg.getHeader();
		byte[] keyIdBytes;
		try {
			keyIdBytes = keyId.getBytes("UTF-8");			
		} catch (UnsupportedEncodingException e) {
			keyIdBytes = keyId.getBytes();
			LOG.info("UTF-8 not available, using platform default encoding for keyIdBytes.");
		}
		head.setSenderKID(new DEROctetString(keyIdBytes));
		// SHA1
		//AlgorithmIdentifier owfAlg = new AlgorithmIdentifier("1.3.14.3.2.26");
		final AlgorithmIdentifier owfAlg = new AlgorithmIdentifier(digestAlgId);
		// iterations, usually something like 1024
		final DERInteger iteration = new DERInteger(iterationCount);
		// HMAC/SHA1
		//AlgorithmIdentifier macAlg = new AlgorithmIdentifier("1.2.840.113549.2.7");
		final AlgorithmIdentifier macAlg = new AlgorithmIdentifier(macAlgId);
		// We need some random bytes for the nonce
		final byte[] saltbytes = createSenderNonce();
		final DEROctetString derSalt = new DEROctetString(saltbytes);
		
		// Create the new protected return message
		//String objectId = "1.2.840.113533.7.66.13" = passwordBasedMac;
		final String objectId = CMPObjectIdentifiers.passwordBasedMac.getId();
		final PBMParameter pp = new PBMParameter(derSalt, owfAlg, iteration, macAlg);
		final AlgorithmIdentifier pAlg = new AlgorithmIdentifier(new DERObjectIdentifier(objectId), pp);
		head.setProtectionAlg(pAlg);
		final PKIBody body = msg.getBody();
		final PKIMessage ret = new PKIMessage(head, body);

		// Calculate the protection bits
		final byte[] rasecret = raSecret.getBytes();
		byte[] basekey = new byte[rasecret.length + saltbytes.length];
		for (int i = 0; i < rasecret.length; i++) {
			basekey[i] = rasecret[i];
		}
		for (int i = 0; i < saltbytes.length; i++) {
			basekey[rasecret.length+i] = saltbytes[i];
		}
		// Construct the base key according to rfc4210, section 5.1.3.1
		final MessageDigest dig = MessageDigest.getInstance(owfAlg.getObjectId().getId(), "BC");
		for (int i = 0; i < iterationCount; i++) {
			basekey = dig.digest(basekey);
			dig.reset();
		}
		// Do the mac
		final String macOid = macAlg.getObjectId().getId();
		final byte[] protectedBytes = ret.getProtectedBytes();
		final Mac mac = Mac.getInstance(macOid, "BC");
		final SecretKey key = new SecretKeySpec(basekey, macOid);
		mac.init(key);
		mac.reset();
		mac.update(protectedBytes, 0, protectedBytes.length);
		final byte[] out = mac.doFinal();
		final DERBitString bs = new DERBitString(out);

		// Finally store the protection bytes in the msg
		ret.setProtection(bs);
		
    	if (LOG.isTraceEnabled()) {
    		LOG.trace("<protectPKIMessageWithPBE()");
    	}
		// Return response as byte array 
		return CmpMessageHelper.pkiMessageToByteArray(ret);
	}

	public static byte[] pkiMessageToByteArray(final PKIMessage msg) throws IOException {
		// Return response as byte array 
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final DEROutputStream mout = new DEROutputStream( baos );
		mout.writeObject( msg );
		mout.close();
		return baos.toByteArray();
	}

	/** Creates a 16 bytes random sender nonce
	 * 
	 * @return byte array of length 16
	 */
	public static byte[] createSenderNonce() {
    	// Sendernonce is a random number
		final byte[] senderNonce = new byte[16];
        final Random randomSource = new Random();
        randomSource.nextBytes(senderNonce);
    	return senderNonce;
	}

	/**
	 * creates a very simple error message in response to msg (that's why we switch sender and recipient)
	 * @param msg
	 * @param status
	 * @param failInfo
	 * @param failText
	 * @return IResponseMessage that can be sent to user
	 */
	public static IResponseMessage createUnprotectedErrorMessage(final BaseCmpMessage msg, final ResponseStatus status, final FailInfo failInfo, final String failText) {
		// Create a failure message
		if (LOG.isDebugEnabled()) {
			LOG.debug("Creating an unprotected error message with status="+status.getValue()+", failInfo="+failInfo+", failText="+failText);
		}
		final CmpErrorResponseMessage resp = new CmpErrorResponseMessage();
		resp.setSenderNonce(new String(Base64.encode(CmpMessageHelper.createSenderNonce())));
		if (msg != null) {
			resp.setRecipientNonce(msg.getSenderNonce());
			resp.setSender(msg.getRecipient());
			resp.setRecipient(msg.getSender());
			resp.setTransactionId(msg.getTransactionId());			
		} else {
			// We didn't even have a request the get these from, so send back some dummy values
			resp.setSender(new GeneralName(CertTools.stringToBcX509Name("CN=Failure Sender")));
			resp.setRecipient(new GeneralName(CertTools.stringToBcX509Name("CN=Failure Recipient")));
		}
		resp.setFailInfo(failInfo);
		resp.setStatus( status);
		resp.setFailText(failText);
		try {
			resp.create();
		} catch (InvalidKeyException e) {
			LOG.error("Exception during CMP processing: ", e);			
		} catch (NoSuchAlgorithmException e) {
			LOG.error("Exception during CMP processing: ", e);			
		} catch (NoSuchProviderException e) {
			LOG.error("Exception during CMP processing: ", e);			
		} catch (SignRequestException e) {
			LOG.error("Exception during CMP processing: ", e);			
		} catch (NotFoundException e) {
			LOG.error("Exception during CMP processing: ", e);			
		} catch (IOException e) {
			LOG.error("Exception during CMP processing: ", e);			
		}
		return resp;
	}
	
	/**
	 * creates a simple error message in response to msg.
	 * 
	 * The protection paramters can be null to create an unprotected message
	 * 
	 * @return IResponseMessage that can be sent to user
	 */
	public static CmpErrorResponseMessage createErrorMessage(final BaseCmpMessage msg, final FailInfo failInfo, final String failText, final int requestId, final int requestType, final CmpPbeVerifyer verifyer, final String keyId, final String responseProt) {
		final CmpErrorResponseMessage cresp = new CmpErrorResponseMessage();
		cresp.setRecipientNonce(msg.getSenderNonce());
		cresp.setSenderNonce(new String(Base64.encode(CmpMessageHelper.createSenderNonce())));
		cresp.setSender(msg.getRecipient());
		cresp.setRecipient(msg.getSender());
		cresp.setTransactionId(msg.getTransactionId());
		cresp.setFailText(failText);
		cresp.setFailInfo(failInfo);
		cresp.setRequestId(requestId);
		cresp.setRequestType(requestType);
		// Set all protection parameters, this is another message than if we generated a cert above
		if(verifyer != null) {
			final String pbeDigestAlg = verifyer.getOwfOid();
			final String pbeMacAlg = verifyer.getMacOid();
			final int pbeIterationCount = verifyer.getIterationCount();
			final String raAuthSecret = verifyer.getLastUsedRaSecret();
			if (StringUtils.equals(responseProt, "pbe") && (pbeDigestAlg != null) && (pbeMacAlg != null) && (keyId != null) && (raAuthSecret != null) ) {
				cresp.setPbeParameters(keyId, raAuthSecret, pbeDigestAlg, pbeMacAlg, pbeIterationCount);
			}
		}
		try {
			// Here we need to create the response message, when coming from SignSession it has already been "created"
			cresp.create();
		} catch (InvalidKeyException e) {
			LOG.error(INTRES.getLocalizedMessage(CMP_ERRORGENERAL), e);
		} catch (NoSuchAlgorithmException e) {
			LOG.error(INTRES.getLocalizedMessage(CMP_ERRORGENERAL), e);
		} catch (NoSuchProviderException e) {
			LOG.error(INTRES.getLocalizedMessage(CMP_ERRORGENERAL), e);
		} catch (SignRequestException e) {
			LOG.error(INTRES.getLocalizedMessage(CMP_ERRORGENERAL), e);
		} catch (NotFoundException e) {
			LOG.error(INTRES.getLocalizedMessage(CMP_ERRORGENERAL), e);
		} catch (IOException e) {
			LOG.error(INTRES.getLocalizedMessage(CMP_ERRORGENERAL), e);
		}
		return cresp;
	}
	
	/**
	 * creates a very simple error message in response to msg (that's why we switch sender and recipient)
	 * @param msg
	 * @param status
	 * @param failInfo
	 * @param failText
	 * @return IResponseMessage that can be sent to user
	 */
	public static PKIBody createCertRequestRejectBody(final PKIHeader header, final PKIStatusInfo info, final int requestId, final int requestType) {
		// Create a failure message
		if (LOG.isDebugEnabled()) {
			LOG.debug("Creating a cert request rejection message");
			LOG.debug("Creating a CertRepMessage 'rejected'");
		}

		/*
		String senderNonce = new String(Base64.encode(CmpMessageHelper.createSenderNonce()));
		String rcptNonce = null;
		X509Name sender = CertTools.stringToBcX509Name("CN=Failure Sender");
		X509Name rcpt = CertTools.stringToBcX509Name("CN=Failure Recipient");
		String transactionId = msg.getTransactionId();
		PKIHeader myPKIHeader = CmpMessageHelper.createPKIHeader(sender, rcpt, senderNonce, rcptNonce, transactionId);
		*/
		
		final CertResponse myCertResponse = new CertResponse(new DERInteger(requestId), info);
		final CertRepMessage myCertRepMessage = new CertRepMessage(myCertResponse);

		final int respType = requestType + 1; // 1 = intitialization response, 3 = certification response etc
		if (LOG.isDebugEnabled()) {
			LOG.debug("Creating response body of type "+respType);
		}
		final PKIBody myPKIBody = new PKIBody(myCertRepMessage, respType); 
		
		return myPKIBody;
	}
}
