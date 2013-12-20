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

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;


/**
 * Classes used by TestAlgorithmTools.
 * 
 * @version $Id: AlgorithmToolsHelper.java 10833 2010-12-13 14:00:06Z anatom $
 */
class AlgorithmToolsHelper {
	
	static class MockPublicKey implements PublicKey {
		private static final long serialVersionUID = 1L;
		public String getAlgorithm() { return null; }
		public byte[] getEncoded() { return null; }
		public String getFormat() { return null; }		
	}
	
	static class MockNotSupportedPublicKey extends MockPublicKey {
		private static final long serialVersionUID = 1L;
	}
	
	static class MockRSAPublicKey extends MockPublicKey implements RSAPublicKey {
		private static final long serialVersionUID = 1L;
		public BigInteger getPublicExponent() { return null; }
		public BigInteger getModulus() { return null; }
	}
	
	static class MockDSAPublicKey extends MockPublicKey implements DSAPublicKey {
		private static final long serialVersionUID = 1L;
		public BigInteger getY() { return null; }
		public DSAParams getParams() { return null; }
	}
	
	static class MockECDSAPublicKey extends MockPublicKey implements ECPublicKey {
		private static final long serialVersionUID = 1L;
		public ECPoint getW() { return null; }
		public ECParameterSpec getParams() { return null; }
	}
}
