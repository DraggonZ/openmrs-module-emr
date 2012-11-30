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

package org.openmrs.module.emr.utils;

import org.apache.commons.lang.StringUtils;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.module.emr.api.EmrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

/**
 * Helper class that lets modules centralize their configuration details. See EmrProperties for an example.
 */
public abstract class ModuleProperties {

    @Autowired
    @Qualifier("conceptService")
    protected ConceptService conceptService;

    @Autowired
    @Qualifier("encounterService")
    protected EncounterService encounterService;

    @Autowired
    @Qualifier("visitService")
    protected VisitService visitService;

    @Autowired
    @Qualifier("orderService")
    protected OrderService orderService;

    @Autowired
    @Qualifier("adminService")
    protected AdministrationService administrationService;

    @Autowired
    @Qualifier("locationService")
    protected LocationService locationService;

    @Autowired
    @Qualifier("userService")
    protected UserService userService;

    @Autowired
    @Qualifier("patientService")
    protected PatientService patientService;

    @Autowired
    @Qualifier("personService")
    protected PersonService personService;
    
    @Autowired
    @Qualifier("emrService")
    protected EmrService emrService;

    public void setConceptService(ConceptService conceptService) {
        this.conceptService = conceptService;
    }

    public void setAdministrationService(AdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    public void setEncounterService(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    public void setOrderService(OrderService orderService) {
        this.orderService = orderService;
    }

    public void setVisitService(VisitService visitService) {
        this.visitService = visitService;
    }

    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void setPatientService(PatientService patientService) {
        this.patientService = patientService;
    }
    
    public void setEmrService(EmrService emrService){
        this.emrService = emrService;
    }

    protected Concept getConceptByGlobalProperty(String globalPropertyName) {
        String globalProperty = administrationService.getGlobalProperty(globalPropertyName);
        Concept concept = conceptService.getConceptByUuid(globalProperty);
        if (concept == null) {
            throw new IllegalStateException("Configuration required: " + globalPropertyName);
        }
        return concept;
    }

    protected EncounterType getEncounterTypeByGlobalProperty(String globalPropertyName) {
        String globalProperty = administrationService.getGlobalProperty(globalPropertyName);
        EncounterType encounterType = encounterService.getEncounterTypeByUuid(globalProperty);
        if (encounterType == null) {
            throw new IllegalStateException("Configuration required: " + globalPropertyName);
        }
        return encounterType;
    }

    protected EncounterRole getEncounterRoleByGlobalProperty(String globalPropertyName) {
        String globalProperty = administrationService.getGlobalProperty(globalPropertyName);
        EncounterRole encounterRole = encounterService.getEncounterRoleByUuid(globalProperty);
        if (encounterRole == null) {
            throw new IllegalStateException("Configuration required: " + globalPropertyName);
        }
        return encounterRole;
    }

    protected VisitType getVisitTypeByGlobalProperty(String globalPropertyName) {
        String globalProperty = administrationService.getGlobalProperty(globalPropertyName);
        VisitType visitType = visitService.getVisitTypeByUuid(globalProperty);
        if (visitType == null) {
            throw new IllegalStateException("Configuration required: " + globalPropertyName);
        }
        return visitType;
    }

    protected OrderType getOrderTypeByGlobalProperty(String globalPropertyName) {
        String globalProperty = administrationService.getGlobalProperty(globalPropertyName);
        OrderType orderType = orderService.getOrderTypeByUuid(globalProperty);
        if (orderType == null) {
            throw new IllegalStateException("Configuration required: " + globalPropertyName);
        }
        return orderType;
    }

    protected PatientIdentifierType getPatientIdentifierTypeByGlobalProperty(String globalPropertyName, boolean required) {
        String globalProperty = getGlobalProperty(globalPropertyName, required);
        PatientIdentifierType patientIdentifierType = GeneralUtils.getPatientIdentifierType(globalProperty, patientService);
        if (required && patientIdentifierType == null) {
            throw new IllegalStateException("Configuration required: " + globalPropertyName);
        }
        return patientIdentifierType;
    }

    private String getGlobalProperty(String globalPropertyName, boolean required) {
        String globalProperty = administrationService.getGlobalProperty(globalPropertyName);
        if (required && StringUtils.isEmpty(globalProperty)) {
            throw new IllegalStateException("Configuration required: " + globalPropertyName);
        }
        return globalProperty;
    }
    
    public List<Location> getAllAvailableLocations(){
        return emrService.getLoginLocations();
    }

}
