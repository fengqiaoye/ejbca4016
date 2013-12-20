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

package org.ejbca.core.model.util;

import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.util.CertTools;
import org.ejbca.util.keystore.KeyTools;

/**
 * Various helper methods for handling the mappings between different key and 
 * signature algorithms.
 * 
 * This class has to be updated when new key or signature algorithms are 
 * added to EJBCA.
 *
 * @see AlgorithmConstants
 * @see CertTools#getSignatureAlgorithm
 * @see KeyTools#getKeyLength
 * 
 * @version $Id: AlgorithmTools.java 11100 2011-01-07 16:34:50Z anatom $
 */
public final class AlgorithmTools {
	
	/** Log4j instance */
	private static final Logger log = Logger.getLogger(AlgorithmTools.class);

    /** Should not be created */
    private AlgorithmTools() {}

	/** String used for an unkown keyspec in CA token properties */
	public static final String KEYSPEC_UNKNOWN = "unknown";

	/** Signature algorithms supported by RSA keys */
	private static final Collection<String> SIG_ALGS_RSA;
	
	/** Signature algorithms supported by DSA keys */
	private static final Collection<String> SIG_ALGS_DSA;
	
	/** Signature algorithms supported by ECDSA keys */
	private static final Collection<String> SIG_ALGS_ECDSA;
	
	static {
		SIG_ALGS_RSA = new LinkedList<String>();
		SIG_ALGS_RSA.add(AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
		SIG_ALGS_RSA.add(AlgorithmConstants.SIGALG_SHA1_WITH_RSA_AND_MGF1);
		SIG_ALGS_RSA.add(AlgorithmConstants.SIGALG_SHA256_WITH_RSA);
		SIG_ALGS_RSA.add(AlgorithmConstants.SIGALG_SHA256_WITH_RSA_AND_MGF1);
		
		SIG_ALGS_DSA = new LinkedList<String>();
		SIG_ALGS_DSA.add(AlgorithmConstants.SIGALG_SHA1_WITH_DSA);
		
		SIG_ALGS_ECDSA = new LinkedList<String>();
		SIG_ALGS_ECDSA.add(AlgorithmConstants.SIGALG_SHA1_WITH_ECDSA);
		SIG_ALGS_ECDSA.add(AlgorithmConstants.SIGALG_SHA224_WITH_ECDSA);
		SIG_ALGS_ECDSA.add(AlgorithmConstants.SIGALG_SHA256_WITH_ECDSA);
		SIG_ALGS_ECDSA.add(AlgorithmConstants.SIGALG_SHA384_WITH_ECDSA);
	}

	/**
	 * Gets the name of matching key algorithm from a public key as defined by 
	 * <i>AlgorithmConstants</i>.
	 * @param publickey Public key to find matching key algorithm for.
	 * @return Name of the matching key algorithm or null if no match.
	 * @see AlgorithmConstants#KEYALGORITHM_RSA
	 * @see AlgorithmConstants#KEYALGORITHM_DSA
	 * @see AlgorithmConstants#KEYALGORITHM_ECDSA
	 */
	public static String getKeyAlgorithm(final PublicKey publickey) {
		String keyAlg = null;
		if ( publickey instanceof RSAPublicKey ) {
			keyAlg  = AlgorithmConstants.KEYALGORITHM_RSA;
		} else if ( publickey instanceof DSAPublicKey ) {
			keyAlg = AlgorithmConstants.KEYALGORITHM_DSA;
		} else if ( publickey instanceof ECPublicKey ) {
			keyAlg = AlgorithmConstants.KEYALGORITHM_ECDSA;
		}
		return keyAlg;
	}
	
	/**
	 * Gets a collection of signature algorithm names supported by the given
	 * key.
	 * @param publickey key to find supported algorithms for.
	 * @return Collection of zero or more signature algorithm names
	 * @see AlgorithmConstants
	 */
	public static Collection<String> getSignatureAlgorithms(final PublicKey publickey) {
		final Collection<String> ret;
		if ( publickey instanceof RSAPublicKey ) {
			ret = SIG_ALGS_RSA;
		} else if ( publickey instanceof DSAPublicKey ) {
			ret = SIG_ALGS_DSA;
		} else if ( publickey instanceof ECPublicKey ) {
			ret = SIG_ALGS_ECDSA;
		} else {
			ret = Collections.emptyList();			
		}
		return ret;
	}
	
	/**
	 * Gets the key algorithm matching a specific signature algorithm.
	 * @param signatureAlgorithm to get matching key algorithm for
	 * @return The key algorithm matching the signature or algorithm or 
	 * the default if no matching was found.
	 * @see AlgorithmConstants 
	 */
	public static String getKeyAlgorithmFromSigAlg(final String signatureAlgorithm) {
		final String ret;
		if ( signatureAlgorithm.contains("ECDSA") ) {
			ret = AlgorithmConstants.KEYALGORITHM_ECDSA;
		} else if ( signatureAlgorithm.contains("DSA") ) {
			ret = AlgorithmConstants.KEYALGORITHM_DSA;
		} else {
			ret = AlgorithmConstants.KEYALGORITHM_RSA;			
		}
		return ret;
	}
	
	/**
	 * Gets the key specification from a public key. Example: "1024" for a RSA 
	 * or DSA key or "prime192v1" for EC key. The EC curve is only detected 
	 * if <i>publickey</i> is an object created with the bouncy castle provider.
	 * @param publicKey The public key to get the key specification from
	 * @return The key specification, "unknown" if it could not be determined and
	 * null if the key algorithm is not supported
	 */
	public static String getKeySpecification(final PublicKey publicKey) {
		if (log.isTraceEnabled()) {
			log.trace(">getKeySpecification");			
		}
		String keyspec = null;
		if ( publicKey instanceof RSAPublicKey ) {
			keyspec = Integer.toString( ((RSAPublicKey) publicKey).getModulus().bitLength() );
		} else if ( publicKey instanceof DSAPublicKey ) {
			keyspec = Integer.toString( ((DSAPublicKey) publicKey).getParams().getP().bitLength() );
		} else if ( publicKey instanceof ECPublicKey) {
			if ( ((ECPublicKey) publicKey).getParams() instanceof ECNamedCurveSpec ) {
				keyspec = ((ECNamedCurveSpec) ((ECPublicKey) publicKey).getParams()).getName();
			} else {
				keyspec = KEYSPEC_UNKNOWN;
			}
		}
		if (log.isTraceEnabled()) {
			log.trace("<getKeySpecification: "+keyspec);
		}
		return keyspec;
	}

	/**
	 * Gets the algorithm to use for encryption given a specific signature algorithm.
	 * Some signature algorithms (i.e. DSA and ECDSA) can not be used for 
	 * encryption so they are instead substituted with RSA with equivalent hash
	 * algorithm.
	 * @param signatureAlgorithm to find a encryption algorithm for
	 * @return an other encryption algorithm or same as signature algorithm if it 
	 * can be used for encryption
	 */
	public static String getEncSigAlgFromSigAlg(final String signatureAlgorithm) {
		String encSigAlg = signatureAlgorithm;
		if ( signatureAlgorithm.equals(AlgorithmConstants.SIGALG_SHA256_WITH_ECDSA) ) {
			encSigAlg = AlgorithmConstants.SIGALG_SHA256_WITH_RSA;
		} else if ( signatureAlgorithm.equals(AlgorithmConstants.SIGALG_SHA224_WITH_ECDSA) ) {
			encSigAlg = AlgorithmConstants.SIGALG_SHA256_WITH_RSA;
		} else if ( signatureAlgorithm.equals(AlgorithmConstants.SIGALG_SHA1_WITH_ECDSA) ) {
			encSigAlg = AlgorithmConstants.SIGALG_SHA1_WITH_RSA;
		} else if( signatureAlgorithm.equals(AlgorithmConstants.SIGALG_SHA1_WITH_DSA) ) {
			encSigAlg = AlgorithmConstants.SIGALG_SHA1_WITH_RSA;
		}
		return encSigAlg;
	}

	/**
	 * Answers if the key can be used together with the given signature algorithm.
	 * @param publicKey public key to use
	 * @param signatureAlgorithm algorithm to test
	 * @return true if signature algorithm can be used with the public key algorithm
	 */
	public static boolean isCompatibleSigAlg(final PublicKey publicKey, final String signatureAlgorithm) {
		boolean ret = false;
		if (StringUtils.contains(signatureAlgorithm, AlgorithmConstants.KEYALGORITHM_RSA)) {
			if (publicKey instanceof RSAPublicKey) {
				ret = true;
			}
		} else if (StringUtils.contains(signatureAlgorithm, AlgorithmConstants.KEYALGORITHM_ECDSA)) {
    		if (publicKey instanceof ECPublicKey) {
    			ret = true;
    		}
    	} else if (StringUtils.contains(signatureAlgorithm, AlgorithmConstants.KEYALGORITHM_DSA)) {
     		if (publicKey instanceof DSAPublicKey) {
     			ret = true;
     		}
     	}
		return ret;
	}
}
