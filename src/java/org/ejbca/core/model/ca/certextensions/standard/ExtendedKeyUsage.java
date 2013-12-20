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

import java.security.PublicKey;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.ejbca.core.model.ca.caadmin.CA;
import org.ejbca.core.model.ca.certextensions.CertificateExtensionException;
import org.ejbca.core.model.ca.certextensions.CertificateExtentionConfigurationException;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ra.UserDataVO;

/**
 * Class for standard X509 certificate extension. 
 * See rfc3280 or later for spec of this extension.
 * 
 * @author: Tomas Gustavsson
 * @version $Id: ExtendedKeyUsage.java 11882 2011-05-04 08:49:33Z anatom $
 */
public class ExtendedKeyUsage extends StandardCertificateExtension {
    private static final Logger log = Logger.getLogger(ExtendedKeyUsage.class);

	@Override
	public void init(final CertificateProfile certProf) {
		super.setOID(X509Extensions.ExtendedKeyUsage.getId());
        // Extended Key Usage may be either critical or non-critical
		super.setCriticalFlag(certProf.getExtendedKeyUsageCritical());
	}
	
	@Override
	public DEREncodable getValue(final UserDataVO subject, final CA ca, final CertificateProfile certProfile, final PublicKey userPublicKey, final PublicKey caPublicKey ) throws CertificateExtentionConfigurationException, CertificateExtensionException {
		org.bouncycastle.asn1.x509.ExtendedKeyUsage ret = null;
        // Get extended key usage from certificate profile
		final Collection<String> c = certProfile.getExtendedKeyUsageOids();
		final Vector usage = new Vector();
		final Iterator<String> iter = c.iterator();
        while (iter.hasNext()) {
            usage.add(new DERObjectIdentifier(iter.next()));
        }
        // Don't add empty key usage extension
        if (!usage.isEmpty()) {
            ret = new org.bouncycastle.asn1.x509.ExtendedKeyUsage(usage);
        }
		if (ret == null) {
			log.error("ExtendedKeyUsage missconfigured, no oids defined");
		}
		return ret;
	}	
}
