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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.ejbca.core.model.InternalResources;
import org.ejbca.ui.web.LimitLengthASN1Reader;

/**
 * Sends HTTP message to CMP server.
 * 
 * @author lars
 * @version $Id: CMPSendHTTP.java 8958 2010-04-29 10:07:31Z primelars $
 *
 */
public class CMPSendHTTP {
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();

	public final String contentType;
	public final byte response[];
	public final int responseCode;

	private CMPSendHTTP(String ct, byte ba[], int rc) {
		this.contentType = ct;
		this.response = ba;
		this.responseCode = rc;
	}
	public static CMPSendHTTP doIt(final byte[] message, final String hostName,
	                               final int port, final String urlPath, final boolean doClose) throws Exception {
		boolean isError = true;
		final HttpURLConnection con = (HttpURLConnection)new URL("http://"+hostName+":"+port+(urlPath!=null ? urlPath:"/ejbca/publicweb/cmp")).openConnection();
		try {
			// POST the CMP request
			// we are going to do a POST
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-type", "application/pkixcmp");
			con.connect();
			// POST it
			final OutputStream os = con.getOutputStream();
			os.write(message);
			os.close();

			final String contentType = con.getContentType();
			final int responseCode = con.getResponseCode();
			if ( responseCode!=HttpURLConnection.HTTP_OK ) {
				return new CMPSendHTTP( contentType, null, responseCode );
			}
			final InputStream in = con.getInputStream();
			final byte response[] = new LimitLengthASN1Reader(in, con.getContentLength()).readObject().getDEREncoded();
			if ( response==null || response.length<1 ) {
				throw new Exception(intres.getLocalizedMessage("cmp.errornoasn1"));
			}
			isError = false;
			return new CMPSendHTTP( contentType, response, responseCode );
		} finally {
			if ( doClose || isError ) {
				con.disconnect();
			}
		}
	}
}
