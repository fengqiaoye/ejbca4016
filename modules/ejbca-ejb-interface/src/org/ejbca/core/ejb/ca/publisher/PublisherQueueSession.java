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

import java.util.Collection;

import javax.ejb.CreateException;

import org.ejbca.core.model.ca.publisher.BasePublisher;
import org.ejbca.core.model.ca.publisher.PublisherQueueData;
import org.ejbca.core.model.ca.publisher.PublisherQueueVolatileData;
import org.ejbca.core.model.log.Admin;

/**
 * @version $Id: PublisherQueueSession.java 11337 2011-02-10 22:37:15Z jeklund $
 * @author mikek
 */
public interface PublisherQueueSession {

	/**
     * Adds an entry to the publisher queue.
	 *
	 * @param publisherId the publisher that this should be published to
	 * @param publishType the type of entry it is, {@link PublisherQueueData#PUBLISH_TYPE_CERT} or CRL
     * @throws CreateException if the entry can not be created
     */
    public void addQueueData(int publisherId, int publishType, String fingerprint,
            PublisherQueueVolatileData queueData, int publishStatus) throws CreateException;

    /** Removes an entry from the publisher queue. */
    public void removeQueueData(java.lang.String pk);

    /**
     * Finds all entries with status PublisherQueueData.STATUS_PENDING for a
     * specific publisherId.
     * 
     * @return Collection of PublisherQueueData, never null
     */
    public Collection<PublisherQueueData> getPendingEntriesForPublisher(int publisherId);

    /**
     * Gets the number of pending entries for a publisher.
     * @param publisherId The publisher to count the number of pending entries for.
     * @return The number of pending entries.
     */
    public int getPendingEntriesCountForPublisher(int publisherId);

    /**
     * Gets an array with the number of new pending entries for a publisher in each intervals specified by 
     * <i>lowerBounds</i> and <i>upperBounds</i>. 
     * 
     * The interval is defined as from lowerBounds[i] to upperBounds[i] and the unit is seconds from now. 
     * A negative value results in no boundary.
     * 
     * @param publisherId The publisher to count the number of pending entries for.
     * @return Array with the number of pending entries corresponding to each element in <i>interval</i>.
     */
    public int[] getPendingEntriesCountForPublisherInIntervals(int publisherId, int[] lowerBounds, int[] upperBounds);

    /**
     * Finds all entries with status PublisherQueueData.STATUS_PENDING for a
     * specific publisherId.
     * 
     * @param orderBy
     *            order by clause for the SQL to the database, for example
     *            "order by timeCreated desc".
     * @return Collection of PublisherQueueData, never null
     */
    public Collection<PublisherQueueData> getPendingEntriesForPublisherWithLimit(int publisherId, int limit, int timeout, String orderBy);

    /**
     * Finds all entries for a specific fingerprint.
     * 
     * @return Collection of PublisherQueueData, never null
     */
    public Collection<PublisherQueueData> getEntriesByFingerprint(String fingerprint);

    /**
     * Updates a record with new status
     * 
     * @param pk primary key of data entry
     * @param status status from PublisherQueueData.STATUS_SUCCESS etc, or -1 to not update status
     * @param tryCounter an updated try counter, or -1 to not update counter
     */
    public void updateData(java.lang.String pk, int status, int tryCounter);

	/**
	 * Intended for use from PublishQueueProcessWorker.
	 * 
	 * Publishing algorithm that is a plain fifo queue, but limited to selecting entries to republish at 100 records at a time. It will select from the database for this particular publisher id, and process 
	 * the record that is returned one by one. The records are ordered by date, descending so the oldest record is returned first. 
	 * Publishing is tried every time for every record returned, with no limit.
     * Repeat this process as long as we actually manage to publish something this is because when publishing starts to work we want to publish everything in one go, if possible.
     * However we don't want to publish more than 20000 certificates each time, because we want to commit to the database some time as well.
     * Now, the OCSP publisher uses a non-transactional data source so it commits every time so...
	 */
    public void plainFifoTryAlwaysLimit100EntriesOrderByTimeCreated(Admin admin, int publisherId, BasePublisher publisher);
}
