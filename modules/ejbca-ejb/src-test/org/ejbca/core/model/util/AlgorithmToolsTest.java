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

import java.util.Collection;
import java.util.Iterator;

import junit.framework.TestCase;
import static org.ejbca.core.model.util.AlgorithmToolsHelper.*;

/**
 * Tests for AlgorithmTools.
 * Mostly tests border cases.
 * 
 * @version $Id: AlgorithmToolsTest.java 10833 2010-12-13 14:00:06Z anatom $
 */
public class AlgorithmToolsTest extends TestCase {

	public void testGetKeyAlgorithm() {
		assertNull("null if no match", AlgorithmTools.getKeyAlgorithm(new MockNotSupportedPublicKey()));
	}

	public void testGetSignatureAlgorithms() {
		Collection<String> algs = AlgorithmTools.getSignatureAlgorithms(new MockNotSupportedPublicKey()); 
		assertNotNull("should not return null", algs);
		assertTrue("no supported algs", algs.isEmpty());
	}

	public void testGetKeyAlgorithmFromSigAlg() {
		
		Collection<String> sigAlgs;
		
		// Test that key algorithm is RSA for all of its signature algorithms
		sigAlgs = AlgorithmTools.getSignatureAlgorithms(new MockRSAPublicKey());
		for(Iterator<String> i = sigAlgs.iterator(); i.hasNext();) {
			assertEquals(AlgorithmTools.getKeyAlgorithm(new MockRSAPublicKey()), AlgorithmTools.getKeyAlgorithmFromSigAlg(i.next()));
		}
		
		// Test that key algorithm is DSA for all of its signature algorithms
		sigAlgs = AlgorithmTools.getSignatureAlgorithms(new MockDSAPublicKey());
		for(Iterator<String> i = sigAlgs.iterator(); i.hasNext();) {
			assertEquals(AlgorithmTools.getKeyAlgorithm(new MockDSAPublicKey()), AlgorithmTools.getKeyAlgorithmFromSigAlg(i.next()));
		}
		
		// Test that key algorithm is ECDSA for all of its signature algorithms
		sigAlgs = AlgorithmTools.getSignatureAlgorithms(new MockDSAPublicKey());
		for(Iterator<String> i = sigAlgs.iterator(); i.hasNext();) {
			assertEquals(AlgorithmTools.getKeyAlgorithm(new MockDSAPublicKey()), AlgorithmTools.getKeyAlgorithmFromSigAlg(i.next()));
		}
		
		// should return a default value
		assertNotNull("should return a default value", AlgorithmTools.getKeyAlgorithmFromSigAlg("_NonExistingAlg"));
		
	}

	public void testGetKeySpecification() {
		assertNull("null if the key algorithm is not supported", AlgorithmTools.getKeySpecification(new MockNotSupportedPublicKey()));
		assertEquals("unknown", AlgorithmTools.getKeySpecification(new MockECDSAPublicKey()));
	}

	public void testGetEncSigAlgFromSigAlg() {
	}

	public void testIsCompatibleSigAlg() {
	}
}
