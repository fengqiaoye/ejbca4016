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

package org.ejbca.core.ejb.hardtoken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.authorization.AdminGroupSessionLocal;
import org.cesecore.core.ejb.ca.store.CertificateProfileSessionLocal;
import org.cesecore.core.ejb.log.LogSessionLocal;
import org.ejbca.core.ejb.JndiHelper;
import org.ejbca.core.ejb.authorization.AuthorizationSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CaSessionLocal;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionLocal;
import org.ejbca.core.ejb.config.GlobalConfigurationSessionLocal;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.HardTokenEncryptCAServiceRequest;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.HardTokenEncryptCAServiceResponse;
import org.ejbca.core.model.hardtoken.HardTokenData;
import org.ejbca.core.model.hardtoken.HardTokenDoesntExistsException;
import org.ejbca.core.model.hardtoken.HardTokenExistsException;
import org.ejbca.core.model.hardtoken.HardTokenIssuer;
import org.ejbca.core.model.hardtoken.HardTokenIssuerData;
import org.ejbca.core.model.hardtoken.HardTokenProfileExistsException;
import org.ejbca.core.model.hardtoken.UnavailableTokenException;
import org.ejbca.core.model.hardtoken.profiles.EIDProfile;
import org.ejbca.core.model.hardtoken.profiles.EnhancedEIDProfile;
import org.ejbca.core.model.hardtoken.profiles.HardTokenProfile;
import org.ejbca.core.model.hardtoken.profiles.SwedishEIDProfile;
import org.ejbca.core.model.hardtoken.profiles.TurkishEIDProfile;
import org.ejbca.core.model.hardtoken.types.EIDHardToken;
import org.ejbca.core.model.hardtoken.types.EnhancedEIDHardToken;
import org.ejbca.core.model.hardtoken.types.HardToken;
import org.ejbca.core.model.hardtoken.types.SwedishEIDHardToken;
import org.ejbca.core.model.hardtoken.types.TurkishEIDHardToken;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.log.LogConstants;
import org.ejbca.core.model.ra.UserAdminConstants;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.util.Base64GetHashMap;
import org.ejbca.util.CertTools;
import org.ejbca.util.ProfileID;

/**
 * Stores data used by web server clients. Uses JNDI name for datasource as
 * defined in env 'Datasource' in ejb-jar.xml.
 * 
 * @version $Id: HardTokenSessionBean.java 15212 2012-08-06 12:05:13Z mikekushner $
 */
@Stateless(mappedName = JndiHelper.APP_JNDI_PREFIX + "HardTokenSessionRemote")
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class HardTokenSessionBean implements HardTokenSessionLocal, HardTokenSessionRemote {

    private static final Logger log = Logger.getLogger(EjbcaHardTokenBatchJobSessionBean.class);
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();


    @PersistenceContext(unitName = "ejbca")
    private EntityManager entityManager;

    @EJB
    private AdminGroupSessionLocal adminGroupSession;
    @EJB
    private AuthorizationSessionLocal authorizationSession;
    @EJB
    private CertificateProfileSessionLocal certificateProfileSession;
    @EJB
    private CertificateStoreSessionLocal certificateStoreSession;
    @EJB
    private CAAdminSessionLocal caAdminSession;
    @EJB
    private CaSessionLocal caSession;
    @EJB
    private LogSessionLocal logSession;
    @EJB
    private GlobalConfigurationSessionLocal globalConfigurationSession;

    private static final String ENCRYPTEDDATA = "ENCRYPTEDDATA";
    public static final int NO_ISSUER = 0;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void addHardTokenProfile(Admin admin, String name, HardTokenProfile profile) throws HardTokenProfileExistsException {
        if (log.isTraceEnabled()) {
            log.trace(">addHardTokenProfile(name: " + name + ")");
        }
        addHardTokenProfile(admin, findFreeHardTokenProfileId(), name, profile);
        log.trace("<addHardTokenProfile()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void addHardTokenProfile(Admin admin, int profileid, String name, HardTokenProfile profile) throws HardTokenProfileExistsException {
        if (log.isTraceEnabled()) {
            log.trace(">addHardTokenProfile(name: " + name + ", id: " + profileid + ")");
        }

        if (HardTokenProfileData.findByName(entityManager, name) == null && HardTokenProfileData.findByPK(entityManager, profileid) == null) {
            entityManager.persist(new HardTokenProfileData(profileid, name, profile));
            String msg = intres.getLocalizedMessage("hardtoken.addedprofile", name);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_INFO_HARDTOKENPROFILEDATA, msg);
        } else {
            String msg = intres.getLocalizedMessage("hardtoken.erroraddprofile", name);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_HARDTOKENPROFILEDATA, msg);
            throw new HardTokenProfileExistsException();
        }
        log.trace("<addHardTokenProfile()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void changeHardTokenProfile(Admin admin, String name, HardTokenProfile profile) {
        if (log.isTraceEnabled()) {
            log.trace(">changeHardTokenProfile(name: " + name + ")");
        }
        HardTokenProfileData htp = HardTokenProfileData.findByName(entityManager, name);
        if (htp != null) {
            htp.setHardTokenProfile(profile);
            String msg = intres.getLocalizedMessage("hardtoken.editedprofile", name);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_INFO_HARDTOKENPROFILEDATA, msg);
        } else {
            String msg = intres.getLocalizedMessage("hardtoken.erroreditprofile", name);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_HARDTOKENPROFILEDATA, msg);
        }
        log.trace("<changeHardTokenProfile()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void cloneHardTokenProfile(Admin admin, String oldname, String newname) throws HardTokenProfileExistsException {
        if (log.isTraceEnabled()) {
            log.trace(">cloneHardTokenProfile(name: " + oldname + ")");
        }
        HardTokenProfileData htp = HardTokenProfileData.findByName(entityManager, oldname);
        try {
            HardTokenProfile profiledata = (HardTokenProfile) getHardTokenProfile(htp).clone();
            try {
                addHardTokenProfile(admin, newname, profiledata);
                String msg = intres.getLocalizedMessage("hardtoken.clonedprofile", newname, oldname);
                logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                        LogConstants.EVENT_INFO_HARDTOKENPROFILEDATA, msg);
            } catch (HardTokenProfileExistsException f) {
                String msg = intres.getLocalizedMessage("hardtoken.errorcloneprofile", newname, oldname);
                logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                        LogConstants.EVENT_ERROR_HARDTOKENPROFILEDATA, msg);
                throw f;
            }
        } catch (CloneNotSupportedException e) {
            throw new EJBException(e);
        }
        log.trace("<cloneHardTokenProfile()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void removeHardTokenProfile(Admin admin, String name) {
        if (log.isTraceEnabled()) {
            log.trace(">removeHardTokenProfile(name: " + name + ")");
        }
        try {
            HardTokenProfileData htp = HardTokenProfileData.findByName(entityManager, name);
            if (htp == null) {
            	if (log.isDebugEnabled()) {
            		log.debug("Trying to remove HardTokenProfileData that does not exist: "+name);                		
            	}
            } else {
            	entityManager.remove(htp);
                String msg = intres.getLocalizedMessage("hardtoken.removedprofile", name);
                logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                        LogConstants.EVENT_INFO_HARDTOKENPROFILEDATA, msg);
            }
        } catch (Exception e) {
            String msg = intres.getLocalizedMessage("hardtoken.errorremoveprofile", name);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_HARDTOKENPROFILEDATA, msg, e);
        }
        log.trace("<removeHardTokenProfile()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void renameHardTokenProfile(Admin admin, String oldname, String newname) throws HardTokenProfileExistsException {
        if (log.isTraceEnabled()) {
            log.trace(">renameHardTokenProfile(from " + oldname + " to " + newname + ")");
        }
        boolean success = false;
        if (HardTokenProfileData.findByName(entityManager, newname) == null) {
            HardTokenProfileData htp = HardTokenProfileData.findByName(entityManager, oldname);
            if (htp != null) {
                htp.setName(newname);
                success = true;
            }
        }
        if (success) {
            String msg = intres.getLocalizedMessage("hardtoken.renamedprofile", oldname, newname);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_INFO_HARDTOKENPROFILEDATA, msg);
        } else {
            String msg = intres.getLocalizedMessage("hardtoken.errorrenameprofile", oldname, newname);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_HARDTOKENPROFILEDATA, msg);
            throw new HardTokenProfileExistsException();
        }
        log.trace("<renameHardTokenProfile()");
    }

    @Override
    public Collection<Integer> getAuthorizedHardTokenProfileIds(Admin admin) {
        ArrayList<Integer> returnval = new ArrayList<Integer>();
        HashSet<Integer> authorizedcertprofiles = new HashSet<Integer>(certificateProfileSession.getAuthorizedCertificateProfileIds(admin,
                SecConst.CERTTYPE_HARDTOKEN, caSession.getAvailableCAs(admin)));
        // It should be possible to indicate that a certificate should not be generated by not specifying a cert profile for this key. 
        authorizedcertprofiles.add(new Integer(SecConst.CERTPROFILE_NO_PROFILE));
        HashSet<Integer> authorizedcaids = new HashSet<Integer>(caSession.getAvailableCAs(admin));
        Collection<HardTokenProfileData> result = HardTokenProfileData.findAll(entityManager);
        Iterator<HardTokenProfileData> i = result.iterator();
        while (i.hasNext()) {
            HardTokenProfileData next = i.next();
            HardTokenProfile profile = getHardTokenProfile(next);
            if (profile instanceof EIDProfile) {
                if (authorizedcertprofiles.containsAll(((EIDProfile) profile).getAllCertificateProfileIds())
                        && authorizedcaids.containsAll(((EIDProfile) profile).getAllCAIds())) {
                    returnval.add(next.getId());
                }
            } else {
                // Implement for other profile types
            }
        }
        return returnval;
    }

    @Override
    public HashMap<Integer, String> getHardTokenProfileIdToNameMap(Admin admin) {
        HashMap<Integer, String> returnval = new HashMap<Integer, String>();
        Collection<HardTokenProfileData> result = HardTokenProfileData.findAll(entityManager);
        Iterator<HardTokenProfileData> i = result.iterator();
        while (i.hasNext()) {
            HardTokenProfileData next = i.next();
            returnval.put(next.getId(), next.getName());
        }
        return returnval;
    }

    @Override
    public HardTokenProfile getHardTokenProfile(Admin admin, String name) {
        HardTokenProfile returnval = null;
        HardTokenProfileData htpd = HardTokenProfileData.findByName(entityManager, name);
        if (htpd != null) {
            returnval = getHardTokenProfile(htpd);
        }
        return returnval;
    }

    @Override
    public HardTokenProfile getHardTokenProfile(Admin admin, int id) {
        HardTokenProfile returnval = null;
        HardTokenProfileData htpd = HardTokenProfileData.findByPK(entityManager, Integer.valueOf(id));
        if (htpd != null) {
            returnval = getHardTokenProfile(htpd);
        }
        return returnval;
    }

    @Override
    public int getHardTokenProfileUpdateCount(Admin admin, int hardtokenprofileid) {
        int returnval = 0;
        HardTokenProfileData htpd = HardTokenProfileData.findByPK(entityManager, Integer.valueOf(hardtokenprofileid));
        if (htpd != null) {
            returnval = htpd.getUpdateCounter();
        }
        return returnval;
    }

    @Override
    public int getHardTokenProfileId(Admin admin, String name) {
        int returnval = 0;
        HardTokenProfileData htpd = HardTokenProfileData.findByName(entityManager, name);
        if (htpd != null) {
            returnval = htpd.getId();
        }
        return returnval;
    }

    @Override
    public String getHardTokenProfileName(Admin admin, int id) {
        if (log.isTraceEnabled()) {
            log.trace(">getHardTokenProfileName(id: " + id + ")");
        }
        String returnval = null;
        HardTokenProfileData htpd = HardTokenProfileData.findByPK(entityManager, Integer.valueOf(id));
        if (htpd != null) {
            returnval = htpd.getName();
        }
        log.trace("<getHardTokenProfileName()");
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public boolean addHardTokenIssuer(Admin admin, String alias, int admingroupid, HardTokenIssuer issuerdata) {
        if (log.isTraceEnabled()) {
            log.trace(">addHardTokenIssuer(alias: " + alias + ")");
        }
        boolean returnval = false;
        if (org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByAlias(entityManager, alias) == null) {
            try {
                entityManager.persist(new org.ejbca.core.ejb.hardtoken.HardTokenIssuerData(findFreeHardTokenIssuerId(), alias, admingroupid, issuerdata));
                returnval = true;
            } catch (Exception e) {
            }
        }
        if (returnval) {
            String msg = intres.getLocalizedMessage("hardtoken.addedissuer", alias);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_INFO_HARDTOKENISSUERDATA, msg);
        } else {
            String msg = intres.getLocalizedMessage("hardtoken.erroraddissuer", alias);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_HARDTOKENISSUERDATA, msg);
        }
        log.trace("<addHardTokenIssuer()");
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public boolean changeHardTokenIssuer(Admin admin, String alias, HardTokenIssuer issuerdata) {
        if (log.isTraceEnabled()) {
            log.trace(">changeHardTokenIssuer(alias: " + alias + ")");
        }
        boolean returnvalue = false;
        org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByAlias(entityManager, alias);
        if (htih != null) {
            htih.setHardTokenIssuer(issuerdata);
            String msg = intres.getLocalizedMessage("hardtoken.editedissuer", alias);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_INFO_HARDTOKENISSUERDATA, msg);
            returnvalue = true;
        } else {
            String msg = intres.getLocalizedMessage("hardtoken.erroreditissuer", alias);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_HARDTOKENISSUERDATA, msg);
        }
        log.trace("<changeHardTokenIssuer()");
        return returnvalue;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public boolean cloneHardTokenIssuer(Admin admin, String oldalias, String newalias, int admingroupid) {
        if (log.isTraceEnabled()) {
            log.trace(">cloneHardTokenIssuer(alias: " + oldalias + ")");
        }
        boolean returnval = false;
        org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByAlias(entityManager, oldalias);
        if (htih != null) {
            try {
            	HardTokenIssuer issuerdata = (HardTokenIssuer) htih.getHardTokenIssuer().clone();
                returnval = addHardTokenIssuer(admin, newalias, admingroupid, issuerdata);
            } catch (CloneNotSupportedException e) {
            }
        }
        if (returnval) {
            String msg = intres.getLocalizedMessage("hardtoken.clonedissuer", newalias, oldalias);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_INFO_HARDTOKENISSUERDATA, msg);
        } else {
            String msg = intres.getLocalizedMessage("hardtoken.errorcloneissuer", newalias, oldalias);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_HARDTOKENISSUERDATA, msg);
        }
        log.trace("<cloneHardTokenIssuer()");
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void removeHardTokenIssuer(Admin admin, String alias) {
        if (log.isTraceEnabled()) {
            log.trace(">removeHardTokenIssuer(alias: " + alias + ")");
        }
        try {
            org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByAlias(entityManager, alias);
            if (htih == null) {
            	if (log.isDebugEnabled()) {
            		log.debug("Trying to remove HardTokenProfileData that does not exist: "+alias);                		
            	}
            } else {
            	entityManager.remove(htih);
            	String msg = intres.getLocalizedMessage("hardtoken.removedissuer", alias);
            	logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
            			LogConstants.EVENT_INFO_HARDTOKENISSUERDATA, msg);
            }
        } catch (Exception e) {
            String msg = intres.getLocalizedMessage("hardtoken.errorremoveissuer", alias);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_HARDTOKENISSUERDATA, msg, e);
        }
        log.trace("<removeHardTokenIssuer()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public boolean renameHardTokenIssuer(Admin admin, String oldalias, String newalias, int newadmingroupid) {
        if (log.isTraceEnabled()) {
            log.trace(">renameHardTokenIssuer(from " + oldalias + " to " + newalias + ")");
        }
        boolean returnvalue = false;
        if (org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByAlias(entityManager, newalias) == null) {
            org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByAlias(entityManager, oldalias);
            if (htih != null) {
                htih.setAlias(newalias);
                htih.setAdminGroupId(newadmingroupid);
                returnvalue = true;
            }
        }
        if (returnvalue) {
            String msg = intres.getLocalizedMessage("hardtoken.renameissuer", oldalias, newalias);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_INFO_HARDTOKENISSUERDATA, msg);
        } else {
            String msg = intres.getLocalizedMessage("hardtoken.errorrenameissuer", oldalias, newalias);
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null,
                    LogConstants.EVENT_ERROR_HARDTOKENISSUERDATA, msg);
        }
        log.trace("<renameHardTokenIssuer()");
        return returnvalue;
    }

    @Override
    public boolean getAuthorizedToHardTokenIssuer(Admin admin, String alias) {
        if (log.isTraceEnabled()) {
            log.trace(">getAuthorizedToHardTokenIssuer(" + alias + ")");
        }
        boolean returnval = false;
        org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByAlias(entityManager, alias);
        if (htih != null) {
            int admingroupid = htih.getAdminGroupId();
            returnval = authorizationSession.isAuthorizedNoLog(admin, "/hardtoken_functionality/issue_hardtokens")
                    && adminGroupSession.existsAdministratorInGroup(admin, admingroupid);

        }
        log.trace("<getAuthorizedToHardTokenIssuer(" + returnval + ")");
        return returnval;
    }

    @Override
    public Collection<HardTokenIssuerData> getHardTokenIssuerDatas(Admin admin) {
        log.trace(">getHardTokenIssuerDatas()");
        ArrayList<HardTokenIssuerData> returnval = new ArrayList<HardTokenIssuerData>();
        Collection<Integer> authorizedhardtokenprofiles = getAuthorizedHardTokenProfileIds(admin);
        Collection<org.ejbca.core.ejb.hardtoken.HardTokenIssuerData> result = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findAll(entityManager);
        Iterator<org.ejbca.core.ejb.hardtoken.HardTokenIssuerData> i = result.iterator();
        while (i.hasNext()) {
            org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = i.next();
            if (authorizedhardtokenprofiles.containsAll(htih.getHardTokenIssuer().getAvailableHardTokenProfiles())) {
                returnval.add(new HardTokenIssuerData(htih.getId(), htih.getAlias(), htih.getAdminGroupId(), htih.getHardTokenIssuer()));
            }
        }
        Collections.sort(returnval);
        log.trace("<getHardTokenIssuerDatas()");
        return returnval;
    }

    @Override
    public Collection<String> getHardTokenIssuerAliases(Admin admin) {
        log.trace(">getHardTokenIssuerAliases()");
        ArrayList<String> returnval = new ArrayList<String>();
        Collection<Integer> authorizedhardtokenprofiles = getAuthorizedHardTokenProfileIds(admin);
        Collection<org.ejbca.core.ejb.hardtoken.HardTokenIssuerData> result = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findAll(entityManager);
        Iterator<org.ejbca.core.ejb.hardtoken.HardTokenIssuerData> i = result.iterator();
        while (i.hasNext()) {
            org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = i.next();
            if (authorizedhardtokenprofiles.containsAll(htih.getHardTokenIssuer().getAvailableHardTokenProfiles())) {
                returnval.add(htih.getAlias());
            }
        }
        Collections.sort(returnval);
        log.trace("<getHardTokenIssuerAliases()");
        return returnval;
    }

    @Override
    public TreeMap<String, HardTokenIssuerData> getHardTokenIssuers(Admin admin) {
        log.trace(">getHardTokenIssuers()");
        Collection<Integer> authorizedhardtokenprofiles = getAuthorizedHardTokenProfileIds(admin);
        TreeMap<String, HardTokenIssuerData> returnval = new TreeMap<String, HardTokenIssuerData>();
        Collection<org.ejbca.core.ejb.hardtoken.HardTokenIssuerData> result = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findAll(entityManager);
        Iterator<org.ejbca.core.ejb.hardtoken.HardTokenIssuerData> i = result.iterator();
        while (i.hasNext()) {
            org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = i.next();
            if (authorizedhardtokenprofiles.containsAll(htih.getHardTokenIssuer().getAvailableHardTokenProfiles())) {
                returnval.put(htih.getAlias(), new HardTokenIssuerData(htih.getId(), htih.getAlias(), htih.getAdminGroupId(), htih
                        .getHardTokenIssuer()));
            }
        }
        log.trace("<getHardTokenIssuers()");
        return returnval;
    }

    @Override
    public HardTokenIssuerData getHardTokenIssuerData(Admin admin, String alias) {
        if (log.isTraceEnabled()) {
            log.trace(">getHardTokenIssuerData(alias: " + alias + ")");
        }
        HardTokenIssuerData returnval = null;
        org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByAlias(entityManager, alias);
        if (htih != null) {
            returnval = new HardTokenIssuerData(htih.getId(), htih.getAlias(), htih.getAdminGroupId(), htih.getHardTokenIssuer());
        }
        log.trace("<getHardTokenIssuerData()");
        return returnval;
    }

    @Override
    public HardTokenIssuerData getHardTokenIssuerData(Admin admin, int id) {
        if (log.isTraceEnabled()) {
            log.trace(">getHardTokenIssuerData(id: " + id + ")");
        }
        HardTokenIssuerData returnval = null;
        org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByPK(entityManager, Integer.valueOf(id));
        if (htih != null) {
            returnval = new HardTokenIssuerData(htih.getId(), htih.getAlias(), htih.getAdminGroupId(), htih.getHardTokenIssuer());
        }
        log.trace("<getHardTokenIssuerData()");
        return returnval;
    }

    @Override
    public int getNumberOfHardTokenIssuers(Admin admin) {
        log.trace(">getNumberOfHardTokenIssuers()");
        int returnval = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findAll(entityManager).size();
        log.trace("<getNumberOfHardTokenIssuers()");
        return returnval;
    }

    @Override
    public int getHardTokenIssuerId(Admin admin, String alias) {
        if (log.isTraceEnabled()) {
            log.trace(">getHardTokenIssuerId(alias: " + alias + ")");
        }
        int returnval = NO_ISSUER;
        org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByAlias(entityManager, alias);
        if (htih != null) {
            returnval = htih.getId();
        }
        log.trace("<getHardTokenIssuerId()");
        return returnval;
    }

    @Override
    public String getHardTokenIssuerAlias(Admin admin, int id) {
        if (log.isTraceEnabled()) {
            log.trace(">getHardTokenIssuerAlias(id: " + id + ")");
        }
        String returnval = null;
        org.ejbca.core.ejb.hardtoken.HardTokenIssuerData htih = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByPK(entityManager, Integer.valueOf(id));
        if (htih != null) {
            returnval = htih.getAlias();
        }
        log.trace("<getHardTokenIssuerAlias()");
        return returnval;
    }

    @Override
    public void getIsHardTokenProfileAvailableToIssuer(Admin admin, int issuerid, UserDataVO userdata) throws UnavailableTokenException {
        if (log.isTraceEnabled()) {
            log.trace(">getIsTokenTypeAvailableToIssuer(issuerid: " + issuerid + ", tokentype: " + userdata.getTokenType() + ")");
        }
        boolean returnval = false;
        ArrayList<Integer> availabletokentypes = getHardTokenIssuerData(admin, issuerid).getHardTokenIssuer().getAvailableHardTokenProfiles();
        for (int i = 0; i < availabletokentypes.size(); i++) {
            if (availabletokentypes.get(i).intValue() == userdata.getTokenType()) {
                returnval = true;
            }
        }
        if (!returnval) {
            String msg = intres.getLocalizedMessage("hardtoken.unavailabletoken", userdata.getUsername());
            throw new UnavailableTokenException(msg);
        }
        log.trace("<getIsTokenTypeAvailableToIssuer()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void addHardToken(Admin admin, String tokensn, String username, String significantissuerdn, int tokentype, HardToken hardtokendata,
            Collection<Certificate> certificates, String copyof) throws HardTokenExistsException {
        if (log.isTraceEnabled()) {
            log.trace(">addHardToken(tokensn : " + tokensn + ")");
        }
        String bcdn = CertTools.stringToBCDNString(significantissuerdn);
        org.ejbca.core.ejb.hardtoken.HardTokenData data = org.ejbca.core.ejb.hardtoken.HardTokenData.findByTokenSN(entityManager, tokensn);
        if (data == null) {
            try {
                entityManager.persist(new org.ejbca.core.ejb.hardtoken.HardTokenData(tokensn, username, new java.util.Date(), new java.util.Date(),
                        tokentype, bcdn, setHardToken(admin, globalConfigurationSession.getCachedGlobalConfiguration(admin).getHardTokenEncryptCA(),
                                hardtokendata)));
                if (certificates != null) {
                    Iterator<Certificate> i = certificates.iterator();
                    while (i.hasNext()) {
                        addHardTokenCertificateMapping(admin, tokensn, (X509Certificate)i.next());
                    }
                }
                if (copyof != null) {
                    entityManager.persist(new HardTokenPropertyData(tokensn, HardTokenPropertyData.PROPERTY_COPYOF, copyof));
                }
                String msg = intres.getLocalizedMessage("hardtoken.addedtoken", tokensn);
                logSession.log(admin, bcdn.hashCode(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), username, null,
                        LogConstants.EVENT_INFO_HARDTOKENDATA, msg);
            } catch (Exception e) {
                String msg = intres.getLocalizedMessage("hardtoken.tokenexists", tokensn);
                logSession.log(admin, bcdn.hashCode(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), username, null,
                        LogConstants.EVENT_ERROR_HARDTOKENDATA, msg);
                throw new HardTokenExistsException("Tokensn : " + tokensn);
            }
        } else {
            String msg = intres.getLocalizedMessage("hardtoken.tokenexists", tokensn);
            logSession.log(admin, bcdn.hashCode(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), username, null, LogConstants.EVENT_ERROR_HARDTOKENDATA,
                    msg);
            throw new HardTokenExistsException("Tokensn : " + tokensn);
        }
        log.trace("<addHardToken()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void changeHardToken(Admin admin, String tokensn, int tokentype, HardToken hardtokendata) throws HardTokenDoesntExistsException {
        if (log.isTraceEnabled()) {
            log.trace(">changeHardToken(tokensn : " + tokensn + ")");
        }
        int caid = LogConstants.INTERNALCAID;
        try {
            org.ejbca.core.ejb.hardtoken.HardTokenData htd = org.ejbca.core.ejb.hardtoken.HardTokenData.findByTokenSN(entityManager, tokensn);
            if (htd == null) {
                throw new FinderException();
            }
            htd.setTokenType(tokentype);
            htd.setData(setHardToken(admin, globalConfigurationSession.getCachedGlobalConfiguration(admin).getHardTokenEncryptCA(), hardtokendata));
            htd.setModifyTime(new java.util.Date());
            caid = htd.getSignificantIssuerDN().hashCode();
            String msg = intres.getLocalizedMessage("hardtoken.changedtoken", tokensn);
            logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), htd.getUsername(), null, LogConstants.EVENT_INFO_HARDTOKENDATA,
                    msg);
        } catch (Exception e) {
            String msg = intres.getLocalizedMessage("hardtoken.errorchangetoken", tokensn);
            logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_HARDTOKENDATA, msg);
            throw new HardTokenDoesntExistsException("Tokensn : " + tokensn);
        }
        log.trace("<changeHardToken()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void removeHardToken(Admin admin, String tokensn) throws HardTokenDoesntExistsException {
        if (log.isTraceEnabled()) {
            log.trace(">removeHardToken(tokensn : " + tokensn + ")");
        }
        int caid = LogConstants.INTERNALCAID;

        org.ejbca.core.ejb.hardtoken.HardTokenData htd = org.ejbca.core.ejb.hardtoken.HardTokenData.findByTokenSN(entityManager, tokensn);
        if (htd == null) {
            String msg = intres.getLocalizedMessage("hardtoken.errorremovetoken", tokensn);
            logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_HARDTOKENDATA, msg);
            throw new HardTokenDoesntExistsException("Tokensn : " + tokensn);
        }
        caid = htd.getSignificantIssuerDN().hashCode();
        entityManager.remove(htd);
        // Remove all certificate mappings.
        removeHardTokenCertificateMappings(admin, tokensn);
        // Remove all copyof references id property database if they exist.
        HardTokenPropertyData htpd = HardTokenPropertyData.findByProperty(entityManager, tokensn, HardTokenPropertyData.PROPERTY_COPYOF);
        if (htpd != null) {
            entityManager.remove(htpd);
        }

        for (HardTokenPropertyData hardTokenPropertyData : HardTokenPropertyData.findIdsByPropertyAndValue(entityManager,
                HardTokenPropertyData.PROPERTY_COPYOF, tokensn)) {
            entityManager.remove(hardTokenPropertyData);
        }
        String msg = intres.getLocalizedMessage("hardtoken.removedtoken", tokensn);
        logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_INFO_HARDTOKENDATA, msg);

        log.trace("<removeHardToken()");
    }

    @Override
    public boolean existsHardToken(Admin admin, String tokensn) {
        if (log.isTraceEnabled()) {
            log.trace(">existsHardToken(tokensn : " + tokensn + ")");
        }
        boolean ret = false;
        if (org.ejbca.core.ejb.hardtoken.HardTokenData.findByTokenSN(entityManager, tokensn) != null) {
            ret = true;
        }
        log.trace("<existsHardToken()");
        return ret;
    }

    @Override
    public HardTokenData getHardToken(Admin admin, String tokensn, boolean includePUK) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace("<getHardToken(tokensn :" + tokensn + ")");
        }
        HardTokenData returnval = null;
        org.ejbca.core.ejb.hardtoken.HardTokenData htd = org.ejbca.core.ejb.hardtoken.HardTokenData.findByTokenSN(entityManager, tokensn);
        if (htd != null) {
            // Find Copyof
            String copyof = null;
            HardTokenPropertyData htpd = HardTokenPropertyData.findByProperty(entityManager, tokensn, HardTokenPropertyData.PROPERTY_COPYOF);
            if (htpd != null) {
                copyof = htpd.getValue();
            }
            ArrayList<String> copies = null;
            if (copyof == null) {
                // Find Copies
                Collection<HardTokenPropertyData> copieslocal = HardTokenPropertyData.findIdsByPropertyAndValue(entityManager,
                        HardTokenPropertyData.PROPERTY_COPYOF, tokensn);
                if (copieslocal.size() > 0) {
                    copies = new ArrayList<String>();
                    Iterator<HardTokenPropertyData> iter = copieslocal.iterator();
                    while (iter.hasNext()) {
                        copies.add(iter.next().getId());
                    }
                }
            }
            if (htd != null) {
                returnval = new HardTokenData(htd.getTokenSN(), htd.getUsername(), htd.getCreateTime(), htd.getModifyTime(), htd.getTokenType(), htd
                        .getSignificantIssuerDN(), getHardToken(admin, globalConfigurationSession.getCachedGlobalConfiguration(admin).getHardTokenEncryptCA(),
                        includePUK, htd.getData()), copyof, copies);
                String msg = intres.getLocalizedMessage("hardtoken.viewedtoken", tokensn);
                logSession.log(admin, htd.getSignificantIssuerDN().hashCode(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), htd.getUsername(), null,
                        LogConstants.EVENT_INFO_HARDTOKENVIEWED, msg);
                if (includePUK) {
                    msg = intres.getLocalizedMessage("hardtoken.viewedpuk", tokensn);
                    logSession.log(admin, htd.getSignificantIssuerDN().hashCode(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), htd.getUsername(),
                            null, LogConstants.EVENT_INFO_PUKVIEWED, msg);
                }
            }
        }
        log.trace("<getHardToken()");
        return returnval;
    }

    @Override
    public Collection<HardTokenData> getHardTokens(Admin admin, String username, boolean includePUK) {
        if (log.isTraceEnabled()) {
            log.trace("<getHardToken(username :" + username + ")");
        }
        final ArrayList<HardTokenData> returnval = new ArrayList<HardTokenData>();
        final Collection<org.ejbca.core.ejb.hardtoken.HardTokenData> result = org.ejbca.core.ejb.hardtoken.HardTokenData.findByUsername(entityManager, username);
        final Iterator<org.ejbca.core.ejb.hardtoken.HardTokenData> i = result.iterator();
        for (org.ejbca.core.ejb.hardtoken.HardTokenData htd : result) {
            // Find Copyof
            String copyof = null;
            HardTokenPropertyData htpd = HardTokenPropertyData.findByProperty(entityManager, htd.getTokenSN(), HardTokenPropertyData.PROPERTY_COPYOF);
            if (htpd != null) {
                copyof = htpd.getValue();
            }
            ArrayList<String> copies = null;
            if (copyof == null) {
                // Find Copies
                Collection<HardTokenPropertyData> copieslocal = HardTokenPropertyData.findIdsByPropertyAndValue(entityManager,
                        HardTokenPropertyData.PROPERTY_COPYOF, htd.getTokenSN());
                if (copieslocal.size() > 0) {
                    copies = new ArrayList<String>();
                    Iterator<HardTokenPropertyData> iter = copieslocal.iterator();
                    while (iter.hasNext()) {
                        copies.add(iter.next().getId());
                    }
                }
            }
            returnval.add(new HardTokenData(htd.getTokenSN(), htd.getUsername(), htd.getCreateTime(), htd.getModifyTime(), htd.getTokenType(), htd
                    .getSignificantIssuerDN(), getHardToken(admin, globalConfigurationSession.getCachedGlobalConfiguration(admin).getHardTokenEncryptCA(),
                    includePUK, htd.getData()), copyof, copies));
            String msg = intres.getLocalizedMessage("hardtoken.viewedtoken", htd.getTokenSN());
            logSession.log(admin, htd.getSignificantIssuerDN().hashCode(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), htd.getUsername(), null,
                    LogConstants.EVENT_INFO_HARDTOKENVIEWED, msg);
            if (includePUK) {
                msg = intres.getLocalizedMessage("hardtoken.viewedpuk", htd.getTokenSN());
                logSession.log(admin, htd.getSignificantIssuerDN().hashCode(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(), htd.getUsername(), null,
                        LogConstants.EVENT_INFO_PUKVIEWED, msg);
            }
        }
        Collections.sort(returnval);
        log.trace("<getHardToken()");
        return returnval;
    }

    @Override
    public Collection<String> matchHardTokenByTokenSerialNumber(Admin admin, String searchpattern) {
        log.trace(">findHardTokenByTokenSerialNumber()");
        return org.ejbca.core.ejb.hardtoken.HardTokenData.findUsernamesByHardTokenSerialNumber(entityManager, searchpattern, UserAdminConstants.MAXIMUM_QUERY_ROWCOUNT);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void addHardTokenCertificateMapping(Admin admin, String tokensn, Certificate certificate) {
        String certificatesn = CertTools.getSerialNumberAsString(certificate);
        if (log.isTraceEnabled()) {
            log.trace(">addHardTokenCertificateMapping(certificatesn : " + certificatesn + ", tokensn : " + tokensn + ")");
        }
        int caid = CertTools.getIssuerDN(certificate).hashCode();
        String fp = CertTools.getFingerprintAsString(certificate);
        if (HardTokenCertificateMap.findByCertificateFingerprint(entityManager, fp) == null) {
            try {
                entityManager.persist(new HardTokenCertificateMap(fp, tokensn));
                String msg = intres.getLocalizedMessage("hardtoken.addedtokencertmapping", certificatesn, tokensn);
                logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_INFO_HARDTOKENCERTIFICATEMAP,
                        msg);
            } catch (Exception e) {
                String msg = intres.getLocalizedMessage("hardtoken.erroraddtokencertmapping", certificatesn, tokensn);
                logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_HARDTOKENCERTIFICATEMAP,
                        msg);
            }
        } else {
            String msg = intres.getLocalizedMessage("hardtoken.erroraddtokencertmapping", certificatesn, tokensn);
            logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_HARDTOKENCERTIFICATEMAP, msg);
        }
        log.trace("<addHardTokenCertificateMapping()");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void removeHardTokenCertificateMapping(Admin admin, Certificate certificate) {
        String certificatesn = CertTools.getSerialNumberAsString(certificate);
        if (log.isTraceEnabled()) {
            log.trace(">removeHardTokenCertificateMapping(Certificatesn: " + certificatesn + ")");
        }
        int caid = CertTools.getIssuerDN(certificate).hashCode();
        try {
            HardTokenCertificateMap htcm = HardTokenCertificateMap.findByCertificateFingerprint(entityManager, CertTools.getFingerprintAsString(certificate));
            if (htcm == null) {
            	if (log.isDebugEnabled()) {
            		log.debug("Trying to remove HardTokenCertificateMap that does not exist: "+CertTools.getFingerprintAsString(certificate));                		
            	}
            } else {
            	entityManager.remove(htcm);
            	String msg = intres.getLocalizedMessage("hardtoken.removedtokencertmappingcert", certificatesn);
            	logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_INFO_HARDTOKENCERTIFICATEMAP, msg);
            }
        } catch (Exception e) {
            String msg = intres.getLocalizedMessage("hardtoken.errorremovetokencertmappingcert", certificatesn);
            logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_HARDTOKENCERTIFICATEMAP, msg);
        }
        log.trace("<removeHardTokenCertificateMapping()");
    }

    /**
     * Removes all mappings between a hard token and a certificate.
     * 
     * @param admin the administrator calling the function
     * @param tokensn the serial number to remove.
     */
    private void removeHardTokenCertificateMappings(Admin admin, String tokensn) {
        if (log.isTraceEnabled()) {
            log.trace(">removeHardTokenCertificateMappings(tokensn: " + tokensn + ")");
        }
        int caid = admin.getCaId();
        try {
            Iterator<HardTokenCertificateMap> result = HardTokenCertificateMap.findByTokenSN(entityManager, tokensn).iterator();
            while (result.hasNext()) {
                HardTokenCertificateMap htcm = result.next();
                entityManager.remove(htcm);
            }
            String msg = intres.getLocalizedMessage("hardtoken.removedtokencertmappingtoken", tokensn);
            logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_INFO_HARDTOKENCERTIFICATEMAP, msg);
        } catch (Exception e) {
            String msg = intres.getLocalizedMessage("hardtoken.errorremovetokencertmappingtoken", tokensn);
            logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_HARDTOKENCERTIFICATEMAP, msg);
        }
        log.trace("<removeHardTokenCertificateMappings()");
    }

    @Override
    public Collection<Certificate> findCertificatesInHardToken(Admin admin, String tokensn) {
        if (log.isTraceEnabled()) {
            log.trace("<findCertificatesInHardToken(username :" + tokensn + ")");
        }
        ArrayList<Certificate> returnval = new ArrayList<Certificate>();
        try {
            Iterator<HardTokenCertificateMap> i = HardTokenCertificateMap.findByTokenSN(entityManager, tokensn).iterator();
            while (i.hasNext()) {
                HardTokenCertificateMap htcm = i.next();
                Certificate cert = certificateStoreSession.findCertificateByFingerprint(admin, htcm.getCertificateFingerprint());
                if (cert != null) {
                    returnval.add(cert);
                }
            }
        } catch (Exception e) {
            throw new EJBException(e);
        }
        log.trace("<findCertificatesInHardToken()");
        return returnval;
    }

    @Override
    public String findHardTokenByCertificateSNIssuerDN(Admin admin, BigInteger certificatesn, String issuerdn) {
        if (log.isTraceEnabled()) {
            log.trace("<findHardTokenByCertificateSNIssuerDN(certificatesn :" + certificatesn + ", issuerdn :" + issuerdn + ")");
        }
        String returnval = null;
        X509Certificate cert = (X509Certificate) certificateStoreSession.findCertificateByIssuerAndSerno(admin, issuerdn, certificatesn);
        if (cert != null) {
            HardTokenCertificateMap htcm = HardTokenCertificateMap.findByCertificateFingerprint(entityManager, CertTools.getFingerprintAsString(cert));
            if (htcm != null) {
                returnval = htcm.getTokenSN();
            }
        }
        log.trace("<findHardTokenByCertificateSNIssuerDN()");
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void tokenGenerated(Admin admin, String tokensn, String username, String significantissuerdn) {
        int caid = CertTools.stringToBCDNString(significantissuerdn).hashCode();
        try {
            String msg = intres.getLocalizedMessage("hardtoken.generatedtoken", tokensn);
            logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), username, null, LogConstants.EVENT_INFO_HARDTOKENGENERATED, msg);
        } catch (Exception e) {
            throw new EJBException(e);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void errorWhenGeneratingToken(Admin admin, String tokensn, String username, String significantissuerdn) {
        int caid = CertTools.stringToBCDNString(significantissuerdn).hashCode();
        try {
            String msg = intres.getLocalizedMessage("hardtoken.errorgeneratetoken", tokensn);
            logSession.log(admin, caid, LogConstants.MODULE_HARDTOKEN, new java.util.Date(), username, null, LogConstants.EVENT_ERROR_HARDTOKENGENERATED, msg);
        } catch (Exception e) {
            throw new EJBException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getHardTokenProfileUsingCertificateProfile(int certificateProfileId) {
        List<String> result = new ArrayList<String>();
        Collection<Integer> certprofiles = null;
        HardTokenProfile profile = null;
        for(HardTokenProfileData profileData : HardTokenProfileData.findAll(entityManager)) {
            profile = getHardTokenProfile(profileData);
            if (profile instanceof EIDProfile) {
                certprofiles = ((EIDProfile) profile).getAllCertificateProfileIds();
                if (certprofiles.contains(certificateProfileId)) {
                    result.add(profileData.getName());
                }
            }
        }
        return result;
    }

    @Override
    public boolean existsHardTokenProfileInHardTokenIssuer(Admin admin, int id) {
        HardTokenIssuer issuer = null;
        Collection<Integer> hardtokenissuers = null;
        boolean exists = false;
        Collection<org.ejbca.core.ejb.hardtoken.HardTokenIssuerData> result = org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findAll(entityManager);
        Iterator<org.ejbca.core.ejb.hardtoken.HardTokenIssuerData> i = result.iterator();
        while (i.hasNext() && !exists) {
            issuer = i.next().getHardTokenIssuer();
            hardtokenissuers = issuer.getAvailableHardTokenProfiles();
            if (hardtokenissuers.contains(Integer.valueOf(id))) {
                exists = true;
            }
        }
        return exists;
    }

	private int findFreeHardTokenProfileId() {
		final ProfileID.DB db=new ProfileID.DB() {
			@Override
			public boolean isFree(int i) {
				return HardTokenProfileData.findByPK(entityManager, Integer.valueOf(i))==null;
			}
		};
		return ProfileID.getNotUsedID(db);
	}

	private int findFreeHardTokenIssuerId() {
		final ProfileID.DB db=new ProfileID.DB() {
			@Override
			public boolean isFree(int i) {
				return org.ejbca.core.ejb.hardtoken.HardTokenIssuerData.findByPK(entityManager, Integer.valueOf(i))==null;
			}
		};
		return ProfileID.getNotUsedID(db);
	}

    /** Method that returns the hard token data from a hashmap and updates it if necessary. */
    private HardToken getHardToken(Admin admin, int encryptcaid, boolean includePUK, HashMap data) {
        HardToken returnval = null;

        if (data.get(ENCRYPTEDDATA) != null) {
            // Data in encrypted, decrypt
            byte[] encdata = (byte[]) data.get(ENCRYPTEDDATA);

            HardTokenEncryptCAServiceRequest request = new HardTokenEncryptCAServiceRequest(HardTokenEncryptCAServiceRequest.COMMAND_DECRYPTDATA, encdata);
            try {
                HardTokenEncryptCAServiceResponse response = (HardTokenEncryptCAServiceResponse) caAdminSession.extendedService(admin, encryptcaid, request);
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(response.getData()));
                data = (HashMap) ois.readObject();
            } catch (Exception e) {
                throw new EJBException(e);
            }
        }

        int tokentype = ((Integer) data.get(HardToken.TOKENTYPE)).intValue();

        switch (tokentype) {
        case SecConst.TOKEN_SWEDISHEID:
            returnval = new SwedishEIDHardToken(includePUK);
            break;
        case SecConst.TOKEN_ENHANCEDEID:
            returnval = new EnhancedEIDHardToken(includePUK);
            break;
        case SecConst.TOKEN_TURKISHEID:
            returnval = new TurkishEIDHardToken(includePUK);
            break;
        case SecConst.TOKEN_EID: // Left for backward compability
            returnval = new EIDHardToken(includePUK);
            break;
        default:
            returnval = new EIDHardToken(includePUK);
            break;
        }

        returnval.loadData(data);
        return returnval;
    }

    /**
     * Method that saves the hard token issuer data to a HashMap that can be
     * saved to database.
     */
	private HashMap<String,byte[]> setHardToken(Admin admin, int encryptcaid, HardToken tokendata) {
        HashMap<String,byte[]> retval = null;
        if (encryptcaid != 0) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream ois = new ObjectOutputStream(baos);
                ois.writeObject(tokendata.saveData());
                HardTokenEncryptCAServiceRequest request = new HardTokenEncryptCAServiceRequest(HardTokenEncryptCAServiceRequest.COMMAND_ENCRYPTDATA, baos
                        .toByteArray());
                HardTokenEncryptCAServiceResponse response = (HardTokenEncryptCAServiceResponse) caAdminSession.extendedService(admin, encryptcaid, request);
                HashMap<String,byte[]> data = new HashMap<String,byte[]>();
                data.put(ENCRYPTEDDATA, response.getData());
                retval = data;
            } catch (Exception e) {
                throw new EJBException(e);
            }
        } else {
            // Don't encrypt data
            retval = (HashMap<String,byte[]>) tokendata.saveData();
        }
        return retval;
    }

    private HardTokenProfile getHardTokenProfile(HardTokenProfileData htpData) {
        HardTokenProfile profile = null;
        java.beans.XMLDecoder decoder;
        try {
            decoder = new java.beans.XMLDecoder(new java.io.ByteArrayInputStream(htpData.getData().getBytes("UTF8")));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        HashMap h = (HashMap) decoder.readObject();
        decoder.close();
        // Handle Base64 encoded string values
        HashMap data = new Base64GetHashMap(h);
        switch (((Integer) (data.get(HardTokenProfile.TYPE))).intValue()) {
        case SwedishEIDProfile.TYPE_SWEDISHEID:
            profile = new SwedishEIDProfile();
            break;
        case EnhancedEIDProfile.TYPE_ENHANCEDEID:
            profile = new EnhancedEIDProfile();
            break;
        case TurkishEIDProfile.TYPE_TURKISHEID:
            profile = new TurkishEIDProfile();
            break;
        }
        profile.loadData(data);
        return profile;
    }
}
