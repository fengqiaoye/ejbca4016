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

package org.ejbca.ui.web.protocol;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;

import javax.ejb.EJB;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cesecore.core.ejb.ca.crl.CrlSessionLocal;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionLocal;
import org.ejbca.core.protocol.certificatestore.HashID;
import org.ejbca.core.protocol.crlstore.CRLCacheFactory;
import org.ejbca.core.protocol.crlstore.ICRLCache;
import org.ejbca.util.HTMLTools;

/** 
 * Servlet implementing server side of the CRL Store.
 * For a detailed description see rfc4378.
 * 
 * @author Lars Silven PrimeKey
 * @version  $Id: CRLStoreServlet.java 15003 2012-06-17 23:55:15Z primelars $
 */
public class CRLStoreServlet extends StoreServletBase {

	private static final long serialVersionUID = 1L;

	@EJB
	private CrlSessionLocal crlSession;
	@EJB
	private CertificateStoreSessionLocal certificateStoreSession;
	
	private ICRLCache crlCache;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config, this.certificateStoreSession);
		this.crlCache = CRLCacheFactory.getInstance(this.crlSession, this.certCache);		
	}

	@Override
	void sHash(String iHash, HttpServletResponse resp, HttpServletRequest req) throws IOException, ServletException {
		// do nothing for CRLs
	}

	@Override
	void iHash(String iHash, HttpServletResponse resp, HttpServletRequest req) throws IOException, ServletException {
		returnCrl( this.crlCache.findLatestByIssuerDN(HashID.getFromB64(iHash), isDelta(req)), resp, iHash, isDelta(req) );		
	}

	@Override
	void sKIDHash(String sKIDHash, HttpServletResponse resp, HttpServletRequest req) throws IOException, ServletException {
		sKIDHash( sKIDHash, resp, req, sKIDHash);
	}

	@Override
	void sKIDHash(String sKIDHash, HttpServletResponse resp, HttpServletRequest req, String name) throws IOException, ServletException {
		returnCrl( this.crlCache.findBySubjectKeyIdentifier(HashID.getFromB64(sKIDHash), isDelta(req)), resp, name, isDelta(req) );
	}

	@Override
	void printInfo(X509Certificate cert, String indent, PrintWriter pw, String url) {
		pw.println(indent+cert.getSubjectX500Principal());
		pw.println(indent+" "+RFC4387URL.iHash.getRef(url, HashID.getFromSubjectDN(cert)));
		pw.println(indent+" "+RFC4387URL.sKIDHash.getRef(url, HashID.getFromKeyID(cert)));
		pw.println(indent+" "+RFC4387URL.iHash.getRef(url, HashID.getFromSubjectDN(cert), true));
		pw.println(indent+" "+RFC4387URL.sKIDHash.getRef(url, HashID.getFromKeyID(cert), true));
	}

	@Override
	String getTitle() {
		return "CRLs";
	}

	private boolean isDelta(HttpServletRequest req) {
		return req.getParameterMap().get("delta")!=null;
	}

	private void returnCrl( byte crl[], HttpServletResponse resp, String name, boolean isDelta ) throws IOException {
		if ( crl==null || crl.length<1 ) {
			resp.sendError(HttpServletResponse.SC_NO_CONTENT, "No CRL with hash: "+HTMLTools.htmlescape(name));
			return;
		}
		resp.setContentType("application/pkix-crl");
		resp.setHeader("Content-disposition", "attachment; filename="+(isDelta?"delta":"") + name + ".crl");
		resp.setContentLength(crl.length);
		resp.getOutputStream().write(crl);
	}
}
