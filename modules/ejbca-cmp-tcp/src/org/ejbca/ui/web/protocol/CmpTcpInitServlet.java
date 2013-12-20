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

import java.net.UnknownHostException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.log4j.Logger;
import org.ejbca.ui.tcp.CmpTcpServer;

/**
 * Servlet with the sole purpose of starting the CMP TCP server. 
 *
 * @version $Id: CmpTcpInitServlet.java 10288 2010-10-26 11:27:21Z anatom $
 */
public class CmpTcpInitServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = Logger.getLogger(CmpTcpInitServlet.class);
	
	private transient CmpTcpServer cmpTcpServer;	// HttpServlet implements Serializable

	public void init(final ServletConfig config) throws ServletException {
		super.init(config);
		LOG.info("Starting CMP TCP Service..");
		cmpTcpServer = new CmpTcpServer();
		try {
			cmpTcpServer.start();
		} catch (UnknownHostException e) {
			throw new ServletException(e);
		}
	}
	
	public void destroy() {
		LOG.info("Stopping CMP TCP Service..");
		try {
			cmpTcpServer.stop();
		} catch (Throwable t) {
			LOG.error("", t);
		}
		super.destroy();
	}
}
