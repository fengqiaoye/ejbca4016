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
package org.cesecore.core.ejb.ra.raadmin;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileExistsException;

/** Session bean for managing End Entity Profiles.
 * 
 * @version $Id: EndEntityProfileSession.java 15212 2012-08-06 12:05:13Z mikekushner $
 */
public interface EndEntityProfileSession {

    static final String EMPTY_ENDENTITYPROFILENAME = "EMPTY";

    /**
     * Adds a profile to the database.
     * 
     * @param admin administrator performing task
     * @param profilename readable profile name
     * @param profile profile to be added
     */
    void addEndEntityProfile(Admin admin, String profilename, EndEntityProfile profile) throws EndEntityProfileExistsException;

    /**
     * Adds a profile to the database.
     * 
     * @param admin administrator performing task
     * @param profileid internal ID of new profile, use only if you know it's
     *            right.
     * @param profilename readable profile name
     * @param profile profile to be added
     */
    void addEndEntityProfile(Admin admin, int profileid, String profilename, EndEntityProfile profile) throws EndEntityProfileExistsException;

    /**
     * Adds a end entity profile to a group with the same content as the
     * original profile.
     */
    void cloneEndEntityProfile(Admin admin, String originalprofilename, String newprofilename) throws EndEntityProfileExistsException;

    /** @return an unused end id number. */
    int findFreeEndEntityProfileId();

    /**
     * Removes an end entity profile from the database, does not throw any
     * errors if the profile does not exist.
     */
    void removeEndEntityProfile(Admin admin, String profilename);

    /** Renames a end entity profile. */
    void renameEndEntityProfile(Admin admin, String oldprofilename, String newprofilename) throws EndEntityProfileExistsException;

    /** Updates profile data. */
    void changeEndEntityProfile(Admin admin, String profilename, EndEntityProfile profile);

    /**
     * Do NOT use, use changeEndEntityProfile instead. Used internally for
     * testing only. Updates a profile without flushing caches.
     */
    void internalChangeEndEntityProfileNoFlushCache(Admin admin, String profilename, EndEntityProfile profile);

    /** Retrieves a Collection of id:s (Integer) to authorized profiles. */
    Collection<Integer> getAuthorizedEndEntityProfileIds(Admin admin);

    /** @return mapping of profile id (Integer) to profile name (String). */
    Map<Integer, String> getEndEntityProfileIdToNameMap(Admin admin);

    /** Clear and reload end entity profile caches. */
    void flushProfileCache();

    /**
     * Finds a end entity profile by id.
     * @return EndEntityProfile (cloned) or null if it does not exist
     */
    EndEntityProfile getEndEntityProfile(Admin admin, int id);

    /**
     * Get a copy of an EndEntityProfile.
     * @return EndEntityProfile (cloned) or null if it does not exist
     */
    EndEntityProfile getEndEntityProfile(Admin admin, java.lang.String profilename);

    /**
     * Returns a end entity profiles id, given it's profilename
     * @return the id or 0 if profile cannot be found.
     */
    int getEndEntityProfileId(Admin admin, java.lang.String profilename);

    /**
     * Returns a end entity profiles name given it's id.
     * @return profile's name or null if profile id doesn't exists.
     */
    String getEndEntityProfileName(Admin admin, int id);

    /**
     * Method to check if a CertificateProfile exists in any of the end entity
     * profiles. Used to avoid desynchronization of certificate profile data.
     * 
     * @param certificateprofileid the CertificateProfile's id to search for.
     * @return  a collection of EndEntityProfile names using the sought CertificateProfile
     */
    List<String> getEndEntityProfilesUsingCertificateProfile(int certificateprofileid);

    /**
     * Method to check if a CA exists in any of the end entity profiles. Used to
     * avoid desynchronization of CA data.
     * 
     * @param caid the caid to search for.
     * @return true if CA exists in any of the end entity profiles.
     */
    boolean existsCAInEndEntityProfiles(Admin admin, int caid);

    /**
     * A method designed to be called at startup time to (possibly) upgrade end
     * entity profiles. This method will read all End Entity Profiles and as a
     * side-effect upgrade them if the version if changed for upgrade. Can have
     * a side-effect of upgrading a profile, therefore the Required transaction
     * setting.
     * 
     * @param admin administrator calling the method
     */
    void initializeAndUpgradeProfiles(Admin admin);

}
