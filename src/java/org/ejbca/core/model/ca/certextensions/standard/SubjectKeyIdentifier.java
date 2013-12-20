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

package org.ejbca.core.model.ca.certextensions.standard;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PublicKey;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.ejbca.core.model.ca.caadmin.CA;
import org.ejbca.core.model.ca.certextensions.CertificateExtensionException;
import org.ejbca.core.model.ca.certextensions.CertificateExtentionConfigurationException;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ra.UserDataVO;

/** 
 * 
 * Class for standard X509 certificate extension. 
 * See rfc3280 or later for spec of this extension.      
 * 
 * @author: Tomas Gustavsson
 * @version $Id: SubjectKeyIdentifier.java 11882 2011-05-04 08:49:33Z anatom $
 */
public class SubjectKeyIdentifier extends StandardCertificateExtension {

	@Override
	public void init(final CertificateProfile certProf) {
		super.setOID(X509Extensions.SubjectKeyIdentifier.getId());
		super.setCriticalFlag(certProf.getSubjectKeyIdentifierCritical());
	}
	
	@Override
	public DEREncodable getValue(final UserDataVO subject, final CA ca, final CertificateProfile certProfile, final PublicKey userPublicKey, final PublicKey caPublicKey ) throws CertificateExtentionConfigurationException, CertificateExtensionException {
        SubjectPublicKeyInfo spki;
		try {
			spki = new SubjectPublicKeyInfo(
                (ASN1Sequence) new ASN1InputStream(new ByteArrayInputStream(userPublicKey.getEncoded())).readObject());
		} catch (IOException e) {
			throw new CertificateExtensionException("IOException parsing user public key: "+e.getMessage(), e);
		}
		return new org.bouncycastle.asn1.x509.SubjectKeyIdentifier(spki);
	}	
}
