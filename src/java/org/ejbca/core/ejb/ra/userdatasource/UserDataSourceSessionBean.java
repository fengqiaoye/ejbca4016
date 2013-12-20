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

package org.ejbca.core.ejb.ra.userdatasource;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.log.LogSessionLocal;
import org.ejbca.core.ejb.JndiHelper;
import org.ejbca.core.ejb.authorization.AuthorizationSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CaSessionLocal;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.log.LogConstants;
import org.ejbca.core.model.ra.userdatasource.BaseUserDataSource;
import org.ejbca.core.model.ra.userdatasource.CustomUserDataSourceContainer;
import org.ejbca.core.model.ra.userdatasource.MultipleMatchException;
import org.ejbca.core.model.ra.userdatasource.UserDataSourceConnectionException;
import org.ejbca.core.model.ra.userdatasource.UserDataSourceException;
import org.ejbca.core.model.ra.userdatasource.UserDataSourceExistsException;
import org.ejbca.core.model.ra.userdatasource.UserDataSourceVO;
import org.ejbca.util.Base64GetHashMap;
import org.ejbca.util.ProfileID;

/**
 * Stores data used by web server clients.
 * 
 * @version $Id: UserDataSourceSessionBean.java 12738 2011-09-27 15:22:25Z primelars $
 */
@Stateless(mappedName = JndiHelper.APP_JNDI_PREFIX + "UserDataSourceSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class UserDataSourceSessionBean implements UserDataSourceSessionLocal, UserDataSourceSessionRemote {

	private static final Logger log = Logger.getLogger(UserDataSourceSessionBean.class);
	/** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();
    
    @PersistenceContext(unitName="ejbca")
    private EntityManager entityManager;

    @EJB
    private AuthorizationSessionLocal authorizationSession;
    @EJB
    private CaSessionLocal caSession;
    @EJB
    private LogSessionLocal logSession;

    @Override
    public Collection<UserDataSourceVO> fetch(Admin admin, Collection<Integer> userdatasourceids, String searchstring) throws AuthorizationDeniedException, UserDataSourceException{
    	Iterator<Integer> iter = userdatasourceids.iterator();
    	ArrayList<UserDataSourceVO> result = new ArrayList<UserDataSourceVO>();
    	while (iter.hasNext()) {
    		Integer id = iter.next();
    		UserDataSourceData pdl = UserDataSourceData.findById(entityManager, id);
    		if (pdl != null) {
    			BaseUserDataSource userdatasource = getUserDataSource(pdl);
    			if(isAuthorizedToUserDataSource(admin,id.intValue(),userdatasource,false)){
    				try {
    					result.addAll(getUserDataSource(pdl).fetchUserDataSourceVOs(admin,searchstring));
    					String msg = intres.getLocalizedMessage("userdatasource.fetcheduserdatasource", pdl.getName());            	
    					logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null,
    							null, LogConstants.EVENT_INFO_USERDATAFETCHED,msg);
    				} catch (UserDataSourceException pe) {
    					String msg = intres.getLocalizedMessage("userdatasource.errorfetchuserdatasource", pdl.getName());            	
    					logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null,
    							null, LogConstants.EVENT_ERROR_USERDATAFETCHED,msg);
    					throw pe;
    				}
    			}else{
    				String msg = intres.getLocalizedMessage("userdatasource.errornotauth", pdl.getName());
    				logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE,msg);
    			}
    		} else {
    			String msg = intres.getLocalizedMessage("userdatasource.erroruserdatasourceexist", id);            	
    			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null,
    					LogConstants.EVENT_ERROR_USERDATAFETCHED, msg);
    			throw new UserDataSourceException(msg);
    		}
    	}
        return result;
    }

    @Override
    public boolean removeUserData(Admin admin, Collection<Integer> userdatasourceids, String searchstring, boolean removeMultipleMatch) throws AuthorizationDeniedException, MultipleMatchException, UserDataSourceException{
    	boolean retval = false;
    	Iterator<Integer> iter = userdatasourceids.iterator();
    	while (iter.hasNext()) {
    		Integer id = iter.next();
    		UserDataSourceData pdl = UserDataSourceData.findById(entityManager, id);
    		if (pdl != null) {
    			BaseUserDataSource userdatasource = getUserDataSource(pdl);
    			if(isAuthorizedToUserDataSource(admin,id.intValue(),userdatasource,true)){
    				try {
    					retval = retval || getUserDataSource(pdl).removeUserData(admin, searchstring, removeMultipleMatch);
    					String msg = intres.getLocalizedMessage("userdatasource.removeduserdata", pdl.getName());            	
    					logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null,
    							null, LogConstants.EVENT_INFO_USERDATAREMOVED,msg);
    				} catch (UserDataSourceException pe) {
    					String msg = intres.getLocalizedMessage("userdatasource.errorremovinguserdatasource", pdl.getName());            	
    					logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null,
    							null, LogConstants.EVENT_ERROR_USERDATAREMOVED,msg);
    					throw pe;

    				}
    			}else{
    				String msg = intres.getLocalizedMessage("userdatasource.errornotauth", pdl.getName());
    				logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE,msg);
    			}
    		} else {
    			String msg = intres.getLocalizedMessage("userdatasource.erroruserdatasourceexist", id);            	
    			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null,
    					LogConstants.EVENT_ERROR_USERDATAREMOVED, msg);
    			throw new UserDataSourceException(msg);
    		}
    	}
    	return retval;
    }

    @Override
    public void testConnection(Admin admin, int userdatasourceid) throws UserDataSourceConnectionException {
    	if (log.isTraceEnabled()) {
            log.trace(">testConnection(id: " + userdatasourceid + ")");
    	}
    	UserDataSourceData pdl = UserDataSourceData.findById(entityManager, userdatasourceid);
    	if (pdl != null) {
        	BaseUserDataSource userdatasource = getUserDataSource(pdl);
        	if(isAuthorizedToEditUserDataSource(admin,userdatasource)){
        		try {
        			userdatasource.testConnection(admin);
        			String msg = intres.getLocalizedMessage("userdatasource.testedcon", pdl.getName());            	
        			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null,
        					null, LogConstants.EVENT_INFO_USERDATASOURCEDATA,msg);
        		} catch (UserDataSourceConnectionException pe) {
        			String msg = intres.getLocalizedMessage("userdatasource.errortestcon", pdl.getName());            	
        			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null,
        					LogConstants.EVENT_ERROR_USERDATASOURCEDATA, msg, pe);        			
        			throw pe;
        		}
        	}else{
    			String msg = intres.getLocalizedMessage("userdatasource.errortestconauth", pdl.getName());            	
            	logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE,msg);
        	}
    	} else {
			String msg = intres.getLocalizedMessage("userdatasource.erroruserdatasourceexist", Integer.valueOf(userdatasourceid));            	
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_USERDATASOURCEDATA, msg);
        }
    	if (log.isTraceEnabled()) {
            log.trace("<testConnection(id: " + userdatasourceid + ")");
    	}
    }

    @Override
    public void addUserDataSource(Admin admin, String name, BaseUserDataSource userdatasource) throws UserDataSourceExistsException {
    	if (log.isTraceEnabled()) {
            log.trace(">addUserDataSource(name: " + name + ")");
    	}
        addUserDataSource(admin,findFreeUserDataSourceId(),name,userdatasource);
        log.trace("<addUserDataSource()");
    }

    @Override
    public void addUserDataSource(Admin admin, int id, String name, BaseUserDataSource userdatasource) throws UserDataSourceExistsException {
    	if (log.isTraceEnabled()) {
            log.trace(">addUserDataSource(name: " + name + ", id: " + id + ")");
    	}
        boolean success = false;
        if (isAuthorizedToEditUserDataSource(admin,userdatasource)) {
        	if (UserDataSourceData.findByName(entityManager, name) == null) {
        		if (UserDataSourceData.findById(entityManager, id) == null) {
        			try {
        				entityManager.persist(new UserDataSourceData(Integer.valueOf(id), name, userdatasource));
        				success = true;
        			} catch (Exception e) {
        				log.error("Unexpected error creating new user data source: ", e);
        			}
        		}
        	}
        	if (success) {
    			String msg = intres.getLocalizedMessage("userdatasource.addedsource", name);            	
        		logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_USERDATASOURCEDATA, msg);
        	} else {
    			String msg = intres.getLocalizedMessage("userdatasource.erroraddsource", name);            	
        		logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_USERDATASOURCEDATA, msg);
        		throw new UserDataSourceExistsException();
        	}
        } else {
			String msg = intres.getLocalizedMessage("userdatasource.errornotauth", name);            	
        	logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE,msg);
        }
        log.trace("<addUserDataSource()");
    }

    @Override
    public void changeUserDataSource(Admin admin, String name, BaseUserDataSource userdatasource) {
    	if (log.isTraceEnabled()) {
            log.trace(">changeUserDataSource(name: " + name + ")");
    	}
        boolean success = false;
        if(isAuthorizedToEditUserDataSource(admin,userdatasource)){
        	UserDataSourceData htp = UserDataSourceData.findByName(entityManager, name);
        	if (htp != null) {
        		htp.setUserDataSource(userdatasource);
        		success = true;
        	}
        	if (success) {
    			String msg = intres.getLocalizedMessage("userdatasource.changedsource", name);            	
        		logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_USERDATASOURCEDATA, msg);
        	} else {
    			String msg = intres.getLocalizedMessage("userdatasource.errorchangesource", name);            	
        		logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_USERDATASOURCEDATA, msg);
        	}
        }else{
			String msg = intres.getLocalizedMessage("userdatasource.errornotauth", name);            	
        	logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE,msg);
        }
        log.trace("<changeUserDataSource()");
    }

    @Override
    public void cloneUserDataSource(Admin admin, String oldname, String newname) throws UserDataSourceExistsException {
    	if (log.isTraceEnabled()) {
            log.trace(">cloneUserDataSource(name: " + oldname + ")");
    	}
        BaseUserDataSource userdatasourcedata = null;
        UserDataSourceData htp = UserDataSourceData.findByName(entityManager, oldname);
        if (htp == null) {
			String msg = intres.getLocalizedMessage("userdatasource.errorclonesource", newname, oldname);            	
            log.error(msg);
            throw new EJBException(msg);
        }
        try {
        	userdatasourcedata = (BaseUserDataSource) getUserDataSource(htp).clone();
        	if(isAuthorizedToEditUserDataSource(admin,userdatasourcedata)){                   		
        		try {
        			addUserDataSource(admin, newname, userdatasourcedata);
        			String msg = intres.getLocalizedMessage("userdatasource.clonedsource", newname, oldname);            	
        			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_USERDATASOURCEDATA, msg);
        		} catch (UserDataSourceExistsException f) {
        			String msg = intres.getLocalizedMessage("userdatasource.errorclonesource", newname, oldname);            	
        			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_USERDATASOURCEDATA, msg);
        			throw f;
        		}        		
        	}else{
    			String msg = intres.getLocalizedMessage("userdatasource.errornotauth", oldname);            	
        		logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE,msg);
        	}            
        } catch (CloneNotSupportedException e) {
			String msg = intres.getLocalizedMessage("userdatasource.errorclonesource", newname, oldname);            	
            log.error(msg, e);
            throw new EJBException(e);
		}
        log.trace("<cloneUserDataSource()");
    }

    @Override
    public boolean removeUserDataSource(Admin admin, String name) {
    	if (log.isTraceEnabled()) {
    		log.trace(">removeUserDataSource(name: " + name + ")");
    	}
    	boolean retval = false;
    	UserDataSourceData htp = UserDataSourceData.findByName(entityManager, name);
    	try {
    		if (htp == null) {
    			throw new Exception("No such UserDataSource.");
    		}
    		BaseUserDataSource userdatasource = getUserDataSource(htp);
    		if(isAuthorizedToEditUserDataSource(admin,userdatasource)){
    			entityManager.remove(htp);
    			String msg = intres.getLocalizedMessage("userdatasource.removedsource", name);            	
    			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_USERDATASOURCEDATA, msg);
    			retval = true;
    		}else{
    			String msg = intres.getLocalizedMessage("userdatasource.errornotauth", name);            	
    			logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE,msg);
    		}
    	} catch (Exception e) {
    		String msg = intres.getLocalizedMessage("userdatasource.errorremovesource", name);            	
    		logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_USERDATASOURCEDATA, msg, e);
    	}
    	log.trace("<removeUserDataSource()");
    	return retval;
    }

    @Override
    public void renameUserDataSource(Admin admin, String oldname, String newname) throws UserDataSourceExistsException {
    	if (log.isTraceEnabled()) {
            log.trace(">renameUserDataSource(from " + oldname + " to " + newname + ")");
    	}
        boolean success = false;
        if (UserDataSourceData.findByName(entityManager, newname) == null) {
        	UserDataSourceData htp = UserDataSourceData.findByName(entityManager, oldname);
        	if (htp != null) {
            	if(isAuthorizedToEditUserDataSource(admin,getUserDataSource(htp))){
                  htp.setName(newname);
                  success = true;
            	}else{
        			String msg = intres.getLocalizedMessage("userdatasource.errornotauth", oldname);            	
            		logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE, msg);
            	}
            }
        }
        if (success) {
        	String msg = intres.getLocalizedMessage("userdatasource.renamedsource", oldname, newname);            	
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_USERDATASOURCEDATA, msg);
        } else {
            String msg = intres.getLocalizedMessage("userdatasource.errorrenamesource", oldname, newname);            	
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_USERDATASOURCEDATA, msg);
            throw new UserDataSourceExistsException();
        }
        log.trace("<renameUserDataSource()");
    }

    @Override
    public Collection<Integer> getAuthorizedUserDataSourceIds(Admin admin, boolean includeAnyCA) {
        HashSet<Integer> returnval = new HashSet<Integer>();
        boolean superadmin = false;
        // If superadmin return all available user data sources
        superadmin = authorizationSession.isAuthorizedNoLog(admin, AccessRulesConstants.ROLE_SUPERADMINISTRATOR);
        Collection<Integer> authorizedcas = caSession.getAvailableCAs(admin);
        Iterator<UserDataSourceData> i = UserDataSourceData.findAll(entityManager).iterator();
        while (i.hasNext()) {
        	UserDataSourceData next = i.next();
        	if(superadmin){
        		returnval.add(next.getId());
        	}else{
        		BaseUserDataSource userdatasource = getUserDataSource(next);
        		if(userdatasource.getApplicableCAs().contains(Integer.valueOf(BaseUserDataSource.ANYCA))){
        			if(includeAnyCA){
        				returnval.add(next.getId());
        			}
        		}else{
        			if(authorizedcas.containsAll(userdatasource.getApplicableCAs())){
        				returnval.add(next.getId());
        			}
        		}
        	}
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public HashMap<Integer,String> getUserDataSourceIdToNameMap(Admin admin) {
        HashMap<Integer,String> returnval = new HashMap<Integer,String>();
        Collection<UserDataSourceData> result = UserDataSourceData.findAll(entityManager);
        Iterator<UserDataSourceData> i = result.iterator();
        while (i.hasNext()) {
        	UserDataSourceData next = i.next();
        	returnval.put(next.getId(), next.getName());
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public BaseUserDataSource getUserDataSource(Admin admin, String name) {
        BaseUserDataSource returnval = null;
        UserDataSourceData udsd = UserDataSourceData.findByName(entityManager, name);
        if (udsd != null) {
        	BaseUserDataSource result = getUserDataSource(udsd);
            if(isAuthorizedToEditUserDataSource(admin,result)){
            	returnval = result;
            }else{
    			String msg = intres.getLocalizedMessage("userdatasource.errornotauth", name);            	
        		logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE,msg);
            }
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public BaseUserDataSource getUserDataSource(Admin admin, int id) {
        BaseUserDataSource returnval = null;
        UserDataSourceData udsd = UserDataSourceData.findById(entityManager, id);
        if (udsd != null) {
        	BaseUserDataSource result = getUserDataSource(udsd);
            if(isAuthorizedToEditUserDataSource(admin,result)){
            	returnval = result;
            }else{
    			String msg = intres.getLocalizedMessage("userdatasource.errornotauth", Integer.valueOf(id));            	
        		logSession.log(admin, admin.getCaId(),LogConstants.MODULE_RA,new Date(),null,null,LogConstants.EVENT_ERROR_NOTAUTHORIZEDTORESOURCE,msg);
            }
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public int getUserDataSourceUpdateCount(Admin admin, int userdatasourceid) {
        int returnval = 0;
        UserDataSourceData udsd = UserDataSourceData.findById(entityManager, userdatasourceid);
        if (udsd != null) {
        	returnval = udsd.getUpdateCounter();
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public int getUserDataSourceId(Admin admin, String name) {
        int returnval = 0;
        UserDataSourceData udsd = UserDataSourceData.findByName(entityManager, name);
        if (udsd != null) {
        	returnval = udsd.getId();
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public String getUserDataSourceName(Admin admin, int id) {
    	if (log.isTraceEnabled()) {
            log.trace(">getUserDataSourceName(id: " + id + ")");
    	}
        String returnval = null;
        UserDataSourceData udsd = UserDataSourceData.findById(entityManager, id);
        if (udsd != null) {
        	returnval = udsd.getName();
        }
        log.trace("<getUserDataSourceName()");
        return returnval;
    }
    
    /**
     * Method to check if an admin is authorized to fetch user data from userdata source
     * The following checks are performed.
     * 
     * 1. If the admin is an administrator
     * 2. If the admin is authorized to all cas applicable to userdata source.
     *    or
     *    If the userdatasource have "ANYCA" set.
     * 3. The admin is authorized to the fetch or remove rule depending on the remove parameter
     * @param if the call is aremove call, othervise fetch authorization is used.
     * @return true if the administrator is authorized
     */
    private boolean isAuthorizedToUserDataSource(Admin admin, int id,  BaseUserDataSource userdatasource,boolean remove) {    	
    	if(authorizationSession.isAuthorizedNoLog(admin,AccessRulesConstants.ROLE_SUPERADMINISTRATOR)){
    		return true;
        }
        if (remove) {
            if(!authorizationSession.isAuthorized(admin, AccessRulesConstants.USERDATASOURCEPREFIX + id + AccessRulesConstants.UDS_REMOVE_RIGHTS)) {
                return false;
            }
        } else {
            if(!authorizationSession.isAuthorized(admin, AccessRulesConstants.USERDATASOURCEPREFIX + id + AccessRulesConstants.UDS_FETCH_RIGHTS)) {
                return false;
            }
        }
        if (authorizationSession.isAuthorizedNoLog(admin, AccessRulesConstants.ROLE_ADMINISTRATOR)) {
            if (userdatasource.getApplicableCAs().contains(Integer.valueOf(BaseUserDataSource.ANYCA))) {
                return true;
            }
            Collection<Integer> authorizedcas = caSession.getAvailableCAs(admin);
            if (authorizedcas.containsAll(userdatasource.getApplicableCAs())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Method to check if an admin is authorized to edit an user data source
     * The following checks are performed.
     * 
     * 1. If the admin is an administrator
     * 2. If tha admin is authorized AccessRulesConstants.REGULAR_EDITUSERDATASOURCES
     * 3. Only the superadmin should have edit access to user data sources with 'ANYCA' set
     * 4. Administrators should be authorized to all the user data source applicable cas.
     * 
     * @return true if the administrator is authorized
     */
    private boolean isAuthorizedToEditUserDataSource(Admin admin, BaseUserDataSource userdatasource) {

        if (authorizationSession.isAuthorizedNoLog(admin, AccessRulesConstants.ROLE_SUPERADMINISTRATOR)) {
            return true;
        }

        if (authorizationSession.isAuthorizedNoLog(admin, AccessRulesConstants.ROLE_ADMINISTRATOR)
                && authorizationSession.isAuthorizedNoLog(admin, AccessRulesConstants.REGULAR_EDITUSERDATASOURCES)) {
            if (userdatasource.getApplicableCAs().contains(Integer.valueOf(BaseUserDataSource.ANYCA))) {
                return false;
            }
            Collection<Integer> authorizedcas = caSession.getAvailableCAs(admin);
            if (authorizedcas.containsAll(userdatasource.getApplicableCAs())) {
                return true;
            }
        }

        return false;
    }

    private int findFreeUserDataSourceId() {
		final ProfileID.DB db = new ProfileID.DB() {
			@Override
			public boolean isFree(int i) {
				return UserDataSourceData.findById(UserDataSourceSessionBean.this.entityManager, i)==null;
			}
		};
		return ProfileID.getNotUsedID(db);
    }

    /** Method that returns the UserDataSource data and updates it if necessary. */
    private BaseUserDataSource getUserDataSource(UserDataSourceData udsData) {
    	BaseUserDataSource userdatasource = udsData.getCachedUserDataSource();
        if (userdatasource == null) {
        	java.beans.XMLDecoder decoder;
        	try {
        		decoder = new java.beans.XMLDecoder(new java.io.ByteArrayInputStream(udsData.getData().getBytes("UTF8")));
        	} catch (UnsupportedEncodingException e) {
        		throw new EJBException(e);
        	}
        	HashMap h = (HashMap) decoder.readObject();
        	decoder.close();
        	// Handle Base64 encoded string values
        	HashMap data = new Base64GetHashMap(h);
        	switch (((Integer) (data.get(BaseUserDataSource.TYPE))).intValue()) {
        	case CustomUserDataSourceContainer.TYPE_CUSTOMUSERDATASOURCECONTAINER:
        		userdatasource = new CustomUserDataSourceContainer();
        		break;
        	}
        	userdatasource.loadData(data);
    	}
    	return userdatasource;
    }
}
