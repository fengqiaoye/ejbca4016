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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.cesecore.core.ejb.ca.store.CertificateProfileSession;
import org.ejbca.core.ejb.authorization.AuthorizationSession;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSession;
import org.ejbca.core.ejb.ca.caadmin.CaSession;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.log.Admin;

/**
 * A class that looks up the which CA:s and certificate profiles the administrator is authorized to view.
 * 
 * @version $Id: CAAuthorization.java 11155 2011-01-12 11:54:27Z anatom $
 */
public class CAAuthorization implements Serializable {

    private static final long serialVersionUID = -7397428143642714604L;

    private Collection<Integer> authcas = null;
    private TreeMap<String, Integer> profilenamesendentity = null;
    private TreeMap<String, Integer> profilenamessubca = null;
    private TreeMap<String, Integer> profilenamesrootca = null;
    private TreeMap<String, Integer> canames = null;
    private TreeMap<String, Integer> allcanames = null;
    private TreeMap<String, Integer> allprofilenames = null;
    private Admin admin;
    private CAAdminSession caadminsession;
    private CaSession caSession;
    private AuthorizationSession authorizationsession;
    private CertificateProfileSession certificateProfileSession;
    
    /** Creates a new instance of CAAuthorization. */
    public CAAuthorization(Admin admin,  
                           CAAdminSession caadminsession, CaSession caSession,
                           AuthorizationSession authorizationsession, CertificateProfileSession certificateProfileSession) {
      this.admin=admin;
      this.caadminsession=caadminsession;      
      this.caSession=caSession;      
      this.authorizationsession=authorizationsession;
        this.certificateProfileSession = certificateProfileSession;
    }

    /**
     * Method returning a Collection of authorized CA id's (Integer).
     *
     */
    public Collection<Integer> getAuthorizedCAIds() {         
    	if(authcas == null || authcas.size() == 0){
    		authcas = caSession.getAvailableCAs(admin);
    	}
    	return authcas;
    } 
    
    
    
    public TreeMap<String, Integer> getAuthorizedEndEntityCertificateProfileNames(boolean usehardtokenprofiles){
      if(profilenamesendentity==null){
        profilenamesendentity = new TreeMap<String, Integer>();  
        Iterator<Integer> iter = null;
        if(usehardtokenprofiles) {         
          iter = certificateProfileSession.getAuthorizedCertificateProfileIds(admin, SecConst.CERTTYPE_HARDTOKEN, getAuthorizedCAIds()).iterator();
        } else {         
		  iter = certificateProfileSession.getAuthorizedCertificateProfileIds(admin, SecConst.CERTTYPE_ENDENTITY, getAuthorizedCAIds()).iterator();
        }
        Map<Integer, String> idtonamemap = certificateProfileSession.getCertificateProfileIdToNameMap(admin);
        while(iter.hasNext()){
          Integer id = (Integer) iter.next();
          profilenamesendentity.put(idtonamemap.get(id),id);
        }
      }
      return profilenamesendentity;  
    }
            
    public TreeMap<String, Integer> getAuthorizedSubCACertificateProfileNames(){
      if(profilenamessubca==null){
        profilenamessubca = new TreeMap<String, Integer>();  
        Iterator<Integer> iter = certificateProfileSession.getAuthorizedCertificateProfileIds(admin, SecConst.CERTTYPE_SUBCA, getAuthorizedCAIds()).iterator();      
        Map<Integer, String> idtonamemap = certificateProfileSession.getCertificateProfileIdToNameMap(admin);
        while(iter.hasNext()){
          Integer id = (Integer) iter.next();
          profilenamessubca.put(idtonamemap.get(id),id);
        }
      }
      return profilenamessubca;  
    }
    
    
    public TreeMap<String, Integer> getAuthorizedRootCACertificateProfileNames(){
      if(profilenamesrootca==null){
        profilenamesrootca = new TreeMap<String, Integer>();  
        Iterator<Integer> iter = certificateProfileSession.getAuthorizedCertificateProfileIds(admin, SecConst.CERTTYPE_ROOTCA, getAuthorizedCAIds()).iterator();      
        Map<Integer, String> idtonamemap = certificateProfileSession.getCertificateProfileIdToNameMap(admin);
        while(iter.hasNext()){
          Integer id = (Integer) iter.next();
          profilenamesrootca.put(idtonamemap.get(id),id);
        }
      }
      return profilenamesrootca;  
    }
    
    public TreeMap<String, Integer> getEditCertificateProfileNames(boolean includefixedhardtokenprofiles){
        if (allprofilenames == null) {
            // check if administrator
            boolean superadministrator = false;

            superadministrator = authorizationsession.isAuthorizedNoLog(admin, "/super_administrator");

            allprofilenames = new TreeMap<String, Integer>();
        Iterator<Integer> iter= null;  
        if(includefixedhardtokenprofiles){
          iter = certificateProfileSession.getAuthorizedCertificateProfileIds(admin, 0, getAuthorizedCAIds()).iterator();
        }else{
          ArrayList<Integer> certprofiles = new ArrayList<Integer>();
		  certprofiles.addAll(certificateProfileSession.getAuthorizedCertificateProfileIds(admin, SecConst.CERTTYPE_ENDENTITY, getAuthorizedCAIds()));
		  certprofiles.addAll(certificateProfileSession.getAuthorizedCertificateProfileIds(admin, SecConst.CERTTYPE_ROOTCA, getAuthorizedCAIds()));
		  certprofiles.addAll(certificateProfileSession.getAuthorizedCertificateProfileIds(admin, SecConst.CERTTYPE_SUBCA, getAuthorizedCAIds()));
		  iter = certprofiles.iterator();
        }
        Map<Integer, String> idtonamemap = certificateProfileSession.getCertificateProfileIdToNameMap(admin);
        while(iter.hasNext()){
        
          Integer id = iter.next();
          CertificateProfile certprofile = certificateProfileSession.getCertificateProfile(admin,id.intValue());
 
          // If not superadministrator, then should only end entity profiles be added.
          if(superadministrator || certprofile.getType() == CertificateProfile.TYPE_ENDENTITY){                      
            // if default profiles, add fixed to name.
            if(id.intValue() <= SecConst.FIXED_CERTIFICATEPROFILE_BOUNDRY || (!superadministrator && certprofile.isApplicableToAnyCA())) {
			  allprofilenames.put(idtonamemap.get(id) + " (FIXED)",id);   
            } else {
		      allprofilenames.put(idtonamemap.get(id),id);
            }
          }
        }  
      }
      return allprofilenames;  
    }    
        
    
    
    public TreeMap<String, Integer> getCANames(){        
      if(canames==null){        
        canames = new TreeMap<String, Integer>();        
        HashMap<Integer, String> idtonamemap = this.caadminsession.getCAIdToNameMap(admin);
        Iterator<Integer> iter = getAuthorizedCAIds().iterator();
        while(iter.hasNext()){          
          Integer id = (Integer) iter.next();          
          canames.put(idtonamemap.get(id),id);
        }        
      }       
      return canames;  
    }
    
	public TreeMap<String, Integer> getAllCANames(){              
		allcanames = new TreeMap<String, Integer>();        
		HashMap<Integer, String> idtonamemap = this.caadminsession.getCAIdToNameMap(admin);
		Iterator<Integer> iter = idtonamemap.keySet().iterator();
		while(iter.hasNext()){          
		  Integer id = (Integer) iter.next();          
		  allcanames.put(idtonamemap.get(id),id);
		}        
       
	  return allcanames;  
	}    
    public void clear(){
      authcas=null;
      profilenamesendentity = null;
      profilenamessubca = null;
      profilenamesrootca = null;
      allprofilenames = null;
      canames=null;
      allcanames=null;
    }    
}
