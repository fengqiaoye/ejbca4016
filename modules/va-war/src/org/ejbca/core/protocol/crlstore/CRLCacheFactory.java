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
package org.ejbca.core.protocol.crlstore;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.cesecore.core.ejb.ca.crl.CrlSessionLocal;
import org.ejbca.core.protocol.certificatestore.ICertificateCache;

/**
 * Factory for creating a {@link CRLCache} object to be used by the OCSP responder of the CA.
 * 
 * @author primelars
 * @version $Id: CRLCacheFactory.java 11148 2011-01-11 17:01:22Z jeklund $
 * 
 */
public class CRLCacheFactory {
    private static ICRLCache instance = null;
    private static final Lock lock = new ReentrantLock();
    /**
     * @return  {@link CRLCache} for the CA.
     */
    public static ICRLCache getInstance(CrlSessionLocal crlSession, ICertificateCache certCache) {
        if (instance != null) {
        	return instance;
        }
        lock.lock();
        try {
        	if ( instance==null ) {
        		instance = new CRLCache(crlSession, certCache);
        	}
    		return instance;
        } finally {
        	lock.unlock();
        }
    }
}
