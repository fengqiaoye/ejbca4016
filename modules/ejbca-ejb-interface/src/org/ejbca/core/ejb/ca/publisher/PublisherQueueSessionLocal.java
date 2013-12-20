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
package org.ejbca.core.ejb.ca.publisher;

import java.security.cert.Certificate;

import javax.ejb.Local;

import org.ejbca.core.model.ca.publisher.BasePublisher;
import org.ejbca.core.model.ca.publisher.PublisherException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.ExtendedInformation;

/**
 * Local interface for PublisherQueueSession.
 * @version $Id: PublisherQueueSessionLocal.java 12894 2011-10-13 14:39:40Z anderspki $
 */
@Local
public interface PublisherQueueSessionLocal extends PublisherQueueSession {

    /** Publishers do not run a part of regular transactions and expect to run in auto-commit mode. */
	public boolean storeCertificateNonTransactional(BasePublisher publisher, Admin admin, Certificate cert, String username, String password, String userDN,
    		String cafp, int status, int type, long revocationDate, int revocationReason, String tag, int certificateProfileId,
    		long lastUpdate, ExtendedInformation extendedinformation) throws PublisherException;

    /** Publishers do not run a part of regular transactions and expect to run in auto-commit mode. */
	public boolean storeCRLNonTransactional(BasePublisher publisher, Admin admin, byte[] incrl, String cafp, int number, String userDN) throws PublisherException;

    /** Publisher queues are digested in transaction-based "chunks". */
	int doChunk(Admin admin, int publisherId, BasePublisher publisher);
}
