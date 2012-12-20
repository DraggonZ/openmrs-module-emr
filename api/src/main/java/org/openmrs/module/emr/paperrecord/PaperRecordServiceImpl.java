/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.emr.paperrecord;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.emr.EmrProperties;
import org.openmrs.module.emr.paperrecord.db.PaperRecordMergeRequestDAO;
import org.openmrs.module.emr.paperrecord.db.PaperRecordRequestDAO;
import org.openmrs.module.emr.printer.Printer;
import org.openmrs.module.emr.printer.PrinterService;
import org.openmrs.module.emr.utils.GeneralUtils;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.openmrs.module.emr.paperrecord.PaperRecordRequest.PENDING_STATUSES;
import static org.openmrs.module.emr.paperrecord.PaperRecordRequest.Status;

public class PaperRecordServiceImpl extends BaseOpenmrsService implements PaperRecordService {

    private final Log log = LogFactory.getLog(getClass());

    private PaperRecordRequestDAO paperRecordRequestDAO;

    private PaperRecordMergeRequestDAO paperRecordMergeRequestDAO;

    private AdministrationService administrationService;

    private PatientService patientService;

    private MessageSourceService messageSourceService;

    private IdentifierSourceService identifierSourceService;

    private PrinterService printerService;

    private EmrProperties emrProperties;

    private PaperRecordLabelTemplate paperRecordLabelTemplate;


    public void setPaperRecordRequestDAO(PaperRecordRequestDAO paperRecordRequestDAO) {
        this.paperRecordRequestDAO = paperRecordRequestDAO;
    }

    public void setPaperRecordMergeRequestDAO(PaperRecordMergeRequestDAO paperRecordMergeRequestDAO) {
        this.paperRecordMergeRequestDAO = paperRecordMergeRequestDAO;
    }

    public void setMessageSourceService(MessageSourceService messageSourceService) {
        this.messageSourceService = messageSourceService;
    }
    
    public void setAdministrationService(AdministrationService administrationService) {
    	this.administrationService = administrationService;
    }

    public void setIdentifierSourceService(IdentifierSourceService identifierSourceService) {
        this.identifierSourceService = identifierSourceService;
    }

    public void setPatientService(PatientService patientService) {
        this.patientService = patientService;
    }

    public void setPrinterService(PrinterService printerService) {
        this.printerService = printerService;
    }

    public void setEmrProperties(EmrProperties emrProperties) {
        this.emrProperties = emrProperties;
    }

    public void setPaperRecordLabelTemplate(PaperRecordLabelTemplate paperRecordLabelTemplate) {
        this.paperRecordLabelTemplate = paperRecordLabelTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public PaperRecordRequest getPaperRecordRequestById(Integer id) {
        return paperRecordRequestDAO.getById(id);
    }

    @Override
    public PaperRecordMergeRequest getPaperRecordMergeRequestById(Integer id) {
       return paperRecordMergeRequestDAO.getById(id);
    }

    @Override
    @Transactional
    public PaperRecordRequest requestPaperRecord(Patient patient, Location recordLocation, Location requestLocation) {

        // TODO: we will have to handle the case if there is already a request for this patient's record in the "SENT" state
        // TODO: (ie, what to do if the record is already out on the floor--right now it will just create a new request)

        if (patient == null) {
            throw new IllegalStateException("Patient cannot be null");
        }

        if (recordLocation == null) {
            throw new IllegalStateException("Record Location cannot be null");
        }

        if (requestLocation == null) {
            throw new IllegalStateException("Request Location cannot be null");
        }

        // see if there is an pending request for this patient at this location
        List<PaperRecordRequest> requests = paperRecordRequestDAO.findPaperRecordRequests(PENDING_STATUSES, patient, recordLocation, null, null);

        if (requests.size() > 1) {
            // this should not be allowed, but it could possibility happen if you merge two patients that both have
            // open paper record requests for the same location; we should fix this when we handle story #186
            log.warn("Duplicate pending record requests exist for patient " + patient);
        }

        // if an pending records exists, simply update that request location, don't issue a new request
        if (requests.size() > 0) {   // TODO: change this to size() == 1 once we  implement story #186 and can guarantee that there won't be multiple requests (see comment above)
            PaperRecordRequest request = requests.get(0);
            request.setRequestLocation(requestLocation);
            paperRecordRequestDAO.saveOrUpdate(request);
            return request;
        }

        // if no pending record exists, create a new request
        // fetch the appropriate identifier (if it exists)
        PatientIdentifier paperRecordIdentifier = GeneralUtils.getPatientIdentifier(patient, emrProperties.getPaperRecordIdentifierType(), recordLocation);
        String identifier = paperRecordIdentifier != null ? paperRecordIdentifier.getIdentifier() : null;

        PaperRecordRequest request = new PaperRecordRequest();
        request.setCreator(Context.getAuthenticatedUser());
        request.setDateCreated(new Date());
        request.setIdentifier(identifier);
        request.setRecordLocation(recordLocation);
        request.setPatient(patient);
        request.setRequestLocation(requestLocation);

        paperRecordRequestDAO.saveOrUpdate(request);

        return request;
    }

    @Override
    @Transactional
    public PaperRecordRequest savePaperRecordRequest(PaperRecordRequest paperRecordRequest) {
	    PaperRecordRequest request =null;
    	if(paperRecordRequest!=null){
    		return paperRecordRequestDAO.saveOrUpdate(paperRecordRequest);
    	}
	    return request;
    }

	@Override
    @Transactional(readOnly = true)
    public List<PaperRecordRequest> getOpenPaperRecordRequestsToPull() {
        // TODO: once we have multiple medical record locations, we will need to add location as a criteria
        return paperRecordRequestDAO.findPaperRecordRequests(Collections.singletonList(PaperRecordRequest.Status.OPEN), null, null, null, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaperRecordRequest> getOpenPaperRecordRequestsToCreate() {
        // TODO: once we have multiple medical record locations, we will need to add location as a criteria
        return paperRecordRequestDAO.findPaperRecordRequests(Collections.singletonList(PaperRecordRequest.Status.OPEN), null, null, null, false);
    }

    @Override
    @Transactional
    public synchronized List<PaperRecordRequest> assignRequests(List<PaperRecordRequest> requests, Person assignee, Location location) {

        if (requests == null) {
            throw new IllegalStateException("Requests cannot be null");
        }

        if (assignee == null) {
            throw new IllegalStateException("Assignee cannot be null");
        }

        // first verify that all of these requests are open, or else we can't assign them
        for (PaperRecordRequest request : requests) {
            if (request.getStatus() != PaperRecordRequest.Status.OPEN) {
                throw new IllegalStateException("Cannot assign a request that is not open");
            }
        }

        for (PaperRecordRequest request : requests) {
             // first do a sanity check, in case an identifier has been created since the request was placed
            if (StringUtils.isBlank(request.getIdentifier())) {
               request.setIdentifier(getPaperMedicalRecordNumberFor(request.getPatient(), request.getRecordLocation()));
            }

            // if there is still no identifier, assign an identifier and mark it as to create, otherwise mark to pull
            if (StringUtils.isBlank(request.getIdentifier())) {
                String identifier = createPaperMedicalRecordNumberFor(request.getPatient(), request.getRecordLocation());
                request.setIdentifier(identifier);
                request.updateStatus(PaperRecordRequest.Status.ASSIGNED_TO_CREATE);

                // TODO: do we want to make printing a label configurable via a global property?
                printPaperRecordLabel(request, location);
            }
            else {
                request.updateStatus(PaperRecordRequest.Status.ASSIGNED_TO_PULL);
            }

            // set the assignee and save the record
            request.setAssignee(assignee);
            paperRecordRequestDAO.saveOrUpdate(request);
        }

        return requests;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaperRecordRequest> getAssignedPaperRecordRequestsToPull() {
        // TODO: once we have multiple medical record locations, we will need to add location as a criteria
        return paperRecordRequestDAO.findPaperRecordRequests(Collections.singletonList(PaperRecordRequest.Status.ASSIGNED_TO_PULL), null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaperRecordRequest> getAssignedPaperRecordRequestsToCreate() {
        // TODO: once we have multiple medical record locations, we will need to add location as a criteria
        return paperRecordRequestDAO.findPaperRecordRequests(Collections.singletonList(PaperRecordRequest.Status.ASSIGNED_TO_CREATE), null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaperRecordRequest> getPaperRecordRequestsByPatient(Patient patient) {
	    return paperRecordRequestDAO.findPaperRecordRequests(null, patient, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PaperRecordRequest getPendingPaperRecordRequestByIdentifier(String identifier) {
        // TODO: once we have multiple medical record locations, we will need to add location as a criteria
        List<PaperRecordRequest> requests = paperRecordRequestDAO.findPaperRecordRequests(PENDING_STATUSES, null, null, identifier, null);

        if (requests == null || requests.size() == 0) {
            return null;
        }
        else if (requests.size() > 1) {
            throw new IllegalStateException("Duplicate pending record requests exist with identifier " + identifier);
        }
        else {
            return requests.get(0);
        }

    }

    @Override
    @Transactional(readOnly = true)
    public PaperRecordRequest getSentPaperRecordRequestByIdentifier(String identifier) {
        // TODO: once we have multiple medical record locations, we will need to add location as a criteria
        List<PaperRecordRequest> requests = paperRecordRequestDAO.findPaperRecordRequests(Collections.singletonList(Status.SENT), null, null, identifier, null);

        if (requests == null || requests.size() == 0) {
            return null;
        }
        else if (requests.size() > 1) {
            throw new IllegalStateException("Duplicate sent record requests exist with identifier " + identifier);
        }
        else {
            return requests.get(0);
        }
    }

    @Override
    @Transactional
    public void markPaperRecordRequestAsSent(PaperRecordRequest request) {
        // I don't think we really need to do any verification here
        request.updateStatus(Status.SENT);
        savePaperRecordRequest(request);
    }

    @Override
    public Boolean printPaperRecordLabel(PaperRecordRequest request, Location location) {
        String data = paperRecordLabelTemplate.generateLabel(request);
        String encoding = paperRecordLabelTemplate.getEncoding();
        return printerService.printViaSocket(data, Printer.Type.LABEL, location, encoding);
    }

    @Override
    @Transactional
    public void markPaperRecordsForMerge(PatientIdentifier preferredIdentifier, PatientIdentifier notPreferredIdentifier) {

        if (!preferredIdentifier.getIdentifierType().equals(emrProperties.getPaperRecordIdentifierType())
                || !notPreferredIdentifier.getIdentifierType().equals(emrProperties.getPaperRecordIdentifierType())) {
            throw new IllegalArgumentException("One of the passed identifiers is not a paper record identifier: "
                    + preferredIdentifier + ", " + notPreferredIdentifier);
        }

        if (!preferredIdentifier.getLocation().equals(notPreferredIdentifier.getLocation())) {
            throw new IllegalArgumentException("Cannot merge two records from different locations: "
                    + preferredIdentifier + ", " + notPreferredIdentifier);
        }

        // create the request
        PaperRecordMergeRequest mergeRequest = new PaperRecordMergeRequest();
        mergeRequest.setStatus(PaperRecordMergeRequest.Status.OPEN);
        mergeRequest.setPreferredPatient(preferredIdentifier.getPatient());
        mergeRequest.setNotPreferredPatient(notPreferredIdentifier.getPatient());
        mergeRequest.setPreferredIdentifier(preferredIdentifier.getIdentifier());
        mergeRequest.setNotPreferredIdentifier(notPreferredIdentifier.getIdentifier());
        mergeRequest.setRecordLocation(preferredIdentifier.getLocation());
        mergeRequest.setCreator(Context.getAuthenticatedUser());
        mergeRequest.setDateCreated(new Date());

        paperRecordMergeRequestDAO.saveOrUpdate(mergeRequest);

        // void the non-preferred identifier; we do this now (instead of when the merge is confirmed)
        // so that all new requests for records for this patient use the right identifier
        patientService.voidPatientIdentifier(notPreferredIdentifier, "voided during paper record merge");
    }

    @Override
    @Transactional
    public void markPaperRecordsAsMerged(PaperRecordMergeRequest mergeRequest) {

        // merge any pending paper record requests associated with the two records we are merging
        mergePendingPaperRecordRequests(mergeRequest);

        // if the archivist has just merged the records, we should be able to safely close out
        // any request for the not preferred record, as this record should no longer exist
        closeOutSentPaperRecordRequestsForNotPreferredRecord(mergeRequest);

        // the just mark the request as merged
        mergeRequest.setStatus(PaperRecordMergeRequest.Status.MERGED);
        paperRecordMergeRequestDAO.saveOrUpdate(mergeRequest);
    }

    @Override
    public List<PaperRecordMergeRequest> getOpenPaperRecordMergeRequests() {
        return paperRecordMergeRequestDAO.findPaperRecordMergeRequest(Collections.singletonList(PaperRecordMergeRequest.Status.OPEN));
    }

    // leaving this method as public so that it can be tested by integration test in mirebalais module
    public String createPaperMedicalRecordNumberFor(Patient patient, Location medicalRecordLocation) {
        if (patient == null){
            throw new IllegalArgumentException("Patient shouldn't be null");
        }

        if (StringUtils.isNotBlank(getPaperMedicalRecordNumberFor(patient, medicalRecordLocation))) {
            // TODO: we probably want to actually throw an exception here, but we should wait until this method is removed from patient registration and made protected
            //throw new IllegalStateException("Cannot create paper record number for patient.  Paper record number already exists for patient:" + patient);
            return "";
        }

        PatientIdentifierType paperRecordIdentifierType = emrProperties.getPaperRecordIdentifierType();
        String paperRecordId = "";

        paperRecordId = identifierSourceService.generateIdentifier(paperRecordIdentifierType, "generating a new dossier number");
        PatientIdentifier paperRecordIdentifier = new PatientIdentifier(paperRecordId, paperRecordIdentifierType, medicalRecordLocation);
        patient.addIdentifier(paperRecordIdentifier);
        patientService.savePatientIdentifier(paperRecordIdentifier);

        return paperRecordId;
    }

    protected String getPaperMedicalRecordNumberFor(Patient patient, Location medicalRecordLocation) {
        PatientIdentifier paperRecordIdentifier = GeneralUtils.getPatientIdentifier(patient,
                emrProperties.getPaperRecordIdentifierType(), medicalRecordLocation);
        return paperRecordIdentifier != null ? paperRecordIdentifier.getIdentifier() : "";
    }

    private void mergePendingPaperRecordRequests(PaperRecordMergeRequest mergeRequest) {

        // (note that we are not searching by patient here because the patient may have been changed during the merge)
        List<PaperRecordRequest> preferredRequests = paperRecordRequestDAO.findPaperRecordRequests(PENDING_STATUSES,
                null, mergeRequest.getRecordLocation(), mergeRequest.getPreferredIdentifier(), null);

        if (preferredRequests.size() > 1) {
            throw new IllegalStateException("Duplicate pending record requests exist with identifier " + mergeRequest.getPreferredIdentifier());
        }

        List<PaperRecordRequest> notPreferredRequests = paperRecordRequestDAO.findPaperRecordRequests(PENDING_STATUSES,
                null, mergeRequest.getRecordLocation(), mergeRequest.getNotPreferredIdentifier(), null);

        if (notPreferredRequests.size() > 1) {
            throw new IllegalStateException("Duplicate pending record requests exist with identifier " + mergeRequest.getNotPreferredIdentifier());
        }

        PaperRecordRequest preferredRequest = null;
        PaperRecordRequest notPreferredRequest = null;

        if (preferredRequests.size() == 1) {
           preferredRequest = preferredRequests.get(0);
        }

        if (notPreferredRequests.size() == 1) {
            notPreferredRequest = notPreferredRequests.get(0);
        }

        // if both the preferred and not-preferred records have a request, we need to
        // cancel on of them
        if (preferredRequest != null && notPreferredRequest != null) {
            // update the request location if the non-preferred  is more recent
            if (notPreferredRequest.getDateCreated().after(preferredRequest.getDateCreated())) {
                preferredRequest.setRequestLocation(notPreferredRequest.getRequestLocation());
            }

            notPreferredRequest.updateStatus(Status.CANCELLED);
            paperRecordRequestDAO.saveOrUpdate(preferredRequest);
            paperRecordRequestDAO.saveOrUpdate(notPreferredRequest);
        }

        // if there is only a non-preferred request, we need to update it with the right identifier
        if (preferredRequest == null && notPreferredRequest != null) {
            notPreferredRequest.setIdentifier(mergeRequest.getPreferredIdentifier());
            paperRecordRequestDAO.saveOrUpdate(notPreferredRequest);
        }

    }

    private void closeOutSentPaperRecordRequestsForNotPreferredRecord(PaperRecordMergeRequest mergeRequest) {
        List<PaperRecordRequest> notPreferredRequests = paperRecordRequestDAO.findPaperRecordRequests(Collections.singletonList(Status.SENT), null,
                mergeRequest.getRecordLocation(), mergeRequest.getNotPreferredIdentifier(), null);

        for (PaperRecordRequest notPreferredRequest : notPreferredRequests) {
            notPreferredRequest.updateStatus(Status.RETURNED);
            paperRecordRequestDAO.saveOrUpdate(notPreferredRequest);
        }
    }

}
