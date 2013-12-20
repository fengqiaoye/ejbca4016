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
/*
 * This file is part of the QuickServer library 
 * Copyright (C) 2003-2005 QuickServer.org
 *
 * Use, modification, copying and distribution of this software is subject to
 * the terms and conditions of the GNU Lesser General Public License. 
 * You should have received a copy of the GNU LGP License along with this 
 * library; if not, you can download a copy from <http://www.quickserver.org/>.
 *
 * For questions, suggestions, bug-reports, enhancement-requests etc.
 * visit http://www.quickserver.org
 *
 */

package org.ejbca.ui.tcp;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateEncodingException;

import org.apache.log4j.Logger;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.util.EjbLocalHelper;
import org.ejbca.core.protocol.IResponseMessage;
import org.quickserver.net.server.ClientBinaryHandler;
import org.quickserver.net.server.ClientEventHandler;
import org.quickserver.net.server.ClientHandler;
import org.quickserver.net.server.DataMode;
import org.quickserver.net.server.DataType;

/**
 * Class receiving TCP messages from QuickServer (receives quickserver events) and routing them to the correct CMP handler class.
 * 
 * @author tomas
 * @version $Id: CmpTcpCommandHandler.java 11268 2011-01-26 23:02:58Z jeklund $
 */
public class CmpTcpCommandHandler implements ClientEventHandler, ClientBinaryHandler  {

	private static final Logger LOG = Logger.getLogger(CmpTcpCommandHandler.class.getName());
    private static final InternalResources INTRES = InternalResources.getInstance();
    private static EjbLocalHelper ejb = null;
	
	private static synchronized EjbLocalHelper getEjb() {
		if (ejb == null) {
			ejb = new EjbLocalHelper();
		}
		return ejb;
	}
	
	public void gotConnected(final ClientHandler handler) throws SocketTimeoutException, IOException {
		if (LOG.isDebugEnabled()) {
			LOG.debug("CMP connection opened: "+handler.getHostAddress());
		}
		handler.setDataMode(DataMode.BINARY, DataType.IN);
		handler.setDataMode(DataMode.BINARY, DataType.OUT);
	}

	public void lostConnection(final ClientHandler handler) throws IOException {
		LOG.debug("Connection lost: "+handler.getHostAddress());
	}

	public void closingConnection(final ClientHandler handler) throws IOException {
		LOG.debug("Connection closed: "+handler.getHostAddress());
	}

	public void handleBinary(final ClientHandler handler, final byte command[])	throws SocketTimeoutException, IOException {
		LOG.info(INTRES.getLocalizedMessage("cmp.receivedmsg", handler.getHostAddress()));
		long startTime = System.currentTimeMillis();
		final TcpReceivedMessage cmpTcpMessage = TcpReceivedMessage.getTcpMessage(command);
		if ( cmpTcpMessage.message==null )  {
			handler.closeConnection();
		} else {
			// We must use an administrator with rights to create users
			final Admin administrator = new Admin(Admin.TYPE_RA_USER, handler.getHostAddress());
			final IResponseMessage resp;
			try {
				 resp = getEjb().getCmpMessageDispatcherSession().dispatch(administrator, cmpTcpMessage.message);
			} catch (IOException e) {
				LOG.error( INTRES.getLocalizedMessage("cmp.errornoasn1"), e );
				handler.closeConnection();
				return;
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug("Sending back CMP response to client.");
			}
			// Send back reply
			final TcpReturnMessage sendBack;
			{
				byte tmp[] = null;
				try {
					if (resp!=null) {
						tmp = resp.getResponseMessage();
					}
				} catch (CertificateEncodingException e) {
					LOG.debug("CertificateEncodingException: " + e.getMessage());
				}
				sendBack = TcpReturnMessage.createMessage(tmp, cmpTcpMessage.doClose);
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug("Sending "+sendBack.message.length+" bytes to client");
			}
			handler.sendClientBinary(sendBack.message);
			long endTime = System.currentTimeMillis();
			final String iMsg = INTRES.getLocalizedMessage("cmp.sentresponsemsg", handler.getHostAddress(), Long.valueOf(endTime - startTime));
			LOG.info(iMsg);
			if ( cmpTcpMessage.doClose || sendBack.doClose ) {
				handler.closeConnection(); // It's time to say good bye			
			}
		}
	}
}