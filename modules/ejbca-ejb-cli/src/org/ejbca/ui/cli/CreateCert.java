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
 
package org.ejbca.ui.cli;

import java.io.FileOutputStream;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;

import org.ejbca.core.protocol.IRequestMessage;
import org.ejbca.core.protocol.IResponseMessage;
import org.ejbca.core.protocol.PKCS10RequestMessage;
import org.ejbca.core.protocol.X509ResponseMessage;
import org.ejbca.util.CertTools;
import org.ejbca.util.FileTools;
import org.ejbca.util.RequestMessageUtils;

/**
 * Issue a certificate for a user based on a CSR
 *
 * @version $Id: CreateCert.java 10952 2010-12-22 12:18:06Z anatom $
 */
public class CreateCert extends BaseCommand {
	
	public String getMainCommand() { return null; }
	public String getSubCommand() { return "createcert"; }
	public String getDescription() { return "Issue a certificate for a user based on a CSR"; }

	public void execute(String[] args) throws ErrorAdminCommandException {
        if ( args.length != 5 ) {
            getLogger().info("Usage: " + getCommand() + " <username> <password> <csr.pem> <cert.pem>");
            getLogger().info(" <csr.pem> must be a PKCS#10 request in PEM format.");
            getLogger().info(" The issued certificate will be written to <cert.pem>.");
            return;
        }
        String username = args[1];
        String password = args[2];
        String csr = args[3];
        String certf = args[4];
        try {
			byte[] bytes = FileTools.readFiletoBuffer(csr);
			IRequestMessage req = RequestMessageUtils.parseRequestMessage(bytes);
			if (req instanceof PKCS10RequestMessage) {
				PKCS10RequestMessage p10req = (PKCS10RequestMessage) req;
				p10req.setUsername(username);
				p10req.setPassword(password);
			} else {
				getLogger().error("Input file '"+csr+"' is not a PKCS#10 request.");
				return;
			}
			// Call signsession to create a certificate
			IResponseMessage resp = ejb.getSignSession().createCertificate(getAdmin(), req, X509ResponseMessage.class, null);
			byte[] respBytes = resp.getResponseMessage();
			// Convert to PEM
			Certificate cert = CertTools.getCertfromByteArray(respBytes);
			Collection certs = new ArrayList();
			certs.add(cert);
			byte[] pembytes = CertTools.getPEMFromCerts(certs);
			// Write the resulting cert to file
			FileOutputStream fos = new FileOutputStream(certf);
			fos.write(pembytes);
			fos.close();
			getLogger().info("PEM certificate written to file '"+certf+"'");
		} catch (Exception e) {
			throw new ErrorAdminCommandException(e);
		}
	}
}
