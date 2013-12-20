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

import org.cesecore.core.ejb.ca.store.CertificateProfileSession;
import org.ejbca.core.ejb.authorization.AuthorizationSession;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSession;
import org.ejbca.core.ejb.ca.publisher.PublisherSession;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ca.publisher.BasePublisher;
import org.ejbca.core.model.ca.publisher.PublisherConnectionException;
import org.ejbca.core.model.ca.publisher.PublisherExistsException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.ui.web.admin.configuration.InformationMemory;

/**
 * A class handling the hardtoken profile data in the webinterface.
 *
 * @author  TomSelleck
 * @version $Id: PublisherDataHandler.java 10397 2010-11-08 14:18:57Z anatom $
 */
public class PublisherDataHandler implements Serializable {
	   
    private static final long serialVersionUID = -5646053740072121787L;
    
    private PublisherSession publishersession; 
    private AuthorizationSession authorizationsession;
    private CAAdminSession caadminsession;
    private CertificateProfileSession certificateProfileSession;
    private Admin administrator;
    private InformationMemory info;

    /** Creates a new instance of PublisherDataHandler */
    public PublisherDataHandler(Admin administrator, PublisherSession publishersession, AuthorizationSession authorizationsession, 
                                CAAdminSession caadminsession, CertificateProfileSession certificateProfileSession, InformationMemory info) {
       this.publishersession = publishersession;           
       this.authorizationsession = authorizationsession;
       this.caadminsession = caadminsession;
       this.certificateProfileSession = certificateProfileSession;
       this.administrator = administrator;          
       this.info = info;       
    }
    
       /** Method to add a publisher. Throws PublisherExitsException if profile already exists  */
    public void addPublisher(String name, BasePublisher publisher) throws PublisherExistsException, AuthorizationDeniedException {
      if(authorizedToEditPublishers()){
        publishersession.addPublisher(administrator, name, publisher);
        this.info.publishersEdited();
      }else {
        throw new AuthorizationDeniedException("Not authorized to add publisher");
      }
    }    

       /** Method to change a publisher. */     
    public void changePublisher(String name, BasePublisher publisher) throws AuthorizationDeniedException{
      if(authorizedToEditPublishers()){ 
        publishersession.changePublisher(administrator, name,publisher);   
		this.info.publishersEdited();
      }else {
        throw new AuthorizationDeniedException("Not authorized to edit publisher");
      }
    }
    
    /** Method to remove a publisher, returns true if deletion failed.*/ 
    public boolean removePublisher(String name) throws AuthorizationDeniedException{
      boolean returnval = true;  

      if(authorizedToEditPublishers()){
      	int publisherid = publishersession.getPublisherId(administrator, name);
        if(!caadminsession.exitsPublisherInCAs(administrator, publisherid) && !certificateProfileSession.existsPublisherInCertificateProfiles(administrator,publisherid)){      	
		  publishersession.removePublisher(administrator, name);
		  this.info.publishersEdited();
		  returnval = false;
        }  
      }else {
        throw new AuthorizationDeniedException("Not authorized to remove publisher.");
      }
        
      return returnval;          
    }
    
    /** Metod to rename a publisher */
    public void renamePublisher(String oldname, String newname) throws PublisherExistsException, AuthorizationDeniedException{
     if(authorizedToEditPublishers()){    
		publishersession.renamePublisher(administrator, oldname,newname);
	   this.info.publishersEdited();
     }else {
       throw new AuthorizationDeniedException("Not authorized to rename publisher");
     }
    }
    

    public void clonePublisher(String originalname, String newname) throws AuthorizationDeniedException{         
      if(authorizedToEditPublishers()){
        publishersession.clonePublisher(administrator, originalname,newname);
        this.info.publishersEdited();
      }else {
         throw new AuthorizationDeniedException("Not authorized to clone publisher");
      }
    }
    
    public void testConnection(String name) throws PublisherConnectionException, AuthorizationDeniedException{         
    	if(authorizedToPublisherName(name)){
    		publishersession.testConnection(administrator, publishersession.getPublisherId(administrator, name));    		
    	}else {
    		throw new AuthorizationDeniedException("Not authorized to clone publisher");
    	}
    }        
    
      /** Method to get a reference to a publisher.*/ 
    public BasePublisher getPublisher(int id) throws AuthorizationDeniedException{
      if(!authorizedToPublisherId(id)) {
        throw new AuthorizationDeniedException("Not authorized to publisher");
      }
      
      return publishersession.getPublisher(administrator, id); 
    }      
          
    public BasePublisher getPublisher(String name) throws AuthorizationDeniedException{
     if(!authorizedToPublisherName(name)) {
        throw new AuthorizationDeniedException("Not authorized to publisher");
     }
         
      return publishersession.getPublisher(administrator, name);
    }
   
      
    public int getPublisherId(String name){
      return publishersession.getPublisherId(administrator, name);  
    }
    
    
    /**
     * Help function that checks if administrator is authorized to edit publisher with given name.
     */
    private boolean authorizedToPublisherName(String name){
	  int id = publishersession.getPublisherId(administrator, name);
      return authorizedToPublisherId(id);
    }
     
    
    /**
     * Help function that checks if administrator is authorized to edit publisher with given id.
     */
    private boolean authorizedToPublisherId(int id){      
      return info.getAuthorizedPublisherNames().values().contains(Integer.valueOf(id));
    }
    
    /**
     * Help function that checks if administrator is authorized to edit publisher.
     */    
    private boolean authorizedToEditPublishers() {
        return authorizationsession.isAuthorizedNoLog(administrator, AccessRulesConstants.ROLE_SUPERADMINISTRATOR);
    } 
}
