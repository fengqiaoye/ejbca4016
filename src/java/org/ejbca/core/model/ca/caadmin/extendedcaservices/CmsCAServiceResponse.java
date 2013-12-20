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
 
package org.ejbca.core.model.ca.caadmin.extendedcaservices;

import java.io.Serializable;

/**
 * Class used when delevering CMS service response from a CA.  
 *
 * @version $Id: CmsCAServiceResponse.java 8373 2009-11-30 14:07:00Z jeklund $
 */
public class CmsCAServiceResponse extends ExtendedCAServiceResponse implements Serializable {    
                 
    /**
     * Determines if a de-serialized file is compatible with this class.
     *
     * Maintainers must change this value if and only if the new version
     * of this class is not compatible with old versions. See Sun docs
     * for <a href=http://java.sun.com/products/jdk/1.1/docs/guide
     * /serialization/spec/version.doc.html> details. </a>
     */
	private static final long serialVersionUID = 7704310763496240017L;

	private byte[] doc = null;
    
        
    public CmsCAServiceResponse(byte[] doc) {
        this.doc = doc;        
    }    
           
    public byte[] getCmsDocument() { return this.doc; }
        
}
