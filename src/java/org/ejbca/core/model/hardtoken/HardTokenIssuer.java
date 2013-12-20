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
 * HardTokenIssuer.java
 *
 * Created on den 19 januari 2003, 12:53
 */
package org.ejbca.core.model.hardtoken;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.ejbca.core.model.UpgradeableDataHashMap;

/**
 * HardTokenIssuer V3 is a class representing the data saved for each HardTokenIssuer.
 * it isn't back compatible with the old version.
 *
 * @author  TomSelleck
 * @version $Id: HardTokenIssuer.java 9541 2010-07-26 16:21:24Z jeklund $
 */
public  class HardTokenIssuer extends UpgradeableDataHashMap implements Serializable, Cloneable {

    // Default Values
    public static final float LATEST_VERSION = 1;

    // Protected Constants, must be overloaded by all deriving classes.   
    protected static final String AVAILABLEHARDTOKENSPROFILES  = "availablehardtokensprofiles"; 
	protected static final String DESCRIPTION                  = "description"; 
    
    public HardTokenIssuer(){
      data.put(AVAILABLEHARDTOKENSPROFILES,new ArrayList<Integer>());
      data.put(DESCRIPTION,"");         
    }
    
    // Public Methods
    
    // Availablehardtokens defines which hard tokens the issuer is able to issue. 
    public ArrayList<Integer> getAvailableHardTokenProfiles(){
      return (ArrayList<Integer>) data.get(AVAILABLEHARDTOKENSPROFILES); 
    }
    
    public void setAvailableHardTokenProfiles(ArrayList<Integer> availablehardtokens){
      data.put(AVAILABLEHARDTOKENSPROFILES,availablehardtokens); 
    }    
    
    public String getDescription(){
      return  (String) data.get(DESCRIPTION);	
    }

	public void setDescription(String description){
	  data.put(DESCRIPTION, description);	
	}    
    
    public void setField(String field, Object value){ 
       data.put(field,value);   
    }
    
    /** Implementation of UpgradableDataHashMap function getLatestVersion */
    public float getLatestVersion(){
       return LATEST_VERSION;
    }

    /** Implementation of UpgradableDataHashMap function upgrade. */
    public void upgrade(){	
    }
    
    public Object clone() throws CloneNotSupportedException {
      HardTokenIssuer clone = new HardTokenIssuer();
      HashMap clonedata = (HashMap) clone.saveData();
      
      Iterator i = (data.keySet()).iterator();
      while(i.hasNext()){
        Object key = i.next();  
        clonedata.put(key,data.get(key)); 
      }
      
      clone.loadData(clonedata);
      return clone;
    }    

}
