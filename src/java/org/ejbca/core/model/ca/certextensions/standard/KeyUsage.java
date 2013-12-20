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

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.jce.X509KeyUsage;
import org.ejbca.core.model.ca.caadmin.CA;
import org.ejbca.core.model.ca.certextensions.CertificateExtensionException;
import org.ejbca.core.model.ca.certextensions.CertificateExtentionConfigurationException;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.util.CertTools;

/**
 * Class for standard X509 certificate extension. 
 * See rfc3280 or later for spec of this extension.
 * 
 * @author: Tomas Gustavsson
 * @version $Id: KeyUsage.java 11882 2011-05-04 08:49:33Z anatom $
 */
public class KeyUsage extends StandardCertificateExtension {
    private static final Logger log = Logger.getLogger(KeyUsage.class);

	@Override
	public void init(final CertificateProfile certProf) {
		super.setOID(X509Extensions.KeyUsage.getId());
		super.setCriticalFlag(certProf.getKeyUsageCritical());
	}
	
	@Override
	public DEREncodable getValue(final UserDataVO subject, final CA ca, final CertificateProfile certProfile, final PublicKey userPublicKey, final PublicKey caPublicKey ) throws CertificateExtentionConfigurationException, CertificateExtensionException {
		// Key usage
		X509KeyUsage ret = null;
		final int keyUsage = CertTools.sunKeyUsageToBC(certProfile.getKeyUsage());
		if (log.isDebugEnabled()) {
			log.debug("Using KeyUsage from profile: "+keyUsage);
		}
		if (keyUsage >=0) {
			ret = new X509KeyUsage(keyUsage);
		}
		if (ret == null) {
			log.error("KeyUsage missconfigured, key usage flag invalid: "+keyUsage);
		}
		return ret;
	}	
}
