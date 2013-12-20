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
 
package org.ejbca.ui.web.admin.cainterface;


import java.util.HashMap;

import org.cesecore.core.ejb.ca.store.CertificateProfileSession;
import org.ejbca.core.model.log.Admin;

/**
 * A class used to improve performance by proxying certificateprofile id to certificate name mappings by minimizing the number of needed lockups over rmi.
 * 
 * @version $Id: CertificateProfileNameProxy.java 10397 2010-11-08 14:18:57Z anatom $
 */
public class CertificateProfileNameProxy implements java.io.Serializable {
    
    private HashMap certificateprofilenamestore;
    private CertificateProfileSession certificateProfileSession;
    private Admin admin;

    /** Creates a new instance of ProfileNameProxy */
    public CertificateProfileNameProxy(Admin administrator, CertificateProfileSession certificateProfileSession){
      this.certificateProfileSession = certificateProfileSession;
      certificateprofilenamestore = new HashMap(); 
      this.admin = administrator;
    }
    
    /**
     * Method that first tries to find certificateprofile name in local hashmap and if it doesn't exists looks it up over RMI.
     *
     * @param certificateprofileid the certificateprofile id number to look up.
     * @return the certificateprofilename or null if no certificateprofilename is relatied to the given id
     */
    public String getCertificateProfileName(int certificateprofileid)  {
      String returnval = null;  
      // Check if name is in hashmap
      returnval = (String) certificateprofilenamestore.get(Integer.valueOf(certificateprofileid));
      
      if(returnval==null){
        // Retreive profilename 
        returnval = certificateProfileSession.getCertificateProfileName(admin, certificateprofileid);
        if(returnval != null) {
          certificateprofilenamestore.put(Integer.valueOf(certificateprofileid),returnval);
        }
      }    
      return returnval;
    }
}
